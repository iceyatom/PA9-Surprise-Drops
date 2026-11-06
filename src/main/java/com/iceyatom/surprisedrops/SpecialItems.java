package com.iceyatom.surprisedrops;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Some items only work with extra data that vanilla adds when it generates them: a filled map
 * needs a map, an explorer map needs a structure to point at, a potion needs a potion type, an
 * enchanted book needs an enchantment. A bare {@code new ItemStack(item)} of these is a broken item
 * (a blank map that does nothing, an "Uncraftable Potion", an enchanted book with no enchantment)
 * that survival players can never get. This class fills that data in the same way vanilla loot does,
 * reusing vanilla's own loot functions where possible.
 */
final class SpecialItems {
	/**
	 * Explorer map items and the vanilla {@code exploration_map} settings that create them, copied
	 * from the vanilla loot tables and cartographer trades that hand them out.
	 */
	private static final Map<String, String> EXPLORER_MAPS = Map.ofEntries(
			explorer("abandoned_camp_map", "#minecraft:abandoned_camp", "minecraft:abandoned_camp"),
			explorer("buried_ancient_city_map", "#minecraft:on_ancient_city_maps", "minecraft:ancient_city"),
			explorer("buried_mineshaft_map", "#minecraft:on_mineshaft_maps", "minecraft:mineshaft"),
			Map.entry("minecraft:buried_treasure_map", """
					{"type": "minecraft:exploration_map", "destination": "#minecraft:on_treasure_maps",
					 "decoration": "minecraft:red_x", "zoom": 1, "skip_existing_chunks": false}"""),
			explorer("buried_trial_chambers_map", "#minecraft:on_buried_trial_chambers_maps", "minecraft:trial_chambers"),
			explorer("desert_pyramid_map", "#minecraft:on_desert_pyramid_maps", "minecraft:desert_pyramid"),
			explorer("desert_village_map", "#minecraft:on_desert_village_maps", "minecraft:village_desert"),
			explorer("jungle_pyramid_map", "#minecraft:on_jungle_pyramid_maps", "minecraft:jungle_temple"),
			explorer("ocean_monument_map", "#minecraft:on_ocean_monument_maps", "minecraft:monument"),
			explorer("plains_village_map", "#minecraft:on_plains_village_maps", "minecraft:village_plains"),
			explorer("savanna_village_map", "#minecraft:on_savanna_village_maps", "minecraft:village_savanna"),
			explorer("snowy_village_map", "#minecraft:on_snowy_village_maps", "minecraft:village_snowy"),
			explorer("swamp_hut_map", "#minecraft:on_swamp_hut_maps", "minecraft:swamp_hut"),
			explorer("taiga_village_map", "#minecraft:on_taiga_village_maps", "minecraft:village_taiga"),
			explorer("warm_ocean_ruins_map", "#minecraft:on_ocean_ruin_warm_maps", "minecraft:ocean_ruin_warm"),
			explorer("woodland_mansion_map", "#minecraft:on_woodland_mansion_maps", "minecraft:mansion")
	);

	/** Vanilla's random enchanted book (dungeon and temple chests). */
	private static final String ENCHANTED_BOOK = """
			{"type": "minecraft:enchant_randomly", "options": "#minecraft:on_random_loot"}""";

	/** Vanilla's random suspicious stew (desert well archaeology). */
	private static final String SUSPICIOUS_STEW = """
			{"type": "minecraft:set_stew_effect", "effects": [
			  {"type": "minecraft:night_vision", "duration": {"type": "minecraft:uniform", "min": 7, "max": 10}},
			  {"type": "minecraft:jump_boost", "duration": {"type": "minecraft:uniform", "min": 7, "max": 10}},
			  {"type": "minecraft:weakness", "duration": {"type": "minecraft:uniform", "min": 6, "max": 8}},
			  {"type": "minecraft:blindness", "duration": {"type": "minecraft:uniform", "min": 5, "max": 7}},
			  {"type": "minecraft:poison", "duration": {"type": "minecraft:uniform", "min": 10, "max": 20}},
			  {"type": "minecraft:saturation", "duration": {"type": "minecraft:uniform", "min": 7, "max": 10}}
			]}""";

	/** Items that hold a potion type. */
	private static final Set<String> POTION_ITEMS = Set.of(
			"minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:tipped_arrow");

	/** Potion types that can't be brewed in survival. */
	private static final Set<String> UNBREWABLE_POTIONS = Set.of("minecraft:luck", "minecraft:empty");

	/** Decoded loot functions, keyed by their JSON. Cleared on server start and datapack reload. */
	private static final Map<String, LootItemFunction> FUNCTIONS = new ConcurrentHashMap<>();

	private SpecialItems() {
	}

	private static Map.Entry<String, String> explorer(String item, String destination, String decoration) {
		return Map.entry("minecraft:" + item, "{\"type\": \"minecraft:exploration_map\", \"destination\": \""
				+ destination + "\", \"decoration\": \"" + decoration + "\"}");
	}

	static void clearCache() {
		FUNCTIONS.clear();
	}

	/**
	 * Returns a count-1 stack of {@code item} with whatever data it needs to work. Usually that is
	 * just the plain item. An explorer map with no matching structure in range (for example in the
	 * Nether) becomes an empty map, as is a filled map dropped with no known position.
	 */
	static ItemStack prepare(Item item, LootContext context) {
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		ItemStack stack = new ItemStack(item);

		String explorerMap = EXPLORER_MAPS.get(id);
		if (explorerMap != null) {
			ItemStack result = function(explorerMap, context).apply(stack, context);
			return result.has(DataComponents.MAP_ID) ? result : new ItemStack(Items.MAP);
		}
		if (item == Items.FILLED_MAP) {
			Vec3 origin = context.getOptional(LootContextParams.ORIGIN);
			if (origin == null) {
				return new ItemStack(Items.MAP);
			}
			// Same as using an empty map: a fresh map centred on where the drop happened.
			return MapItem.create(context.getLevel(), (int) Math.floor(origin.x), (int) Math.floor(origin.z), (byte) 0, true, false);
		}
		if (POTION_ITEMS.contains(id)) {
			Holder<Potion> potion = randomPotion(context);
			if (potion != null) {
				stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
			}
			return stack;
		}
		if (item == Items.ENCHANTED_BOOK) {
			// enchant_randomly turns a plain book into an enchanted book.
			ItemStack book = function(ENCHANTED_BOOK, context).apply(new ItemStack(Items.BOOK), context);
			return book.is(Items.ENCHANTED_BOOK) ? book : stack;
		}
		if (item == Items.SUSPICIOUS_STEW) {
			return function(SUSPICIOUS_STEW, context).apply(stack, context);
		}
		return stack;
	}

	private static Holder<Potion> randomPotion(LootContext context) {
		List<Holder.Reference<Potion>> potions = BuiltInRegistries.POTION.listElements()
				.filter(p -> !UNBREWABLE_POTIONS.contains(p.key().identifier().toString()))
				.toList();
		return potions.isEmpty() ? null : potions.get(context.getRandom().nextInt(potions.size()));
	}

	private static LootItemFunction function(String json, LootContext context) {
		ServerLevel level = context.getLevel();
		return FUNCTIONS.computeIfAbsent(json, j -> LootItemFunctions.TYPED_CODEC
				.parse(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), JsonParser.parseString(j))
				.getOrThrow(message -> new IllegalStateException("Bad built-in loot function " + j + ": " + message)));
	}
}
