package com.iceyatom.surprisedrops;

import net.minecraft.util.RandomSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Assignment, multiplier and stack-splitting logic (NFR-13).
 *
 * <p>Pure Java with no Minecraft event or render dependencies, so it can be unit-tested without a
 * running game. The only Minecraft type used is {@link RandomSource}, a plain random-number
 * interface that works without bootstrapping the game.
 */
public final class SurpriseDropsHelper {
	/** Hard limits for the multiplier fields (FR-08). */
	public static final int MULTIPLIER_MIN_LIMIT = 1;
	public static final int MULTIPLIER_MAX_LIMIT = 10_000;
	/** Above this maximum the screen shows a performance warning (FR-09). */
	public static final int MULTIPLIER_WARNING_THRESHOLD = 1_000;
	/** Allowed range for max_unstackable_count (Section 6). */
	public static final int UNSTACKABLE_CAP_MIN = 1;
	public static final int UNSTACKABLE_CAP_MAX = 1_000;

	private SurpriseDropsHelper() {
	}

	/**
	 * Builds the replacement pool (FR-17): every item ID except air and the excluded IDs,
	 * sorted by registry ID so the result is stable for a given set of items.
	 */
	public static List<String> buildPool(Collection<String> allItemIds, Set<String> excluded) {
		return buildPool(allItemIds, excluded, false);
	}

	/**
	 * Vanilla items a survival player can never obtain: operator/technical blocks, blocks that
	 * can't be mined as items even with Silk Touch, and items that only exist through commands.
	 * Spawn eggs are matched separately by their {@code _spawn_egg} suffix.
	 */
	public static final Set<String> SURVIVAL_UNOBTAINABLE = Set.of(
			"minecraft:air",
			"minecraft:barrier",
			"minecraft:bedrock",
			"minecraft:budding_amethyst",
			"minecraft:chain_command_block",
			"minecraft:chorus_plant",
			"minecraft:command_block",
			"minecraft:command_block_minecart",
			"minecraft:debug_stick",
			"minecraft:dirt_path",
			"minecraft:end_portal_frame",
			"minecraft:farmland",
			"minecraft:frogspawn",
			"minecraft:infested_chiseled_stone_bricks",
			"minecraft:infested_cobblestone",
			"minecraft:infested_cracked_stone_bricks",
			"minecraft:infested_deepslate",
			"minecraft:infested_mossy_stone_bricks",
			"minecraft:infested_stone",
			"minecraft:infested_stone_bricks",
			"minecraft:jigsaw",
			"minecraft:knowledge_book",
			"minecraft:light",
			"minecraft:petrified_oak_slab",
			"minecraft:player_head",
			"minecraft:reinforced_deepslate",
			"minecraft:repeating_command_block",
			"minecraft:spawner",
			"minecraft:structure_block",
			"minecraft:structure_void",
			"minecraft:suspicious_gravel",
			"minecraft:suspicious_sand",
			"minecraft:test_block",
			"minecraft:test_instance_block",
			"minecraft:trial_spawner",
			"minecraft:vault"
	);

	/**
	 * Whether a survival player can obtain this item. Spawn eggs from any mod count as unobtainable;
	 * other modded items are assumed obtainable (add them to {@code excluded_items} if not).
	 */
	public static boolean isSurvivalObtainable(String id) {
		return !SURVIVAL_UNOBTAINABLE.contains(id) && !id.endsWith("_spawn_egg");
	}

	/** As {@link #buildPool(Collection, Set)}, optionally also dropping every survival-unobtainable item. */
	public static List<String> buildPool(Collection<String> allItemIds, Set<String> excluded, boolean survivalOnly) {
		List<String> pool = new ArrayList<>(allItemIds.size());
		for (String id : allItemIds) {
			if (id == null || id.equals("minecraft:air") || excluded.contains(id)) {
				continue;
			}
			if (survivalOnly && !isSurvivalObtainable(id)) {
				continue;
			}
			pool.add(id);
		}
		pool.sort(null);
		return List.copyOf(pool);
	}

	/**
	 * Fallback pick for a single loot table that is not in the {@link #assignAll} list (for example
	 * one added after the list was built): hash (seed, loot table ID) to an integer and take
	 * pool[hash mod pool size]. Same inputs always give the same item (FR-16).
	 *
	 * @return the chosen item ID, or {@code null} if the pool is empty
	 */
	public static String assignedItem(long seed, String lootTableId, List<String> pool) {
		if (pool.isEmpty()) {
			return null;
		}
		long hash = hash(seed, lootTableId);
		return pool.get((int) Math.floorMod(hash, (long) pool.size()));
	}

