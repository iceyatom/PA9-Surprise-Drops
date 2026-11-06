package com.iceyatom.surprisedrops;

import com.iceyatom.surprisedrops.network.SurpriseDropsPayloads;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint. All drop logic runs on the logical server (dedicated or integrated).
 */
public class SurpriseDrops implements ModInitializer {
	public static final String MOD_ID = "surprisedrops";
	public static final Logger LOGGER = LoggerFactory.getLogger("PA9 Surprise Drops");

	/** Cached world state for the running server, so a loot roll is a field read (NFR-02). */
	private static volatile MinecraftServer currentServer;
	private static volatile WorldState currentState;

	@Override
	public void onInitialize() {
		SurpriseDropsConfig.load();

		// Section 8: payloads only carry configuration-screen data. Clients without the mod
		// never receive them (PKT-05), so vanilla clients can still join (NFR-08).
		PayloadTypeRegistry.serverboundPlay().register(SurpriseDropsPayloads.ApplySettings.TYPE, SurpriseDropsPayloads.ApplySettings.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SurpriseDropsPayloads.Shuffle.TYPE, SurpriseDropsPayloads.Shuffle.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SurpriseDropsPayloads.Sync.TYPE, SurpriseDropsPayloads.Sync.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SurpriseDropsPayloads.RequestTables.TYPE, SurpriseDropsPayloads.RequestTables.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SurpriseDropsPayloads.TableList.TYPE, SurpriseDropsPayloads.TableList.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SurpriseDropsPayloads.RequestTables.TYPE, SurpriseDropsServer::onRequestTables);
		ServerPlayNetworking.registerGlobalReceiver(SurpriseDropsPayloads.ApplySettings.TYPE, SurpriseDropsServer::onApplySettings);
		ServerPlayNetworking.registerGlobalReceiver(SurpriseDropsPayloads.Shuffle.TYPE, SurpriseDropsServer::onShuffle);

		// Section 7.2: the Fabric API drops event is the interception point; no mixin needed (NFR-12).
		LootTableEvents.MODIFY_DROPS.register(LootReplacer::onModifyDrops);

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			currentServer = server;
			currentState = WorldState.get(server);
			LootReplacer.resetSession();
			LootReplacer.rebuildPool(server);
		});
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> LootReplacer.rebuildPool(server));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			currentServer = null;
			currentState = null;
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				SurpriseDropsServer.sync(handler.getPlayer(), WorldState.get(server).settings()));

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> SurpriseDropsCommands.register(dispatcher));

		LOGGER.info("PA9 Surprise Drops initialised");
	}

	/** Current per-world settings for a server. */
	public static DropSettings settings(MinecraftServer server) {
		WorldState state = currentState;
		if (state == null || currentServer != server) {
			state = WorldState.get(server);
		}
		return state.settings();
	}

	/** Called after any settings change: push live values to modded clients (PKT-04). */
	public static void onSettingsChanged(MinecraftServer server) {
		SurpriseDropsServer.syncAll(server);
	}
}
