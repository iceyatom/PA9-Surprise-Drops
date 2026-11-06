package com.iceyatom.surprisedrops;

import com.iceyatom.surprisedrops.mixin.LootContextAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replaces loot-table output (Section 7.1). Registered on Fabric API's
 * {@code LootTableEvents.MODIFY_DROPS}, which receives every generated item list — block drops,
 * mob drops, container loot, fishing and the rest — before vanilla spawns or stores it.
 */
public final class LootReplacer {
	private static final int MAX_CONSECUTIVE_FAILURES = 3;

	/**
	 * Sorted item IDs (FR-17): every allowed item, and the survival-obtainable subset. Each world
	 * picks one with its survival-items-only setting. Rebuilt on start and datapack reload only (NFR-01).
	 */
	private static volatile List<String> fullPool;
	private static volatile List<String> survivalPool;
	private static volatile Map<String, Item> poolItems = Map.of();
	/** Every loot table Surprise Drops can change (any category), sorted. Rebuilt with the pools. */
	private static volatile List<String> tableIds = List.of();
	/** Table-to-item assignments for the last seed and pool used; recomputed when either changes. */
	private static volatile Assignments assignments;

	private record Assignments(long seed, List<String> pool, List<String> tables, Map<String, Item> items) {
	}

	private static final AtomicInteger consecutiveFailures = new AtomicInteger();
	private static volatile boolean sessionDisabled;

	private LootReplacer() {
	}

	/**
	 * Rebuilds both replacement pools from the item registry and the exclusion list (FR-17, NFR-10),
	 * and the list of loot tables from the server's loaded data packs.
	 */
	public static synchronized void rebuildPool(MinecraftServer server) {
		if (server != null) {
			List<String> tables = new ArrayList<>();
			server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElements().forEach(holder -> {
				if (DropCategory.of(holder.value().getParamSet()) != null) {
					tables.add(holder.key().identifier().toString());
				}
			});
			tables.sort(null);
			tableIds = List.copyOf(tables);
		}
		rebuildItemPools();
	}

	private static synchronized void rebuildItemPools() {
		SurpriseDropsConfig config = SurpriseDropsConfig.get();
		Map<String, Item> items = new HashMap<>();
		for (Item item : BuiltInRegistries.ITEM) {
			items.put(BuiltInRegistries.ITEM.getKey(item).toString(), item);
		}
		HashSet<String> excluded = new HashSet<>(config.excludedItems);
		List<String> full = SurpriseDropsHelper.buildPool(items.keySet(), excluded, false);
		List<String> survival = SurpriseDropsHelper.buildPool(items.keySet(), excluded, true);
		Map<String, Item> map = new HashMap<>(full.size() * 2);
		for (String id : full) {
			map.put(id, items.get(id));
		}
		poolItems = map;
		fullPool = full;
		survivalPool = survival;
		assignments = null;
		SpecialItems.clearCache();
		SurpriseDrops.LOGGER.info("Surprise Drops replacement pool built: {} survival items, {} items in total, {} loot tables",
				survival.size(), full.size(), tableIds.size());
	}

	/** The pool a world with this setting draws from. */
	public static List<String> pool(boolean survivalItemsOnly) {
		if (fullPool == null) {
			rebuildItemPools();
		}
		return survivalItemsOnly ? survivalPool : fullPool;
	}

	/** Called at server start: a new session clears the failure state (NFR-04). */
	public static void resetSession() {
		consecutiveFailures.set(0);
		sessionDisabled = false;
	}

	public static boolean isSessionDisabled() {
		return sessionDisabled;
	}

	/** The item assigned to a loot table under these settings (FR-15), or {@code null} if the pool is empty. */
	public static Item assignedItem(DropSettings settings, Identifier lootTableId) {
		List<String> pool = pool(settings.survivalItemsOnly());
		Item item = assignments(settings.seed(), pool).items().get(lootTableId.toString());
		if (item != null) {
			return item;
		}
		// A table missing from the list (none expected): fall back to a single hashed pick.
		String id = SurpriseDropsHelper.assignedItem(settings.seed(), lootTableId.toString(), pool);
		return id == null ? null : poolItems.get(id);
	}

	/** Each table gets its own item (see {@link SurpriseDropsHelper#assignAll}); cached per seed and pool. */
	private static Assignments assignments(long seed, List<String> pool) {
		Assignments current = assignments;
		List<String> tables = tableIds;
		if (current != null && current.seed() == seed && current.pool() == pool && current.tables() == tables) {
			return current;
		}
		synchronized (LootReplacer.class) {
			current = assignments;
			if (current != null && current.seed() == seed && current.pool() == pool && current.tables() == tables) {
				return current;
			}
			Map<String, Item> items = new HashMap<>(tables.size() * 2);
			Map<String, Item> lookup = poolItems;
			SurpriseDropsHelper.assignAll(seed, tables, pool).forEach((table, item) -> items.put(table, lookup.get(item)));
			current = new Assignments(seed, pool, tables, Map.copyOf(items));
			assignments = current;
			return current;
		}
	}

