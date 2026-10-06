package com.studioderiva.carves_and_crafts.client.screen;

import com.studioderiva.carves_and_crafts.client.render.PumpkinAtlas;
import com.studioderiva.carves_and_crafts.client.schematic.LocalSchematics;
import com.studioderiva.carves_and_crafts.client.schematic.LocalSchematics.Entry;
import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.design.CanvasFace;
import com.studioderiva.carves_and_crafts.design.PumpkinDesign;
import com.studioderiva.carves_and_crafts.menu.CarvingBenchMenu;
import com.studioderiva.carves_and_crafts.model.PumpkinModel;
import com.studioderiva.carves_and_crafts.model.PumpkinModels;
import com.studioderiva.carves_and_crafts.network.ImportSchematicPayload;
import com.studioderiva.carves_and_crafts.network.SchematicActionPayload;
import com.studioderiva.carves_and_crafts.network.SchematicActionPayload.Action;
import com.studioderiva.carves_and_crafts.network.SchematicPagePayload;
import com.studioderiva.carves_and_crafts.registry.ModComponents;
import com.studioderiva.carves_and_crafts.schematic.PumpkinFile;
import com.studioderiva.carves_and_crafts.schematic.Schematic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Bench view with three tabs: Carving (pumpkin + tools, opens the editor), Palette (crafting) and Schematics
 * (the player's library in the world, the .pumpkin files on their computer, or the presets). Drawn with plain
 * fills, no GUI textures.
 */
public class CarvingBenchScreen extends AbstractContainerScreen<CarvingBenchMenu> {
	private static final int PANEL = 0xFFC6C6C6;
	private static final int PANEL_DARK = 0xFF555555;
	private static final int PANEL_LIGHT = 0xFFFFFFFF;
	private static final int SLOT_BG = 0xFF8B8B8B;
	private static final int LABEL = 0xFF404040;
	private static final int ROW_SELECTED = 0xFFFFFFFF;
	private static final int ROW_HOVER = 0xFFDADADA;
	private static final int LIST_X = 64;
	private static final int LIST_Y = 18;
	private static final int LIST_W = 104;
	private static final int ROW_H = 20;
	private static final int PREVIEW = 16;
	private static final int PAGE_SIZE = SchematicPagePayload.PAGE_SIZE;
	/** How often the local folder is checked for files added or removed outside the game. */
	private static final int LOCAL_REFRESH_TICKS = 40;

	private Button carveTab;
	private Button paletteTab;
	private Button schematicsTab;
	private Button carveButton;
	private Button rechargeButton;
	private final List<AbstractWidget> schematicWidgets = new ArrayList<>();
	private Button saveButton;
	private Button copyButton;
	private Button renameButton;
	private Button deleteButton;
	private Button prevButton;
	private Button nextButton;
	private Button libraryButton;
	private Button exportButton;
	private Button importButton;
	private Button folderButton;
	private Button presetSaveButton;
	private Button presetCopyButton;
	private EditBox nameBox;

	private @Nullable SchematicPagePayload page;
	/** Which list the Schematics tab shows. */
	private enum View {
		/** The player's library in this world. */
		WORLD,
		/** The .pumpkin files on this computer. */
		LOCAL,
		/** Ready-made designs from the server's data packs. */
		PRESETS
	}

	private View view = View.WORLD;
	private int worldPage;
	private int presetPage;
	private List<Entry> localEntries = List.of();
	private int localPage;
	private int refreshTimer;
	private int selectedId = -1;
	private boolean confirmDelete;
	private final Map<Integer, PumpkinDesign> previews = new HashMap<>();

	public CarvingBenchScreen(CarvingBenchMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	protected void init() {
		super.init();
		schematicWidgets.clear();
		carveTab = addRenderableWidget(Button.builder(Component.translatable("gui.carves_and_crafts.tab.carve"), b -> switchTab(CarvingBenchMenu.BUTTON_TAB_CARVE))
			.bounds(leftPos, topPos - 20, 52, 20).build());
		paletteTab = addRenderableWidget(Button.builder(Component.translatable("gui.carves_and_crafts.tab.palette"), b -> switchTab(CarvingBenchMenu.BUTTON_TAB_PALETTE))
			.bounds(leftPos + 53, topPos - 20, 52, 20).build());
		schematicsTab = addRenderableWidget(Button.builder(Component.translatable("gui.carves_and_crafts.tab.schematics"), b -> switchTab(CarvingBenchMenu.BUTTON_TAB_SCHEMATICS))
			.bounds(leftPos + 106, topPos - 20, 70, 20).build());
		carveButton = addRenderableWidget(Button.builder(Component.translatable("gui.carves_and_crafts.carve"), b -> openEditor())
			.bounds(leftPos + 104, topPos + 33, 56, 20).build());
		rechargeButton = addRenderableWidget(Button.builder(Component.literal("+"), b -> pressButton(CarvingBenchMenu.BUTTON_RECHARGE))
			.bounds(leftPos + 50, topPos + 42, 20, 20)
			.tooltip(Tooltip.create(Component.translatable("gui.carves_and_crafts.recharge")))
			.build());

		int bx = leftPos + 4;
		saveButton = schematic(Button.builder(Component.translatable("gui.carves_and_crafts.schematic.save"), b -> sendAction(Action.SAVE_FROM_PUMPKIN, -1, nameBox.getValue()))
			.bounds(bx, topPos + 40, 56, 18).tooltip(Tooltip.create(Component.translatable("gui.carves_and_crafts.schematic.save.tip"))).build());
		copyButton = schematic(Button.builder(Component.translatable("gui.carves_and_crafts.schematic.copy"), b -> sendAction(Action.APPLY, selectedId, ""))
			.bounds(bx, topPos + 60, 56, 18).tooltip(Tooltip.create(Component.translatable("gui.carves_and_crafts.schematic.copy.tip"))).build());
		renameButton = schematic(Button.builder(Component.translatable("gui.carves_and_crafts.schematic.rename"), b -> sendAction(Action.RENAME, selectedId, nameBox.getValue()))
			.bounds(bx, topPos + 80, 56, 18).build());
		deleteButton = schematic(Button.builder(Component.translatable("gui.carves_and_crafts.schematic.delete"), b -> onDelete())
			.bounds(bx, topPos + 100, 56, 18).build());
		exportButton = schematic(Button.builder(Component.translatable("gui.carves_and_crafts.schematic.export"), b -> exportSelected())
			.bounds(bx, topPos + 120, 56, 18).tooltip(Tooltip.create(Component.translatable("gui.carves_and_crafts.schematic.export.tip"))).build());
		importButton = schematic(Button.builder(Component.translatable("gui.carves_and_crafts.schematic.import"), b -> importSelected())
			.bounds(bx, topPos + 40, 56, 18).tooltip(Tooltip.create(Component.translatable("gui.carves_and_crafts.schematic.import.tip"))).build());
		folderButton = schematic(Button.builder(Component.translatable("gui.carves_and_crafts.schematic.folder"), b -> LocalSchematics.openFolder())
			.bounds(bx, topPos + 60, 56, 18).tooltip(Tooltip.create(Component.translatable("gui.carves_and_crafts.schematic.folder.tip"))).build());
		presetSaveButton = schematic(Button.builder(Component.translatable("gui.carves_and_crafts.schematic.save"), b -> sendAction(Action.PRESET_SAVE, selectedId, ""))
			.bounds(bx, topPos + 40, 56, 18).tooltip(Tooltip.create(Component.translatable("gui.carves_and_crafts.schematic.preset_save.tip"))).build());
		presetCopyButton = schematic(Button.builder(Component.translatable("gui.carves_and_crafts.schematic.copy"), b -> sendAction(Action.PRESET_APPLY, selectedId, ""))
			.bounds(bx, topPos + 60, 56, 18).tooltip(Tooltip.create(Component.translatable("gui.carves_and_crafts.schematic.copy.tip"))).build());
		libraryButton = schematic(Button.builder(Component.empty(), b -> switchLibrary())
			.bounds(leftPos + 28, topPos + 18, 32, 18).tooltip(Tooltip.create(Component.translatable("gui.carves_and_crafts.schematic.library.tip"))).build());
		nameBox = schematic(new EditBox(font, leftPos + 4, topPos + 142, 90, 16, Component.translatable("gui.carves_and_crafts.schematic.name")));
		nameBox.setMaxLength(Schematic.MAX_NAME_LENGTH);
		nameBox.setHint(Component.translatable("gui.carves_and_crafts.schematic.name"));
		prevButton = schematic(Button.builder(Component.literal("<"), b -> turnPage(-1))
			.bounds(leftPos + 130, topPos + 140, 18, 18).build());
		nextButton = schematic(Button.builder(Component.literal(">"), b -> turnPage(1))
			.bounds(leftPos + 152, topPos + 140, 18, 18).build());

		if (menu.tab() == CarvingBenchMenu.TAB_SCHEMATICS) {
			requestPage(currentPage());
		}
		if (view == View.LOCAL) {
			refreshLocal();
		}
		updateWidgets();
	}

	private <T extends AbstractWidget> T schematic(T widget) {
		schematicWidgets.add(addRenderableWidget(widget));
		return widget;
	}

	// ------------------------------------------------------------------ actions

	private void switchTab(int button) {
		pressButton(button);
		if (button == CarvingBenchMenu.BUTTON_TAB_SCHEMATICS) {
			requestPage(currentPage());
		}
		updateWidgets();
	}

	/** Applies the button locally (prediction) and sends it to the server, like the enchanting table. */
	private void pressButton(int id) {
		if (minecraft != null && minecraft.gameMode != null && menu.clickMenuButton(minecraft.player, id)) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
		}
	}

	/** Page of the server-side list (world library or presets) the view shows. */
	private int currentPage() {
		return view == View.PRESETS ? presetPage : worldPage;
	}

	private void requestPage(int pageIndex) {
		int index = Math.max(0, pageIndex);
		ClientPlayNetworking.send(view == View.PRESETS
			? SchematicActionPayload.presetPage(menu.containerId, index)
			: SchematicActionPayload.page(menu.containerId, index));
	}

	/** The last page received from the server, if it belongs to the current view. */
	private @Nullable SchematicPagePayload shownPage() {
		return page != null && view != View.LOCAL && page.presets() == (view == View.PRESETS) ? page : null;
	}

	private void sendAction(Action action, int schematicId, String name) {
		Schematic target = action.onPresets() ? selectedSchematic(schematicId) : null;
		String designHash = target != null ? target.design().hash() : "";
		ClientPlayNetworking.send(new SchematicActionPayload(menu.containerId, action, schematicId, designHash, name, currentPage()));
		confirmDelete = false;
		if (action == Action.SAVE_FROM_PUMPKIN || action == Action.RENAME) {
			nameBox.setValue("");
		}
	}

	private void onDelete() {
		if (!confirmDelete) {
			confirmDelete = true; // second click deletes
			return;
		}
		if (view == View.LOCAL) {
			deleteLocal();
		} else {
			sendAction(Action.DELETE, selectedId, "");
		}
		selectedId = -1;
	}

	private void turnPage(int delta) {
		if (view == View.LOCAL) {
			localPage = Math.max(0, Math.min(localPage + delta, localPageCount() - 1));
			selectedId = -1;
			confirmDelete = false;
		} else {
			requestPage(currentPage() + delta);
		}
	}

	private void switchLibrary() {
		view = View.values()[(view.ordinal() + 1) % View.values().length];
		selectedId = -1;
		confirmDelete = false;
		previews.clear();
		if (view == View.LOCAL) {
			refreshLocal();
		} else {
			requestPage(currentPage());
		}
	}

	/** Re-reads the local folder; files changed outside the game show up within a couple of seconds. */
	private void refreshLocal() {
		refreshTimer = 0;
		List<Entry> fresh = LocalSchematics.list();
		if (fresh.equals(localEntries)) {
			return;
		}
		Entry selected = selectedLocal();
		localEntries = fresh;
		previews.clear();
		localPage = Math.min(localPage, localPageCount() - 1);
		selectedId = selected == null ? -1 : fresh.indexOf(selected);
		if (selectedId == -1) {
			confirmDelete = false;
		}
	}

	private int localPageCount() {
		return Math.max(1, (localEntries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	private @Nullable Entry selectedLocal() {
		return view == View.LOCAL && selectedId >= 0 && selectedId < localEntries.size() ? localEntries.get(selectedId) : null;
	}

	/** Saves the selected world schematic as a .pumpkin file on this computer. */
	private void exportSelected() {
		Schematic schematic = selectedSchematic(selectedId);
		if (schematic == null) {
			return;
		}
		try {
			String fileName = LocalSchematics.export(schematic);
			message(Component.translatable("message.carves_and_crafts.schematic.exported", fileName));
		} catch (IOException e) {
			CarvesAndCrafts.LOGGER.warn("Could not export schematic {}", schematic.name(), e);
			message(Component.translatable("message.carves_and_crafts.schematic.export_failed"));
		}
	}

	/** Sends the selected local file to the server, which adds it to the world library. */
	private void importSelected() {
		Entry entry = selectedLocal();
		if (entry == null || entry.data() == null) {
			return;
		}
		PumpkinFile file = entry.data();
		if (PumpkinModels.byId(file.model()) == null) {
			message(Component.translatable("message.carves_and_crafts.carving_bench.unknown_model", file.model()));
			return;
		}
		ClientPlayNetworking.send(ImportSchematicPayload.of(menu.containerId, displayName(entry), file, currentPage()));
	}

	private void deleteLocal() {
		Entry entry = selectedLocal();
		if (entry == null) {
			return;
		}
		try {
			LocalSchematics.delete(entry);
		} catch (IOException e) {
			CarvesAndCrafts.LOGGER.warn("Could not delete {}", entry.file(), e);
			message(Component.translatable("message.carves_and_crafts.schematic.delete_failed"));
		}
		confirmDelete = false;
		refreshLocal();
	}

	private void message(Component text) {
		if (minecraft != null && minecraft.player != null) {
			minecraft.player.displayClientMessage(text, true);
		}
	}

	/** Local files are shown (and imported) under their file name: players rename them outside the game. */
	private static String displayName(Entry entry) {
		String name = entry.fileName();
		return name.substring(0, name.length() - PumpkinFile.EXTENSION.length());
	}

	/** Called when the server sends a page of the library or of the presets. */
	public void onPage(SchematicPagePayload payload) {
		page = payload;
		if (payload.presets()) {
			presetPage = payload.page();
		} else {
			worldPage = payload.page();
		}
		if (shownPage() == null) {
			return; // another list is shown; keep its selection
		}
		previews.clear();
		if (payload.entries().stream().noneMatch(s -> s.id() == selectedId)) {
			selectedId = -1;
			confirmDelete = false;
		}
	}

	/** Schematic currently selected in the list, or -1. */
	public int selectedSchematic() {
		return selectedId;
	}

	private void openEditor() {
		if (minecraft != null && PumpkinModels.of(menu.getPumpkin()) != null) {
			minecraft.setScreen(new PumpkinEditorScreen(this, menu));
		}
	}

	@Override
	protected void containerTick() {
		if (view == View.LOCAL && menu.tab() == CarvingBenchMenu.TAB_SCHEMATICS && ++refreshTimer >= LOCAL_REFRESH_TICKS) {
			refreshLocal();
		}
		updateWidgets();
	}

	private void updateWidgets() {
		int tab = menu.tab();
		boolean carve = tab == CarvingBenchMenu.TAB_CARVE;
		boolean schematics = tab == CarvingBenchMenu.TAB_SCHEMATICS;
		carveTab.active = tab != CarvingBenchMenu.TAB_CARVE;
		paletteTab.active = tab != CarvingBenchMenu.TAB_PALETTE;
		schematicsTab.active = !schematics;
		carveButton.visible = carve;
		carveButton.active = PumpkinModels.of(menu.getPumpkin()) != null;
		rechargeButton.visible = carve;
		rechargeButton.active = menu.canRecharge();

		for (AbstractWidget widget : schematicWidgets) {
			widget.visible = schematics;
		}
		PumpkinModel pumpkinModel = PumpkinModels.of(menu.getPumpkin());
		boolean designed = pumpkinModel != null && menu.getPumpkin().has(ModComponents.DESIGN);
		boolean selected = selectedId != -1;
		if (schematics) {
			for (AbstractWidget widget : List.of(saveButton, copyButton, renameButton, exportButton, nameBox)) {
				widget.visible = view == View.WORLD;
			}
			importButton.visible = view == View.LOCAL;
			folderButton.visible = view == View.LOCAL;
			presetSaveButton.visible = view == View.PRESETS;
			presetCopyButton.visible = view == View.PRESETS;
			deleteButton.visible = view != View.PRESETS;
		}
		libraryButton.setMessage(Component.translatable("gui.carves_and_crafts.schematic.library." + view.name().toLowerCase(Locale.ROOT)));
		boolean copyable = selected && pumpkinModel != null && !designed && compatible(selectedSchematic(selectedId), pumpkinModel);
		presetSaveButton.active = selected;
		presetCopyButton.active = copyable;
		Entry localEntry = selectedLocal();
		importButton.active = localEntry != null && localEntry.data() != null;
		exportButton.active = selected && view == View.WORLD;
		saveButton.active = designed;
		copyButton.active = copyable;
		renameButton.active = selected && !Schematic.sanitizeName(nameBox.getValue()).isEmpty();
		deleteButton.active = selected;
		deleteButton.setMessage(Component.translatable(confirmDelete ? "gui.carves_and_crafts.schematic.delete_confirm" : "gui.carves_and_crafts.schematic.delete"));
		if (view == View.LOCAL) {
			prevButton.active = localPage > 0;
			nextButton.active = localPage < localPageCount() - 1;
		} else {
			SchematicPagePayload shown = shownPage();
			prevButton.active = shown != null && shown.page() > 0;
			nextButton.active = shown != null && shown.page() < shown.pageCount() - 1;
		}
	}

	/** A line of the list: a world schematic, or a local file ({@code schematic} is null if the file is unreadable). */
	private record Row(int id, String name, @Nullable Schematic schematic) {
	}

	/** Lines of the page currently shown. Local rows use the file's index in the folder as id. */
	private List<Row> rows() {
		List<Row> rows = new ArrayList<>(PAGE_SIZE);
		if (view == View.LOCAL) {
			for (int i = localPage * PAGE_SIZE; i < Math.min(localEntries.size(), (localPage + 1) * PAGE_SIZE); i++) {
				Entry entry = localEntries.get(i);
				String name = displayName(entry);
				PumpkinFile data = entry.data();
				Schematic schematic = data == null ? null : new Schematic(i, name, data.createdAt(), data.model(), data.design(), data.authors());
				rows.add(new Row(i, name, schematic));
			}
		} else if (shownPage() != null) {
			for (Schematic schematic : shownPage().entries()) {
				rows.add(new Row(schematic.id(), schematic.name(), schematic));
			}
		}
		return rows;
	}

	/** World schematic or preset with this id on the current page (null in the local view). */
	private @Nullable Schematic selectedSchematic(int id) {
		SchematicPagePayload shown = shownPage();
		return shown == null ? null : shown.entries().stream().filter(s -> s.id() == id).findFirst().orElse(null);
	}

	/** A schematic can only be copied onto the model it was made for. */
	private static boolean compatible(@Nullable Schematic schematic, @Nullable PumpkinModel pumpkinModel) {
		return schematic != null && pumpkinModel != null && pumpkinModel.id().equals(schematic.model());
	}

	/** Model of a schematic; schematics of models that no longer exist are previewed on the classic one. */
	private static PumpkinModel modelOf(Schematic schematic) {
		PumpkinModel model = PumpkinModels.byId(schematic.model());
		return model != null ? model : PumpkinModels.CLASSIC;
	}

	private static Component modelName(Schematic schematic) {
		return PumpkinModels.byId(schematic.model()) != null
			? Component.translatable("model.carves_and_crafts." + schematic.model())
			: Component.literal(schematic.model());
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (menu.tab() == CarvingBenchMenu.TAB_SCHEMATICS && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			int row = rowAt(event.x(), event.y());
			List<Row> rows = rows();
			if (row >= 0 && row < rows.size()) {
				int id = rows.get(row).id();
				if (id != selectedId) {
					selectedId = id;
					confirmDelete = false;
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// typing a name must not trigger the inventory key (E) and close the screen
		if (nameBox.isVisible() && nameBox.isFocused() && event.key() != GLFW.GLFW_KEY_ESCAPE) {
			nameBox.keyPressed(event);
			return true;
		}
		return super.keyPressed(event);
	}

	private int rowAt(double mx, double my) {
		int x = leftPos + LIST_X;
		int y = topPos + LIST_Y;
		if (mx < x || mx >= x + LIST_W || my < y) {
			return -1;
		}
		int row = (int) ((my - y) / ROW_H);
		return row < PAGE_SIZE ? row : -1;
	}

	// ------------------------------------------------------------------ rendering

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		int x = leftPos;
		int y = topPos;
		graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
		graphics.fill(x, y, x + imageWidth, y + 1, PANEL_LIGHT);
		graphics.fill(x, y, x + 1, y + imageHeight, PANEL_LIGHT);
		graphics.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, PANEL_DARK);
		graphics.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, PANEL_DARK);
		for (Slot slot : menu.slots) {
			if (!slot.isActive()) {
				continue;
			}
			int sx = x + slot.x - 1;
			int sy = y + slot.y - 1;
			graphics.fill(sx, sy, sx + 18, sy + 18, PANEL_DARK);
			graphics.fill(sx + 1, sy + 1, sx + 18, sy + 18, PANEL_LIGHT);
			graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT_BG);
		}
		if (menu.tab() == CarvingBenchMenu.TAB_PALETTE) {
			int ax = x + 124;
			int ay = y + 59;
			graphics.fill(ax, ay + 3, ax + 20, ay + 6, PANEL_DARK);
			graphics.fill(ax + 20, ay, ax + 23, ay + 9, PANEL_DARK);
		} else if (menu.tab() == CarvingBenchMenu.TAB_SCHEMATICS) {
			renderList(graphics, mouseX, mouseY);
		}
	}

	private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
		int x = leftPos + LIST_X;
		int y = topPos + LIST_Y;
		graphics.fill(x - 1, y - 1, x + LIST_W + 1, y + ROW_H * PAGE_SIZE + 1, PANEL_DARK);
		graphics.fill(x, y, x + LIST_W, y + ROW_H * PAGE_SIZE, SLOT_BG);
		int hovered = rowAt(mouseX, mouseY);
		List<Row> rows = rows();
		PumpkinModel pumpkinModel = PumpkinModels.of(menu.getPumpkin());
		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			Schematic schematic = row.schematic();
			int ry = y + i * ROW_H;
			if (row.id() == selectedId) {
				graphics.fill(x, ry, x + LIST_W, ry + ROW_H, ROW_SELECTED);
			} else if (i == hovered) {
				graphics.fill(x, ry, x + LIST_W, ry + ROW_H, ROW_HOVER);
			}
			// dimmed: world schematics for another model (can't be copied onto this pumpkin),
			// local files that are unreadable or for a model that isn't installed (can't be imported)
			boolean usable = view == View.LOCAL
				? schematic != null && PumpkinModels.byId(schematic.model()) != null
				: pumpkinModel == null || compatible(schematic, pumpkinModel);
			String name = font.plainSubstrByWidth(row.name(), LIST_W - PREVIEW - 8);
			graphics.drawString(font, name, x + PREVIEW + 5, ry + 2, usable ? LABEL : 0xFF808080, false);
			Component info;
			if (schematic == null) {
				graphics.fill(x + 2, ry + 2, x + 2 + PREVIEW, ry + 2 + PREVIEW, 0xFF000000);
				info = Component.translatable("gui.carves_and_crafts.schematic.unreadable");
			} else {
				renderPreview(graphics, schematic, x + 2, ry + 2);
				PumpkinDesign design = preview(schematic);
				Integer density = design == null ? null : modelOf(schematic).density(design);
				info = modelName(schematic).copy().append(density == null ? "" : " x" + density);
			}
			graphics.drawString(font, font.plainSubstrByWidth(info.getString(), LIST_W - PREVIEW - 8), x + PREVIEW + 5, ry + 11,
				usable ? 0xFF606060 : 0xFF909090, false);
		}
	}

	/** Front face, sampled down to fit a 16×16 thumbnail (aspect ratio kept). */
	private void renderPreview(GuiGraphics graphics, Schematic schematic, int x, int y) {
		PumpkinDesign design = preview(schematic);
		graphics.fill(x, y, x + PREVIEW, y + PREVIEW, 0xFF000000);
		if (design == null || !design.hasFace(CanvasFace.NORTH)) {
			return;
		}
		int w = design.width(CanvasFace.NORTH);
		int h = design.height(CanvasFace.NORTH);
		int side = Math.max(w, h);
		int pw = Math.max(1, PREVIEW * w / side);
		int ph = Math.max(1, PREVIEW * h / side);
		int ox = x + (PREVIEW - pw) / 2;
		int oy = y + (PREVIEW - ph) / 2;
		for (int py = 0; py < ph; py++) {
			for (int px = 0; px < pw; px++) {
				int cx = px * w / pw;
				int cy = py * h / ph;
				int color;
				if (design.isCut(CanvasFace.NORTH, cx, cy)) {
					color = 0xFF2A1A08;
				} else {
					int paint = design.colorAt(CanvasFace.NORTH, cx, cy);
					color = paint != -1 ? 0xFF000000 | paint : PumpkinAtlas.skinColor(modelOf(schematic), design, CanvasFace.NORTH, cx, cy);
				}
				graphics.fill(ox + px, oy + py, ox + px + 1, oy + py + 1, color);
			}
		}
	}

	private @Nullable PumpkinDesign preview(Schematic schematic) {
		if (previews.containsKey(schematic.id())) {
			return previews.get(schematic.id());
		}
		PumpkinDesign design;
		try {
			design = schematic.design().decode();
		} catch (IllegalArgumentException e) {
			design = null;
		}
		previews.put(schematic.id(), design);
		return design;
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, titleLabelX, titleLabelY, LABEL, false);
		int tab = menu.tab();
		if (tab != CarvingBenchMenu.TAB_SCHEMATICS) {
			graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
		}
		if (tab == CarvingBenchMenu.TAB_CARVE) {
			graphics.drawString(font, Component.translatable("gui.carves_and_crafts.slot.tools"), 28, 24, LABEL, false);
		} else if (tab == CarvingBenchMenu.TAB_PALETTE) {
			graphics.drawString(font, Component.translatable("gui.carves_and_crafts.slot.plank"), 28, 59, LABEL, false);
		} else if (view == View.LOCAL) {
			renderCount(graphics, localPage, localPageCount(), localEntries.size(), "gui.carves_and_crafts.schematic.empty_local");
		} else if (shownPage() != null) {
			SchematicPagePayload shown = shownPage();
			renderCount(graphics, shown.page(), shown.pageCount(), shown.total(),
				view == View.PRESETS ? "gui.carves_and_crafts.schematic.empty_presets" : "gui.carves_and_crafts.schematic.empty");
		}
	}

	private void renderCount(GuiGraphics graphics, int pageIndex, int pageCount, int total, String emptyKey) {
		String count = (pageIndex + 1) + "/" + pageCount + " (" + total + ")";
		graphics.drawString(font, count, imageWidth - 6 - font.width(count), titleLabelY, LABEL, false);
		if (total == 0) {
			graphics.drawWordWrap(font, Component.translatable(emptyKey), LIST_X + 4, LIST_Y + 4, LIST_W - 8, LABEL, false);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}
}
