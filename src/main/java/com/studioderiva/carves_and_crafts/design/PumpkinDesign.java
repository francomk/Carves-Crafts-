package com.studioderiva.carves_and_crafts.design;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Mutable in-memory design of a carvable pumpkin.
 *
 * <p>Every enabled face holds a dense W×H grid (sizes from its {@link CanvasLayout}) of 16-bit values:
 * {@link #UNPAINTED} (original skin), {@link #CUT} (hole), or a 1-based index into the design palette (RGB colors).
 *
 * <p>The palette may contain unused colors while editing; {@link DesignCodec} compacts it on encode.
 * Rules such as "CUT is irreversible" are gameplay rules enforced by the carving bench, not by this class.
 */
public final class PumpkinDesign {
	public static final int UNPAINTED = 0;
	public static final int CUT = 0xFFFF;
	/** Palette indices are 1..MAX_PALETTE_SIZE; CUT stays reserved. */
	public static final int MAX_PALETTE_SIZE = 0xFFFE;

	private final CanvasLayout layout;
	private final char[][] pixels;
	private int[] palette;
	private int paletteSize;
	private final Map<Integer, Integer> paletteLookup = new HashMap<>();

	public PumpkinDesign(CanvasLayout layout) {
		this.layout = layout;
		this.pixels = new char[CanvasFace.count()][];
		for (CanvasFace face : CanvasFace.values()) {
			if (layout.hasFace(face)) {
				this.pixels[face.ordinal()] = new char[layout.area(face)];
			}
		}
		this.palette = new int[8];
	}

	public CanvasLayout layout() {
		return layout;
	}

	public int faceMask() {
		return layout.faceMask();
	}

	public boolean hasFace(CanvasFace face) {
		return layout.hasFace(face);
	}

	public int width(CanvasFace face) {
		return layout.width(face);
	}

	public int height(CanvasFace face) {
		return layout.height(face);
	}

	public int paletteSize() {
		return paletteSize;
	}

	/** RGB (0xRRGGBB) of a 1-based palette index. */
	public int paletteColor(int index) {
		if (index < 1 || index > paletteSize) {
			throw new IndexOutOfBoundsException("Palette index " + index + " of " + paletteSize);
		}
		return palette[index - 1];
	}

	/** Raw stored value: UNPAINTED, CUT or a palette index. */
	public int rawAt(CanvasFace face, int x, int y) {
		return grid(face)[offset(face, x, y)];
	}

	public boolean isCut(CanvasFace face, int x, int y) {
		return rawAt(face, x, y) == CUT;
	}

	/** RGB color painted on the pixel, or -1 if unpainted or cut. */
	public int colorAt(CanvasFace face, int x, int y) {
		int raw = rawAt(face, x, y);
		return raw == UNPAINTED || raw == CUT ? -1 : palette[raw - 1];
	}

	/** @return true if the pixel changed */
	public boolean cut(CanvasFace face, int x, int y) {
		return setRaw(face, x, y, CUT);
	}

	/**
	 * Paints a pixel. Cut pixels can't be painted.
	 *
	 * @return true if the pixel changed
	 */
	public boolean paint(CanvasFace face, int x, int y, int rgb) {
		if (isCut(face, x, y)) {
			return false;
		}
		int index = paletteIndexFor(rgb & 0xFFFFFF);
		return index != -1 && setRaw(face, x, y, index);
	}

	/** Removes paint (back to skin). Does not restore cut pixels. */
	public boolean erase(CanvasFace face, int x, int y) {
		if (isCut(face, x, y)) {
			return false;
		}
		return setRaw(face, x, y, UNPAINTED);
	}

	public PumpkinDesign copy() {
		PumpkinDesign copy = new PumpkinDesign(layout);
		for (int i = 0; i < pixels.length; i++) {
			if (pixels[i] != null) {
				copy.pixels[i] = pixels[i].clone();
			}
		}
		copy.palette = Arrays.copyOf(palette, palette.length);
		copy.paletteSize = paletteSize;
		copy.paletteLookup.putAll(paletteLookup);
		return copy;
	}

	/** Number of pixels whose visible content (skin / color / cut) differs. Designs must share their layout. */
	public int countDifferences(PumpkinDesign other) {
		if (!other.layout.equals(layout)) {
			throw new IllegalArgumentException("Designs are not comparable");
		}
		int diff = 0;
		for (int f = 0; f < pixels.length; f++) {
			char[] a = pixels[f];
			if (a == null) {
				continue;
			}
			char[] b = other.pixels[f];
			for (int i = 0; i < a.length; i++) {
				if (resolve(a[i]) != other.resolve(b[i])) {
					diff++;
				}
			}
		}
		return diff;
	}

	/** Two designs are equal when they look identical, regardless of palette order or unused colors. */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof PumpkinDesign other) || !other.layout.equals(layout)) {
			return false;
		}
		return countDifferences(other) == 0;
	}

	@Override
	public int hashCode() {
		int h = layout.hashCode();
		for (char[] grid : pixels) {
			if (grid == null) {
				continue;
			}
			for (char value : grid) {
				h = 31 * h + Long.hashCode(resolve(value));
			}
		}
		return h;
	}

	// package-private access for the codec

	char[] gridOrNull(int faceOrdinal) {
		return pixels[faceOrdinal];
	}

	/** Sets a raw value without palette checks; used by the decoder. */
	void setRawUnchecked(int faceOrdinal, int offset, int value) {
		pixels[faceOrdinal][offset] = (char) value;
	}

	/** Appends or finds a color; returns its 1-based index or -1 if the palette is full. */
	int paletteIndexFor(int rgb) {
		Integer existing = paletteLookup.get(rgb);
		if (existing != null) {
			return existing;
		}
		if (paletteSize >= MAX_PALETTE_SIZE) {
			compactPalette();
			if (paletteSize >= MAX_PALETTE_SIZE) {
				return -1;
			}
		}
		if (paletteSize == palette.length) {
			palette = Arrays.copyOf(palette, Math.min(palette.length * 2, MAX_PALETTE_SIZE));
		}
		palette[paletteSize++] = rgb;
		paletteLookup.put(rgb, paletteSize);
		return paletteSize;
	}

	/** Drops colors no pixel uses, renumbering indices. */
	void compactPalette() {
		int[] remap = new int[paletteSize + 1];
		boolean[] used = new boolean[paletteSize + 1];
		for (char[] grid : pixels) {
			if (grid == null) {
				continue;
			}
			for (char value : grid) {
				if (value != UNPAINTED && value != CUT) {
					used[value] = true;
				}
			}
		}
		int next = 0;
		int[] newPalette = new int[Math.max(8, paletteSize)];
		paletteLookup.clear();
		for (int i = 1; i <= paletteSize; i++) {
			if (used[i]) {
				newPalette[next] = palette[i - 1];
				remap[i] = ++next;
				paletteLookup.put(palette[i - 1], next);
			}
		}
		for (char[] grid : pixels) {
			if (grid == null) {
				continue;
			}
			for (int i = 0; i < grid.length; i++) {
				char value = grid[i];
				if (value != UNPAINTED && value != CUT) {
					grid[i] = (char) remap[value];
				}
			}
		}
		palette = newPalette;
		paletteSize = next;
	}

	/** Visible content of a raw value: -2 = unpainted, -1 = cut, otherwise RGB. */
	long resolve(int raw) {
		if (raw == UNPAINTED) {
			return -2;
		}
		if (raw == CUT) {
			return -1;
		}
		return palette[raw - 1];
	}

	private boolean setRaw(CanvasFace face, int x, int y, int value) {
		char[] grid = grid(face);
		int offset = offset(face, x, y);
		if (grid[offset] == value) {
			return false;
		}
		grid[offset] = (char) value;
		return true;
	}

	private char[] grid(CanvasFace face) {
		char[] grid = pixels[face.ordinal()];
		if (grid == null) {
			throw new IllegalArgumentException("Face " + face + " is not part of this design");
		}
		return grid;
	}

	private int offset(CanvasFace face, int x, int y) {
		int w = layout.width(face);
		int h = layout.height(face);
		if (x < 0 || y < 0 || x >= w || y >= h) {
			throw new IndexOutOfBoundsException("Pixel (" + x + "," + y + ") outside " + w + "x" + h);
		}
		return y * w + x;
	}
}
