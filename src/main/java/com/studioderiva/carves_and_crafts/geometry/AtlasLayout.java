package com.studioderiva.carves_and_crafts.geometry;

import com.studioderiva.carves_and_crafts.design.CanvasFace;
import com.studioderiva.carves_and_crafts.design.PumpkinDesign;

/**
 * Horizontal strip of slots that makes up the per-design texture of a pumpkin body.
 * <pre>
 * 0..5   outer skin + paint, alpha 0 where cut   (CubeFace order: N, S, E, W, UP, DOWN)
 * 6..10  inner flesh, alpha 0 where cut          (canvas faces N, S, E, W, UP)
 * 11     fresh flesh (tunnel walls, blind-hole caps), one block of flesh texture
 * 12     shadowed flesh (inner faces without a canvas, e.g. the floor)
 * </pre>
 * Face slots are sized in texels: face size in model pixels × {@code texelsPerPixel}, so both the skin texture
 * (2 texels per model pixel) and the canvas (density pixels per model pixel) land on whole texels.
 */
public final class AtlasLayout {
	public static final int OUTER_FIRST = 0;
	public static final int INNER_FIRST = 6;
	public static final int FLESH = 11;
	public static final int INNER_PLAIN = 12;
	public static final int SLOTS = 13;
	/** Texels per model pixel of the artists' skin textures. */
	public static final int SKIN_TEXELS_PER_PIXEL = 2;
	/** The flesh slot holds one block of the 16 px flesh texture. */
	public static final int FLESH_SIZE = 16;

	private final int[] slotX = new int[SLOTS];
	private final int[] slotW = new int[SLOTS];
	private final int[] slotH = new int[SLOTS];
	private final int width;
	private final int height;

	private AtlasLayout(PumpkinShape shape, int texelsPerPixel) {
		CubeFace[] faces = CubeFace.values();
		for (CubeFace face : faces) {
			setSize(OUTER_FIRST + face.ordinal(), shape.uPixels(face) * texelsPerPixel, shape.vPixels(face) * texelsPerPixel);
		}
		for (CanvasFace canvas : CanvasFace.values()) {
			CubeFace face = faces[canvas.ordinal()];
			setSize(INNER_FIRST + canvas.ordinal(), shape.uPixels(face) * texelsPerPixel, shape.vPixels(face) * texelsPerPixel);
		}
		setSize(FLESH, FLESH_SIZE, FLESH_SIZE);
		setSize(INNER_PLAIN, FLESH_SIZE, FLESH_SIZE);
		int x = 0;
		int h = 0;
		for (int slot = 0; slot < SLOTS; slot++) {
			slotX[slot] = x;
			x += slotW[slot];
			h = Math.max(h, slotH[slot]);
		}
		width = x;
		height = h;
	}

	/** Layout for a design on a body: fine enough for both the skin texture and the design's canvases. */
	public static AtlasLayout of(PumpkinDesign design, PumpkinShape shape) {
		int texels = SKIN_TEXELS_PER_PIXEL;
		CubeFace[] faces = CubeFace.values();
		for (CanvasFace canvas : CanvasFace.values()) {
			if (design.hasFace(canvas)) {
				CubeFace face = faces[canvas.ordinal()];
				texels = Math.max(texels, ceilDiv(design.width(canvas), shape.uPixels(face)));
				texels = Math.max(texels, ceilDiv(design.height(canvas), shape.vPixels(face)));
			}
		}
		return new AtlasLayout(shape, texels);
	}

	private static int ceilDiv(int a, int b) {
		return (a + b - 1) / b;
	}

	private void setSize(int slot, int w, int h) {
		slotW[slot] = w;
		slotH[slot] = h;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public int slotX(int slot) {
		return slotX[slot];
	}

	public int slotWidth(int slot) {
		return slotW[slot];
	}

	public int slotHeight(int slot) {
		return slotH[slot];
	}

	/** Atlas U of a point at {@code localU} (0..1) across a slot. */
	public float u(int slot, float localU) {
		return (slotX[slot] + localU * slotW[slot]) / width;
	}

	/** Atlas V of a point at {@code localV} (0..1) down a slot. */
	public float v(int slot, float localV) {
		return localV * slotH[slot] / height;
	}
}