	/**
	 * Assigns every loot table its own item: the pool is shuffled with the seed and the tables, in
	 * sorted order, take items one after another. While there are at least as many items as tables,
	 * no two tables share an item. If there are more tables than items, the shuffled pool is reused
	 * in order so items repeat as evenly as possible.
	 *
	 * @param tableIds every loot table to assign (any order; it is sorted here)
	 * @return loot table ID to item ID; empty if the pool is empty
	 */
	public static Map<String, String> assignAll(long seed, Collection<String> tableIds, List<String> pool) {
		Map<String, String> result = new HashMap<>(tableIds.size() * 2);
		if (pool.isEmpty()) {
			return result;
		}
		List<String> tables = new ArrayList<>(new TreeSet<>(tableIds));
		List<String> items = shuffled(seed, pool);
		for (int i = 0; i < tables.size(); i++) {
			result.put(tables.get(i), items.get(i % items.size()));
		}
		return result;
	}

	/** Fisher-Yates shuffle driven by SplitMix64, so the order is the same on every Java version. */
	static List<String> shuffled(long seed, List<String> pool) {
		List<String> items = new ArrayList<>(pool);
		long state = mix64(seed ^ 0x5D0B5EEDL);
		for (int i = items.size() - 1; i > 0; i--) {
			state += 0x9e3779b97f4a7c15L;
			long r = mix64(state);
			int j = (int) Math.floorMod(r, (long) (i + 1));
			Collections.swap(items, i, j);
		}
		return items;
	}

	/** Deterministic 64-bit hash of (seed, loot table ID). FNV-1a over the ID, then a SplitMix64 finish. */
	public static long hash(long seed, String lootTableId) {
		long h = 0xcbf29ce484222325L;
		for (byte b : lootTableId.getBytes(StandardCharsets.UTF_8)) {
			h ^= (b & 0xff);
			h *= 0x100000001b3L;
		}
		return mix64(h ^ mix64(seed));
	}

	private static long mix64(long z) {
		z += 0x9e3779b97f4a7c15L;
		z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
		z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
		return z ^ (z >>> 31);
	}

	/** Draws the multiplier uniformly from [min, max] inclusive (FR-21). */
	public static int rollCount(int min, int max, RandomSource random) {
		if (max <= min) {
			return min;
		}
		return random.nextIntBetweenInclusive(min, max);
	}

	/**
	 * Splits a total count into stack sizes (FR-22, FR-23). Items with a maximum stack size of 1
	 * are clamped to {@code unstackableCap} single stacks.
	 */
	public static int[] splitStacks(int count, int maxStackSize, int unstackableCap) {
		if (count <= 0) {
			return new int[0];
		}
		if (maxStackSize <= 1) {
			int n = Math.min(count, Math.max(1, unstackableCap));
			int[] stacks = new int[n];
			java.util.Arrays.fill(stacks, 1);
			return stacks;
		}
		int full = count / maxStackSize;
		int rest = count % maxStackSize;
		int[] stacks = new int[full + (rest > 0 ? 1 : 0)];
		java.util.Arrays.fill(stacks, 0, full, maxStackSize);
		if (rest > 0) {
			stacks[full] = rest;
		}
		return stacks;
	}

	/** Multiplies base × multiplier without overflowing an int. */
	public static int replacementCount(int base, int multiplier) {
		long n = (long) base * (long) multiplier;
		return (int) Math.min(n, Integer.MAX_VALUE);
	}

	/**
	 * Turns seed text into a seed (FR-11), the same way vanilla world seeds work: a whole number is
	 * used as-is, any other non-blank text is hashed with {@link String#hashCode()}.
	 *
	 * @return the seed, or {@code null} if the text is blank
	 */
	public static Long parseSeed(String text) {
		if (text == null) {
			return null;
		}
		String trimmed = text.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		try {
			return Long.parseLong(trimmed);
		} catch (NumberFormatException e) {
			return (long) trimmed.hashCode();
		}
	}

	/** True if min/max obey FR-08: 1 ≤ min ≤ max ≤ 10,000. */
	public static boolean isValidRange(int min, int max) {
		return min >= MULTIPLIER_MIN_LIMIT && max <= MULTIPLIER_MAX_LIMIT && min <= max;
	}

	/** Parses a whole number for a multiplier field, or returns {@code null} if it is not one. */
	public static Integer parseWholeNumber(String text) {
		if (text == null) {
			return null;
		}
		String trimmed = text.trim();
		if (trimmed.isEmpty() || !trimmed.chars().allMatch(Character::isDigit)) {
			return null;
		}
		try {
			return Integer.parseInt(trimmed);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