	/** The {@code MODIFY_DROPS} listener. */
	public static void onModifyDrops(Holder<LootTable> table, LootContext context, List<ItemStack> drops) {
		// Cheap early returns first: no hashing while OFF or while the category is disabled (NFR-02).
		if (sessionDisabled || drops.isEmpty()) {
			return; // FR-20: an empty vanilla roll stays empty.
		}
		MinecraftServer server = context.getLevel().getServer();
		DropSettings settings = SurpriseDrops.settings(server);
		if (settings == null || !settings.enabled()) {
			return; // FR-05
		}
		DropCategory category = DropCategory.of(table.value().getParamSet());
		if (category == null || !settings.affects(category)) {
			return; // FR-24
		}
		Optional<ResourceKey<LootTable>> key = table.unwrapKey();
		if (key.isEmpty()) {
			return; // inline table with no ID: nothing to assign against (FR-13)
		}
		if (!((LootContextAccessor) context).surprisedrops$getVisitedElements().isEmpty()) {
			return; // nested table: the outer table's roll is replaced instead
		}

		List<ItemStack> vanilla = List.copyOf(drops);
		try {
			replace(key.get().identifier(), context, drops, settings);
			consecutiveFailures.set(0);
		} catch (Throwable t) {
			// NFR-04: fall back to the vanilla result for this roll.
			drops.clear();
			drops.addAll(vanilla);
			onFailure(server, key.get().identifier(), t);
		}
	}

	private static void replace(Identifier tableId, LootContext context, List<ItemStack> drops, DropSettings settings) {
		// NFR-05: never replace a drop that carries a player's stored items (e.g. a filled shulker box).
		if (carriesContents(drops)) {
			return;
		}
		Item item = assignedItem(settings, tableId);
		if (item == null) {
			return;
		}
		int base = 0;
		for (ItemStack stack : drops) {
			base += stack.getCount();
		}
		if (base <= 0) {
			return;
		}
		SurpriseDropsConfig config = SurpriseDropsConfig.get();
		int multiplier = SurpriseDropsHelper.rollCount(settings.multiplierMin(), settings.multiplierMax(), context.getRandom());
		int count = SurpriseDropsHelper.replacementCount(base, multiplier);
		// Maps, potions, enchanted books and similar items need data to work; one prepared copy is
		// shared by every stack of this drop. The item may change (an explorer map with no structure
		// in range becomes an empty map).
		ItemStack template = SpecialItems.prepare(item, context);
		int maxStack = template.getMaxStackSize();
		int[] sizes = SurpriseDropsHelper.splitStacks(count, maxStack, config.maxUnstackableCount);

		drops.clear();
		for (int size : sizes) {
			drops.add(template.copyWithCount(size));
		}
		if (config.debugLogging) {
			SurpriseDrops.LOGGER.info("[debug] {} -> {} : base {} x {} = {} in {} stack(s){}", tableId,
					BuiltInRegistries.ITEM.getKey(template.getItem()), base, multiplier, count, sizes.length,
					maxStack <= 1 && count > sizes.length ? " (clamped by max_unstackable_count)" : "");
		}
	}

	private static boolean carriesContents(List<ItemStack> drops) {
		for (ItemStack stack : drops) {
			ItemContainerContents container = stack.get(DataComponents.CONTAINER);
			if (container != null && container.nonEmptyItems().iterator().hasNext()) {
				return true;
			}
			BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
			if (bundle != null && !bundle.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static void onFailure(MinecraftServer server, Identifier tableId, Throwable t) {
		int failures = consecutiveFailures.incrementAndGet();
		SurpriseDrops.LOGGER.warn("Surprise Drops failed to replace loot for {} ({} in a row); vanilla drops kept", tableId, failures, t);
		boolean disable = failures >= MAX_CONSECUTIVE_FAILURES;
		if (disable) {
			sessionDisabled = true;
			SurpriseDrops.LOGGER.warn("Surprise Drops disabled for the rest of this session after {} consecutive failures", failures);
		}
		Component message = Component.translatable(disable ? "surprisedrops.message.failure_disabled" : "surprisedrops.message.failure",
				tableId.toString()).withStyle(ChatFormatting.RED);
		server.execute(() -> SurpriseDropsServer.notifyOperators(server, message));
	}
}
