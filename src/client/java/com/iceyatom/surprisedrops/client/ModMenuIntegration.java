package com.iceyatom.surprisedrops.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** FR-06(c) / FR-35: the same configuration screen from Mod Menu, if installed (optional dependency). */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SurpriseDropsConfigScreen::new;
	}
}
