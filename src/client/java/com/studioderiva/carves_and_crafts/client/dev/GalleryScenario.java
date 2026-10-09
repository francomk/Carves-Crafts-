package com.studioderiva.carves_and_crafts.client.dev;

import static com.studioderiva.carves_and_crafts.client.dev.DevScenarios.Step;
import static com.studioderiva.carves_and_crafts.client.dev.DevScenarios.button;
import static com.studioderiva.carves_and_crafts.client.dev.DevScenarios.click;
import static com.studioderiva.carves_and_crafts.client.dev.DevScenarios.serverPlayer;
import static com.studioderiva.carves_and_crafts.client.dev.DevScenarios.shot;
import static com.studioderiva.carves_and_crafts.client.dev.DevScenarios.steps;
import static com.studioderiva.carves_and_crafts.client.dev.DevScenarios.teleport;

import com.mojang.datafixers.util.Pair;
import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.block.AttachedPumpkinStemBlock;
import com.studioderiva.carves_and_crafts.block.CustomPumpkinBlock;
import com.studioderiva.carves_and_crafts.block.LightSource;
import com.studioderiva.carves_and_crafts.block.entity.BenchSlots;
import com.studioderiva.carves_and_crafts.block.entity.CarvingBenchBlockEntity;
import com.studioderiva.carves_and_crafts.block.entity.CustomPumpkinBlockEntity;
import com.studioderiva.carves_and_crafts.client.screen.CarvingBenchScreen;
import com.studioderiva.carves_and_crafts.design.AuthorList;
import com.studioderiva.carves_and_crafts.design.EncodedDesign;
import com.studioderiva.carves_and_crafts.design.PumpkinDesign;
import com.studioderiva.carves_and_crafts.item.PaintbrushItem;
import com.studioderiva.carves_and_crafts.item.ToolBalance;
import com.studioderiva.carves_and_crafts.model.PumpkinModel;
import com.studioderiva.carves_and_crafts.model.PumpkinModels;
import com.studioderiva.carves_and_crafts.registry.ModAttachments;
import com.studioderiva.carves_and_crafts.registry.ModBlocks;
import com.studioderiva.carves_and_crafts.registry.ModComponents;
import com.studioderiva.carves_and_crafts.registry.ModItems;
import com.studioderiva.carves_and_crafts.schematic.SchematicLibrary;
import com.studioderiva.carves_and_crafts.variety.PumpkinVarieties;
import com.studioderiva.carves_and_crafts.variety.PumpkinVariety;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Development aid: screenshots for the Modrinth gallery, in the world run/saves/gallery.
 * {@code gallery_build} finds a spot per scene, builds it and takes preview shots without shaders;
 * {@code gallery} (with -Pshaders) only sets the time, moves the camera and shoots, plus the bench GUIs.
 * Spots are kept in run/gallery_spots.properties so both runs use the same places.
 */
final class GalleryScenario {
	private enum Scene {
		LANTERNS(Biomes.DARK_FOREST, 9),
		PORCH(Biomes.BIRCH_FOREST, 8),
		GARDEN(Biomes.PLAINS, 9),
		WILD(Biomes.TAIGA, 5),
		WORKSHOP(Biomes.PLAINS, 6);

		final ResourceKey<Biome> biome;
		/** Half size of the area flattened for the build; 0 keeps the terrain as it is. */
		final int radius;

		Scene(ResourceKey<Biome> biome, int radius) {
			this.biome = biome;
			this.radius = radius;
		}
	}

	/** A camera: position and target relative to the scene origin, time of day. */
	private record Shot(String name, Scene scene, double x, double y, double z, double tx, double ty, double tz, int time) {
	}

