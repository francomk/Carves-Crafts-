package com.studioderiva.carves_and_crafts.client.dev;

import com.studioderiva.carves_and_crafts.client.render.ClientDesigns;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.entity.player.Inventory;
import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.block.CustomPumpkinBlock;
import com.studioderiva.carves_and_crafts.block.AttachedPumpkinStemBlock;
import com.studioderiva.carves_and_crafts.variety.PumpkinVarieties;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.StemBlock;
import com.studioderiva.carves_and_crafts.block.LightSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import com.studioderiva.carves_and_crafts.design.AuthorList;
import com.studioderiva.carves_and_crafts.registry.ModAttachments;
import com.studioderiva.carves_and_crafts.schematic.Schematic;
import com.studioderiva.carves_and_crafts.schematic.SchematicLibrary;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import com.studioderiva.carves_and_crafts.design.EncodedDesign;
import com.studioderiva.carves_and_crafts.model.PumpkinModels;
import com.studioderiva.carves_and_crafts.network.SchematicActionPayload;
import com.studioderiva.carves_and_crafts.registry.ModBlocks;
import com.studioderiva.carves_and_crafts.block.entity.BenchSlots;
import com.studioderiva.carves_and_crafts.design.CanvasFace;
import com.studioderiva.carves_and_crafts.design.DesignCodec;
import com.studioderiva.carves_and_crafts.design.PumpkinDesign;
import com.studioderiva.carves_and_crafts.item.PaintbrushItem;
import com.studioderiva.carves_and_crafts.item.ToolBalance;
import com.studioderiva.carves_and_crafts.menu.CarvingBenchMenu;
import com.studioderiva.carves_and_crafts.network.ConfirmDesignPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Items;
import com.studioderiva.carves_and_crafts.block.entity.CarvingBenchBlockEntity;
import com.studioderiva.carves_and_crafts.block.entity.CustomPumpkinBlockEntity;
import com.studioderiva.carves_and_crafts.client.screen.CarvingBenchScreen;
import com.studioderiva.carves_and_crafts.client.screen.PumpkinEditorScreen;
import com.studioderiva.carves_and_crafts.design.EncodedDesign;
import com.studioderiva.carves_and_crafts.registry.ModComponents;
import com.studioderiva.carves_and_crafts.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import com.studioderiva.carves_and_crafts.client.schematic.LocalSchematics;
import com.studioderiva.carves_and_crafts.config.ServerConfig;
import com.studioderiva.carves_and_crafts.network.ImportSchematicPayload;
import com.studioderiva.carves_and_crafts.schematic.PumpkinFile;
import com.studioderiva.carves_and_crafts.schematic.PumpkinPresets;
import com.studioderiva.carves_and_crafts.dev.DevServerScenario;
import com.studioderiva.carves_and_crafts.client.ClientConfig;
import com.studioderiva.carves_and_crafts.block.BenchPart;
import com.studioderiva.carves_and_crafts.block.CarvingBenchBlock;
import net.minecraft.world.level.block.state.BlockState;
import com.studioderiva.carves_and_crafts.client.compat.JeiIntegration;
import com.studioderiva.carves_and_crafts.client.screen.ConfigScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Development aid: scripted scenarios run in the "Client (test scene)" run config, selected with
 * {@code -Dcarves_and_crafts.scenario=render|bench}. They drive the game with the same input events a player
 * produces, save in-game screenshots (run/screenshots) and quit. Captures the game framebuffer only.
 */
public final class DevScenarios {
	private static final int WARMUP_TICKS = 80;
	private static final BlockPos BENCH = new BlockPos(6, 100, 3);

	private record Step(int waitAfter, Consumer<Minecraft> action) {
	}

	private static final List<Step> steps = new ArrayList<>();
	/** Screenshots normally hide the HUD; the compat scenario needs it for the Jade/WTHIT overlay. */
	private static boolean showHud;
	private static int ticks;
	private static int stepIndex;
	private static int waitUntil;

	private DevScenarios() {
	}

	public static void registerIfEnabled() {
		String scenario = System.getProperty("carves_and_crafts.scenario", "");
		if (!scenario.equals("large")) {
			// the test world keeps the player's inventory between runs: start every scenario empty-handed
			steps.add(new Step(5, mc -> runCommand(mc, "clear @a")));
		}
		switch (scenario) {
			case "render" -> renderScenario();
			case "bench" -> benchScenario();
			case "tools" -> toolsScenario();
			case "schematics" -> schematicsScenario();
			case "schematics_reload" -> schematicsReloadScenario();
			case "models" -> modelsScenario();
			case "light" -> lightScenario();
			case "farm" -> farmScenario();
			case "files" -> filesScenario();
			case "presets" -> presetsScenario();
			case "large" -> largeRemoteScenario();
			case "compat" -> compatScenario();
			case "bench_model" -> benchModelScenario();
			case "bench_parts" -> benchPartsScenario();
			case "items" -> itemsScenario();
			case "colors" -> colorsScenario();
			default -> {
				return;
			}
		}
		steps.add(new Step(5, mc -> {
			CarvesAndCrafts.LOGGER.info("Scenario done");
			mc.stop();
		}));
		ClientTickEvents.END_CLIENT_TICK.register(DevScenarios::tick);
	}

