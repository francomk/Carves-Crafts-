package com.studioderiva.carves_and_crafts.registry;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.menu.CarvingBenchMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
	public static final MenuType<CarvingBenchMenu> CARVING_BENCH = Registry.register(
		BuiltInRegistries.MENU,
		CarvesAndCrafts.id("carving_bench"),
		new MenuType<>(CarvingBenchMenu::new, FeatureFlags.VANILLA_SET)
	);

	private ModMenus() {
	}

	public static void init() {
	}
}