	private static final List<Shot> SHOTS = List.of(
		new Shot("gallery_lanterns_night", Scene.LANTERNS, 0.5, 2.0, 5.0, 0.5, 0.7, -1.5, 18000),
		new Shot("gallery_lantern_closeup", Scene.LANTERNS, 0.5, 0.9, 1.4, 0.5, 0.5, -1.0, 18000),
		new Shot("gallery_porch_sunset", Scene.PORCH, -4.0, 2.4, 5.5, -0.5, 1.2, -1.0, 10800),
		new Shot("gallery_porch_closeup", Scene.PORCH, -1.0, 2.0, 3.6, -2.0, 1.3, -0.5, 10800),
		new Shot("gallery_garden_morning", Scene.GARDEN, 6.5, 4.0, 8.5, 0.0, 0.0, -1.0, 3500),
		new Shot("gallery_wild_taiga", Scene.WILD, 0.5, 1.8, 5.0, 0.0, 0.3, -0.5, 3000),
		new Shot("gallery_workshop", Scene.WORKSHOP, 2.6, 2.0, 2.6, -0.5, 0.8, -2.5, 6000));

	private static final Path SPOTS = FabricLoader.getInstance().getGameDir().resolve("gallery_spots.properties");
	private static final Map<Scene, BlockPos> spots = new EnumMap<>(Scene.class);

	private GalleryScenario() {
	}

	private static boolean shaders() {
		return FabricLoader.getInstance().isModLoaded("iris");
	}

	// ------------------------------------------------------------------ entry points

	static void build() {
		setUp();
		// rebuilding reuses the saved spots: the ground under a finished build is no longer the natural surface
		boolean rebuild = Files.exists(SPOTS);
		steps.add(new Step(5, mc -> {
			if (rebuild) {
				loadSpots();
			} else {
				serverPlayer(mc, sp -> findSpots(sp));
			}
		}));
		for (Scene scene : Scene.values()) {
			// the spot's chunks are generated when the player arrives, so build after waiting there
			steps.add(new Step(200, mc -> {
				BlockPos o = spots.get(scene);
				teleport(mc, o.getX() + 0.5, o.getY() + 30, o.getZ() + 0.5, 0, 90);
			}));
			steps.add(new Step(40, mc -> serverPlayer(mc, sp -> buildScene(sp, scene, !rebuild))));
		}
		steps.add(new Step(5, mc -> saveSpots()));
		shots();
	}

	static void shoot() {
		setUp();
		steps.add(new Step(5, mc -> loadSpots()));
		shots();
		guis();
	}

	private static void setUp() {
		steps.add(new Step(5, mc -> {
			DevScenarios.runCommand(mc, "gamemode creative @a");
			DevScenarios.runCommand(mc, "gamerule advance_time false");
			DevScenarios.runCommand(mc, "gamerule advance_weather false");
			DevScenarios.runCommand(mc, "gamerule spawn_mobs false");
			DevScenarios.runCommand(mc, "gamerule random_tick_speed 0");
			DevScenarios.runCommand(mc, "weather clear");
			DevScenarios.runCommand(mc, "kill @e[type=!player]");
		}));
	}

	private static void shots() {
		int settle = shaders() ? 160 : 60;
		for (Shot shot : SHOTS) {
			steps.add(new Step(settle, mc -> {
				BlockPos o = spots.get(shot.scene());
				DevScenarios.runCommand(mc, "time set " + shot.time());
				double x = o.getX() + shot.x();
				double y = o.getY() + shot.y();
				double z = o.getZ() + shot.z();
				double dx = o.getX() + shot.tx() - x;
				double dy = o.getY() + shot.ty() - y;
				double dz = o.getZ() + shot.tz() - z;
				float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
				float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
				teleport(mc, x, y - 1.62, z, yaw, pitch); // teleport moves the feet, the camera is at eye height
			}));
			steps.add(new Step(5, mc -> shot(mc, shot.name() + (shaders() ? "" : "_preview"))));
		}
	}

	// ------------------------------------------------------------------ bench GUIs

