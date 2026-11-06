package com.iceyatom.surprisedrops.client;

import com.iceyatom.surprisedrops.SurpriseDrops;
import com.iceyatom.surprisedrops.SurpriseDropsConfig;
import com.iceyatom.surprisedrops.network.SurpriseDropsPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Client entrypoint: configuration screen access (FR-03, FR-06) and the client side of
 * settings synchronisation (PKT-04). Contains no drop logic.
 */
public class SurpriseDropsClient implements ClientModInitializer {
	private static KeyMapping openConfigKey;

	@Override
	public void onInitializeClient() {
		// FR-03: keybind, default unbound (configurable in surprisedrops.toml and the Controls screen).
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SurpriseDrops.MOD_ID, "main"));
		InputConstants.Key defaultKey = defaultKey(SurpriseDropsConfig.get().openConfigKeybind);
		openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.surprisedrops.open_config",
				defaultKey.getType(), defaultKey.getValue(), category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openConfigKey.consumeClick()) {
				if (client.player != null && client.gui.screen() == null) {
					client.gui.setScreen(new SurpriseDropsConfigScreen(null));
				}
			}
		});

		// FR-06(a): button on the vanilla pause menu. Uses Fabric's screen event instead of a
		// PauseScreen mixin (NFR-12); the button is added after the vanilla widgets are laid out.
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof PauseScreen pause && pause.showsPauseMenu()) {
				Screens.getWidgets(screen).add(Button.builder(Component.translatable("surprisedrops.button.pause"),
								b -> client.gui.setScreen(new SurpriseDropsConfigScreen(screen)))
						.bounds(4, scaledHeight - 24, 100, 20)
						.build());
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(SurpriseDropsPayloads.Sync.TYPE, (payload, context) -> {
			ClientState.update(payload.settings(), payload.canEdit());
			if (context.client().gui.screen() instanceof SurpriseDropsConfigScreen screen) {
				screen.onServerSync(payload.settings());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.clear());
	}

	private static InputConstants.Key defaultKey(String name) {
		if (name == null || name.isBlank() || name.equalsIgnoreCase("unbound")) {
			return InputConstants.UNKNOWN;
		}
		try {
			return InputConstants.getKey(name.trim());
		} catch (RuntimeException e) {
			SurpriseDrops.LOGGER.warn("Unknown open_config_keybind '{}', leaving it unbound", name);
			return InputConstants.UNKNOWN;
		}
	}
}
