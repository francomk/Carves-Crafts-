package com.studioderiva.carves_and_crafts.design;

import java.util.ArrayDeque;
import java.util.Deque;
import org.jspecify.annotations.Nullable;

/**
 * Editing state of the carving editor: working copy, tools, mirror and undo/redo.
 * Pure Java (no Minecraft classes); the screen only translates input into calls on this class.
 *
 * <p>Undo works per stroke: call {@link #beginStroke()} on mouse down, apply any number of pixels, then
 * {@link #endStroke()} on mouse up.
 */
public final class EditorSession {
	public enum Tool {
		CUT,
		PAINT,
		ERASE,
		FILL,
		PICKER
	}

	public static final int MAX_UNDO = 64;

	private final @Nullable PumpkinDesign original;
	private @Nullable PumpkinDesign working;
	private final Deque<PumpkinDesign> undo = new ArrayDeque<>();
	private final Deque<PumpkinDesign> redo = new ArrayDeque<>();
	private @Nullable PumpkinDesign strokeStart;
	private boolean mirror;

	/**
	 * @param original design being edited, or null for a virgin pumpkin (a canvas layout must be chosen first)
	 */
	public EditorSession(@Nullable PumpkinDesign original) {
		this.original = original;
		this.working = original != null ? original.copy() : null;
	}

	public boolean needsLayout() {
		return working == null;
	}

	/** Virgin pumpkins only (the screen offers the model's densities); allowed again until something is drawn. */
	public void chooseLayout(CanvasLayout layout) {
		if (original != null || isDirty()) {
			throw new IllegalStateException("Canvas size is fixed");
		}
		working = new PumpkinDesign(layout);
		undo.clear();
		redo.clear();
	}

	public @Nullable PumpkinDesign working() {
		return working;
	}

	public @Nullable PumpkinDesign original() {
		return original;
	}

	public boolean mirror() {
		return mirror;
	}

	public void setMirror(boolean mirror) {
		this.mirror = mirror;
	}

	public void beginStroke() {
		if (working != null && strokeStart == null) {
			strokeStart = working.copy();
		}
	}

	public void endStroke() {
		if (strokeStart == null || working == null) {
			return;
		}
		if (!strokeStart.equals(working)) {
			undo.push(strokeStart);
			if (undo.size() > MAX_UNDO) {
				undo.removeLast();
			}
			redo.clear();
		}
		strokeStart = null;
	}

	/** Applies CUT, PAINT or ERASE to a pixel (and its mirror twin). */
	public boolean apply(Tool tool, CanvasFace face, int x, int y, int rgb) {
		if (working == null || !inside(face, x, y)) {
			return false;
		}
		boolean changed = applyOne(tool, face, x, y, rgb);
		int mx = working.width(face) - 1 - x;
		if (mirror && mx != x) {
			changed |= applyOne(tool, face, mx, y, rgb);
		}
		return changed;
	}

	/** Paint-bucket: paints the 4-connected region of identical look (cut pixels never change). */
	public boolean fill(CanvasFace face, int x, int y, int rgb) {
		if (working == null || !inside(face, x, y)) {
			return false;
		}
		boolean changed = floodPaint(face, x, y, rgb);
		int mx = working.width(face) - 1 - x;
		if (mirror && mx != x) {
			changed |= floodPaint(face, mx, y, rgb);
		}
		return changed;
	}

	/** Color under a pixel: paint if any, otherwise -1 (skin or cut; the screen supplies skin colors). */
	public int pick(CanvasFace face, int x, int y) {
		return working == null || !inside(face, x, y) ? -1 : working.colorAt(face, x, y);
	}

	public boolean canUndo() {
		return !undo.isEmpty() && strokeStart == null;
	}

	public boolean canRedo() {
		return !redo.isEmpty() && strokeStart == null;
	}

	public boolean undo() {
		if (!canUndo()) {
			return false;
		}
		redo.push(working);
		working = undo.pop();
		return true;
	}

	public boolean redo() {
		if (!canRedo()) {
			return false;
		}
		undo.push(working);
		working = redo.pop();
		return true;
	}

	/** Pixels that differ from the design the session started from. */
	public int changedPixels() {
		if (working == null) {
			return 0;
		}
		PumpkinDesign base = original != null ? original : new PumpkinDesign(working.layout());
		return base.countDifferences(working);
	}

	public boolean isDirty() {
		return changedPixels() > 0;
	}

	private boolean applyOne(Tool tool, CanvasFace face, int x, int y, int rgb) {
		return switch (tool) {
			case CUT -> working.cut(face, x, y);
			case PAINT -> working.paint(face, x, y, rgb);
			case ERASE -> working.erase(face, x, y);
			case FILL, PICKER -> false;
		};
	}

	private boolean floodPaint(CanvasFace face, int startX, int startY, int rgb) {
		int w = working.width(face);
		int h = working.height(face);
		long target = look(face, startX, startY);
		if (target == -1 || target == rgb) {
			return false; // cut region, or already that color
		}
		boolean[] seen = new boolean[w * h];
		int[] stack = new int[w * h];
		int top = 0;
		stack[top++] = startY * w + startX;
		seen[startY * w + startX] = true;
		boolean changed = false;
		while (top > 0) {
			int p = stack[--top];
			int x = p % w;
			int y = p / w;
			changed |= working.paint(face, x, y, rgb);
			int[][] neighbours = {{x + 1, y}, {x - 1, y}, {x, y + 1}, {x, y - 1}};
			for (int[] nb : neighbours) {
				if (nb[0] < 0 || nb[1] < 0 || nb[0] >= w || nb[1] >= h) {
					continue;
				}
				int q = nb[1] * w + nb[0];
				if (!seen[q] && look(face, nb[0], nb[1]) == target) {
					seen[q] = true;
					stack[top++] = q;
				}
			}
		}
		return changed;
	}

	/** -2 skin, -1 cut, otherwise RGB. */
	private long look(CanvasFace face, int x, int y) {
		if (working.isCut(face, x, y)) {
			return -1;
		}
		int color = working.colorAt(face, x, y);
		return color == -1 ? -2 : color;
	}

	private boolean inside(CanvasFace face, int x, int y) {
		return working.hasFace(face) && x >= 0 && y >= 0 && x < working.width(face) && y < working.height(face);
	}
}