	private static void tick(Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}
		ticks++;
		client.options.hideGui = !showHud;
		if (ticks < WARMUP_TICKS || ticks < waitUntil || stepIndex >= steps.size()) {
			return;
		}
		Step step = steps.get(stepIndex++);
		try {
			step.action.accept(client);
		} catch (RuntimeException e) {
			CarvesAndCrafts.LOGGER.error("Scenario step {} failed", stepIndex, e);
		}
		waitUntil = ticks + step.waitAfter;
	}

	// ------------------------------------------------------------------ scenarios

	/** Every model in a row (front facing the camera), then carved ones: demo faces, a wart over a cut, a diagonal. */
	private static void renderScenario() {
		steps.add(new Step(20, mc -> {
			runCommand(mc, "gamemode creative @a");
			runCommand(mc, "fill -1 100 -1 18 104 6 air");
			for (int i = 0; i < PumpkinModels.ALL.size(); i++) {
				runCommand(mc, "setblock " + (i * 2) + " 100 4 " + CarvesAndCrafts.MOD_ID + ":" + PumpkinModels.ALL.get(i).id() + "[rotation=0]");
			}
			carvedRow(mc);
		}));
		shotFrom("p6b_row_front", 8.5, 100.4, 7.2, 180, 25);
		shotFrom("p6b_row_side", 18.5, 100.2, 6.5, 120, 25);
		shotFrom("p6b_carved_classic", 2.5, 99.75, 2.9, 180, 25);
		shotFrom("p6b_carved_warty", 5.5, 99.85, 2.8, 180, 15);
		shotFrom("p6b_carved_white", 8.5, 99.7, 2.8, 180, 25);
		shotFrom("p6b_carved_blue", 12.4, 99.8, 2.6, 155, 25);
		shotFrom("p6b_carved_inside", 2.5, 99.4, 2.0, 180, 5);
		// block renderer vs vanilla baked model of the same pumpkin; item displays turn the item 180°, like rotation 0
		steps.add(new Step(10, mc -> {
			runCommand(mc, "setblock 14 100 -1 carves_and_crafts:classic_pumpkin[rotation=0]");
			runCommand(mc, "summon item_display 15.5 100.5 -0.5 {item:{id:\"carves_and_crafts:classic_pumpkin\",count:1}}");
			runCommand(mc, "setblock 14 100 -3 carves_and_crafts:warty_pumpkin[rotation=0]");
			runCommand(mc, "summon item_display 15.5 100.5 -2.5 {item:{id:\"carves_and_crafts:warty_pumpkin\",count:1}}");
		}));
		shotFrom("p6b_vs_vanilla_classic", 15.0, 99.9, -2.3, 0, 25);
		shotFrom("p6b_vs_vanilla_warty", 15.0, 99.9, -4.3, 0, 25);
		shotFrom("p6b_vs_vanilla_side", 17.3, 100.0, -1.5, 90, 20);
	}

	/** Second row, z = 1: classic x2 demo, warty x1 with a cut under one wart, white x4 demo, blue x2 demo turned 45°. */
	private static void carvedRow(Minecraft mc) {
		String ns = CarvesAndCrafts.MOD_ID + ":";
		runCommand(mc, "setblock 2 100 1 " + ns + "classic_pumpkin[rotation=0]");
		runCommand(mc, "dpumpkin 2 100 1 init 2");
		runCommand(mc, "dpumpkin 2 100 1 demo");
		runCommand(mc, "setblock 5 100 1 " + ns + "warty_pumpkin[rotation=0]");
		runCommand(mc, "dpumpkin 5 100 1 init 1");
		runCommand(mc, "dpumpkin 5 100 1 cut north 5 2");
		runCommand(mc, "dpumpkin 5 100 1 fill north 3 9 5 9 cut");
		runCommand(mc, "dpumpkin 5 100 1 paint north 7 5 #2E7D32");
		runCommand(mc, "setblock 8 100 1 " + ns + "white_pumpkin[rotation=0]");
		runCommand(mc, "dpumpkin 8 100 1 init 4");
		runCommand(mc, "dpumpkin 8 100 1 demo");
		runCommand(mc, "setblock 11 100 1 " + ns + "blue_pumpkin[rotation=1]");
		runCommand(mc, "dpumpkin 11 100 1 init 2");
		runCommand(mc, "dpumpkin 11 100 1 demo");
	}

	private static void benchScenario() {
		steps.add(new Step(10, mc -> {
			runCommand(mc, "gamemode creative @a"); // tool costs are covered by the tools scenario
			runCommand(mc, "setblock " + BENCH.getX() + " " + BENCH.getY() + " " + BENCH.getZ() + " carves_and_crafts:carving_bench[facing=south]");
			teleport(mc, 6.5, 100, 5.5, 180, 30);
		}));
		steps.add(new Step(10, mc -> serverBench(mc, bench -> bench.setItem(0, new ItemStack(ModItems.CLASSIC_PUMPKIN)))));
		// right click the bench like a player
		steps.add(new Step(15, mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(BENCH).add(0, 0, 0.5), Direction.SOUTH, BENCH, false))));
		steps.add(new Step(5, mc -> {
			expect(mc.screen instanceof CarvingBenchScreen, "bench screen open");
			shot(mc, "bench_1");
		}));
		steps.add(new Step(10, mc -> click(mc, button(mc, "gui.carves_and_crafts.carve"))));
		steps.add(new Step(5, mc -> {
			expect(mc.screen instanceof PumpkinEditorScreen, "editor open");
			shot(mc, "editor_1");
		}));
		steps.add(new Step(5, mc -> click(mc, buttonByText(mc, "x2 (20×18)"))));
		// knife: carve two eyes and a mouth with strokes
		steps.add(new Step(5, mc -> {
			PumpkinEditorScreen editor = (PumpkinEditorScreen) mc.screen;
			stroke(editor, 4, 5, 5, 5);
			stroke(editor, 4, 6, 5, 6);
			stroke(editor, 10, 5, 11, 5);
			stroke(editor, 10, 6, 11, 6);
			stroke(editor, 4, 10, 11, 10);
			stroke(editor, 5, 11, 10, 11);
		}));
		// brush: pick the red dye swatch via hex box and paint a band at the top
		steps.add(new Step(5, mc -> click(mc, button(mc, "gui.carves_and_crafts.tool.paint"))));
		steps.add(new Step(5, mc -> {
			PumpkinEditorScreen editor = (PumpkinEditorScreen) mc.screen;
			for (GuiEventListener child : editor.children()) {
				if (child instanceof EditBox box) {
					box.setValue("#7B1FA2");
				}
			}
			stroke(editor, 1, 1, 14, 1);
			stroke(editor, 1, 14, 14, 14);
		}));
		steps.add(new Step(5, mc -> shot(mc, "editor_2")));
		// Esc with changes asks before discarding
		steps.add(new Step(5, mc -> mc.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0))));
		steps.add(new Step(5, mc -> {
			expect(mc.screen instanceof PumpkinEditorScreen, "editor still open after Esc");
			shot(mc, "editor_3");
		}));
		steps.add(new Step(5, mc -> click(mc, button(mc, "gui.carves_and_crafts.editor.keep"))));
		steps.add(new Step(15, mc -> click(mc, button(mc, "gui.carves_and_crafts.editor.confirm"))));
		steps.add(new Step(5, mc -> {
			expect(mc.screen instanceof CarvingBenchScreen, "back to bench after confirm");
			serverBench(mc, bench -> {
				EncodedDesign design = bench.getItem(0).get(ModComponents.DESIGN);
				expect(design != null, "server stored the design");
				if (design != null) {
					CarvesAndCrafts.LOGGER.info("SCENARIO bench design {} bytes, hash {}", design.size(), design.hash());
				}
			});
			shot(mc, "bench_2");
		}));
		// take the pumpkin out (shift-click) and place it to look at the result
		steps.add(new Step(10, mc -> {
			mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, 0, 0, ClickType.QUICK_MOVE, mc.player);
		}));
		steps.add(new Step(10, mc -> {
			mc.player.closeContainer();
			runCommand(mc, "setblock 9 100 4 carves_and_crafts:classic_pumpkin[rotation=0]");
			serverPlayer(mc, sp -> {
				ItemStack carried = sp.getInventory().getItem(firstPumpkinSlot(sp));
				EncodedDesign design = carried.get(ModComponents.DESIGN);
				expect(design != null, "pumpkin left the bench with its design");
				boolean placed = sp.level().getBlockEntity(new BlockPos(9, 100, 4)) instanceof CustomPumpkinBlockEntity be && design != null;
				expect(placed, "result pumpkin placed");
				if (placed) {
					((CustomPumpkinBlockEntity) sp.level().getBlockEntity(new BlockPos(9, 100, 4))).setDesign(design.decode());
				}
			});
		}));
		shotFrom("bench_3", 9.5, 100.2, 6.6, 180, 20);
	}

	private static void toolsScenario() {
		steps.add(new Step(10, mc -> {
			runCommand(mc, "setblock " + BENCH.getX() + " " + BENCH.getY() + " " + BENCH.getZ() + " carves_and_crafts:carving_bench[facing=south]");
			runCommand(mc, "gamemode survival @a");
			runCommand(mc, "recipe give @a carves_and_crafts:carving_knife");
			runCommand(mc, "recipe give @a carves_and_crafts:paintbrush");
			teleport(mc, 6.5, 100, 5.5, 180, 30);
		}));
		steps.add(new Step(10, mc -> serverBench(mc, bench -> {
			bench.setItem(BenchSlots.PUMPKIN, new ItemStack(ModItems.CLASSIC_PUMPKIN));
			bench.setItem(BenchSlots.KNIFE, new ItemStack(ModItems.CARVING_KNIFE));
			bench.setItem(BenchSlots.BRUSH, new ItemStack(ModItems.PAINTBRUSH));
			bench.setItem(BenchSlots.PALETTE, new ItemStack(ModItems.COLOR_PALETTE));
			for (DyeColor color : DyeColor.values()) {
				bench.setItem(BenchSlots.FIRST_DYE + color.getId(), new ItemStack(DyeItem.byColor(color), 2));
			}
			bench.setItem(BenchSlots.PLANK, new ItemStack(Items.OAK_PLANKS, 2));
		})));
		steps.add(new Step(15, mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(BENCH).add(0, 0, 0.5), Direction.SOUTH, BENCH, false))));
		steps.add(new Step(5, mc -> {
			expect(mc.screen instanceof CarvingBenchScreen, "bench screen open");
			expect(PaintbrushItem.charge(menu(mc).getBrush()) == 0, "new paintbrush is empty");
			shot(mc, "p4_bench_1");
		}));
		// recharge the empty brush
		steps.add(new Step(10, mc -> click(mc, buttonByText(mc, "+"))));
		steps.add(new Step(5, mc -> serverBench(mc, bench -> {
			expect(PaintbrushItem.charge(bench.getItem(BenchSlots.BRUSH)) == ToolBalance.BRUSH_CAPACITY, "brush recharged");
			expect(bench.getItem(BenchSlots.PALETTE).isEmpty(), "palette consumed");
		})));
		// carve 8 pixels, paint 12 pixels, confirm
		steps.add(new Step(5, mc -> click(mc, button(mc, "gui.carves_and_crafts.carve"))));
		steps.add(new Step(5, mc -> click(mc, buttonByText(mc, "x2 (20×18)"))));
		steps.add(new Step(5, mc -> {
			PumpkinEditorScreen editor = (PumpkinEditorScreen) mc.screen;
			stroke(editor, 4, 6, 11, 6);
		}));
		steps.add(new Step(5, mc -> click(mc, button(mc, "gui.carves_and_crafts.tool.paint"))));
		steps.add(new Step(5, mc -> {
			PumpkinEditorScreen editor = (PumpkinEditorScreen) mc.screen;
			stroke(editor, 2, 12, 13, 12);
			shot(mc, "p4_editor_1");
		}));
		steps.add(new Step(15, mc -> click(mc, button(mc, "gui.carves_and_crafts.editor.confirm"))));
		steps.add(new Step(5, mc -> serverBench(mc, bench -> {
			ItemStack knife = bench.getItem(BenchSlots.KNIFE);
			expect(knife.getDamageValue() == 8, "knife wore 8 (was " + knife.getDamageValue() + ")");
			int charge = PaintbrushItem.charge(bench.getItem(BenchSlots.BRUSH));
			expect(charge == ToolBalance.BRUSH_CAPACITY - 12, "brush used 12 (left " + charge + ")");
			expect(bench.getItem(BenchSlots.PUMPKIN).has(ModComponents.DESIGN), "design applied");
			// knife nearly broken for the next checks
			ItemStack worn = knife.copy();
			worn.setDamageValue(worn.getMaxDamage() - 1);
			bench.setItem(BenchSlots.KNIFE, worn);
		})));
		// not enough knife: confirm disabled, and a forged packet is refused
		steps.add(new Step(10, mc -> click(mc, button(mc, "gui.carves_and_crafts.carve"))));
		steps.add(new Step(5, mc -> {
			PumpkinEditorScreen editor = (PumpkinEditorScreen) mc.screen;
			stroke(editor, 4, 9, 6, 9);
		}));
		steps.add(new Step(5, mc -> {
			expect(!button(mc, "gui.carves_and_crafts.editor.confirm").active, "confirm disabled without enough knife");
			shot(mc, "p4_editor_2");
			EncodedDesign current = menu(mc).getPumpkin().get(ModComponents.DESIGN);
			PumpkinDesign forged = current.decode();
			forged.cut(CanvasFace.NORTH, 4, 9);
			forged.cut(CanvasFace.NORTH, 5, 9);
			ClientPlayNetworking.send(new ConfirmDesignPayload(menu(mc).containerId, current.hash(), DesignCodec.encode(forged), false));
		}));
		steps.add(new Step(5, mc -> serverBench(mc, bench -> {
			expect(!bench.getItem(BenchSlots.PUMPKIN).get(ModComponents.DESIGN).decode().isCut(CanvasFace.NORTH, 4, 9), "forged edit refused");
			expect(!bench.getItem(BenchSlots.KNIFE).isEmpty(), "knife untouched by refused edit");
		})));
		// exactly one cut left: the knife breaks
		steps.add(new Step(5, mc -> {
			PumpkinEditorScreen editor = (PumpkinEditorScreen) mc.screen;
			editor.keyPressed(new KeyEvent(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL));
			stroke(editor, 4, 9, 4, 9);
		}));
		steps.add(new Step(15, mc -> click(mc, button(mc, "gui.carves_and_crafts.editor.confirm"))));
		steps.add(new Step(5, mc -> serverBench(mc, bench -> {
			expect(bench.getItem(BenchSlots.KNIFE).isEmpty(), "knife broke at zero");
			expect(bench.getItem(BenchSlots.PUMPKIN).get(ModComponents.DESIGN).decode().isCut(CanvasFace.NORTH, 4, 9), "last cut applied");
		})));
		// palette crafting tab
		steps.add(new Step(10, mc -> click(mc, button(mc, "gui.carves_and_crafts.tab.palette"))));
		steps.add(new Step(5, mc -> shot(mc, "p4_palette")));
		steps.add(new Step(10, mc -> mc.gameMode.handleInventoryMouseClick(menu(mc).containerId, CarvingBenchMenu.RESULT_SLOT, 0, ClickType.QUICK_MOVE, mc.player)));
		steps.add(new Step(5, mc -> serverPlayer(mc, sp -> {
			expect(sp.getInventory().countItem(ModItems.COLOR_PALETTE) == 1, "crafted one palette");
			if (sp.containerMenu instanceof CarvingBenchMenu menu) {
				expect(menu.getSlot(BenchSlots.FIRST_DYE).getItem().getCount() == 1, "one of each dye consumed");
				// dupe attempt: remove an input, then grab the stale preview in the same tick
				menu.getSlot(BenchSlots.FIRST_DYE + 5).set(ItemStack.EMPTY);
			}
		})));
		steps.add(new Step(10, mc -> mc.gameMode.handleInventoryMouseClick(menu(mc).containerId, CarvingBenchMenu.RESULT_SLOT, 0, ClickType.QUICK_MOVE, mc.player)));
		steps.add(new Step(5, mc -> serverPlayer(mc, sp ->
			expect(sp.getInventory().countItem(ModItems.COLOR_PALETTE) == 1, "no palette without all dyes"))));
		steps.add(new Step(5, mc -> shot(mc, "p4_palette_2")));
	}

	private static void schematicsScenario() {
		openBenchWithTools(true);
		steps.add(new Step(5, mc -> serverPlayer(mc, sp -> sp.setAttached(ModAttachments.SCHEMATICS, SchematicLibrary.EMPTY))));
		// carve with "save schematic" on
		steps.add(new Step(5, mc -> click(mc, button(mc, "gui.carves_and_crafts.carve"))));
		steps.add(new Step(5, mc -> click(mc, buttonByText(mc, "x4 (40×36)"))));
		steps.add(new Step(5, mc -> {
			PumpkinEditorScreen editor = (PumpkinEditorScreen) mc.screen;
			stroke(editor, 8, 10, 12, 10);
			stroke(editor, 20, 10, 24, 10);
			stroke(editor, 8, 22, 24, 22);
		}));
		steps.add(new Step(5, mc -> click(mc, button(mc, "gui.carves_and_crafts.editor.save_off"))));
		steps.add(new Step(15, mc -> click(mc, button(mc, "gui.carves_and_crafts.editor.confirm"))));
		steps.add(new Step(5, mc -> serverPlayer(mc, sp -> {
			SchematicLibrary library = sp.getAttachedOrCreate(ModAttachments.SCHEMATICS);
			expect(library.entries().size() == 1, "schematic saved on confirm");
			AuthorList authors = menuOf(sp).getPumpkin().get(ModComponents.AUTHORS);
			expect(authors != null && authors.contains(sp.getUUID()), "player recorded as author");
		})));
		// tooltip of the carved pumpkin
		steps.add(new Step(5, mc -> {
			ItemStack pumpkin = menu(mc).getPumpkin();
			boolean hasLine = pumpkin.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL).stream()
				.anyMatch(line -> line.getString().startsWith("Carved by " + mc.player.getName().getString()));
			expect(hasLine, "tooltip shows the author");
		}));
		// library tab
		steps.add(new Step(15, mc -> click(mc, button(mc, "gui.carves_and_crafts.tab.schematics"))));
		steps.add(new Step(5, mc -> {
			shot(mc, "p5_library_1");
			// swap in a blank pumpkin and copy the schematic onto it
			serverBench(mc, bench -> bench.setItem(BenchSlots.PUMPKIN, new ItemStack(ModItems.CLASSIC_PUMPKIN)));
		}));
		steps.add(new Step(5, mc -> clickListRow(mc, 0)));
		steps.add(new Step(15, mc -> click(mc, button(mc, "gui.carves_and_crafts.schematic.copy"))));
		steps.add(new Step(5, mc -> serverPlayer(mc, sp -> {
			CarvingBenchMenu menu = menuOf(sp);
			Schematic schematic = sp.getAttachedOrCreate(ModAttachments.SCHEMATICS).entries().get(0);
			expect(schematic.design().equals(menu.getPumpkin().get(ModComponents.DESIGN)), "copy applied the schematic design");
			expect(schematic.authors().equals(menu.getPumpkin().get(ModComponents.AUTHORS)), "copy kept the original authors");
			// 5 + 5 + 17 cuts, twice (carve + copy)
			expect(menu.getKnife().getDamageValue() == 54, "copy paid full knife cost (damage " + menu.getKnife().getDamageValue() + ")");
		})));
		// copy refused on a pumpkin that already has a design
		steps.add(new Step(15, mc -> click(mc, button(mc, "gui.carves_and_crafts.schematic.copy"))));
		steps.add(new Step(5, mc -> serverPlayer(mc, sp ->
			expect(menuOf(sp).getKnife().getDamageValue() == 54, "no second copy on a carved pumpkin"))));
		// save from the pumpkin with a name, rename, delete
		steps.add(new Step(5, mc -> setNameBox(mc, "Copied §cface")));
		steps.add(new Step(5, mc -> click(mc, button(mc, "gui.carves_and_crafts.schematic.save"))));
		steps.add(new Step(15, mc -> serverPlayer(mc, sp -> {
			SchematicLibrary library = sp.getAttachedOrCreate(ModAttachments.SCHEMATICS);
			expect(library.entries().size() == 2 && library.entries().get(0).name().equals("Copied face"), "saved from pumpkin with sanitized name");
		})));
		steps.add(new Step(5, mc -> clickListRow(mc, 1)));
		steps.add(new Step(5, mc -> setNameBox(mc, "Spooky")));
		steps.add(new Step(5, mc -> click(mc, button(mc, "gui.carves_and_crafts.schematic.rename"))));
		steps.add(new Step(15, mc -> shot(mc, "p5_library_2")));
		steps.add(new Step(5, mc -> clickListRow(mc, 0)));
		steps.add(new Step(5, mc -> click(mc, button(mc, "gui.carves_and_crafts.schematic.delete"))));
		steps.add(new Step(5, mc -> serverPlayer(mc, sp ->
			expect(sp.getAttachedOrCreate(ModAttachments.SCHEMATICS).entries().size() == 2, "first delete click only asks"))));
		steps.add(new Step(5, mc -> click(mc, button(mc, "gui.carves_and_crafts.schematic.delete_confirm"))));
		steps.add(new Step(15, mc -> serverPlayer(mc, sp -> {
			SchematicLibrary library = sp.getAttachedOrCreate(ModAttachments.SCHEMATICS);
			expect(library.entries().size() == 1 && library.entries().get(0).name().equals("Spooky"),
				"deleted one, renamed one kept " + library.entries().stream().map(Schematic::name).toList());
		})));
		steps.add(new Step(5, mc -> shot(mc, "p5_library_3")));
	}

	private static void modelsScenario() {
		steps.add(new Step(10, mc -> {
			runCommand(mc, "fill -1 100 -1 18 104 6 air");
			runCommand(mc, "setblock 0 100 5 carves_and_crafts:classic_pumpkin[rotation=0]");
			runCommand(mc, "setblock 2 100 5 carves_and_crafts:warty_pumpkin[rotation=3]");
		}));
		steps.add(new Step(5, mc -> serverPlayer(mc, sp -> {
			// classic: body 3..13, base/caps inside it, stem tip (rotated) left out: 10 wide, up to the stem top 12.8
			var classic = sp.level().getBlockState(new BlockPos(0, 100, 5)).getShape(sp.level(), new BlockPos(0, 100, 5)).bounds();
			expect(Math.abs(classic.getXsize() - 10 / 16.0) < 1e-6 && Math.abs(classic.maxY - 12.8 / 16.0) < 1e-4,
				"classic hitbox follows the unrotated boxes " + classic);
			// warty stem goes to 16.1: clipped to the block
			var warty = sp.level().getBlockState(new BlockPos(2, 100, 5)).getShape(sp.level(), new BlockPos(2, 100, 5)).bounds();
			expect(warty.maxY <= 1.0 && warty.minX >= 2.9 / 16 - 1e-6, "warty hitbox stays in the block " + warty);
		})));
		// a mini pumpkin in the bench: its densities, and a classic schematic can't be copied onto it
		steps.add(new Step(10, mc -> {
			runCommand(mc, "setblock " + BENCH.getX() + " " + BENCH.getY() + " " + BENCH.getZ() + " carves_and_crafts:carving_bench[facing=south]");
			runCommand(mc, "gamemode creative @a");
			teleport(mc, 6.5, 100, 5.5, 180, 30);
		}));
		steps.add(new Step(10, mc -> serverPlayer(mc, sp -> {
			if (sp.level().getBlockEntity(BENCH) instanceof CarvingBenchBlockEntity bench) {
				bench.setItem(BenchSlots.PUMPKIN, new ItemStack(ModBlocks.PUMPKINS.get(PumpkinModels.MINI_YELLOW)));
			}
			PumpkinDesign classicDesign = new PumpkinDesign(PumpkinModels.CLASSIC.layout(1));
			classicDesign.cut(CanvasFace.NORTH, 3, 3);
			sp.setAttached(ModAttachments.SCHEMATICS, SchematicLibrary.EMPTY
				.add("Classic face", PumpkinModels.CLASSIC.id(), EncodedDesign.of(classicDesign), AuthorList.EMPTY, 0, 0).orElseThrow().library());
		})));
		steps.add(new Step(15, mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(BENCH).add(0, 0, 0.5), Direction.SOUTH, BENCH, false))));
		steps.add(new Step(5, mc -> click(mc, button(mc, "gui.carves_and_crafts.carve"))));
		steps.add(new Step(5, mc -> {
			boolean x1 = mc.screen.children().stream().anyMatch(c -> c instanceof AbstractWidget w && w.visible && w.getMessage().getString().equals("x1 (6×5)"));
			boolean x4 = mc.screen.children().stream().anyMatch(c -> c instanceof AbstractWidget w && w.visible && w.getMessage().getString().equals("x4 (24×20)"));
			expect(x1 && x4, "mini pumpkin editor offers canvases sized from its body");
			shot(mc, "p6b_editor_density");
		}));
		steps.add(new Step(5, mc -> click(mc, buttonByText(mc, "x4 (24×20)"))));
		steps.add(new Step(5, mc -> {
			PumpkinEditorScreen editor = (PumpkinEditorScreen) mc.screen;
			stroke(editor, 3, 4, 20, 4);
			stroke(editor, 3, 15, 20, 15);
		}));
		steps.add(new Step(5, mc -> {
			expect(((PumpkinEditorScreen) mc.screen).pixelCenterOnScreen(23, 19) != null, "mini canvas is 24x20");
			shot(mc, "p6b_editor_mini");
		}));
		steps.add(new Step(5, mc -> mc.screen.onClose()));
		steps.add(new Step(5, mc -> click(mc, button(mc, "gui.carves_and_crafts.editor.discard"))));
		steps.add(new Step(10, mc -> click(mc, button(mc, "gui.carves_and_crafts.tab.schematics"))));
		steps.add(new Step(10, mc -> clickListRow(mc, 0)));
		steps.add(new Step(5, mc -> {
			expect(!button(mc, "gui.carves_and_crafts.schematic.copy").active, "copy disabled for another model");
			shot(mc, "p6b_library_other_model");
			ClientPlayNetworking.send(new SchematicActionPayload(menu(mc).containerId, SchematicActionPayload.Action.APPLY,
				((CarvingBenchScreen) mc.screen).selectedSchematic(), "", "", 0));
		}));
		steps.add(new Step(10, mc -> serverPlayer(mc, sp ->
			expect(!menuOf(sp).getPumpkin().has(ModComponents.DESIGN), "server refuses a schematic of another model"))));
	}

	/** Light sources put inside pumpkins by a survival player, light levels, drops; then the lanterns at night. */
	private static void lightScenario() {
		BlockPos torchAt = new BlockPos(2, 100, 1);
		BlockPos candleAt = new BlockPos(5, 100, 1);
		steps.add(new Step(20, mc -> {
			runCommand(mc, "fill -1 100 -1 18 104 6 air");
			runCommand(mc, "gamemode survival @a");
			runCommand(mc, "time set midnight");
			runCommand(mc, "gamerule doDaylightCycle false");
			runCommand(mc, "setblock 2 100 1 carves_and_crafts:classic_pumpkin[rotation=0]");
			runCommand(mc, "setblock 5 100 1 carves_and_crafts:warty_pumpkin[rotation=0]");
			runCommand(mc, "setblock 8 100 1 carves_and_crafts:white_pumpkin[rotation=0]");
			runCommand(mc, "setblock 11 100 1 carves_and_crafts:blue_pumpkin[rotation=0]");
			runCommand(mc, "setblock 14 100 1 carves_and_crafts:kabocha_squash[rotation=0]");
			for (int x : new int[] {2, 5, 8, 11, 14}) {
				runCommand(mc, "dpumpkin " + x + " 100 1 init 2");
				runCommand(mc, "dpumpkin " + x + " 100 1 demo");
			}
			teleport(mc, 3.5, 100, 3.5, 180, 30);
		}));
		// a survival player right-clicks with a torch
		steps.add(new Step(10, mc -> {
			serverPlayer(mc, sp -> sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH, 2)));
		}));
		steps.add(new Step(10, mc -> useOn(mc, torchAt)));
		steps.add(new Step(10, mc -> serverPlayer(mc, sp -> {
			var state = sp.level().getBlockState(torchAt);
			expect(state.getValue(CustomPumpkinBlock.LIGHT) == LightSource.TORCH, "torch put inside");
			expect(sp.level().getBrightness(LightLayer.BLOCK, torchAt) == 14, "torch light 14 (was " + sp.level().getBrightness(LightLayer.BLOCK, torchAt) + ")");
			expect(sp.getMainHandItem().getCount() == 1, "one torch used");
		})));
		// already lit: a second torch does nothing
		steps.add(new Step(10, mc -> useOn(mc, torchAt)));
		steps.add(new Step(10, mc -> serverPlayer(mc, sp ->
			expect(sp.getMainHandItem().getCount() == 1, "lit pumpkin takes no second source"))));
		// any candle color: light 3
		steps.add(new Step(10, mc -> serverPlayer(mc, sp -> sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RED_CANDLE)))));
		steps.add(new Step(10, mc -> useOn(mc, candleAt)));
		steps.add(new Step(10, mc -> serverPlayer(mc, sp -> {
			expect(sp.level().getBlockState(candleAt).getValue(CustomPumpkinBlock.LIGHT) == LightSource.CANDLE, "red candle put inside");
			// world light here also gets the torch pumpkin 3 blocks away: check what the block itself emits
			expect(sp.level().getBlockState(candleAt).getLightEmission() == 3, "candle light 3");
			expect(sp.getMainHandItem().isEmpty(), "candle used");
		})));
		// other sources, set directly
		steps.add(new Step(10, mc -> serverPlayer(mc, sp -> {
			lightDirectly(sp, new BlockPos(8, 100, 1), LightSource.SOUL_TORCH, Items.SOUL_TORCH);
			lightDirectly(sp, new BlockPos(11, 100, 1), LightSource.REDSTONE_TORCH, Items.REDSTONE_TORCH);
		})));
		// copper torch through the player, like the torch
		steps.add(new Step(10, mc -> serverPlayer(mc, sp -> sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COPPER_TORCH)))));
		steps.add(new Step(25, mc -> teleport(mc, 14.5, 100, 3.5, 180, 30)));
		steps.add(new Step(10, mc -> useOn(mc, new BlockPos(14, 100, 1))));
		steps.add(new Step(10, mc -> serverPlayer(mc, sp -> {
			expect(sp.level().getBlockState(new BlockPos(8, 100, 1)).getLightEmission() == 10, "soul torch light 10");
			expect(sp.level().getBlockState(new BlockPos(11, 100, 1)).getLightEmission() == 7, "redstone torch light 7");
			expect(sp.level().getBlockState(new BlockPos(14, 100, 1)).getValue(CustomPumpkinBlock.LIGHT) == LightSource.COPPER_TORCH
				&& sp.level().getBlockState(new BlockPos(14, 100, 1)).getLightEmission() == 14, "copper torch put inside, light 14");
			expect(sp.level().getSignal(new BlockPos(11, 100, 2), Direction.SOUTH) == 0, "redstone torch pumpkin gives no signal");
		})));
		steps.add(new Step(10, mc -> runCommand(mc, "kill @e[type=item]")));
		shotFrom("p7_lanterns_front", 8.5, 100.6, 5.5, 180, 15);
		shotFrom("p7_lantern_torch", 2.5, 99.75, 2.9, 180, 25);
		shotFrom("p7_lantern_candle", 5.5, 99.85, 2.8, 180, 15);
		shotFrom("p7_lantern_soul", 8.5, 99.7, 2.8, 180, 25);
		shotFrom("p7_lantern_redstone", 11.5, 99.8, 2.8, 180, 25);
		shotFrom("p7_lantern_copper", 14.5, 99.8, 2.8, 180, 25);
		// breaking drops the pumpkin and the exact source
		steps.add(new Step(20, mc -> serverPlayer(mc, sp -> {
			sp.level().destroyBlock(torchAt, true, sp);
			sp.level().destroyBlock(candleAt, true, sp);
		})));
		steps.add(new Step(5, mc -> serverPlayer(mc, sp -> {
			var items = sp.level().getEntitiesOfClass(ItemEntity.class, new AABB(torchAt).inflate(5)).stream().map(ItemEntity::getItem).toList();
			expect(items.stream().anyMatch(i -> i.is(Items.TORCH)), "torch dropped on break " + items);
			expect(items.stream().anyMatch(i -> i.is(Items.RED_CANDLE)), "red candle dropped on break");
			expect(items.stream().anyMatch(i -> i.is(ModItems.CLASSIC_PUMPKIN) && i.has(ModComponents.DESIGN)), "pumpkin dropped with its design");
			// only the soul torch pumpkin 6 blocks away still reaches here: 10 - 6
			expect(sp.level().getBrightness(LightLayer.BLOCK, torchAt) == 4, "light gone with the pumpkin (" + sp.level().getBrightness(LightLayer.BLOCK, torchAt) + ")");
		})));
	}

	private static void lightDirectly(ServerPlayer sp, BlockPos pos, LightSource source, Item item) {
		if (sp.level().getBlockEntity(pos) instanceof CustomPumpkinBlockEntity pumpkin) {
			pumpkin.setLightItem(new ItemStack(item));
			sp.level().setBlockAndUpdate(pos, sp.level().getBlockState(pos).setValue(CustomPumpkinBlock.LIGHT, source));
		}
	}

	/** Right click on the south face of a block, like a player. */
	private static void useOn(Minecraft mc, BlockPos pos) {
		mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0, 0.3), Direction.SOUTH, pos, false));
	}

	/** Seeds, stems, weighted harvest, seed crafting and wild patches. */
	private static void farmScenario() {
		BlockPos stem = new BlockPos(2, 100, 1);
		steps.add(new Step(20, mc -> {
			runCommand(mc, "fill -1 100 -4 18 104 10 air"); // also the area searched for the wild patch below
			runCommand(mc, "fill -1 99 -1 18 99 6 grass_block");
			runCommand(mc, "time set noon");
			runCommand(mc, "gamemode survival @a");
			runCommand(mc, "fill 2 99 1 2 99 1 farmland");
			runCommand(mc, "fill 6 99 1 6 99 1 farmland");
			teleport(mc, 4.5, 100, 4.5, 180, 40);
		}));
		// a survival player plants seeds on farmland
		steps.add(new Step(10, mc -> serverPlayer(mc, sp ->
			sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SEEDS.get(PumpkinVarieties.FIELD), 2)))));
		steps.add(new Step(10, mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(stem.below()).add(0, 0.5, 0), Direction.UP, stem.below(), false))));
		steps.add(new Step(10, mc -> serverPlayer(mc, sp -> {
			expect(sp.level().getBlockState(stem).is(ModBlocks.STEMS.get(PumpkinVarieties.FIELD)), "seed planted a field stem");
			expect(sp.getMainHandItem().getCount() == 1, "one seed used");
			// bone meal like vanilla, then let it grow until it sets fruit
			for (int i = 0; i < 10 && sp.level().getBlockState(stem).is(ModBlocks.STEMS.get(PumpkinVarieties.FIELD)); i++) {
				BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), sp.level(), stem);
			}
			for (int i = 0; i < 5000 && sp.level().getBlockState(stem).is(ModBlocks.STEMS.get(PumpkinVarieties.FIELD)); i++) {
				sp.level().getBlockState(stem).randomTick(sp.level(), stem, sp.level().random);
			}
			var attached = sp.level().getBlockState(stem);
			expect(attached.is(ModBlocks.ATTACHED_STEMS.get(PumpkinVarieties.FIELD)), "stem set fruit and bent toward it");
			if (attached.hasProperty(AttachedPumpkinStemBlock.FACING)) {
				BlockPos fruit = stem.relative(attached.getValue(AttachedPumpkinStemBlock.FACING));
				boolean ok = sp.level().getBlockState(fruit).getBlock() instanceof CustomPumpkinBlock p && PumpkinVarieties.FIELD.grows(p.model())
					&& sp.level().getBlockEntity(fruit) instanceof CustomPumpkinBlockEntity be && be.getDesign() == null;
				expect(ok, "fruit is a blank field pumpkin " + sp.level().getBlockState(fruit));
				sp.level().destroyBlock(fruit, true, sp);
				expect(sp.level().getBlockState(stem).is(ModBlocks.STEMS.get(PumpkinVarieties.FIELD))
					&& sp.level().getBlockState(stem).getValue(StemBlock.AGE) == StemBlock.MAX_AGE, "picking the fruit leaves a grown stem");
			}
		})));
		// harvest weights come from the data pack: 70 / 20 / 10
		steps.add(new Step(5, mc -> serverPlayer(mc, sp -> {
			Map<String, Integer> counts = new HashMap<>();
			for (int i = 0; i < 10000; i++) {
				counts.merge(PumpkinVarieties.FIELD.pick(sp.level().random).id(), 1, Integer::sum);
			}
			int classic = counts.getOrDefault("classic_pumpkin", 0);
			int warty = counts.getOrDefault("warty_pumpkin", 0);
			expect(classic > 6700 && classic < 7300 && warty > 800 && warty < 1200, "field harvest follows the data pack weights " + counts);
		})));
		// seeds: 4 from a blank pumpkin, none from a carved one
		steps.add(new Step(5, mc -> serverPlayer(mc, sp -> {
			ItemStack blank = new ItemStack(ModBlocks.PUMPKINS.get(PumpkinModels.KABOCHA));
			var result = sp.level().recipeAccess().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(1, 1, List.of(blank)), sp.level())
				.map(r -> r.value().assemble(CraftingInput.of(1, 1, List.of(blank)), sp.level().registryAccess()));
			expect(result.isPresent() && result.get().is(ModItems.SEEDS.get(PumpkinVarieties.WINTER)) && result.get().getCount() == 4,
				"blank kabocha crafts into 4 winter squash seeds " + result);
			ItemStack carved = blank.copy();
			PumpkinDesign design = new PumpkinDesign(PumpkinModels.KABOCHA.layout(1));
			design.cut(CanvasFace.NORTH, 1, 1);
			carved.set(ModComponents.DESIGN, EncodedDesign.of(design));
			expect(sp.level().recipeAccess().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(1, 1, List.of(carved)), sp.level()).isEmpty(),
				"a carved pumpkin can't be crafted into seeds");
		})));
		// wild patches: registered in their biomes, and the feature places pumpkins of its variety
		steps.add(new Step(5, mc -> serverPlayer(mc, sp -> {
			var biomes = sp.level().registryAccess().lookupOrThrow(Registries.BIOME);
			boolean plains = biomes.getOrThrow(Biomes.PLAINS).value().getGenerationSettings().features().stream()
				.flatMap(set -> set.stream()).anyMatch(f -> f.is(CarvesAndCrafts.id("patch_field_pumpkin")));
			boolean taiga = biomes.getOrThrow(Biomes.SNOWY_TAIGA).value().getGenerationSettings().features().stream()
				.flatMap(set -> set.stream()).anyMatch(f -> f.is(CarvesAndCrafts.id("patch_winter_squash")));
			boolean desert = biomes.getOrThrow(Biomes.DESERT).value().getGenerationSettings().features().stream()
				.flatMap(set -> set.stream()).anyMatch(f -> f.unwrapKey().map(k -> k.identifier().getNamespace().equals(CarvesAndCrafts.MOD_ID)).orElse(false));
			expect(plains && taiga && !desert, "patches added to their biomes only");
		})));
		steps.add(new Step(20, mc -> runCommand(mc, "place feature carves_and_crafts:patch_winter_squash 10 100 3")));
		steps.add(new Step(5, mc -> serverPlayer(mc, sp -> {
			int winter = 0;
			int other = 0;
			for (BlockPos pos : BlockPos.betweenClosed(2, 99, -4, 18, 103, 10)) {
				if (sp.level().getBlockState(pos).getBlock() instanceof CustomPumpkinBlock p) {
					if (PumpkinVarieties.WINTER.grows(p.model())) {
						winter++;
					} else {
						other++;
					}
				}
			}
			expect(winter > 0 && other == 0, "wild winter squash patch placed " + winter + " pumpkins");
		})));
		shotFrom("p8_patch", 10.5, 102.5, 10.5, 180, 40);
		steps.add(new Step(5, mc -> runCommand(mc, "setblock 6 100 1 carves_and_crafts:heirloom_pumpkin_stem[age=4]")));
		shotFrom("p8_stems", 4.0, 100.8, 3.5, 180, 40);
	}

	/** Run after "schematics" on the same world: the library must survive a full restart. */
	private static void schematicsReloadScenario() {
		steps.add(new Step(5, mc -> serverPlayer(mc, sp -> {
			SchematicLibrary library = sp.getAttachedOrCreate(ModAttachments.SCHEMATICS);
			expect(library.entries().size() == 1 && library.entries().get(0).name().equals("Spooky"),
				"library persisted across restart " + library.entries().stream().map(Schematic::name).toList());
		})));
	}

	/**
	 * Export a schematic to a .pumpkin file, import files back, refuse broken/unknown/disabled imports,
	 * delete a file; plus a design larger than the vanilla 32 KiB client → server limit.
	 */
	private static void filesScenario() {
		Path folder = LocalSchematics.folder();
		PumpkinDesign big = DevServerScenario.largeDesign();
		EncodedDesign bigEncoded = EncodedDesign.of(big);
		steps.add(new Step(5, mc -> {
			try {
				Files.createDirectories(folder);
				try (var files = Files.newDirectoryStream(folder, "*.pumpkin")) {
					for (Path file : files) {
						Files.delete(file);
					}
				}
				Files.write(folder.resolve("broken.pumpkin"), new byte[] {'P', 'M', 'P', 'K', 1, 0});
				PumpkinDesign alienDesign = new PumpkinDesign(PumpkinModels.CLASSIC.layout(1));
				Files.write(folder.resolve("alien.pumpkin"),
					new PumpkinFile("Alien", 0, "giant_pumpkin", EncodedDesign.of(alienDesign), AuthorList.EMPTY).write());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			expect(bigEncoded.size() > 32767, "test design exceeds the vanilla payload limit (" + bigEncoded.size() + " bytes)");
		}));
		openBenchWithTools(true);
		steps.add(new Step(10, mc -> {
			runCommand(mc, "gamemode creative @a");
			serverPlayer(mc, sp -> sp.setAttached(ModAttachments.SCHEMATICS, SchematicLibrary.EMPTY
				.add("Big noise", PumpkinModels.CLASSIC.id(), bigEncoded, AuthorList.EMPTY.with(UUID.randomUUID(), "Artist"), 0, 0)
				.orElseThrow().library()));
		}));
		// a large design confirmed from the editor reaches the server (split by Fabric)
		steps.add(new Step(20, mc -> ClientPlayNetworking.send(new ConfirmDesignPayload(menu(mc).containerId, "", DesignCodec.encode(big), false))));
		steps.add(new Step(5, mc -> serverPlayer(mc, sp ->
			expect(bigEncoded.equals(menuOf(sp).getPumpkin().get(ModComponents.DESIGN)), "large design confirmed"))));
		// export from the world library
		steps.add(new Step(15, mc -> click(mc, button(mc, "gui.carves_and_crafts.tab.schematics"))));
		steps.add(new Step(5, mc -> clickListRow(mc, 0)));
		steps.add(new Step(5, mc -> click(mc, button(mc, "gui.carves_and_crafts.schematic.export"))));
		steps.add(new Step(5, mc -> click(mc, button(mc, "gui.carves_and_crafts.schematic.export"))));
		steps.add(new Step(5, mc -> {
			try {
				PumpkinFile file = PumpkinFile.read(Files.readAllBytes(folder.resolve("Big noise.pumpkin")));
				expect(file.design().equals(bigEncoded) && file.model().equals("classic_pumpkin")
					&& file.authors().names().equals(List.of("Artist")), "exported file holds the schematic");
			} catch (IOException e) {
				expect(false, "exported file exists: " + e);
			}
			expect(Files.exists(folder.resolve("Big noise (1).pumpkin")), "second export gets a new file name");
		}));
		// local view: import the exported file
		steps.add(new Step(10, mc -> click(mc, button(mc, "gui.carves_and_crafts.schematic.library.world"))));
		steps.add(new Step(5, mc -> {
			expect(!button(mc, "gui.carves_and_crafts.schematic.import").active, "import needs a selection");
			shot(mc, "p9_local_list");
			clickListRow(mc, localRow("Big noise.pumpkin"));
		}));
		steps.add(new Step(20, mc -> click(mc, button(mc, "gui.carves_and_crafts.schematic.import"))));
		steps.add(new Step(5, mc -> serverPlayer(mc, sp -> {
			SchematicLibrary library = sp.getAttachedOrCreate(ModAttachments.SCHEMATICS);
			Schematic imported = library.entries().get(0);
			expect(library.entries().size() == 2 && imported.name().equals("Big noise") && imported.design().equals(bigEncoded)
				&& imported.authors().names().equals(List.of("Artist")), "file imported into the world library");
		})));
		// unreadable file: no import; unknown model: refused by the client and by the server
		steps.add(new Step(5, mc -> clickListRow(mc, localRow("broken.pumpkin"))));
		steps.add(new Step(5, mc -> expect(!button(mc, "gui.carves_and_crafts.schematic.import").active, "unreadable file can't be imported")));
		steps.add(new Step(5, mc -> clickListRow(mc, localRow("alien.pumpkin"))));
		steps.add(new Step(5, mc -> click(mc, button(mc, "gui.carves_and_crafts.schematic.import"))));
		steps.add(new Step(20, mc -> {
			PumpkinFile alien = LocalSchematics.list().get(localRow("alien.pumpkin")).data();
			ClientPlayNetworking.send(ImportSchematicPayload.of(menu(mc).containerId, "Alien", alien, 0));
		}));
		steps.add(new Step(5, mc -> serverPlayer(mc, sp ->
			expect(sp.getAttachedOrCreate(ModAttachments.SCHEMATICS).entries().size() == 2, "design of a missing model refused"))));
		// imports turned off in the server config
		steps.add(new Step(20, mc -> {
			ServerConfig.get().allowSchematicImport = false;
			PumpkinFile file = LocalSchematics.list().get(localRow("Big noise.pumpkin")).data();
			ClientPlayNetworking.send(ImportSchematicPayload.of(menu(mc).containerId, "Again", file, 0));
		}));
		steps.add(new Step(5, mc -> {
			ServerConfig.get().allowSchematicImport = true;
			serverPlayer(mc, sp ->
				expect(sp.getAttachedOrCreate(ModAttachments.SCHEMATICS).entries().size() == 2, "import refused when disabled"));
		}));
		// delete a local file (two clicks)
		steps.add(new Step(5, mc -> clickListRow(mc, localRow("broken.pumpkin"))));
		steps.add(new Step(5, mc -> click(mc, button(mc, "gui.carves_and_crafts.schematic.delete"))));
		steps.add(new Step(5, mc -> expect(Files.exists(folder.resolve("broken.pumpkin")), "first delete click only asks")));
		steps.add(new Step(10, mc -> click(mc, button(mc, "gui.carves_and_crafts.schematic.delete_confirm"))));
		steps.add(new Step(5, mc -> {
			expect(!Files.exists(folder.resolve("broken.pumpkin")), "local file deleted");
			shot(mc, "p9_local_after_delete");
		}));
		steps.add(new Step(10, mc -> click(mc, button(mc, "gui.carves_and_crafts.schematic.library.local"))));
		steps.add(new Step(5, mc -> shot(mc, "p9_world_list")));
	}

	/**
	 * Presets from a test data pack (Presets view: save to library, copy onto a blank pumpkin), then
	 * pumpkins beyond the design render distance drawn without their design.
	 */
	private static void presetsScenario() {
		PumpkinDesign face = new PumpkinDesign(PumpkinModels.CLASSIC.layout(1));
		face.cut(CanvasFace.NORTH, 2, 2);
		face.cut(CanvasFace.NORTH, 7, 2);
		for (int x = 2; x <= 7; x++) {
			face.cut(CanvasFace.NORTH, x, 6);
		}
		face.paint(CanvasFace.NORTH, 5, 4, 0x2E7D32);
		EncodedDesign faceEncoded = EncodedDesign.of(face);
		steps.add(new Step(5, mc -> {
			Path pack = mc.getSingleplayerServer().getWorldPath(LevelResource.DATAPACK_DIR).resolve("test_presets");
			Path presets = pack.resolve("data/carves_and_crafts/preset");
			try {
				Files.createDirectories(presets);
				Files.writeString(pack.resolve("pack.mcmeta"),
					"{\"pack\": {\"description\": \"Test presets\", \"min_format\": [94, 1], \"max_format\": [94, 1]}}");
				Files.write(presets.resolve("test_face.pumpkin"), new PumpkinFile("Test face", 0, "classic_pumpkin", faceEncoded,
					AuthorList.EMPTY.with(UUID.randomUUID(), "StudioDeriva")).write());
				Files.write(presets.resolve("alien.pumpkin"), new PumpkinFile("Alien", 0, "giant_pumpkin", faceEncoded, AuthorList.EMPTY).write());
				Files.write(presets.resolve("broken.pumpkin"), new byte[] {1, 2, 3});
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}));
		steps.add(new Step(60, mc -> runCommand(mc, "reload")));
		steps.add(new Step(5, mc -> expect(PumpkinPresets.all().size() == 1 && PumpkinPresets.all().get(0).name().equals("Test face"),
			"data pack preset loaded, bad ones skipped " + PumpkinPresets.all().stream().map(Schematic::name).toList())));
		openBenchWithTools(true);
		steps.add(new Step(5, mc -> serverPlayer(mc, sp -> sp.setAttached(ModAttachments.SCHEMATICS, SchematicLibrary.EMPTY))));
		steps.add(new Step(15, mc -> click(mc, button(mc, "gui.carves_and_crafts.tab.schematics"))));
		steps.add(new Step(5, mc -> click(mc, button(mc, "gui.carves_and_crafts.schematic.library.world"))));
		steps.add(new Step(15, mc -> click(mc, button(mc, "gui.carves_and_crafts.schematic.library.local"))));
		steps.add(new Step(5, mc -> {
			shot(mc, "p10_presets_view");
			clickListRow(mc, 0);
		}));
		steps.add(new Step(15, mc -> click(mc, button(mc, "gui.carves_and_crafts.schematic.save"))));
		steps.add(new Step(5, mc -> serverPlayer(mc, sp -> {
			SchematicLibrary library = sp.getAttachedOrCreate(ModAttachments.SCHEMATICS);
			expect(library.entries().size() == 1 && library.entries().get(0).name().equals("Test face")
				&& library.entries().get(0).design().equals(faceEncoded), "preset saved to the library");
		})));
		steps.add(new Step(15, mc -> click(mc, button(mc, "gui.carves_and_crafts.schematic.copy"))));
		steps.add(new Step(5, mc -> serverPlayer(mc, sp -> {
			CarvingBenchMenu menu = menuOf(sp);
			expect(faceEncoded.equals(menu.getPumpkin().get(ModComponents.DESIGN)), "preset copied onto the blank pumpkin");
			expect(menu.getKnife().getDamageValue() == 8, "copy paid the knife (damage " + menu.getKnife().getDamageValue() + ")");
			AuthorList authors = menu.getPumpkin().get(ModComponents.AUTHORS);
			expect(authors != null && authors.names().equals(List.of("StudioDeriva")), "copy keeps the preset authors");
		})));
		steps.add(new Step(10, mc -> mc.player.closeContainer()));
		// design render distance: same carved pumpkins seen from 12 blocks, with a limit of 64 and of 8 blocks
		steps.add(new Step(20, mc -> {
			runCommand(mc, "gamemode creative @a");
			runCommand(mc, "fill -1 100 -1 18 104 6 air");
			carvedRow(mc);
		}));
		shotFrom("p10_lod_near", 6.5, 101.5, 13.0, 180, 10);
		steps.add(new Step(5, mc -> ClientConfig.get().designRenderDistance = 8));
		steps.add(new Step(10, mc -> shot(mc, "p10_lod_far")));
		steps.add(new Step(5, mc -> {
			ClientConfig.get().designRenderDistance = 64;
			try (var walk = Files.walk(mc.getSingleplayerServer().getWorldPath(LevelResource.DATAPACK_DIR).resolve("test_presets"))) {
				for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
					Files.delete(path);
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}));
	}

	/**
	 * Client half of {@link DevServerScenario} (dedicated server, see there): opens the bench the server prepared,
	 * confirms and imports a design larger than the vanilla 32 KiB packet limit. The server checks and stops.
	 */
	private static void largeRemoteScenario() {
		BlockPos bench = DevServerScenario.BENCH;
		steps.add(new Step(40, mc -> expect(mc.getSingleplayerServer() == null, "connected to a dedicated server")));
		steps.add(new Step(15, mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(bench).add(0, 0, 0.5), Direction.SOUTH, bench, false))));
		steps.add(new Step(5, mc -> {
			expect(mc.screen instanceof CarvingBenchScreen, "bench screen open");
			byte[] bytes = DesignCodec.encode(DevServerScenario.largeDesign());
			ClientPlayNetworking.send(new ConfirmDesignPayload(menu(mc).containerId, "", bytes, false));
		}));
		steps.add(new Step(40, mc -> {
			EncodedDesign large = EncodedDesign.of(DevServerScenario.largeDesign());
			EncodedDesign held = menu(mc).getPumpkin().get(ModComponents.DESIGN);
			expect(large.equals(held), "client sees the confirmed large design");
			expect(held != null && !held.isComplete(), "client item holds only a design reference");
			expect(held != null && large.decode().equals(ClientDesigns.of(held)), "bench sent the full design for the editor");
			PumpkinFile file = new PumpkinFile(DevServerScenario.IMPORT_NAME, 0, "classic_pumpkin", large, AuthorList.EMPTY);
			ClientPlayNetworking.send(ImportSchematicPayload.of(menu(mc).containerId, DevServerScenario.IMPORT_NAME, file, 0));
		}));
		// the server fills the inventory once it has checked the confirm and the import
		steps.add(new Step(200, mc -> mc.setScreen(null)));
		steps.add(new Step(20, mc -> {
			Inventory inventory = mc.player.getInventory();
			int shulkers = 0;
			for (int i = 0; i < DevServerScenario.SHULKERS; i++) {
				shulkers += inventory.getItem(DevServerScenario.FIRST_SHULKER_SLOT + i).is(Items.SHULKER_BOX) ? 1 : 0;
			}
			expect(shulkers == DevServerScenario.SHULKERS, "inventory of shulker boxes full of large designs received (" + shulkers + ")");
			ItemStack probe = inventory.getItem(DevServerScenario.PROBE_SLOT).copy();
			expect(probe.has(ModComponents.DESIGN), "probe pumpkin received");
			// what the creative inventory sends when moving an item: the new slot, then the emptied one (menu slots 36+)
			mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket(36 + DevServerScenario.MOVED_SLOT, probe));
			mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket(36 + DevServerScenario.PROBE_SLOT, ItemStack.EMPTY));
			// a pumpkin with a design reference the server never sent
			ByteBuf fake = Unpooled.buffer();
			fake.writeByte(18).writeByte('D').writeByte('R').writeBytes(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
			ItemStack forged = new ItemStack(ModItems.CLASSIC_PUMPKIN);
			forged.set(ModComponents.DESIGN, EncodedDesign.ITEM_STREAM_CODEC.decode(fake));
			mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket(36 + DevServerScenario.FAKE_SLOT, forged));
		}));
		steps.add(new Step(300, mc -> {
		}));
	}

	/**
	 * Optional integrations are registered, the config screen saves, JEI shows the palette recipe and
	 * Jade (or WTHIT with -Pwthit) shows the pumpkin info.
	 */
	private static void compatScenario() {
		steps.add(new Step(5, mc -> {
			// WTHIT reads waila_plugins.json instead of an entrypoint
			List<String> entrypoints = FabricLoader.getInstance().isModLoaded("wthit") ? List.of("modmenu", "jei_mod_plugin") : List.of("modmenu", "jei_mod_plugin", "jade");
			for (String entrypoint : entrypoints) {
				boolean ours = FabricLoader.getInstance().getEntrypointContainers(entrypoint, Object.class).stream()
					.anyMatch(c -> c.getProvider().getMetadata().getId().equals(CarvesAndCrafts.MOD_ID));
				expect(ours, "entrypoint " + entrypoint + " registered");
			}
			mc.setScreen(new ConfigScreen(null));
		}));
		steps.add(new Step(10, mc -> shot(mc, "p11_config")));
		steps.add(new Step(5, mc -> {
			int before = ClientConfig.get().designRenderDistance;
			mc.screen.onClose();
			expect(ClientConfig.get().designRenderDistance == before && mc.screen == null, "config screen closes and saves");
		}));
		steps.add(new Step(20, mc -> {
			var runtime = JeiIntegration.runtime();
			expect(runtime != null, "JEI runtime available");
			if (runtime != null) {
				runtime.getRecipesGui().showTypes(List.of(JeiIntegration.PALETTE));
			}
		}));
		steps.add(new Step(10, mc -> {
			shot(mc, "p11_jei_palette");
			mc.setScreen(null);
		}));
		steps.add(new Step(20, mc -> {
			runCommand(mc, "gamemode creative @a");
			runCommand(mc, "fill -1 100 -1 18 104 6 air");
			runCommand(mc, "setblock 4 100 2 carves_and_crafts:classic_pumpkin[rotation=0]");
			runCommand(mc, "dpumpkin 4 100 2 init 2");
			runCommand(mc, "dpumpkin 4 100 2 demo");
			teleport(mc, 4.5, 100, 4.5, 180, 25);
		}));
		steps.add(new Step(10, mc -> {
			serverPlayer(mc, sp -> lightDirectly(sp, new BlockPos(4, 100, 2), LightSource.TORCH, Items.TORCH));
			showHud = true;
		}));
		steps.add(new Step(30, mc -> shot(mc, FabricLoader.getInstance().isModLoaded("wthit") ? "p11_overlay_wthit" : "p11_overlay")));
		steps.add(new Step(5, mc -> showHud = false));
	}

	/** /carves maxcolors: the server refuses designs over the limit, the editor counts colors and blocks confirming. */
	private static void colorsScenario() {
		steps.add(new Step(5, mc -> runCommand(mc, "carves maxcolors 2")));
		steps.add(new Step(5, mc -> expect(ServerConfig.get().colorLimit() == 2, "command sets the limit")));
		openBenchWithTools(true);
		steps.add(new Step(5, mc -> expect(menu(mc).maxColors() == 2, "bench tells the client the limit")));
		steps.add(new Step(10, mc -> ClientPlayNetworking.send(new ConfirmDesignPayload(menu(mc).containerId, "",
			DesignCodec.encode(paintedColors(3)), false))));
		steps.add(new Step(5, mc -> serverBench(mc, bench -> expect(!bench.getItem(BenchSlots.PUMPKIN).has(ModComponents.DESIGN),
			"server refuses a design with 3 colors"))));
		steps.add(new Step(10, mc -> ClientPlayNetworking.send(new ConfirmDesignPayload(menu(mc).containerId, "",
			DesignCodec.encode(paintedColors(2)), false))));
		steps.add(new Step(5, mc -> serverBench(mc, bench -> expect(bench.getItem(BenchSlots.PUMPKIN).has(ModComponents.DESIGN),
			"server accepts a design with 2 colors"))));
		// a third color in the editor: counter in red, confirm disabled
		steps.add(new Step(5, mc -> click(mc, button(mc, "gui.carves_and_crafts.carve"))));
		steps.add(new Step(5, mc -> {
			for (GuiEventListener child : mc.screen.children()) {
				if (child instanceof EditBox box && box.isVisible()) {
					box.setValue("#00FF00");
				}
			}
			click(mc, button(mc, "gui.carves_and_crafts.tool.paint"));
		}));
		steps.add(new Step(5, mc -> stroke((PumpkinEditorScreen) mc.screen, 2, 12, 6, 12)));
		steps.add(new Step(5, mc -> {
			expect(!button(mc, "gui.carves_and_crafts.editor.confirm").active, "editor blocks confirming a third color");
			shot(mc, "colors_editor");
		}));
		steps.add(new Step(5, mc -> {
			mc.setScreen(null);
			runCommand(mc, "carves maxcolors off");
		}));
		steps.add(new Step(5, mc -> expect(ServerConfig.get().colorLimit() == 0, "command turns the limit off")));
	}

	/** Classic pumpkin at x2 with the first pixels of the front painted in {@code colors} different colors. */
	private static PumpkinDesign paintedColors(int colors) {
		PumpkinDesign design = new PumpkinDesign(PumpkinModels.CLASSIC.layout(2));
		for (int i = 0; i < colors; i++) {
			design.paint(CanvasFace.NORTH, i, 0, 0x100000 * (i + 1));
		}
		return design;
	}

	/** The tool and seed items: held in hand, in the hotbar and in the inventory screen. */
	private static void itemsScenario() {
		steps.add(new Step(20, mc -> {
			runCommand(mc, "gamemode creative @a");
			runCommand(mc, "time set noon");
			runCommand(mc, "clear @a");
			String[] items = {"carving_knife", "paintbrush", "color_palette",
					"field_pumpkin_seeds", "heirloom_pumpkin_seeds", "winter_squash_seeds"};
			for (int i = 0; i < items.length; i++) {
				runCommand(mc, "item replace entity @a hotbar." + i + " with carves_and_crafts:" + items[i]);
			}
			showHud = true;
			mc.player.getInventory().setSelectedSlot(0);
		}));
		steps.add(new Step(20, mc -> shot(mc, "items_hand")));
		steps.add(new Step(5, mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player))));
		steps.add(new Step(10, mc -> shot(mc, "items_inventory")));
		steps.add(new Step(5, mc -> {
			mc.setScreen(null);
			showHud = false;
		}));
	}

	/** The carving bench's Blockbench model, placed facing each way, from the front, the side and above; then as an item. */
	private static void benchModelScenario() {
		steps.add(new Step(20, mc -> {
			runCommand(mc, "gamemode creative @a");
			runCommand(mc, "time set noon");
			runCommand(mc, "fill -1 100 -6 18 104 10 air");
			runCommand(mc, "setblock 2 100 2 carves_and_crafts:carving_bench[facing=north]");
			runCommand(mc, "setblock 7 100 2 carves_and_crafts:carving_bench[facing=south]");
			runCommand(mc, "setblock 12 100 2 carves_and_crafts:carving_bench[facing=east]");
			runCommand(mc, "setblock 16 100 2 carves_and_crafts:carving_bench[facing=west]");
			runCommand(mc, "item replace entity @a hotbar.0 with carves_and_crafts:carving_bench");
		}));
		shotFrom("bench_model_south_view", 9.5, 101.5, 8.5, 180, 15);
		shotFrom("bench_model_north_view", 9.5, 101.5, -3.5, 0, 15);
		shotFrom("bench_model_close_north", 3.0, 101.3, -0.8, 0, 25);
		shotFrom("bench_model_close_south", 3.0, 101.3, 4.8, 180, 25);
		shotFrom("bench_model_top", 9.5, 106.5, 2.5, 180, 89);
		steps.add(new Step(5, mc -> {
			showHud = true;
			mc.player.getInventory().setSelectedSlot(0);
		}));
		steps.add(new Step(20, mc -> shot(mc, "bench_model_hand")));
		steps.add(new Step(5, mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player))));
		steps.add(new Step(10, mc -> shot(mc, "bench_model_inventory")));
		steps.add(new Step(5, mc -> {
			mc.setScreen(null);
			showHud = false;
		}));
	}

	/** The bench is two blocks wide: placing, opening from either half, breaking (drops) and its hitbox. */
	private static void benchPartsScenario() {
		BlockPos a = new BlockPos(2, 100, 2);
		BlockPos b = new BlockPos(8, 100, 2);
		steps.add(new Step(20, mc -> {
			runCommand(mc, "fill -1 100 -4 18 104 10 air");
			runCommand(mc, "kill @e[type=item]");
			runCommand(mc, "gamemode survival @a");
			// /setblock places the main part; the side part appears one block clockwise from the facing (north -> east)
			runCommand(mc, "setblock 2 100 2 carves_and_crafts:carving_bench[facing=north]");
		}));
		steps.add(new Step(5, mc -> serverPlayer(mc, DevScenarios::benchRecipeCheck)));
		steps.add(new Step(5, mc -> serverPlayer(mc, sp -> {
			BlockState side = sp.level().getBlockState(a.east());
			expect(side.is(ModBlocks.CARVING_BENCH) && side.getValue(CarvingBenchBlock.PART) == BenchPart.SIDE, "side part next to the main part");
			expect(sp.level().getBlockEntity(a) instanceof CarvingBenchBlockEntity && sp.level().getBlockEntity(a.east()) == null,
				"only the main part has the block entity");
			double top = side.getShape(sp.level(), a.east()).bounds().maxY;
			expect(Math.abs(top - 14 / 16.0) < 1e-6, "side part hitbox up to the table top (" + top + ")");
		})));
		// open the bench by clicking its side part
		steps.add(new Step(10, mc -> {
			teleport(mc, 3.5, 100.6, 4.8, 180, 35);
			showHud = true;
		}));
		steps.add(new Step(20, mc -> {
			shot(mc, "bench_parts_outline");
			showHud = false;
		}));
		steps.add(new Step(10, mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(a.east()).add(0, 0.3, 0.5), Direction.SOUTH, a.east(), false))));
		steps.add(new Step(5, mc -> {
			expect(mc.screen instanceof CarvingBenchScreen, "side part opens the bench");
			mc.player.closeContainer();
		}));
		// survival: breaking the side part removes both and drops one bench (and the pumpkin left inside)
		steps.add(new Step(10, mc -> serverPlayer(mc, sp -> {
			if (sp.level().getBlockEntity(a) instanceof CarvingBenchBlockEntity bench) {
				bench.setItem(BenchSlots.PUMPKIN, new ItemStack(ModItems.CLASSIC_PUMPKIN));
			}
			sp.gameMode.destroyBlock(a.east());
		})));
		steps.add(new Step(10, mc -> serverPlayer(mc, sp -> {
			expect(sp.level().getBlockState(a).isAir() && sp.level().getBlockState(a.east()).isAir(), "breaking the side part removes both");
			expect(droppedCount(sp, a, ModItems.CARVING_BENCH) == 1, "one bench dropped (" + droppedCount(sp, a, ModItems.CARVING_BENCH) + ")");
			expect(droppedCount(sp, a, ModItems.CLASSIC_PUMPKIN) == 1, "bench contents dropped");
			runCommand(mc, "kill @e[type=item]");
		})));
		// a player can't place it without room for the side part
		steps.add(new Step(10, mc -> {
			// the player looks north, so the bench faces south and its side part goes west (x = 7)
			runCommand(mc, "setblock 7 100 2 stone");
			teleport(mc, 8.5, 100, 4.5, 180, 30);
			serverPlayer(mc, sp -> sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CARVING_BENCH, 2)));
		}));
		steps.add(new Step(10, mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(b.below()).add(0, 0.5, 0), Direction.UP, b.below(), false))));
		steps.add(new Step(5, mc -> serverPlayer(mc, sp ->
			expect(sp.level().getBlockState(b).isAir(), "no room for the side part: not placed"))));
		steps.add(new Step(10, mc -> {
			runCommand(mc, "setblock 7 100 2 air");
		}));
		steps.add(new Step(10, mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(b.below()).add(0, 0.5, 0), Direction.UP, b.below(), false))));
		steps.add(new Step(5, mc -> serverPlayer(mc, sp -> {
			BlockState main = sp.level().getBlockState(b);
			boolean placed = main.is(ModBlocks.CARVING_BENCH) && main.getValue(CarvingBenchBlock.PART) == BenchPart.MAIN;
			Direction toSide = placed ? main.getValue(CarvingBenchBlock.FACING).getClockWise() : Direction.NORTH;
			expect(placed && sp.level().getBlockState(b.relative(toSide)).is(ModBlocks.CARVING_BENCH), "placed by a player with both parts");
			// survival: breaking the main part removes both, one drop
			sp.gameMode.destroyBlock(b);
		})));
		steps.add(new Step(10, mc -> serverPlayer(mc, sp -> {
			expect(droppedCount(sp, b, ModItems.CARVING_BENCH) == 1, "breaking the main part drops one bench");
			runCommand(mc, "kill @e[type=item]");
			runCommand(mc, "gamemode creative @a");
			runCommand(mc, "setblock 2 100 2 carves_and_crafts:carving_bench[facing=north]");
		})));
		// creative: breaking the side part leaves nothing behind and drops nothing
		steps.add(new Step(10, mc -> serverPlayer(mc, sp -> sp.gameMode.destroyBlock(a.east()))));
		steps.add(new Step(10, mc -> serverPlayer(mc, sp -> {
			expect(sp.level().getBlockState(a).isAir() && droppedCount(sp, a, ModItems.CARVING_BENCH) == 0, "creative: both parts gone, no drop");
		})));
	}

	/** Crafting the bench: slabs on top, plank-iron-plank, two plank legs (any wood). */
	private static void benchRecipeCheck(ServerPlayer sp) {
		ItemStack slab = new ItemStack(Items.OAK_SLAB);
		ItemStack plank = new ItemStack(Items.SPRUCE_PLANKS);
		ItemStack iron = new ItemStack(Items.IRON_INGOT);
		List<ItemStack> grid = List.of(slab, slab, slab, plank, iron, plank, plank, ItemStack.EMPTY, plank);
		var result = sp.level().recipeAccess().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, grid), sp.level())
			.map(r -> r.value().assemble(CraftingInput.of(3, 3, grid), sp.level().registryAccess()));
		expect(result.isPresent() && result.get().is(ModItems.CARVING_BENCH), "carving bench recipe " + result);
	}

	private static int droppedCount(ServerPlayer sp, BlockPos around, Item item) {
		return sp.level().getEntitiesOfClass(ItemEntity.class, new AABB(around).inflate(4)).stream()
			.filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
	}

	/** Index of a local file in the list (also its row, as long as there are fewer than a page). */
	private static int localRow(String fileName) {
		List<LocalSchematics.Entry> entries = LocalSchematics.list();
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).fileName().equals(fileName)) {
				return i;
			}
		}
		throw new IllegalStateException("No local file " + fileName);
	}

	private static void openBenchWithTools(boolean chargedBrush) {
		steps.add(new Step(10, mc -> {
			runCommand(mc, "setblock " + BENCH.getX() + " " + BENCH.getY() + " " + BENCH.getZ() + " carves_and_crafts:carving_bench[facing=south]");
			runCommand(mc, "gamemode survival @a");
			teleport(mc, 6.5, 100, 5.5, 180, 30);
		}));
		steps.add(new Step(10, mc -> serverBench(mc, bench -> {
			bench.setItem(BenchSlots.PUMPKIN, new ItemStack(ModItems.CLASSIC_PUMPKIN));
			bench.setItem(BenchSlots.KNIFE, new ItemStack(ModItems.CARVING_KNIFE));
			ItemStack brush = new ItemStack(ModItems.PAINTBRUSH);
			if (chargedBrush) {
				PaintbrushItem.setCharge(brush, ToolBalance.BRUSH_CAPACITY);
			}
			bench.setItem(BenchSlots.BRUSH, brush);
		})));
		steps.add(new Step(15, mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(BENCH).add(0, 0, 0.5), Direction.SOUTH, BENCH, false))));
		steps.add(new Step(5, mc -> expect(mc.screen instanceof CarvingBenchScreen, "bench screen open")));
	}

	private static CarvingBenchMenu menuOf(ServerPlayer player) {
		return (CarvingBenchMenu) player.containerMenu;
	}

	/** Clicks a row of the schematic list (layout constants of CarvingBenchScreen). */
	private static void clickListRow(Minecraft mc, int row) {
		CarvingBenchScreen screen = (CarvingBenchScreen) mc.screen;
		int left = (screen.width - 176) / 2;
		int top = (screen.height - 166) / 2;
		double x = left + 64 + 50;
		double y = top + 18 + row * 20 + 10;
		MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
		screen.mouseClicked(event, false);
		screen.mouseReleased(event);
	}

	private static void setNameBox(Minecraft mc, String text) {
		for (GuiEventListener child : mc.screen.children()) {
			if (child instanceof EditBox box && box.isVisible()) {
				box.setValue(text);
			}
		}
	}

	private static CarvingBenchMenu menu(Minecraft mc) {
		return (CarvingBenchMenu) mc.player.containerMenu;
	}

	// ------------------------------------------------------------------ helpers

	private static void shotFrom(String name, double x, double y, double z, float yaw, float pitch) {
		steps.add(new Step(25, mc -> teleport(mc, x, y, z, yaw, pitch)));
		steps.add(new Step(5, mc -> shot(mc, name)));
	}

	private static void shot(Minecraft mc, String name) {
		String file = name + ".png";
		Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), 1, msg -> CarvesAndCrafts.LOGGER.info("Saved {}", file));
	}

	private static void expect(boolean condition, String what) {
		if (condition) {
			CarvesAndCrafts.LOGGER.info("SCENARIO OK: {}", what);
		} else {
			CarvesAndCrafts.LOGGER.error("SCENARIO FAIL: {}", what);
		}
	}

	private static AbstractWidget button(Minecraft mc, String translationKey) {
		return buttonByText(mc, Component.translatable(translationKey).getString());
	}

	/** Finds a widget whose label is the text, ignoring a "> " selection prefix. */
	private static AbstractWidget buttonByText(Minecraft mc, String text) {
		Screen screen = mc.screen;
		for (GuiEventListener child : screen.children()) {
			if (child instanceof AbstractWidget widget && widget.visible) {
				String label = widget.getMessage().getString();
				if (label.equals(text) || label.equals("> " + text)) {
					return widget;
				}
			}
		}
		throw new IllegalStateException("No button '" + text + "' on " + screen);
	}

	private static void click(Minecraft mc, AbstractWidget widget) {
		double x = widget.getX() + widget.getWidth() / 2.0;
		double y = widget.getY() + widget.getHeight() / 2.0;
		MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
		mc.screen.mouseClicked(event, false);
		mc.screen.mouseReleased(event);
	}

	/** Left-drag across canvas pixels, as a player would. */
	private static void stroke(PumpkinEditorScreen editor, int x0, int y0, int x1, int y1) {
		double[] a = editor.pixelCenterOnScreen(x0, y0);
		double[] b = editor.pixelCenterOnScreen(x1, y1);
		MouseButtonInfo left = new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0);
		editor.mouseClicked(new MouseButtonEvent(a[0], a[1], left), false);
		editor.mouseDragged(new MouseButtonEvent(b[0], b[1], left), b[0] - a[0], b[1] - a[1]);
		editor.mouseReleased(new MouseButtonEvent(b[0], b[1], left));
	}

	private static void runCommand(Minecraft mc, String command) {
		IntegratedServer server = mc.getSingleplayerServer();
		server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
	}

	private static void serverPlayer(Minecraft mc, Consumer<ServerPlayer> action) {
		IntegratedServer server = mc.getSingleplayerServer();
		UUID id = mc.player.getUUID();
		server.execute(() -> {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				action.accept(player);
			}
		});
	}

	private static void serverBench(Minecraft mc, Consumer<CarvingBenchBlockEntity> action) {
		serverPlayer(mc, player -> {
			if (player.level().getBlockEntity(BENCH) instanceof CarvingBenchBlockEntity bench) {
				action.accept(bench);
			} else {
				expect(false, "bench exists");
			}
		});
	}

	private static int firstPumpkinSlot(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(ModItems.CLASSIC_PUMPKIN)) {
				return i;
			}
		}
		return 0;
	}

	private static void teleport(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
		mc.player.getAbilities().flying = true;
		serverPlayer(mc, player -> {
			player.getAbilities().mayfly = true;
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
			player.teleportTo(player.level(), x, y, z, Set.of(), yaw, pitch, false);
		});
	}
}