	private static void guis() {
		BlockPos[] bench = new BlockPos[1];
		steps.add(new Step(5, mc -> {
			BlockPos o = spots.get(Scene.WORKSHOP);
			bench[0] = o.offset(0, 0, -2);
			DevScenarios.runCommand(mc, "time set 6000");
			teleport(mc, o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 180, 25);
		}));
		steps.add(new Step(40, mc -> serverPlayer(mc, sp -> {
			if (sp.level().getBlockEntity(bench[0]) instanceof CarvingBenchBlockEntity entity) {
				ItemStack pumpkin = new ItemStack(ModItems.CLASSIC_PUMPKIN);
				PumpkinDesign design = GalleryDesigns.classicFace(PumpkinModels.CLASSIC);
				paintRim(design);
				pumpkin.set(ModComponents.DESIGN, EncodedDesign.of(design));
				pumpkin.set(ModComponents.AUTHORS, AuthorList.EMPTY.with(sp.getUUID(), sp.getGameProfile().name()));
				entity.setItem(BenchSlots.PUMPKIN, pumpkin);
				entity.setItem(BenchSlots.KNIFE, new ItemStack(ModItems.CARVING_KNIFE));
				ItemStack brush = new ItemStack(ModItems.PAINTBRUSH);
				PaintbrushItem.setCharge(brush, ToolBalance.BRUSH_CAPACITY);
				entity.setItem(BenchSlots.BRUSH, brush);
				entity.setItem(BenchSlots.PALETTE, new ItemStack(ModItems.COLOR_PALETTE));
			}
			fillLibrary(sp);
		})));
		steps.add(new Step(20, mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(bench[0]).add(0, 0, 0.5), Direction.SOUTH, bench[0], false))));
		steps.add(new Step(20, mc -> shot(mc, "gallery_gui_bench")));
		steps.add(new Step(20, mc -> click(mc, button(mc, "gui.carves_and_crafts.carve"))));
		steps.add(new Step(20, mc -> shot(mc, "gallery_gui_editor")));
		steps.add(new Step(10, mc -> click(mc, button(mc, "gui.carves_and_crafts.editor.cancel"))));
		steps.add(new Step(20, mc -> {
			if (mc.screen instanceof CarvingBenchScreen) {
				click(mc, button(mc, "gui.carves_and_crafts.tab.schematics"));
			}
		}));
		steps.add(new Step(30, mc -> shot(mc, "gallery_gui_library")));
		steps.add(new Step(5, mc -> mc.setScreen(null)));
	}

	/** A painted orange rim around the eyes, so the editor shows paint as well as cuts. */
	private static void paintRim(PumpkinDesign design) {
		var face = com.studioderiva.carves_and_crafts.design.CanvasFace.NORTH;
		for (int y = 0; y < design.height(face); y++) {
			for (int x = 0; x < design.width(face); x++) {
				if (design.isCut(face, x, y)) {
					continue;
				}
				boolean nearCut = false;
				for (int dy = -1; dy <= 1 && !nearCut; dy++) {
					for (int dx = -1; dx <= 1; dx++) {
						int nx = x + dx;
						int ny = y + dy;
						if (nx >= 0 && ny >= 0 && nx < design.width(face) && ny < design.height(face) && design.isCut(face, nx, ny)) {
							nearCut = true;
							break;
						}
					}
				}
				if (nearCut) {
					design.paint(face, x, y, 0xFFB238);
				}
			}
		}
	}

	private record LibraryEntry(String name, PumpkinModel model, Function<PumpkinModel, PumpkinDesign> design) {
	}

	private static void fillLibrary(ServerPlayer sp) {
		List<LibraryEntry> entries = List.of(
			new LibraryEntry("Classic Jack", PumpkinModels.CLASSIC, GalleryDesigns::classicFace),
			new LibraryEntry("Starry Night", PumpkinModels.CINDERELLA, GalleryDesigns::nightSky),
			new LibraryEntry("Grumpy", PumpkinModels.WARTY, GalleryDesigns::scaryFace),
			new LibraryEntry("Candy Cane", PumpkinModels.WHITE, GalleryDesigns::candyStripes),
			new LibraryEntry("Kitty", PumpkinModels.KABOCHA, GalleryDesigns::catFace),
			new LibraryEntry("Sunset", PumpkinModels.BLUE, GalleryDesigns::sunset));
		AuthorList authors = AuthorList.EMPTY.with(sp.getUUID(), sp.getGameProfile().name());
		long now = System.currentTimeMillis();
		SchematicLibrary library = SchematicLibrary.EMPTY;
		for (LibraryEntry entry : entries) {
			EncodedDesign design = EncodedDesign.of(entry.design().apply(entry.model()));
			library = library.add(entry.name(), entry.model().id(), design, authors, now, 0)
				.map(SchematicLibrary.Added::library).orElse(library);
		}
		sp.setAttached(ModAttachments.SCHEMATICS, library);
	}

	// ------------------------------------------------------------------ spots

	private static void findSpots(ServerPlayer sp) {
		ServerLevel level = sp.level();
		BlockPos search = new BlockPos(3000, 70, 3000);
		for (Scene scene : Scene.values()) {
			BlockPos found;
			if (scene == Scene.WORKSHOP) {
				found = spots.get(Scene.GARDEN).offset(24, 0, 0);
			} else {
				Pair<BlockPos, ?> result = level.findClosestBiome3d(holder -> holder.is(scene.biome), search, 4000, 32, 64);
				found = result != null ? result.getFirst().offset(32, 0, 32) : search; // step inside the biome, off its edge
			}
			spots.put(scene, found);
			CarvesAndCrafts.LOGGER.info("Gallery spot {}: {}", scene, found);
		}
	}

	private static void saveSpots() {
		Properties props = new Properties();
		spots.forEach((scene, pos) -> props.setProperty(scene.name(), pos.getX() + "," + pos.getY() + "," + pos.getZ()));
		try (Writer writer = Files.newBufferedWriter(SPOTS)) {
			props.store(writer, "Carves & Crafts gallery spots");
		} catch (IOException e) {
			CarvesAndCrafts.LOGGER.error("Could not save {}", SPOTS, e);
		}
	}

	private static void loadSpots() {
		Properties props = new Properties();
		try (Reader reader = Files.newBufferedReader(SPOTS)) {
			props.load(reader);
		} catch (IOException e) {
			throw new IllegalStateException("Run the gallery_build scenario first", e);
		}
		for (Scene scene : Scene.values()) {
			String[] xyz = props.getProperty(scene.name()).split(",");
			spots.put(scene, new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])));
		}
	}

	// ------------------------------------------------------------------ building

	private static void buildScene(ServerPlayer sp, Scene scene, boolean firstBuild) {
		ServerLevel level = sp.level();
		BlockPos o = spots.get(scene);
		if (firstBuild) {
			o = new BlockPos(o.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, o.getX(), o.getZ()), o.getZ());
			spots.put(scene, o);
		}
		if (scene.radius > 0) {
			int r = scene.radius;
			run(sp, "fill " + p(o, -r, 0, -r) + " " + p(o, r, 20, r) + " air");
			run(sp, "fill " + p(o, -r, -4, -r) + " " + p(o, r, -2, r) + " dirt");
			run(sp, "fill " + p(o, -r, -1, -r) + " " + p(o, r, -1, r) + " grass_block");
		}
		switch (scene) {
			case LANTERNS -> lanterns(sp, o);
			case PORCH -> porch(sp, o);
			case GARDEN -> garden(sp, o);
			case WILD -> wild(sp, o);
			case WORKSHOP -> workshop(sp, o);
		}
	}

	private static void lanterns(ServerPlayer sp, BlockPos o) {
		ServerLevel level = sp.level();
		run(sp, "fill " + p(o, -1, -1, 0) + " " + p(o, 1, -1, 9) + " dirt_path");
		run(sp, "fill " + p(o, -5, 0, -2) + " " + p(o, 5, 0, -2) + " hay_block");
		for (int side : new int[] {-6, 6}) {
			run(sp, "setblock " + p(o, side, 0, -1) + " dark_oak_fence");
			run(sp, "setblock " + p(o, side, 1, -1) + " dark_oak_fence");
			run(sp, "setblock " + p(o, side, 2, -1) + " lantern");
		}
		// front row on the grass, back row on the hay bales
		place(level, o.offset(-4, 0, -1), PumpkinModels.WHITE, 0, GalleryDesigns.catFace(PumpkinModels.WHITE), LightSource.TORCH, Items.TORCH);
		place(level, o.offset(-2, 0, -1), PumpkinModels.WARTY, 0, GalleryDesigns.scaryFace(PumpkinModels.WARTY), LightSource.REDSTONE_TORCH, Items.REDSTONE_TORCH);
		place(level, o.offset(0, 0, -1), PumpkinModels.CLASSIC, 0, GalleryDesigns.classicFace(PumpkinModels.CLASSIC), LightSource.TORCH, Items.TORCH);
		place(level, o.offset(2, 0, -1), PumpkinModels.CINDERELLA, 0, GalleryDesigns.happyFace(PumpkinModels.CINDERELLA), LightSource.SOUL_TORCH, Items.SOUL_TORCH);
		place(level, o.offset(4, 0, -1), PumpkinModels.KABOCHA, 0, GalleryDesigns.winkFace(PumpkinModels.KABOCHA), LightSource.COPPER_TORCH, Items.COPPER_TORCH);
		place(level, o.offset(-3, 1, -2), PumpkinModels.BLUE, 0, GalleryDesigns.classicFace(PumpkinModels.BLUE), LightSource.SOUL_TORCH, Items.SOUL_TORCH);
		place(level, o.offset(-1, 1, -2), PumpkinModels.MINI_YELLOW, 0, GalleryDesigns.happyFace(PumpkinModels.MINI_YELLOW), LightSource.CANDLE, Items.CANDLE);
		place(level, o.offset(1, 1, -2), PumpkinModels.TURBAN, 0, GalleryDesigns.scaryFace(PumpkinModels.TURBAN), LightSource.TORCH, Items.TORCH);
		place(level, o.offset(3, 1, -2), PumpkinModels.BUTTERNUT, 0, GalleryDesigns.winkFace(PumpkinModels.BUTTERNUT), LightSource.COPPER_TORCH, Items.COPPER_TORCH);
	}

	private static void porch(ServerPlayer sp, BlockPos o) {
		ServerLevel level = sp.level();
		run(sp, "fill " + p(o, -5, 0, -3) + " " + p(o, 5, 0, 1) + " spruce_planks");
		run(sp, "fill " + p(o, -2, 0, 2) + " " + p(o, 2, 0, 2) + " spruce_stairs[facing=north]");
		run(sp, "fill " + p(o, -5, 1, -3) + " " + p(o, 5, 4, -3) + " spruce_planks");
		run(sp, "fill " + p(o, -5, 1, -3) + " " + p(o, -5, 4, -3) + " stripped_spruce_log");
		run(sp, "fill " + p(o, 5, 1, -3) + " " + p(o, 5, 4, -3) + " stripped_spruce_log");
		run(sp, "fill " + p(o, -3, 2, -3) + " " + p(o, -2, 3, -3) + " glass_pane");
		run(sp, "fill " + p(o, 2, 2, -3) + " " + p(o, 3, 3, -3) + " glass_pane");
		run(sp, "setblock " + p(o, 0, 1, -3) + " spruce_door[half=lower,facing=south]");
		run(sp, "setblock " + p(o, 0, 2, -3) + " spruce_door[half=upper,facing=south]");
		for (int x : new int[] {-5, 5}) {
			run(sp, "fill " + p(o, x, 1, 1) + " " + p(o, x, 4, 1) + " spruce_fence");
			run(sp, "setblock " + p(o, x, 4, 0) + " lantern[hanging=true]");
		}
		run(sp, "fill " + p(o, -6, 5, -3) + " " + p(o, 6, 5, 2) + " spruce_slab");
		run(sp, "setblock " + p(o, 4, 1, -2) + " barrel[facing=up]");
		run(sp, "setblock " + p(o, -4, 1, 0) + " potted_red_tulip");
		run(sp, "setblock " + p(o, 4, 1, 0) + " potted_azure_bluet");
		run(sp, "fill " + p(o, -8, -1, 3) + " " + p(o, 8, -1, 8) + " grass_block");
		run(sp, "setblock " + p(o, -3, 0, 3) + " rose_bush[half=lower]");
		run(sp, "setblock " + p(o, -3, 1, 3) + " rose_bush[half=upper]");
		run(sp, "setblock " + p(o, 3, 0, 3) + " peony[half=lower]");
		run(sp, "setblock " + p(o, 3, 1, 3) + " peony[half=upper]");
		place(level, o.offset(-3, 1, -1), PumpkinModels.CINDERELLA, 0, GalleryDesigns.sunset(PumpkinModels.CINDERELLA), null, null);
		place(level, o.offset(-2, 1, 0), PumpkinModels.CLASSIC, 7, GalleryDesigns.ghost(PumpkinModels.CLASSIC), null, null);
		place(level, o.offset(-1, 1, -2), PumpkinModels.WHITE, 0, GalleryDesigns.candyStripes(PumpkinModels.WHITE), null, null);
		place(level, o.offset(2, 1, -1), PumpkinModels.BLUE, 1, GalleryDesigns.nightSky(PumpkinModels.BLUE), null, null);
		place(level, o.offset(4, 2, -2), PumpkinModels.MINI_YELLOW, 0, GalleryDesigns.flowers(PumpkinModels.MINI_YELLOW), null, null);
		place(level, o.offset(2, 1, 1), PumpkinModels.KABOCHA, 0, GalleryDesigns.bats(PumpkinModels.KABOCHA), null, null);
		place(level, o.offset(-2, 0, 3), PumpkinModels.TURBAN, 1, GalleryDesigns.flowers(PumpkinModels.TURBAN), null, null);
		place(level, o.offset(1, 0, 3), PumpkinModels.WARTY, 0, null, null, null);
	}

	private static void garden(ServerPlayer sp, BlockPos o) {
		ServerLevel level = sp.level();
		Random random = new Random(11);
		run(sp, "fill " + p(o, -8, 0, -8) + " " + p(o, 8, 0, 8) + " oak_fence");
		run(sp, "fill " + p(o, -7, 0, -7) + " " + p(o, 7, 0, 7) + " air");
		run(sp, "setblock " + p(o, 0, 0, 8) + " oak_fence_gate[facing=south]");
		List<PumpkinVariety> varieties = PumpkinVarieties.ALL;
		for (int i = 0; i < varieties.size(); i++) {
			PumpkinVariety variety = varieties.get(i);
			int z = -6 + 4 * i;
			run(sp, "fill " + p(o, -6, -1, z - 1) + " " + p(o, 6, -1, z - 1) + " water");
			run(sp, "fill " + p(o, -6, -1, z) + " " + p(o, 6, -1, z) + " farmland[moisture=7]");
			for (int x = -6; x <= 6; x++) {
				BlockPos stem = o.offset(x, 0, z);
				if (Math.floorMod(x, 2) == 0) {
					level.setBlockAndUpdate(stem, ModBlocks.ATTACHED_STEMS.get(variety).defaultBlockState()
						.setValue(AttachedPumpkinStemBlock.FACING, Direction.SOUTH));
					PumpkinModel model = variety.models().get(random.nextInt(variety.models().size()));
					place(level, stem.south(), model, random.nextInt(8), null, null, null);
				} else {
					level.setBlockAndUpdate(stem, ModBlocks.STEMS.get(variety).defaultBlockState()
						.setValue(StemBlock.AGE, random.nextInt(8)));
				}
			}
		}
		run(sp, "setblock " + p(o, -7, 0, 6) + " composter[level=5]");
		run(sp, "setblock " + p(o, 7, 0, 6) + " hay_block");
		run(sp, "setblock " + p(o, 7, 1, 6) + " hay_block");
	}

	/** Wild winter squash among the taiga trees: no flattening, pumpkins on the ground as it is. */
	private static void wild(ServerPlayer sp, BlockPos o) {
		ServerLevel level = sp.level();
		Random random = new Random(5);
		run(sp, "fill " + p(o, -5, -1, -5) + " " + p(o, 5, -1, 5) + " podzol");
		int[][] ferns = {{-4, 2}, {4, -1}, {-1, -4}, {3, 3}, {-4, -3}, {1, 4}, {-2, 0}};
		for (int[] fern : ferns) {
			run(sp, "setblock " + p(o, fern[0], 0, fern[1]) + " fern");
		}
		run(sp, "setblock " + p(o, 4, 0, 2) + " sweet_berry_bush[age=3]");
		int[][] offsets = {{0, 0}, {-2, -1}, {2, -2}, {-1, 2}, {2, 1}, {-3, -3}, {1, -3}};
		List<PumpkinModel> models = PumpkinVarieties.WINTER.models();
		for (int[] offset : offsets) {
			int x = o.getX() + offset[0];
			int z = o.getZ() + offset[1];
			BlockPos pos = new BlockPos(x, o.getY(), z);
			place(level, pos, models.get(random.nextInt(models.size())), random.nextInt(8), null, null, null);
		}
	}

	private static void workshop(ServerPlayer sp, BlockPos o) {
		ServerLevel level = sp.level();
		run(sp, "fill " + p(o, -4, -1, -4) + " " + p(o, 4, 4, 4) + " spruce_planks hollow");
		run(sp, "fill " + p(o, -3, -1, -3) + " " + p(o, 3, -1, 3) + " oak_planks");
		run(sp, "fill " + p(o, -3, 4, -3) + " " + p(o, 3, 4, 3) + " dark_oak_planks");
		for (int[] corner : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
			run(sp, "fill " + p(o, corner[0], 0, corner[1]) + " " + p(o, corner[0], 3, corner[1]) + " stripped_spruce_log");
		}
		run(sp, "fill " + p(o, -4, 1, -2) + " " + p(o, -4, 2, 1) + " glass_pane");
		run(sp, "fill " + p(o, 4, 1, -2) + " " + p(o, 4, 2, 1) + " glass_pane");
		run(sp, "fill " + p(o, -1, 1, 4) + " " + p(o, 1, 2, 4) + " glass_pane");
		run(sp, "setblock " + p(o, 0, 0, -2) + " carves_and_crafts:carving_bench[facing=south]");
		run(sp, "setblock " + p(o, -3, 0, -3) + " barrel[facing=up]");
		run(sp, "setblock " + p(o, -3, 1, -3) + " barrel[facing=up]");
		run(sp, "setblock " + p(o, 3, 0, -3) + " bookshelf");
		run(sp, "setblock " + p(o, 3, 1, -3) + " bookshelf");
		run(sp, "setblock " + p(o, 3, 0, -2) + " crafting_table");
		run(sp, "setblock " + p(o, -2, 3, -1) + " lantern[hanging=true]");
		run(sp, "setblock " + p(o, 2, 3, -1) + " lantern[hanging=true]");
		run(sp, "setblock " + p(o, -3, 0, 2) + " red_carpet");
		run(sp, "setblock " + p(o, 0, 3, 1) + " lantern[hanging=true]");
		run(sp, "setblock " + p(o, 0, 3, -3) + " lantern[hanging=true]");
		run(sp, "setblock " + p(o, 3, 0, -1) + " candle[candles=3,lit=true]");
		run(sp, "setblock " + p(o, -2, 0, -3) + " candle[candles=2,lit=true]");
		run(sp, "setblock " + p(o, -1, 0, -3) + " lantern");
		place(level, o.offset(-3, 2, -3), PumpkinModels.MINI_YELLOW, 1, GalleryDesigns.happyFace(PumpkinModels.MINI_YELLOW), LightSource.CANDLE, Items.CANDLE);
		place(level, o.offset(3, 2, -3), PumpkinModels.BLUE, 7, GalleryDesigns.nightSky(PumpkinModels.BLUE), null, null);
		place(level, o.offset(-3, 0, -1), PumpkinModels.CLASSIC, 1, null, null, null);
		place(level, o.offset(-3, 0, 0), PumpkinModels.WHITE, 2, null, null, null);
		place(level, o.offset(3, 0, 0), PumpkinModels.WARTY, 6, GalleryDesigns.scaryFace(PumpkinModels.WARTY), LightSource.TORCH, Items.TORCH);
	}

	// ------------------------------------------------------------------ helpers

	private static void place(ServerLevel level, BlockPos pos, PumpkinModel model, int rotation, PumpkinDesign design,
		LightSource light, Item lightItem) {
		Block block = ModBlocks.PUMPKINS.get(model);
		level.setBlockAndUpdate(pos, block.defaultBlockState().setValue(CustomPumpkinBlock.ROTATION, rotation));
		if (design != null && level.getBlockEntity(pos) instanceof CustomPumpkinBlockEntity entity) {
			entity.setDesign(design);
		}
		if (light != null && level.getBlockEntity(pos) instanceof CustomPumpkinBlockEntity entity) {
			entity.setLightItem(new ItemStack(lightItem));
			level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(CustomPumpkinBlock.LIGHT, light));
		}
	}

	private static void run(ServerPlayer sp, String command) {
		var server = sp.level().getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
	}

	private static String p(BlockPos o, int dx, int dy, int dz) {
		return (o.getX() + dx) + " " + (o.getY() + dy) + " " + (o.getZ() + dz);
	}
}
