package com.iceyatom.surprisedrops;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class SurpriseDropsHelperTest {
	private static final List<String> POOL = SurpriseDropsHelper.buildPool(
			List.of("minecraft:stone", "minecraft:air", "minecraft:diamond", "minecraft:barrier", "minecraft:apple"),
			Set.of("minecraft:barrier"));

	@Test
	void poolIsSortedAndExcludesAirAndExcluded() {
		assertEquals(List.of("minecraft:apple", "minecraft:diamond", "minecraft:stone"), POOL);
	}

	@Test
	void assignmentIsStableForSameSeed() {
		String a = SurpriseDropsHelper.assignedItem(42L, "minecraft:blocks/stone", POOL);
		for (int i = 0; i < 100; i++) {
			assertEquals(a, SurpriseDropsHelper.assignedItem(42L, "minecraft:blocks/stone", POOL));
		}
	}

	@Test
	void assignmentsSpreadAcrossPool() {
		List<String> big = SurpriseDropsHelper.buildPool(
				IntStream.range(0, 1000).mapToObj(i -> "mod:item_" + i).toList(), Set.of());
		long distinct = IntStream.range(0, 500)
				.mapToObj(i -> SurpriseDropsHelper.assignedItem(7L, "minecraft:blocks/b" + i, big)).distinct().count();
		assertTrue(distinct > 300, "distinct=" + distinct);
		assertNull(SurpriseDropsHelper.assignedItem(1L, "x", List.of()));
	}

	@Test
	void rollCountStaysInRangeAndAveragesNearMiddle() {
		RandomSource random = RandomSource.create(123L);
		long sum = 0;
		for (int i = 0; i < 20_000; i++) {
			int n = SurpriseDropsHelper.rollCount(1, 1000, random);
			assertTrue(n >= 1 && n <= 1000);
			sum += n;
		}
		double avg = sum / 20_000.0;
		assertTrue(avg > 480 && avg < 520, "avg=" + avg);
		assertEquals(1, SurpriseDropsHelper.rollCount(1, 1, random));
	}

	@Test
	void splitStacks() {
		assertArrayEquals(new int[]{64, 64, 22}, SurpriseDropsHelper.splitStacks(150, 64, 64));
		assertArrayEquals(new int[]{16, 4}, SurpriseDropsHelper.splitStacks(20, 16, 64));
		assertArrayEquals(new int[]{64}, SurpriseDropsHelper.splitStacks(64, 64, 64));
		assertEquals(64, SurpriseDropsHelper.splitStacks(1000, 1, 64).length);
		assertEquals(3, SurpriseDropsHelper.splitStacks(3, 1, 64).length);
		assertEquals(0, SurpriseDropsHelper.splitStacks(0, 64, 64).length);
	}

	@Test
	void vanillaQuantityKeptAtMultiplierOne() {
		assertEquals(2, SurpriseDropsHelper.replacementCount(2, 1));
		assertEquals(10, SurpriseDropsHelper.replacementCount(2, 5));
		assertEquals(Integer.MAX_VALUE, SurpriseDropsHelper.replacementCount(Integer.MAX_VALUE, 10_000));
	}

	@Test
	void seedParsing() {
		assertEquals(12345L, SurpriseDropsHelper.parseSeed(" 12345 "));
		assertEquals(-9L, SurpriseDropsHelper.parseSeed("-9"));
		assertEquals((long) "hello".hashCode(), SurpriseDropsHelper.parseSeed("hello"));
		assertNull(SurpriseDropsHelper.parseSeed("  "));
	}

	@Test
	void rangeValidation() {
		assertTrue(SurpriseDropsHelper.isValidRange(1, 10_000));
		assertFalse(SurpriseDropsHelper.isValidRange(500, 100));
		assertFalse(SurpriseDropsHelper.isValidRange(0, 5));
		assertFalse(SurpriseDropsHelper.isValidRange(1, 10_001));
		assertNull(SurpriseDropsHelper.parseWholeNumber("1.5"));
		assertNull(SurpriseDropsHelper.parseWholeNumber("-3"));
		assertEquals(7, SurpriseDropsHelper.parseWholeNumber("7"));
	}

	@Test
	void tomlRoundTrip() {
		SurpriseDropsConfig config = new SurpriseDropsConfig();
		config.multiplierMax = 1000;
		config.excludedItems.add("minecraft:bedrock");
		var parsed = SurpriseDropsConfig.parse(config.toToml());
		assertEquals(1000L, parsed.get("multiplier_max"));
		assertEquals(Boolean.TRUE, parsed.get("affect_blocks"));
		assertTrue(((List<?>) parsed.get("excluded_items")).contains("minecraft:bedrock"));
		assertEquals("unbound", parsed.get("open_config_keybind"));
	}

	@Test
	void survivalOnlyPoolDropsUnobtainableItems() {
		List<String> all = List.of("minecraft:stone", "minecraft:bedrock", "minecraft:petrified_oak_slab",
				"minecraft:test_block", "minecraft:pig_spawn_egg", "othermod:zombie_spawn_egg", "othermod:gem", "minecraft:diamond");
		assertEquals(List.of("minecraft:diamond", "minecraft:stone", "othermod:gem"),
				SurpriseDropsHelper.buildPool(all, Set.of(), true));
		assertEquals(all.size(), SurpriseDropsHelper.buildPool(all, Set.of(), false).size());
	}
}
