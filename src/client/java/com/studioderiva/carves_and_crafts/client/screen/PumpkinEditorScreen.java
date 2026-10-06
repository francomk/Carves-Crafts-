package com.studioderiva.carves_and_crafts.client.screen;

import com.studioderiva.carves_and_crafts.client.render.PumpkinAtlas;
import com.studioderiva.carves_and_crafts.design.CanvasFace;
import com.studioderiva.carves_and_crafts.design.CanvasLayout;
import com.studioderiva.carves_and_crafts.design.DesignCodec;
import com.studioderiva.carves_and_crafts.design.DesignEditRules;
import com.studioderiva.carves_and_crafts.design.EditorSession;
import com.studioderiva.carves_and_crafts.design.EditorSession.Tool;
import com.studioderiva.carves_and_crafts.design.EncodedDesign;
import com.studioderiva.carves_and_crafts.design.PumpkinDesign;
import com.studioderiva.carves_and_crafts.item.PaintbrushItem;
import com.studioderiva.carves_and_crafts.menu.CarvingBenchMenu;
import com.studioderiva.carves_and_crafts.model.PumpkinModel;
import com.studioderiva.carves_and_crafts.model.PumpkinModels;
import com.studioderiva.carves_and_crafts.network.ConfirmDesignPayload;
import com.studioderiva.carves_and_crafts.registry.ModComponents;
import com.studioderiva.carves_and_crafts.registry.ModItems;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Full-screen carving editor opened from the bench. Edits a local copy; nothing reaches the server until Confirm.
 *
 * <p>Layout: tools on the left, face tabs on top, canvas in the middle, color picker on the right,
 * confirm/cancel at the bottom. Left mouse uses the tool, right mouse pans, wheel zooms.
 */
public class PumpkinEditorScreen extends Screen {
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFA0A0A0;
	private static final int WARN = 0xFFFF5555;
	private static final int PANEL = 0xC0101010;
	private static final int CANVAS_FRAME = 0xFF000000;
	private static final int HOVER = 0xFFFFFFFF;
	private static final int LEFT_W = 76;
	private static final int RIGHT_W = 124;
	private static final int TOP_H = 28;
	private static final int BOTTOM_H = 30;
	private static final int SV_SIZE = 100;
	private static final int HUE_H = 10;
	private static final int SWATCH = 12;
	private static final int MAX_RECENT = 8;

	private final CarvingBenchScreen parent;
	private final CarvingBenchMenu menu;
	private final String originalHash;
	private final EditorSession session;
	private final PumpkinModel model;

	private CanvasFace face = CanvasFace.NORTH;
	private Tool tool = Tool.CUT;
	private int color = 0xE65100;
	private float hue;
	private float saturation;
	private float value;
	private final List<Integer> recent = new ArrayList<>();

	private float zoom = 1.0F;
	private float panX;
	private float panY;
	private boolean painting;
	private boolean panning;
	private int lastPixelX = -1;
	private int lastPixelY = -1;
	private Drag drag = Drag.NONE;
	private boolean confirmingDiscard;

	private final Map<Tool, Button> toolButtons = new EnumMap<>(Tool.class);
	private final Map<CanvasFace, Button> faceButtons = new EnumMap<>(CanvasFace.class);
	private final List<AbstractWidget> mainWidgets = new ArrayList<>();
	private final List<Button> densityButtons = new ArrayList<>();
	private Button mirrorButton;
	private Button undoButton;
	private Button redoButton;
	private Button confirmButton;
	private Button saveSchematicButton;
	private boolean saveSchematic;
	private Button discardButton;
	private Button keepButton;
	private EditBox hexBox;
	private boolean updatingHex;

	private enum Drag {
		NONE,
		SATURATION_VALUE,
		HUE
	}

