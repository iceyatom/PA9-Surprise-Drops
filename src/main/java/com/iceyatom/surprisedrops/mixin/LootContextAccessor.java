package com.iceyatom.surprisedrops.mixin;

import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

/**
 * Read-only accessor for {@link LootContext}'s private {@code visitedElements} set.
 *
 * <p><b>What is intercepted:</b> nothing; this only exposes a field declared on LootContext itself
 * (NFR-12). No vanilla behaviour changes.
 *
 * <p><b>Why:</b> Fabric API's {@code LootTableEvents.MODIFY_DROPS} fires for every call to
 * {@code LootTable.getRandomItemsRaw}, including nested tables reached through a {@code loot_table}
 * entry (e.g. {@code entities/sheep} rolling {@code entities/sheep/white}). Vanilla keeps every
 * table currently being rolled in this set and removes it when that roll finishes. So when the
 * event fires and the set is still non-empty, an outer table is still mid-roll and this is a
 * nested roll. Surprise Drops only replaces the outermost roll, so the multiplier is applied
 * exactly once per drop event (FR-20, FR-21).
 */
@Mixin(LootContext.class)
public interface LootContextAccessor {
	@Accessor("visitedElements")
	Set<LootContext.VisitedEntry<?>> surprisedrops$getVisitedElements();
}
