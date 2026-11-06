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
								boolean survivalItemsOnly, String seedText) implements CustomPacketPayload {
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
					buf.writeBoolean(p.survivalItemsOnly);
					buf.writeUtf(p.seedText, 256);
				},
				buf -> new ApplySettings(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
						buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readUtf(256))
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

	/** Client → server: the loot table browser asks for every assignment under the current seed. */
	public record RequestTables() implements CustomPacketPayload {
		public static final Type<RequestTables> TYPE = payloadType("request_tables");
		public static final StreamCodec<RegistryFriendlyByteBuf, RequestTables> CODEC = StreamCodec.of((buf, p) -> {
		}, buf -> new RequestTables());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * One loot table and what it currently turns into.
	 *
	 * @param table    loot table ID
	 * @param item     assigned item ID, or "" if the pool is empty
	 * @param category {@link com.iceyatom.surprisedrops.DropCategory} ordinal
	 */
	public record TableEntry(String table, String item, int category) {
	}

	/** Server → client: the answer to {@link RequestTables}, sorted by loot table ID. */
	public record TableList(long seed, java.util.List<TableEntry> entries) implements CustomPacketPayload {
		public static final Type<TableList> TYPE = payloadType("table_list");
		public static final StreamCodec<RegistryFriendlyByteBuf, TableList> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeLong(p.seed);
					buf.writeVarInt(p.entries.size());
					for (TableEntry e : p.entries) {
						buf.writeUtf(e.table());
						buf.writeUtf(e.item());
						buf.writeByte(e.category());
					}
				},
				buf -> {
					long seed = buf.readLong();
					int size = buf.readVarInt();
					java.util.List<TableEntry> entries = new java.util.ArrayList<>(size);
					for (int i = 0; i < size; i++) {
						entries.add(new TableEntry(buf.readUtf(), buf.readUtf(), buf.readByte()));
					}
					return new TableList(seed, entries);
				}
		);

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
