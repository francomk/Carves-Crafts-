package com.studioderiva.carves_and_crafts;

import com.studioderiva.carves_and_crafts.command.CarvesCommand;
import com.studioderiva.carves_and_crafts.command.DebugPumpkinCommand;
import com.studioderiva.carves_and_crafts.design.DesignRefs;
import com.studioderiva.carves_and_crafts.network.HeldDesigns;
import com.studioderiva.carves_and_crafts.config.ServerConfig;
import com.studioderiva.carves_and_crafts.registry.ModAttachments;
import com.studioderiva.carves_and_crafts.registry.ModBlockEntities;
import com.studioderiva.carves_and_crafts.registry.ModBlocks;
import com.studioderiva.carves_and_crafts.registry.ModComponents;
import com.studioderiva.carves_and_crafts.network.ModNetworking;
import com.studioderiva.carves_and_crafts.registry.ModItems;
import com.studioderiva.carves_and_crafts.registry.ModMenus;
import com.studioderiva.carves_and_crafts.dev.DevServerScenario;
import com.studioderiva.carves_and_crafts.schematic.PumpkinPresets;
import com.studioderiva.carves_and_crafts.variety.VarietyWeights;
import com.studioderiva.carves_and_crafts.worldgen.ModWorldgen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CarvesAndCrafts implements ModInitializer {
	public static final String MOD_ID = "carves_and_crafts";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final int PRUNE_INTERVAL_TICKS = 20 * 30;

	@Override
	public void onInitialize() {
		ModComponents.init();
		ModBlocks.init();
		ModBlockEntities.init();
		ModItems.init();
		ModMenus.init();
		ModAttachments.init();
		ServerConfig.load();
		ModNetworking.init();
		ModWorldgen.init();
		ResourceLoader.get(PackType.SERVER_DATA).registerReloader(VarietyWeights.ID, new VarietyWeights());
		ResourceLoader.get(PackType.SERVER_DATA).registerReloader(PumpkinPresets.ID, new PumpkinPresets());
		DevServerScenario.registerIfEnabled();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			DebugPumpkinCommand.register(dispatcher);
			CarvesCommand.register(dispatcher);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % PRUNE_INTERVAL_TICKS == 0) {
				HeldDesigns.rememberOnlinePlayers(server);
				DesignRefs.prune(System.currentTimeMillis());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> DesignRefs.clear());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
