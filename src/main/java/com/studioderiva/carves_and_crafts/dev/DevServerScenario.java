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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Development aid for the dedicated server ({@code ./gradlew runServer -PserverScenario=large} together with
 * {@code ./gradlew runRemote -Pscenario=large}): checks that designs larger than the vanilla 32 KiB packet limit
 * reach a real server. On join, the player gets a bench with a blank pumpkin; the client confirms and imports a
 * large design. Then the player's inventory is filled with shulker boxes of large, all different designs and sent in
 * one packet (far over the protocol's 8 MiB if designs were sent whole), followed by a chat message showing every
 * box as a renamed item (the item hover a death message carries); the client moves one pumpkin through the
 * creative inventory and sends one with a made-up design reference. This checks the results and stops the server.
 */
public final class DevServerScenario {
	public static final BlockPos BENCH = new BlockPos(0, 200, 0);
	public static final String IMPORT_NAME = "Big remote";
	private static final int TIMEOUT_TICKS = 20 * 90;
	/** Inventory slot of the pumpkin the client moves to {@link #MOVED_SLOT}, and where it sends a made-up one. */
	public static final int PROBE_SLOT = 1;
	public static final int MOVED_SLOT = 2;
	public static final int FAKE_SLOT = 3;
	public static final int FIRST_SHULKER_SLOT = 9;
	public static final int SHULKERS = 27;

	private static int ticks = -1;
	private static int filledAt = -1;
	private static int designBytes;

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
		return largeDesign(0x010101);
	}

	/** Like {@link #largeDesign()}, a different design for each start color. */
	public static PumpkinDesign largeDesign(int startColor) {
		PumpkinDesign design = new PumpkinDesign(PumpkinModels.CLASSIC.layout(4));
		int color = startColor;
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
		if (player != null && confirmed && importedOk && filledAt < 0) {
			log(true, "large design confirmed on the dedicated server (" + expected.size() + " bytes)");
			log(true, "large design imported on the dedicated server");
			fillInventory(player);
			filledAt = ticks;
			return;
		}
		boolean moved = player != null && probe().equals(player.getInventory().getItem(MOVED_SLOT).get(ModComponents.DESIGN))
			&& player.getInventory().getItem(MOVED_SLOT).get(ModComponents.DESIGN).isComplete();
		if (moved && filledAt >= 0 && ticks > filledAt + 20 * 10 || ticks > TIMEOUT_TICKS) {
			if (filledAt < 0) {
				log(confirmed, "large design confirmed on the dedicated server (" + expected.size() + " bytes)");
				log(importedOk, "large design imported on the dedicated server");
			}
			log(player != null && filledAt >= 0, "player still connected after an inventory of " + designBytes / 1024 + " KiB of designs");
			log(player != null && filledAt >= 0, "player still connected after a chat message with every box in item hovers");
			log(moved, "creative move kept the full design on the server");
			log(player != null && player.getInventory().getItem(FAKE_SLOT).isEmpty(), "unknown design reference refused");
			ticks = -1;
			server.halt(false);
		}
	}

	private static EncodedDesign probe() {
		return EncodedDesign.of(largeDesign(0x020202));
	}

	/** 27 shulker boxes of 27 different large designs, sent as one packet, plus the probe pumpkin. */
	private static void fillInventory(ServerPlayer player) {
		designBytes = 0;
		int start = 0x030303;
		for (int box = 0; box < SHULKERS; box++) {
			List<ItemStack> pumpkins = new ArrayList<>();
			for (int i = 0; i < 27; i++) {
				EncodedDesign design = EncodedDesign.of(largeDesign(start));
				start += 0x000107;
				designBytes += design.size();
				ItemStack pumpkin = new ItemStack(ModItems.CLASSIC_PUMPKIN);
				pumpkin.set(ModComponents.DESIGN, design);
				pumpkins.add(pumpkin);
			}
			ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
			shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(pumpkins));
			player.getInventory().setItem(FIRST_SHULKER_SLOT + box, shulker);
		}
		ItemStack probe = new ItemStack(ModItems.CLASSIC_PUMPKIN);
		probe.set(ModComponents.DESIGN, probe());
		player.getInventory().setItem(PROBE_SLOT, probe);
		player.inventoryMenu.sendAllDataToRemote();
		CarvesAndCrafts.LOGGER.info("Server scenario: inventory filled with {} KiB of designs", designBytes / 1024);
		// a renamed item's display name carries the whole item (contents included) in its hover
		MutableComponent hovers = Component.literal("Boxes:");
		for (int box = 0; box < SHULKERS; box++) {
			ItemStack named = player.getInventory().getItem(FIRST_SHULKER_SLOT + box).copy();
			named.set(DataComponents.CUSTOM_NAME, Component.literal("Box " + box));
			hovers.append(" ").append(named.getDisplayName());
		}
		player.sendSystemMessage(hovers);
	}

	private static void log(boolean ok, String what) {
		if (ok) {
			CarvesAndCrafts.LOGGER.info("SCENARIO OK: {}", what);
		} else {
			CarvesAndCrafts.LOGGER.error("SCENARIO FAIL: {}", what);
		}
	}
}
