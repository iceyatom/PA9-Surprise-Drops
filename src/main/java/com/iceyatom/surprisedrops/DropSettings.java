package com.iceyatom.surprisedrops;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;

/**
 * The per-world settings (FR-33): master flag, seed, multiplier range and category toggles.
 * Immutable; every change produces a new instance.
 */
public record DropSettings(
		boolean enabled,
		long seed,
		int multiplierMin,
		int multiplierMax,
		boolean affectBlocks,
		boolean affectMobs,
		boolean affectContainers,
		boolean affectOther
) {
	public static final Codec<DropSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.fieldOf("enabled").forGetter(DropSettings::enabled),
			Codec.LONG.fieldOf("seed").forGetter(DropSettings::seed),
			Codec.INT.fieldOf("multiplier_min").forGetter(DropSettings::multiplierMin),
			Codec.INT.fieldOf("multiplier_max").forGetter(DropSettings::multiplierMax),
			Codec.BOOL.fieldOf("affect_blocks").forGetter(DropSettings::affectBlocks),
			Codec.BOOL.fieldOf("affect_mobs").forGetter(DropSettings::affectMobs),
			Codec.BOOL.fieldOf("affect_containers").forGetter(DropSettings::affectContainers),
			Codec.BOOL.fieldOf("affect_other").forGetter(DropSettings::affectOther)
	).apply(i, DropSettings::new));

	/** Settings for a brand-new world: TOML defaults plus a fresh random seed (FR-32, FR-33). */
	public static DropSettings fromDefaults(SurpriseDropsConfig config, long seed) {
		return new DropSettings(config.enabledDefault, seed, config.multiplierMin, config.multiplierMax,
				config.affectBlocks, config.affectMobs, config.affectContainers, config.affectOther);
	}

	public boolean affects(DropCategory category) {
		return switch (category) {
			case BLOCKS -> affectBlocks;
			case MOBS -> affectMobs;
			case CONTAINERS -> affectContainers;
			case OTHER -> affectOther;
		};
	}

	public DropSettings withEnabled(boolean value) {
		return new DropSettings(value, seed, multiplierMin, multiplierMax, affectBlocks, affectMobs, affectContainers, affectOther);
	}

	public DropSettings withSeed(long value) {
		return new DropSettings(enabled, value, multiplierMin, multiplierMax, affectBlocks, affectMobs, affectContainers, affectOther);
	}

	public DropSettings withRange(int min, int max) {
		return new DropSettings(enabled, seed, min, max, affectBlocks, affectMobs, affectContainers, affectOther);
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeBoolean(enabled);
		buf.writeLong(seed);
		buf.writeVarInt(multiplierMin);
		buf.writeVarInt(multiplierMax);
		buf.writeBoolean(affectBlocks);
		buf.writeBoolean(affectMobs);
		buf.writeBoolean(affectContainers);
		buf.writeBoolean(affectOther);
	}

	public static DropSettings read(FriendlyByteBuf buf) {
		return new DropSettings(buf.readBoolean(), buf.readLong(), buf.readVarInt(), buf.readVarInt(),
				buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
	}
}
