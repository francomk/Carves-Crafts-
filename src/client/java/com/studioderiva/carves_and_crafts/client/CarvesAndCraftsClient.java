package com.studioderiva.carves_and_crafts.client;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.client.dev.DevScenarios;
import com.studioderiva.carves_and_crafts.client.render.ClientDesigns;
import com.studioderiva.carves_and_crafts.client.render.CustomPumpkinRenderer;
import com.studioderiva.carves_and_crafts.client.render.PumpkinRenderCache;
import com.studioderiva.carves_and_crafts.client.screen.CarvingBenchScreen;
import com.studioderiva.carves_and_crafts.design.AuthorList;
import com.studioderiva.carves_and_crafts.network.DesignDataPayload;
import com.studioderiva.carves_and_crafts.network.SchematicPagePayload;
import com.studioderiva.carves_and_crafts.registry.ModBlockEntities;
import com.studioderiva.carves_and_crafts.registry.ModComponents;
import com.studioderiva.carves_and_crafts.registry.ModMenus;
import java.util.List;
import com.studioderiva.carves_and_crafts.registry.ModBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StemBlock;

public class CarvesAndCraftsClient implements ClientModInitializer {
	/** Names shown in a pumpkin tooltip before "and N more". */
	private static final int TOOLTIP_AUTHORS = 3;
	/** Vanilla BlockColors value for attached stems. */
	private static final int ATTACHED_STEM_COLOR = -2046180;

	@Override
	public void onInitializeClient() {
		ClientConfig.load();
		BlockEntityRenderers.register(ModBlockEntities.CUSTOM_PUMPKIN, CustomPumpkinRenderer::new);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			PumpkinRenderCache.INSTANCE.clear();
			ClientDesigns.clear();
		}));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientDesigns.tick();
			PumpkinRenderCache.INSTANCE.tick();
		});
		// cached textures are built from the pumpkin skin and flesh textures, which a resource pack can replace
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(CarvesAndCrafts.id("pumpkin_textures"),
			(ResourceManagerReloadListener) manager -> PumpkinRenderCache.INSTANCE.clear());
		MenuScreens.register(ModMenus.CARVING_BENCH, CarvingBenchScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(DesignDataPayload.TYPE, (payload, context) -> ClientDesigns.receive(payload.design()));
		ClientPlayNetworking.registerGlobalReceiver(SchematicPagePayload.TYPE, (payload, context) -> {
			if (context.client().screen instanceof CarvingBenchScreen screen) {
				screen.onPage(payload);
			}
		});
		ItemTooltipCallback.EVENT.register((stack, tooltipContext, type, lines) -> {
			AuthorList authors = stack.get(ModComponents.AUTHORS);
			if (authors != null && !authors.isEmpty()) {
				lines.add(1, authorsLine(authors));
			}
		});
		registerStems();
		BlockRenderLayerMap.putBlock(ModBlocks.CARVING_BENCH, ChunkSectionLayer.CUTOUT); // see-through pixels in its textures
		DevScenarios.registerIfEnabled();
	}

	/** Same look as vanilla pumpkin stems: cutout, green turning yellow with age, attached ones fixed. */
	private static void registerStems() {
		Block[] stems = ModBlocks.STEMS.values().toArray(Block[]::new);
		Block[] attached = ModBlocks.ATTACHED_STEMS.values().toArray(Block[]::new);
		BlockRenderLayerMap.putBlocks(ChunkSectionLayer.CUTOUT, stems);
		BlockRenderLayerMap.putBlocks(ChunkSectionLayer.CUTOUT, attached);
		ColorProviderRegistry.BLOCK.register((state, level, pos, tint) -> {
			int age = state.getValue(StemBlock.AGE);
			return ARGB.color(age * 32, 255 - age * 8, age * 4);
		}, stems);
		ColorProviderRegistry.BLOCK.register((state, level, pos, tint) -> ATTACHED_STEM_COLOR, attached);
	}

	/** "Carved by A, B, C and N more", as in the item tooltip, flagged when the names came from a file. */
	public static Component authorsLine(AuthorList authors) {
		List<String> names = authors.names();
		String shown = String.join(", ", names.subList(0, Math.min(TOOLTIP_AUTHORS, names.size())));
		int more = names.size() - TOOLTIP_AUTHORS;
		MutableComponent line = more > 0
			? Component.translatable("tooltip.carves_and_crafts.carved_by_more", shown, more)
			: Component.translatable("tooltip.carves_and_crafts.carved_by", shown);
		if (authors.fromFile()) {
			line.append(" ").append(Component.translatable("tooltip.carves_and_crafts.from_file"));
		}
		return line.withStyle(ChatFormatting.GRAY);
	}
}
