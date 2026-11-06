package com.iceyatom.surprisedrops.network;

import com.iceyatom.surprisedrops.DropSettings;
import com.iceyatom.surprisedrops.SurpriseDrops;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Custom payloads used only by the configuration screen (Section 8). Loot replacement itself uses
 * no custom packets (PKT-06).
 */
public final class SurpriseDropsPayloads {
	private SurpriseDropsPayloads() {
	}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
		return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SurpriseDrops.MOD_ID, path));
	}

	/**
	 * PKT-01: Done in the screen sends the edited settings. The seed travels as the raw text the
	 * player typed (FR-11); the server turns it into a number. Blank text keeps the current seed.
	 */
	public record ApplySettings(boolean enabled, int multiplierMin, int multiplierMax,
								boolean affectBlocks, boolean affectMobs, boolean affectContainers, boolean affectOther,
								String seedText) implements CustomPacketPayload {
		public static final Type<ApplySettings> TYPE = payloadType("apply_settings");
		public static final StreamCodec<RegistryFriendlyByteBuf, ApplySettings> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeBoolean(p.enabled);
					buf.writeVarInt(p.multiplierMin);
					buf.writeVarInt(p.multiplierMax);
					buf.writeBoolean(p.affectBlocks);
					buf.writeBoolean(p.affectMobs);
					buf.writeBoolean(p.affectContainers);
					buf.writeBoolean(p.affectOther);
					buf.writeUtf(p.seedText, 256);
				},
				buf -> new ApplySettings(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
						buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readUtf(256))
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** PKT-03: a Shuffle request carries no seed; the server picks one (FR-28). */
	public record Shuffle() implements CustomPacketPayload {
		public static final Type<Shuffle> TYPE = payloadType("shuffle");
		public static final StreamCodec<RegistryFriendlyByteBuf, Shuffle> CODEC = StreamCodec.of((buf, p) -> {
		}, buf -> new Shuffle());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** PKT-04: server → client, the live settings and whether this player may edit them (FR-12). */
	public record Sync(DropSettings settings, boolean canEdit) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = payloadType("sync");
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.of(
				(buf, p) -> {
					p.settings.write(buf);
					buf.writeBoolean(p.canEdit);
				},
				buf -> new Sync(DropSettings.read((FriendlyByteBuf) buf), buf.readBoolean())
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