	public PumpkinEditorScreen(CarvingBenchScreen parent, CarvingBenchMenu menu) {
		super(Component.translatable("gui.carves_and_crafts.editor.title"));
		this.parent = parent;
		this.menu = menu;
		EncodedDesign encoded = menu.getPumpkin().get(ModComponents.DESIGN);
		PumpkinDesign original = encoded != null ? encoded.decode() : null;
		this.originalHash = encoded != null ? encoded.hash() : "";
		this.model = Objects.requireNonNull(PumpkinModels.of(menu.getPumpkin()), "editor opened without a pumpkin");
		this.session = new EditorSession(original);
		if (!model.hasFace(face)) {
			for (CanvasFace f : CanvasFace.values()) {
				if (model.hasFace(f)) {
					face = f;
					break;
				}
			}
		}
		setColor(color);
	}

	// ------------------------------------------------------------------ layout

	@Override
	protected void init() {
		toolButtons.clear();
		faceButtons.clear();
		mainWidgets.clear();
		densityButtons.clear();

		int y = TOP_H + 4;
		for (Tool t : Tool.values()) {
			Button b = main(Button.builder(Component.empty(), btn -> selectTool(t)).bounds(6, y, LEFT_W - 12, 20).build());
			toolButtons.put(t, b);
			y += 22;
		}
		y += 6;
		mirrorButton = main(Button.builder(Component.empty(), b -> session.setMirror(!session.mirror())).bounds(6, y, LEFT_W - 12, 20).build());
		y += 28;
		undoButton = main(Button.builder(Component.translatable("gui.carves_and_crafts.editor.undo"), b -> session.undo()).bounds(6, y, LEFT_W - 12, 20).build());
		y += 22;
		redoButton = main(Button.builder(Component.translatable("gui.carves_and_crafts.editor.redo"), b -> session.redo()).bounds(6, y, LEFT_W - 12, 20).build());

		int tabX = LEFT_W + 4;
		for (CanvasFace f : CanvasFace.values()) {
			if (!model.hasFace(f)) {
				continue;
			}
			Button b = main(Button.builder(Component.translatable("gui.carves_and_crafts.face." + f.name().toLowerCase(Locale.ROOT)), btn -> face = f)
				.bounds(tabX, 4, 50, 20).build());
			faceButtons.put(f, b);
			tabX += 52;
		}

		int rightX = width - RIGHT_W + 6;
		int hexY = TOP_H + 4 + SV_SIZE + 4 + HUE_H + 6;
		hexBox = main(new EditBox(font, rightX + 14, hexY, 60, 16, Component.literal("hex")));
		hexBox.setMaxLength(7);
		hexBox.setResponder(this::onHexChanged);
		updateHexBox();

		confirmButton = main(Button.builder(Component.translatable("gui.carves_and_crafts.editor.confirm"), b -> confirm())
			.bounds(width - 166, height - BOTTOM_H + 5, 78, 20).build());
		saveSchematicButton = main(Button.builder(Component.empty(), b -> saveSchematic = !saveSchematic)
			.bounds(width - 290, height - BOTTOM_H + 5, 120, 20).build());
		main(Button.builder(Component.translatable("gui.carves_and_crafts.editor.cancel"), b -> requestClose())
			.bounds(width - 84, height - BOTTOM_H + 5, 78, 20).build());

		// one button per density, labelled with the front canvas size
		List<Integer> densities = model.densities();
		int rx = canvasCenterX() - (densities.size() * 70) / 2;
		for (int d : densities) {
			CanvasLayout layout = model.layout(d);
			CanvasFace front = firstFace();
			Component label = Component.translatable("gui.carves_and_crafts.editor.density", d, layout.width(front), layout.height(front));
			densityButtons.add(addRenderableWidget(Button.builder(label, b -> session.chooseLayout(layout))
				.bounds(rx, height / 2, 66, 20).build()));
			rx += 70;
		}

		int cx = width / 2;
		discardButton = addRenderableWidget(Button.builder(Component.translatable("gui.carves_and_crafts.editor.discard"), b -> backToBench())
			.bounds(cx - 104, height / 2 + 6, 100, 20).build());
		keepButton = addRenderableWidget(Button.builder(Component.translatable("gui.carves_and_crafts.editor.keep"), b -> confirmingDiscard = false)
			.bounds(cx + 4, height / 2 + 6, 100, 20).build());
		refreshWidgets();
	}

