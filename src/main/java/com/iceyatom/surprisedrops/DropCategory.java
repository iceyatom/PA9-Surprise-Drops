package com.iceyatom.surprisedrops;

import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/** The four drop categories (FR-14), decided by a loot table's context type. */
public enum DropCategory {
	BLOCKS,
	MOBS,
	CONTAINERS,
	OTHER;

	/**
	 * Classifies a loot table by its parameter set. Returns {@code null} for context types that are
	 * not item drops at all and are always left vanilla: mob spawn equipment (out of scope, Section 10)
	 * and villager trade definitions.
	 */
	public static DropCategory of(ContextKeySet paramSet) {
		if (paramSet == LootContextParamSets.BLOCK) {
			return BLOCKS;
		}
		if (paramSet == LootContextParamSets.ENTITY) {
			return MOBS;
		}
		if (paramSet == LootContextParamSets.CHEST || paramSet == LootContextParamSets.VAULT) {
			return CONTAINERS;
		}
		if (paramSet == LootContextParamSets.EQUIPMENT || paramSet == LootContextParamSets.VILLAGER_TRADE) {
			return null;
		}
		return OTHER;
	}
}
