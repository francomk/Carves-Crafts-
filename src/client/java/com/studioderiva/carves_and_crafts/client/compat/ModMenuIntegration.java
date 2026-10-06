package com.studioderiva.carves_and_crafts.client.compat;

import com.studioderiva.carves_and_crafts.client.screen.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu entry point (only loaded when Mod Menu is installed): the Config button opens {@link ConfigScreen}. */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ConfigScreen::new;
	}
}