	private <T extends AbstractWidget> T main(T widget) {
		mainWidgets.add(addRenderableWidget(widget));
		return widget;
	}

	private void refreshWidgets() {
		boolean editing = !session.needsLayout() && !confirmingDiscard;
		for (AbstractWidget w : mainWidgets) {
			w.active = !confirmingDiscard;
		}
		for (var e : toolButtons.entrySet()) {
			Component name = Component.translatable("gui.carves_and_crafts.tool." + e.getKey().name().toLowerCase(Locale.ROOT));
			e.getValue().setMessage(e.getKey() == tool ? Component.literal("> ").append(name) : name);
			e.getValue().active = editing;
		}
		for (var e : faceButtons.entrySet()) {
			PumpkinDesign working = session.working();
			e.getValue().active = editing && working != null && working.hasFace(e.getKey()) && e.getKey() != face;
		}
		Component mirror = Component.translatable("gui.carves_and_crafts.editor.mirror");
		mirrorButton.setMessage(session.mirror() ? Component.literal("> ").append(mirror) : mirror);
		mirrorButton.active = editing;
		undoButton.active = editing && session.canUndo();
		redoButton.active = editing && session.canRedo();
		confirmButton.active = editing && session.isDirty() && affordable();
		saveSchematicButton.setMessage(Component.translatable(saveSchematic ? "gui.carves_and_crafts.editor.save_on" : "gui.carves_and_crafts.editor.save_off"));
		saveSchematicButton.active = editing;
		for (Button b : densityButtons) {
			b.visible = session.needsLayout();
			b.active = !confirmingDiscard;
		}
		discardButton.visible = confirmingDiscard;
		keepButton.visible = confirmingDiscard;
	}

	@Override
	public void tick() {
		if (minecraft != null && minecraft.player != null && minecraft.player.containerMenu != menu) {
			minecraft.setScreen(null); // bench closed or broken
			return;
		}
		refreshWidgets();
	}

	// ------------------------------------------------------------------ geometry helpers

	private int canvasLeft() {
		return LEFT_W + 4;
	}

	private int canvasTop() {
		return TOP_H + 4;
	}

	private int canvasRight() {
		return width - RIGHT_W - 4;
	}

	private int canvasBottom() {
		return height - BOTTOM_H - 4;
	}

	private int canvasCenterX() {
		return (canvasLeft() + canvasRight()) / 2;
	}

	private CanvasFace firstFace() {
		for (CanvasFace f : CanvasFace.values()) {
			if (model.hasFace(f)) {
				return f;
			}
		}
		throw new IllegalStateException("Model without carvable faces");
	}

	/** Pixel size on screen at zoom 1: largest integer that fits the area. */
	private int baseCell(PumpkinDesign design) {
		int w = design.width(face);
		int h = design.height(face);
		return Math.max(1, Math.min((canvasRight() - canvasLeft()) / w, (canvasBottom() - canvasTop()) / h));
	}

	private float cell(PumpkinDesign design) {
		return baseCell(design) * zoom;
	}

	private float originX(PumpkinDesign design) {
		return canvasCenterX() - cell(design) * design.width(face) / 2.0F + panX;
	}

	private float originY(PumpkinDesign design) {
		return (canvasTop() + canvasBottom()) / 2.0F - cell(design) * design.height(face) / 2.0F + panY;
	}

	private boolean inCanvasArea(double mx, double my) {
		return mx >= canvasLeft() && mx < canvasRight() && my >= canvasTop() && my < canvasBottom();
	}

	/** Canvas pixel under the mouse, or null. */
	private int @Nullable [] pixelAt(double mx, double my) {
		PumpkinDesign working = session.working();
		if (working == null || !inCanvasArea(mx, my)) {
			return null;
		}
		int x = Mth.floor((mx - originX(working)) / cell(working));
		int y = Mth.floor((my - originY(working)) / cell(working));
		return x >= 0 && y >= 0 && x < working.width(face) && y < working.height(face) ? new int[] {x, y} : null;
	}

