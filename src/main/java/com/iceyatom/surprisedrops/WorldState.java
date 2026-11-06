package com.iceyatom.surprisedrops;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-world state saved with the world (FR-33, Section 7.6). Attached to the overworld's data
 * storage; the single source of truth read by the loot interception on every roll.
 */
public final class WorldState extends SavedData {
	private static final Codec<WorldState> CODEC = DropSettings.CODEC.xmap(WorldState::new, WorldState::settings);

	public static final SavedDataType<WorldState> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(SurpriseDrops.MOD_ID, "world_state"),
			WorldState::createForNewWorld,
			CODEC,
			null
	);

	private volatile DropSettings settings;

	private WorldState(DropSettings settings) {
		this.settings = settings;
	}

	/** First load in a world: TOML defaults plus a random seed (FR-32, FR-33). */
	private static WorldState createForNewWorld() {
		WorldState state = new WorldState(DropSettings.fromDefaults(SurpriseDropsConfig.get(), ThreadLocalRandom.current().nextLong()));
		state.setDirty();
		return state;
	}

	public static WorldState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public DropSettings settings() {
		return settings;
	}

	public void setSettings(DropSettings settings) {
		this.settings = settings;
		setDirty();
	}
}
