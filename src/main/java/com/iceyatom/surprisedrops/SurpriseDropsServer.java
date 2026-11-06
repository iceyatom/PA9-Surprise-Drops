package com.iceyatom.surprisedrops;

import com.iceyatom.surprisedrops.network.SurpriseDropsPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Server-side operations shared by the commands and the network receivers, so both paths apply
 * exactly the same checks and messages.
 */
public final class SurpriseDropsServer {
	private SurpriseDropsServer() {
	}

	/** FR-02 / FR-12: permission level 2, or the singleplayer host. */
	public static boolean canEdit(ServerPlayer player) {
		if (Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
			return true;
		}
		MinecraftServer server = player.level().getServer();
		return !server.isDedicatedServer() && server.isSingleplayerOwner(player.nameAndId());
	}

	public static void setEnabled(MinecraftServer server, ServerPlayer actor, boolean enabled) {
		WorldState state = WorldState.get(server);
		state.setSettings(state.settings().withEnabled(enabled));
		SurpriseDrops.onSettingsChanged(server);
		actionBar(actor, enabled);
	}

	/** Applies new settings from the screen (FR-12). Values must already be validated. */
	public static void apply(MinecraftServer server, ServerPlayer actor, DropSettings settings) {
		WorldState state = WorldState.get(server);
		DropSettings old = state.settings();
		state.setSettings(settings);
		SurpriseDrops.onSettingsChanged(server);
		if (actor != null) {
			if (old.enabled() != settings.enabled()) {
				actionBar(actor, settings.enabled());
			} else {
				actor.sendSystemMessage(Component.translatable("surprisedrops.message.saved"), true);
			}
			if (old.seed() != settings.seed()) {
				actor.sendSystemMessage(seedMessage("surprisedrops.message.seed_set", settings.seed()));
			}
		}
	}

	/**
	 * Shuffle (FR-28 to FR-31): a new seed, applied immediately. Range and toggles are untouched;
	 * already-dropped items and already-generated containers are unaffected by construction.
	 *
	 * @param seed an explicit seed, or {@code null} for a new random 64-bit seed
	 * @return the seed now in effect
	 */
	public static long shuffle(MinecraftServer server, Long seed) {
		long newSeed = seed != null ? seed : ThreadLocalRandom.current().nextLong();
		WorldState state = WorldState.get(server);
		state.setSettings(state.settings().withSeed(newSeed));
		SurpriseDrops.onSettingsChanged(server);
		return newSeed;
	}

	/** FR-31: the new seed, clickable to copy. */
	public static Component seedMessage(String key, long seed) {
		String text = Long.toString(seed);
		Component seedComponent = Component.literal(text).withStyle(style -> style
				.withColor(ChatFormatting.GREEN)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.CopyToClipboard(text))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.copy.click"))));
		return Component.translatable(key, seedComponent);
	}

	/** FR-04: brief action-bar message on a state change. */
	public static void actionBar(ServerPlayer player, boolean enabled) {
		if (player != null) {
			player.sendSystemMessage(Component.translatable(enabled ? "surprisedrops.message.on" : "surprisedrops.message.off"), true);
		}
	}

	/** PKT-04/PKT-05: send live settings to one player, only if their client has the mod. */
	public static void sync(ServerPlayer player, DropSettings settings) {
		if (ServerPlayNetworking.canSend(player, SurpriseDropsPayloads.Sync.TYPE)) {
			ServerPlayNetworking.send(player, new SurpriseDropsPayloads.Sync(settings, canEdit(player)));
		}
	}

	public static void syncAll(MinecraftServer server) {
		DropSettings settings = WorldState.get(server).settings();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sync(player, settings);
		}
	}

	/** NFR-04: tell operators about replacement failures. */
	public static void notifyOperators(MinecraftServer server, Component message) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (canEdit(player)) {
				player.sendSystemMessage(message);
			}
		}
	}

	// --- Network receivers -----------------------------------------------------------------

	/** PKT-01/PKT-02: re-check permission and ranges; silently ignore the payload if either fails. */
	public static void onApplySettings(SurpriseDropsPayloads.ApplySettings payload, ServerPlayNetworking.Context context) {
		ServerPlayer player = context.player();
		MinecraftServer server = context.server();
		if (!canEdit(player)) {
			SurpriseDrops.LOGGER.warn("Ignored Surprise Drops settings from {}: insufficient permission", player.getName().getString());
			sync(player, WorldState.get(server).settings());
			return;
		}
		if (!SurpriseDropsHelper.isValidRange(payload.multiplierMin(), payload.multiplierMax())) {
			SurpriseDrops.LOGGER.warn("Ignored Surprise Drops settings from {}: invalid range {}-{}",
					player.getName().getString(), payload.multiplierMin(), payload.multiplierMax());
			sync(player, WorldState.get(server).settings());
			return;
		}
		DropSettings current = WorldState.get(server).settings();
		Long parsedSeed = SurpriseDropsHelper.parseSeed(payload.seedText());
		long seed = parsedSeed != null ? parsedSeed : current.seed();
		apply(server, player, new DropSettings(payload.enabled(), seed, payload.multiplierMin(), payload.multiplierMax(),
				payload.affectBlocks(), payload.affectMobs(), payload.affectContainers(), payload.affectOther()));
	}

	/** PKT-03 */
	public static void onShuffle(SurpriseDropsPayloads.Shuffle payload, ServerPlayNetworking.Context context) {
		ServerPlayer player = context.player();
		if (!canEdit(player)) {
			SurpriseDrops.LOGGER.warn("Ignored Surprise Drops shuffle from {}: insufficient permission", player.getName().getString());
			return;
		}
		long seed = shuffle(context.server(), null);
		player.sendSystemMessage(seedMessage("surprisedrops.message.shuffled", seed));
	}
}