	/** Screen position of the center of a canvas pixel; used by the dev scenario driver. */
	public double @Nullable [] pixelCenterOnScreen(int x, int y) {
		PumpkinDesign working = session.working();
		if (working == null) {
			return null;
		}
		return new double[] {originX(working) + (x + 0.5) * cell(working), originY(working) + (y + 0.5) * cell(working)};
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, LEFT_W, height, PANEL);
		graphics.fill(width - RIGHT_W, 0, width, height, PANEL);
		graphics.fill(LEFT_W, 0, width - RIGHT_W, TOP_H, PANEL);
		graphics.fill(LEFT_W, height - BOTTOM_H, width - RIGHT_W, height, PANEL);

		PumpkinDesign working = session.working();
		if (working == null) {
			graphics.drawCenteredString(font, Component.translatable("gui.carves_and_crafts.editor.choose_density"), canvasCenterX(), height / 2 - 30, TEXT);
			graphics.drawCenteredString(font, Component.translatable("gui.carves_and_crafts.editor.density_note"), canvasCenterX(), height / 2 - 16, TEXT_DIM);
		} else {
			renderCanvas(graphics, working, mouseX, mouseY);
			renderCost(graphics);
		}
		renderColorPanel(graphics);

		super.render(graphics, mouseX, mouseY, partialTick);

