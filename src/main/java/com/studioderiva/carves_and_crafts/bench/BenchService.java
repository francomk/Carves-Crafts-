package com.studioderiva.carves_and_crafts.bench;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.block.entity.BenchSlots;
import com.studioderiva.carves_and_crafts.config.ServerConfig;
import com.studioderiva.carves_and_crafts.design.AuthorList;
import com.studioderiva.carves_and_crafts.design.DesignCodec;
import com.studioderiva.carves_and_crafts.design.DesignEditRules;
import com.studioderiva.carves_and_crafts.design.EncodedDesign;
import com.studioderiva.carves_and_crafts.design.PumpkinDesign;
import com.studioderiva.carves_and_crafts.item.PaintbrushItem;
import com.studioderiva.carves_and_crafts.menu.CarvingBenchMenu;
import com.studioderiva.carves_and_crafts.model.PumpkinModel;
import com.studioderiva.carves_and_crafts.model.PumpkinModels;
import com.studioderiva.carves_and_crafts.network.ConfirmDesignPayload;
import com.studioderiva.carves_and_crafts.network.ImportSchematicPayload;
import com.studioderiva.carves_and_crafts.network.OutboundBudget;
import com.studioderiva.carves_and_crafts.network.SchematicActionPayload;
import com.studioderiva.carves_and_crafts.network.SchematicPagePayload;
import com.studioderiva.carves_and_crafts.registry.ModAttachments;
import com.studioderiva.carves_and_crafts.registry.ModComponents;
import com.studioderiva.carves_and_crafts.registry.ModItems;
import com.studioderiva.carves_and_crafts.schematic.PumpkinPresets;
import com.studioderiva.carves_and_crafts.schematic.Schematic;
import com.studioderiva.carves_and_crafts.schematic.SchematicLibrary;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Server-side carving bench actions: confirming an edit, copying a schematic onto a pumpkin and managing the
 * player's schematic library. Every entry point runs on the server thread and re-validates the open menu.
 */
public final class BenchService {
	private static final long RATE_WINDOW_MS = 60_000;
	private static final Map<UUID, Deque<Long>> recentActions = new HashMap<>();
	/** Rough size of a page entry besides its design: name, model, authors. */
	private static final int PAGE_ENTRY_OVERHEAD = 256;

	private BenchService() {
	}

	// ------------------------------------------------------------------ editing

	public static void confirmDesign(ServerPlayer player, ConfirmDesignPayload payload) {
		CarvingBenchMenu menu = openMenu(player, payload.containerId());
		if (menu == null) {
			return;
		}
		ItemStack pumpkin = menu.getPumpkin();
		PumpkinModel model = PumpkinModels.of(pumpkin);
		if (model == null) {
			reject(player, "no_pumpkin");
			return;
		}
		EncodedDesign current = pumpkin.get(ModComponents.DESIGN);
		String currentHash = current == null ? "" : current.hash();
		if (!currentHash.equals(payload.originalHash())) {
			reject(player, "stale");
			return;
		}
		PumpkinDesign edited;
		try {
			edited = DesignCodec.decode(payload.design());
		} catch (IllegalArgumentException e) {
			CarvesAndCrafts.LOGGER.warn("Invalid design from {}: {}", player.getName().getString(), e.getMessage());
			reject(player, "invalid");
			return;
		}
		PumpkinDesign original = current == null ? null : current.decode();
		DesignEditRules.Result result = DesignEditRules.check(original, edited, model.canvasFaces());
		if (!result.ok() || original == null && !model.fits(edited)) {
			reject(player, "invalid");
			return;
		}
		if (result.changedPixels() == 0) {
			return;
		}
		if (!payTools(player, menu, DesignEditRules.cost(original, edited))) {
			return;
		}
		EncodedDesign encoded = EncodedDesign.of(edited);
		AuthorList authors = pumpkin.getOrDefault(ModComponents.AUTHORS, AuthorList.EMPTY)
			.with(player.getUUID(), player.getGameProfile().name());
		ItemStack updated = pumpkin.copy();
		updated.set(ModComponents.DESIGN, encoded);
		updated.set(ModComponents.AUTHORS, authors);
		menu.setPumpkin(updated);

		if (payload.saveSchematic()) {
			if (allowAction(player)) {
				addSchematic(player, "", model, encoded, authors);
			} else {
				reject(player, "too_fast");
			}
		}
	}

