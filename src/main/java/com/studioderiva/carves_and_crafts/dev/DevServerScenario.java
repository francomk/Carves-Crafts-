package com.studioderiva.carves_and_crafts.dev;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.block.entity.BenchSlots;
import com.studioderiva.carves_and_crafts.block.entity.CarvingBenchBlockEntity;
import com.studioderiva.carves_and_crafts.design.CanvasFace;
import com.studioderiva.carves_and_crafts.design.EncodedDesign;
import com.studioderiva.carves_and_crafts.design.PumpkinDesign;
import com.studioderiva.carves_and_crafts.model.PumpkinModels;
import com.studioderiva.carves_and_crafts.registry.ModAttachments;
import com.studioderiva.carves_and_crafts.registry.ModBlocks;
import com.studioderiva.carves_and_crafts.registry.ModComponents;
import com.studioderiva.carves_and_crafts.registry.ModItems;
import com.studioderiva.carves_and_crafts.schematic.Schematic;
import com.studioderiva.carves_and_crafts.schematic.SchematicLibrary;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Development aid for the dedicated server ({@code ./gradlew runServer -PserverScenario=large} together with
 * {@code ./gradlew runRemote -Pscenario=large}): checks that designs larger than the vanilla 32 KiB packet limit
 * reach a real server. On join, the player gets a bench with a blank pumpkin; the client confirms and imports a
 * large design, and this checks the results and stops the server.
 */
public final class DevServerScenario {
	public static final BlockPos BENCH = new BlockPos(0, 200, 0);
	public static final String IMPORT_NAME = "Big remote";
	private static final int TIMEOUT_TICKS = 20 * 60;

	private static int ticks = -1;

	private DevServerScenario() {
	}

	public static void registerIfEnabled() {
		if (!"large".equals(System.getProperty("carves_and_crafts.serverScenario", ""))) {
			return;
		}
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> setUp(handler.getPlayer())));
		ServerTickEvents.END_SERVER_TICK.register(DevServerScenario::tick);
	}

	/** Classic pumpkin at x4, every pixel a different color: encodes to more than 32 KiB. */
	public static PumpkinDesign largeDesign() {
		PumpkinDesign design = new PumpkinDesign(PumpkinModels.CLASSIC.layout(4));
		int color = 0x010101;
		for (CanvasFace face : CanvasFace.values()) {
			if (!design.hasFace(face)) {
				continue;
			}
			for (int y = 0; y < design.height(face); y++) {
				for (int x = 0; x < design.width(face); x++) {
					design.paint(face, x, y, color);
					color = (color + 0x0F0D0B) & 0xFFFFFF;
				}
			}
		}
		return design;
	}

	private static void setUp(ServerPlayer player) {
		ServerLevel level = player.level();
		level.setBlockAndUpdate(BENCH.below(), Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(BENCH.offset(0, -1, 2), Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(BENCH, ModBlocks.CARVING_BENCH.defaultBlockState());
		if (level.getBlockEntity(BENCH) instanceof CarvingBenchBlockEntity bench) {
			bench.setItem(BenchSlots.PUMPKIN, new ItemStack(ModItems.CLASSIC_PUMPKIN));
		}
		player.setAttached(ModAttachments.SCHEMATICS, SchematicLibrary.EMPTY);
		player.setGameMode(GameType.CREATIVE);
		player.teleportTo(level, BENCH.getX() + 0.5, BENCH.getY(), BENCH.getZ() + 2.5, Set.of(), 180, 30, false);
		ticks = 0;
		CarvesAndCrafts.LOGGER.info("Server scenario: bench ready for {}", player.getName().getString());
	}

	private static void tick(MinecraftServer server) {
		if (ticks < 0) {
			return;
		}
		ticks++;
		ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
		EncodedDesign expected = EncodedDesign.of(largeDesign());
		boolean confirmed = server.overworld().getBlockEntity(BENCH) instanceof CarvingBenchBlockEntity bench
			&& expected.equals(bench.getItem(BenchSlots.PUMPKIN).get(ModComponents.DESIGN));
		Schematic imported = player == null ? null : player.getAttachedOrCreate(ModAttachments.SCHEMATICS).entries().stream()
			.filter(s -> s.name().equals(IMPORT_NAME)).findFirst().orElse(null);
		boolean importedOk = imported != null && imported.design().equals(expected);
		if (confirmed && importedOk || ticks > TIMEOUT_TICKS) {
			log(confirmed, "large design confirmed on the dedicated server (" + expected.size() + " bytes)");
			log(importedOk, "large design imported on the dedicated server");
			ticks = -1;
			server.halt(false);
		}
	}

	private static void log(boolean ok, String what) {
		if (ok) {
			CarvesAndCrafts.LOGGER.info("SCENARIO OK: {}", what);
		} else {
			CarvesAndCrafts.LOGGER.error("SCENARIO FAIL: {}", what);
		}
	}
}