		if (confirmingDiscard) {
			graphics.nextStratum();
			graphics.fill(0, 0, width, height, 0xA0000000);
			graphics.fill(width / 2 - 120, height / 2 - 30, width / 2 + 120, height / 2 + 34, 0xFF202020);
			graphics.drawCenteredString(font, Component.translatable("gui.carves_and_crafts.editor.discard_title"), width / 2, height / 2 - 16, TEXT);
			discardButton.render(graphics, mouseX, mouseY, partialTick);
			keepButton.render(graphics, mouseX, mouseY, partialTick);
		}
	}

	/** Knife and brush usage of the pending edit against what the bench holds. */
	private void renderCost(GuiGraphics graphics) {
		DesignEditRules.Cost cost = cost();
		int y = height - BOTTOM_H + 6;
		int x = LEFT_W + 6;
		if (creative()) {
			graphics.drawString(font, Component.translatable("gui.carves_and_crafts.editor.creative"), x, y + 5, TEXT_DIM);
			return;
		}
		int knifeLeft = knifeLeft();
		int paintLeft = paintLeft();
		graphics.drawString(font, Component.translatable("gui.carves_and_crafts.editor.knife_cost", cost.cuts(), knifeLeft),
			x, y, cost.cuts() > knifeLeft ? WARN : TEXT);
		graphics.drawString(font, Component.translatable("gui.carves_and_crafts.editor.paint_cost", cost.paints(), paintLeft),
			x, y + 11, cost.paints() > paintLeft ? WARN : TEXT);
	}

	private DesignEditRules.Cost cost() {
		PumpkinDesign working = session.working();
		return working == null ? DesignEditRules.Cost.NONE : DesignEditRules.cost(session.original(), working);
	}

	private boolean creative() {
		return minecraft != null && minecraft.player != null && minecraft.player.isCreative();
	}

	private int knifeLeft() {
		ItemStack knife = menu.getKnife();
		return knife.is(ModItems.CARVING_KNIFE) ? knife.getMaxDamage() - knife.getDamageValue() : 0;
	}

	private int paintLeft() {
		ItemStack brush = menu.getBrush();
		return brush.is(ModItems.PAINTBRUSH) ? PaintbrushItem.charge(brush) : 0;
	}

	/** Same check the server does; the server stays authoritative. */
	private boolean affordable() {
		if (creative()) {
			return true;
		}
		DesignEditRules.Cost cost = cost();
		return cost.cuts() <= knifeLeft() && cost.paints() <= paintLeft();
	}

	private void renderCanvas(GuiGraphics graphics, PumpkinDesign design, int mouseX, int mouseY) {
		int w = design.width(face);
		int h = design.height(face);
		float c = cell(design);
		float ox = originX(design);
		float oy = originY(design);
		graphics.enableScissor(canvasLeft(), canvasTop(), canvasRight(), canvasBottom());
		graphics.fill(Mth.floor(ox) - 1, Mth.floor(oy) - 1, Mth.ceil(ox + c * w) + 1, Mth.ceil(oy + c * h) + 1, CANVAS_FRAME);
		for (int y = 0; y < h; y++) {
			int y0 = Mth.floor(oy + y * c);
			int y1 = Mth.floor(oy + (y + 1) * c);
			if (y1 < canvasTop() || y0 > canvasBottom()) {
				continue;
			}
			for (int x = 0; x < w; x++) {
				int x0 = Mth.floor(ox + x * c);
				int x1 = Mth.floor(ox + (x + 1) * c);
				if (x1 < canvasLeft() || x0 > canvasRight()) {
					continue;
				}
				graphics.fill(x0, y0, x1, y1, pixelColor(design, x, y));
			}
		}
		int[] hovered = pixelAt(mouseX, mouseY);
		if (hovered != null && !confirmingDiscard) {
			outlinePixel(graphics, hovered[0], hovered[1], ox, oy, c);
			if (session.mirror() && tool != Tool.PICKER) {
				outlinePixel(graphics, w - 1 - hovered[0], hovered[1], ox, oy, c);
			}
		}
		graphics.disableScissor();
	}

	private int pixelColor(PumpkinDesign design, int x, int y) {
		if (design.isCut(face, x, y)) {
			// darkened flesh: reads as a hole
			int flesh = PumpkinAtlas.fleshColor(model, design, face, x, y);
			return 0xFF000000 | ((((flesh >> 16) & 0xFF) / 3) << 16) | ((((flesh >> 8) & 0xFF) / 3) << 8) | ((flesh & 0xFF) / 3);
		}
		int paint = design.colorAt(face, x, y);
		return paint != -1 ? 0xFF000000 | paint : PumpkinAtlas.skinColor(model, design, face, x, y);
	}

	private void outlinePixel(GuiGraphics graphics, int x, int y, float ox, float oy, float c) {
		int x0 = Mth.floor(ox + x * c);
		int y0 = Mth.floor(oy + y * c);
		int size = Math.max(2, Mth.floor(c));
		graphics.renderOutline(x0, y0, size, size, HOVER);
	}

	private void renderColorPanel(GuiGraphics graphics) {
		int x = width - RIGHT_W + 6;
		int y = TOP_H + 4;
		// saturation (horizontal) / value (vertical) square for the current hue
		int cells = 25;
		int cellSize = SV_SIZE / cells;
		for (int i = 0; i < cells; i++) {
			for (int j = 0; j < cells; j++) {
				float s = (i + 0.5F) / cells;
				float v = 1.0F - (j + 0.5F) / cells;
				graphics.fill(x + i * cellSize, y + j * cellSize, x + (i + 1) * cellSize, y + (j + 1) * cellSize, 0xFF000000 | Mth.hsvToRgb(hue, s, v));
			}
		}
		int markX = x + Math.round(saturation * SV_SIZE);
		int markY = y + Math.round((1.0F - value) * SV_SIZE);
		graphics.renderOutline(markX - 2, markY - 2, 5, 5, 0xFFFFFFFF);

		int hueY = y + SV_SIZE + 4;
		int segments = 50;
		for (int i = 0; i < segments; i++) {
			graphics.fill(x + i * 2, hueY, x + i * 2 + 2, hueY + HUE_H, 0xFF000000 | Mth.hsvToRgb((i + 0.5F) / segments, 1.0F, 1.0F));
		}
		int hueMark = x + Math.round(hue * SV_SIZE);
		graphics.fill(hueMark - 1, hueY - 1, hueMark + 1, hueY + HUE_H + 1, 0xFFFFFFFF);

		int hexY = hueY + HUE_H + 6;
		graphics.fill(x, hexY, x + 12, hexY + 16, 0xFF000000 | color);
		graphics.renderOutline(x, hexY, 12, 16, 0xFFFFFFFF);

		int swY = hexY + 22;
		DyeColor[] dyes = DyeColor.values();
		for (int i = 0; i < dyes.length; i++) {
			int sx = x + (i % 8) * (SWATCH + 2);
			int sy = swY + (i / 8) * (SWATCH + 2);
			graphics.fill(sx, sy, sx + SWATCH, sy + SWATCH, 0xFF000000 | (dyes[i].getTextureDiffuseColor() & 0xFFFFFF));
		}
		int recentY = swY + 2 * (SWATCH + 2) + 6;
		for (int i = 0; i < recent.size(); i++) {
			int sx = x + i * (SWATCH + 2);
			graphics.fill(sx, recentY, sx + SWATCH, recentY + SWATCH, 0xFF000000 | recent.get(i));
		}
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (confirmingDiscard) {
			return discardButton.mouseClicked(event, doubleClick) || keepButton.mouseClicked(event, doubleClick);
		}
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		double mx = event.x();
		double my = event.y();
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			if (handleColorPanelClick(mx, my)) {
				return true;
			}
			int[] pixel = pixelAt(mx, my);
			if (pixel != null) {
				useTool(pixel[0], pixel[1], true);
				return true;
			}
		} else if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && inCanvasArea(mx, my)) {
			panning = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (panning) {
			panX += (float) dx;
			panY += (float) dy;
			return true;
		}
		if (drag != Drag.NONE) {
			updateDrag(event.x(), event.y());
			return true;
		}
		if (painting) {
			int[] pixel = pixelAt(event.x(), event.y());
			if (pixel != null) {
				strokeTo(pixel[0], pixel[1]);
			}
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (painting) {
			painting = false;
			session.endStroke();
			pushRecent();
		}
		panning = false;
		drag = Drag.NONE;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		PumpkinDesign working = session.working();
		if (working == null || !inCanvasArea(mx, my) || scrollY == 0) {
			return super.mouseScrolled(mx, my, scrollX, scrollY);
		}
		// keep the point under the mouse fixed while zooming
		float beforeX = (float) ((mx - originX(working)) / cell(working));
		float beforeY = (float) ((my - originY(working)) / cell(working));
		zoom = Mth.clamp(zoom * (scrollY > 0 ? 1.25F : 0.8F), 1.0F, 8.0F);
		panX += (float) (mx - (originX(working) + beforeX * cell(working)));
		panY += (float) (my - (originY(working) + beforeY * cell(working)));
		if (zoom == 1.0F) {
			panX = 0;
			panY = 0;
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!confirmingDiscard && !hexBox.isFocused() && event.hasControlDown()) {
			if (event.key() == GLFW.GLFW_KEY_Z) {
				if (event.hasShiftDown()) {
					session.redo();
				} else {
					session.undo();
				}
				return true;
			}
			if (event.key() == GLFW.GLFW_KEY_Y) {
				session.redo();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		requestClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ actions

	private void selectTool(Tool t) {
		tool = t;
	}

	private void useTool(int x, int y, boolean start) {
		switch (tool) {
			case PICKER -> {
				int picked = session.pick(face, x, y);
				PumpkinDesign working = session.working();
				if (picked == -1 && working != null && !working.isCut(face, x, y)) {
					picked = PumpkinAtlas.skinColor(model, working, face, x, y) & 0xFFFFFF;
				}
				if (picked != -1) {
					setColor(picked);
				}
			}
			case FILL -> {
				session.beginStroke();
				session.fill(face, x, y, color);
				session.endStroke();
				pushRecent();
			}
			default -> {
				if (start) {
					session.beginStroke();
					painting = true;
				}
				session.apply(tool, face, x, y, color);
				lastPixelX = x;
				lastPixelY = y;
			}
		}
	}

	/** Continues a stroke, filling the gap from the last pixel so fast drags leave no holes. */
	private void strokeTo(int x, int y) {
		int x0 = lastPixelX;
		int y0 = lastPixelY;
		int steps = Math.max(Math.abs(x - x0), Math.abs(y - y0));
		for (int i = 1; i <= steps; i++) {
			int px = x0 + Math.round((x - x0) * (float) i / steps);
			int py = y0 + Math.round((y - y0) * (float) i / steps);
			session.apply(tool, face, px, py, color);
		}
		lastPixelX = x;
		lastPixelY = y;
	}

	private boolean handleColorPanelClick(double mx, double my) {
		int x = width - RIGHT_W + 6;
		int y = TOP_H + 4;
		if (mx >= x && mx < x + SV_SIZE && my >= y && my < y + SV_SIZE) {
			drag = Drag.SATURATION_VALUE;
			updateDrag(mx, my);
			return true;
		}
		int hueY = y + SV_SIZE + 4;
		if (mx >= x && mx < x + SV_SIZE && my >= hueY && my < hueY + HUE_H) {
			drag = Drag.HUE;
			updateDrag(mx, my);
			return true;
		}
		int swY = hueY + HUE_H + 6 + 22;
		DyeColor[] dyes = DyeColor.values();
		for (int i = 0; i < dyes.length; i++) {
			int sx = x + (i % 8) * (SWATCH + 2);
			int sy = swY + (i / 8) * (SWATCH + 2);
			if (mx >= sx && mx < sx + SWATCH && my >= sy && my < sy + SWATCH) {
				setColor(dyes[i].getTextureDiffuseColor() & 0xFFFFFF);
				return true;
			}
		}
		int recentY = swY + 2 * (SWATCH + 2) + 6;
		for (int i = 0; i < recent.size(); i++) {
			int sx = x + i * (SWATCH + 2);
			if (mx >= sx && mx < sx + SWATCH && my >= recentY && my < recentY + SWATCH) {
				setColor(recent.get(i));
				return true;
			}
		}
		return false;
	}

	private void updateDrag(double mx, double my) {
		int x = width - RIGHT_W + 6;
		int y = TOP_H + 4;
		if (drag == Drag.SATURATION_VALUE) {
			saturation = Mth.clamp((float) (mx - x) / SV_SIZE, 0.0F, 1.0F);
			value = 1.0F - Mth.clamp((float) (my - y) / SV_SIZE, 0.0F, 1.0F);
		} else if (drag == Drag.HUE) {
			hue = Mth.clamp((float) (mx - x) / SV_SIZE, 0.0F, 0.999F);
		}
		color = Mth.hsvToRgb(hue, saturation, value) & 0xFFFFFF;
		updateHexBox();
	}

	private void setColor(int rgb) {
		color = rgb & 0xFFFFFF;
		float[] hsv = java.awt.Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
		hue = hsv[0];
		saturation = hsv[1];
		value = hsv[2];
		updateHexBox();
	}

	private void updateHexBox() {
		if (hexBox == null) {
			return;
		}
		updatingHex = true;
		hexBox.setValue(String.format(Locale.ROOT, "#%06X", color));
		updatingHex = false;
	}

	private void onHexChanged(String text) {
		if (updatingHex) {
			return;
		}
		String hex = text.startsWith("#") ? text.substring(1) : text;
		if (hex.length() == 6) {
			try {
				int rgb = Integer.parseInt(hex, 16);
				updatingHex = true;
				setColor(rgb);
				updatingHex = false;
			} catch (NumberFormatException ignored) {
				// keep typing
			}
		}
	}

	private void pushRecent() {
		if (tool != Tool.PAINT && tool != Tool.FILL) {
			return;
		}
		recent.remove(Integer.valueOf(color));
		recent.add(0, color);
		while (recent.size() > MAX_RECENT) {
			recent.remove(recent.size() - 1);
		}
	}

	private void confirm() {
		PumpkinDesign working = session.working();
		if (working == null || !session.isDirty()) {
			backToBench();
			return;
		}
		ClientPlayNetworking.send(new ConfirmDesignPayload(menu.containerId, originalHash, DesignCodec.encode(working), saveSchematic));
		backToBench();
	}

	private void requestClose() {
		if (session.isDirty()) {
			confirmingDiscard = true;
			refreshWidgets();
		} else {
			backToBench();
		}
	}

	private void backToBench() {
		if (minecraft != null) {
			minecraft.setScreen(parent);
		}
	}
}