	/**
	 * Wears the knife (1 per new cut, breaks at 0) and drains the brush (1 per painted pixel); free in creative.
	 * Checks everything first so a refused action costs nothing.
	 */
	private static boolean payTools(ServerPlayer player, CarvingBenchMenu menu, DesignEditRules.Cost cost) {
		if (player.isCreative()) {
			return true;
		}
		ItemStack knife = menu.getKnife();
		ItemStack brush = menu.getBrush();
		if (cost.cuts() > 0 && (!knife.is(ModItems.CARVING_KNIFE) || knife.getMaxDamage() - knife.getDamageValue() < cost.cuts())) {
			reject(player, "need_knife");
			return false;
		}
		if (cost.paints() > 0 && (!brush.is(ModItems.PAINTBRUSH) || PaintbrushItem.charge(brush) < cost.paints())) {
			reject(player, "need_paint");
			return false;
		}
		if (cost.cuts() > 0) {
			ItemStack worn = knife.copy();
			worn.hurtAndBreak(cost.cuts(), player.level(), player, item -> player.level().playSound(
				null, player.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.8F, 1.0F));
			menu.getSlot(BenchSlots.KNIFE).set(worn);
		}
		if (cost.paints() > 0) {
			ItemStack drained = brush.copy();
			PaintbrushItem.setCharge(drained, PaintbrushItem.charge(brush) - cost.paints());
			menu.getSlot(BenchSlots.BRUSH).set(drained);
		}
		return true;
	}

	// ------------------------------------------------------------------ schematics

	public static void schematicAction(ServerPlayer player, SchematicActionPayload payload) {
		CarvingBenchMenu menu = openMenu(player, payload.containerId());
		if (menu == null) {
			return;
		}
		SchematicActionPayload.Action action = payload.action();
		boolean paging = action == SchematicActionPayload.Action.PAGE || action == SchematicActionPayload.Action.PRESET_PAGE;
		if (!paging && !allowAction(player)) {
			reject(player, "too_fast");
			return; // no page back either: the reply would cost far more than the request
		}
		runAction(player, menu, payload);
		if (action.onPresets()) {
			sendPresetPage(player, payload.page());
		} else {
			sendPage(player, payload.page());
		}
	}

	private static void runAction(ServerPlayer player, CarvingBenchMenu menu, SchematicActionPayload payload) {
		SchematicLibrary library = library(player);
		List<Schematic> presets = PumpkinPresets.all();
		int presetIndex = payload.schematicId();
		Schematic preset = presetIndex >= 0 && presetIndex < presets.size() ? presets.get(presetIndex) : null;
		switch (payload.action()) {
			case PAGE, PRESET_PAGE -> {
			}
			case SAVE_FROM_PUMPKIN -> {
				ItemStack pumpkin = menu.getPumpkin();
				PumpkinModel model = PumpkinModels.of(pumpkin);
				EncodedDesign design = pumpkin.get(ModComponents.DESIGN);
				if (model == null || design == null) {
					reject(player, "no_design");
				} else {
					AuthorList authors = pumpkin.getOrDefault(ModComponents.AUTHORS, AuthorList.EMPTY);
					addSchematic(player, payload.name(), model, design, authors);
				}
			}
			case RENAME -> library.rename(payload.schematicId(), payload.name()).ifPresentOrElse(
				next -> store(player, next), () -> reject(player, "bad_name"));
			case DELETE -> library.delete(payload.schematicId()).ifPresent(next -> store(player, next));
			case APPLY -> library.find(payload.schematicId()).ifPresentOrElse(
				schematic -> applySchematic(player, menu, schematic), () -> reject(player, "missing_schematic"));
			case PRESET_APPLY -> {
				if (preset == null) {
					reject(player, "missing_schematic");
				} else if (!preset.design().hash().equals(payload.designHash())) {
					reject(player, "stale_preset");
				} else {
					applySchematic(player, menu, preset);
				}
			}
			case PRESET_SAVE -> {
				PumpkinModel model = preset == null ? null : PumpkinModels.byId(preset.model());
				if (model == null) {
					reject(player, "missing_schematic");
				} else if (!preset.design().hash().equals(payload.designHash())) {
					reject(player, "stale_preset");
				} else {
					addSchematic(player, preset.name(), model, preset.design(), preset.authors());
				}
			}
		}
	}

	/**
	 * Adds a design from a {@code .pumpkin} file on the player's computer to their library. Free, like saving:
	 * carving it onto a pumpkin later costs the usual tools. The file is untrusted and fully re-validated here.
	 */
	public static void importSchematic(ServerPlayer player, ImportSchematicPayload payload) {
		if (openMenu(player, payload.containerId()) == null) {
			return;
		}
		if (!ServerConfig.get().allowSchematicImport) {
			reject(player, "import_disabled");
		} else if (!allowAction(player)) {
			reject(player, "too_fast");
			return;
		} else if (!payload.authors().isValid()) {
			reject(player, "invalid");
		} else {
			PumpkinModel model = PumpkinModels.byId(payload.model());
			EncodedDesign design = null;
			if (model != null) {
				try {
					design = EncodedDesign.of(payload.design());
				} catch (IllegalArgumentException e) {
					CarvesAndCrafts.LOGGER.warn("Invalid imported design from {}: {}", player.getName().getString(), e.getMessage());
				}
			}
			if (model == null) {
				player.displayClientMessage(Component.translatable("message.carves_and_crafts.carving_bench.unknown_model", payload.model()), true);
			} else if (design == null || !model.fits(design.decode())) {
				reject(player, "invalid");
			} else {
				addSchematic(player, payload.name(), model, design, payload.authors().asFromFile());
			}
		}
		sendPage(player, payload.page());
	}

	/** Copies a schematic onto the virgin pumpkin in the bench, paying the full tool cost. */
	private static void applySchematic(ServerPlayer player, CarvingBenchMenu menu, Schematic schematic) {
		ItemStack pumpkin = menu.getPumpkin();
		PumpkinModel model = PumpkinModels.of(pumpkin);
		if (model == null) {
			reject(player, "no_pumpkin");
			return;
		}
		if (!model.id().equals(schematic.model())) {
			reject(player, "wrong_model");
			return;
		}
		if (pumpkin.has(ModComponents.DESIGN)) {
			reject(player, "not_virgin");
			return;
		}
		PumpkinDesign design;
		try {
			design = schematic.design().decode();
		} catch (IllegalArgumentException e) {
			reject(player, "invalid");
			return;
		}
		if (!model.fits(design)) {
			reject(player, "invalid");
			return;
		}
		if (!payTools(player, menu, DesignEditRules.cost(null, design))) {
			return;
		}
		ItemStack updated = pumpkin.copy();
		updated.set(ModComponents.DESIGN, schematic.design());
		if (!schematic.authors().isEmpty()) {
			updated.set(ModComponents.AUTHORS, schematic.authors());
		}
		menu.setPumpkin(updated);
	}

	private static void addSchematic(ServerPlayer player, String name, PumpkinModel model, EncodedDesign design, AuthorList authors) {
		Optional<SchematicLibrary.Added> added = library(player)
			.add(name, model.id(), design, authors, System.currentTimeMillis(), ServerConfig.get().maxSchematicsPerPlayer);
		if (added.isEmpty()) {
			reject(player, "library_full");
			return;
		}
		store(player, added.get().library());
		player.displayClientMessage(Component.translatable("message.carves_and_crafts.schematic.saved", added.get().schematic().name()), true);
	}

	public static void sendPage(ServerPlayer player, int page) {
		SchematicLibrary library = library(player);
		int pageCount = library.pageCount(SchematicPagePayload.PAGE_SIZE);
		int clamped = Math.max(0, Math.min(page, pageCount - 1));
		send(player, new SchematicPagePayload(clamped, pageCount, library.entries().size(),
			library.page(clamped, SchematicPagePayload.PAGE_SIZE), false));
	}

	private static void sendPresetPage(ServerPlayer player, int page) {
		List<Schematic> presets = PumpkinPresets.all();
		int size = SchematicPagePayload.PAGE_SIZE;
		int pageCount = Math.max(1, (presets.size() + size - 1) / size);
		int clamped = Math.max(0, Math.min(page, pageCount - 1));
		List<Schematic> entries = presets.subList(Math.min(clamped * size, presets.size()), Math.min((clamped + 1) * size, presets.size()));
		send(player, new SchematicPagePayload(clamped, pageCount, presets.size(), entries, true));
	}

	/** Pages are skipped when over budget; the screen keeps the last page it got and asks again on the next click. */
	private static void send(ServerPlayer player, SchematicPagePayload page) {
		long bytes = page.entries().stream().mapToLong(s -> s.design().size() + PAGE_ENTRY_OVERHEAD).sum();
		if (OutboundBudget.tryConsume(player, bytes)) {
			ServerPlayNetworking.send(player, page);
		}
	}

	private static SchematicLibrary library(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.SCHEMATICS);
	}

	private static void store(ServerPlayer player, SchematicLibrary library) {
		player.setAttached(ModAttachments.SCHEMATICS, library);
	}

	/** Optional per-player rate limit from the server config (off by default). */
	private static boolean allowAction(ServerPlayer player) {
		int limit = ServerConfig.get().schematicActionsPerMinute;
		if (limit <= 0) {
			return true;
		}
		long now = System.currentTimeMillis();
		Deque<Long> times = recentActions.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
		while (!times.isEmpty() && now - times.peekFirst() > RATE_WINDOW_MS) {
			times.pollFirst();
		}
		if (times.size() >= limit) {
			return false;
		}
		times.addLast(now);
		return true;
	}

	public static void forget(UUID player) {
		recentActions.remove(player);
	}

	// ------------------------------------------------------------------ helpers

	private static @Nullable CarvingBenchMenu openMenu(ServerPlayer player, int containerId) {
		if (player.containerMenu instanceof CarvingBenchMenu menu && menu.containerId == containerId && menu.stillValid(player)) {
			return menu;
		}
		return null; // bench closed meanwhile
	}

	private static void reject(ServerPlayer player, String reason) {
		player.displayClientMessage(Component.translatable("message.carves_and_crafts.carving_bench." + reason), true);
	}
}
