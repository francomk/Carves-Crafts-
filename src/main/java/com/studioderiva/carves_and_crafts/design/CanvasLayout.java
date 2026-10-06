package com.studioderiva.carves_and_crafts.design;

import java.util.Arrays;

/**
 * Which faces a design has and the size in pixels of each face canvas. Canvases are rectangular:
 * a model's face is W×H model pixels, and at density d its canvas is (W·d)×(H·d), so canvas pixels stay square.
 */
public final class CanvasLayout {
	/** Largest canvas side, in pixels. */
	public static final int MAX_SIZE = 64;

	private final int faceMask;
	private final int[] widths;
	private final int[] heights;

	private CanvasLayout(int[] widths, int[] heights) {
		int mask = 0;
		for (CanvasFace face : CanvasFace.values()) {
			int w = widths[face.ordinal()];
			int h = heights[face.ordinal()];
			if (w == 0 && h == 0) {
				continue;
			}
			if (w < 1 || h < 1 || w > MAX_SIZE || h > MAX_SIZE) {
				throw new IllegalArgumentException("Bad canvas size " + w + "x" + h + " on " + face);
			}
			mask |= face.bit();
		}
		if (mask == 0) {
			throw new IllegalArgumentException("A design needs at least one face");
		}
		this.faceMask = mask;
		this.widths = widths;
		this.heights = heights;
	}

	public static Builder builder() {
		return new Builder();
	}

	/** Same size on every face of the mask; handy for tests and debug tools. */
	public static CanvasLayout uniform(int faceMask, int width, int height) {
		Builder builder = builder();
		for (CanvasFace face : CanvasFace.values()) {
			if ((faceMask & face.bit()) != 0) {
				builder.face(face, width, height);
			}
		}
		return builder.build();
	}

	public int faceMask() {
		return faceMask;
	}

	public boolean hasFace(CanvasFace face) {
		return (faceMask & face.bit()) != 0;
	}

	/** Canvas width of a face, 0 if the face is not part of the layout. */
	public int width(CanvasFace face) {
		return widths[face.ordinal()];
	}

	public int height(CanvasFace face) {
		return heights[face.ordinal()];
	}

	public int area(CanvasFace face) {
		return widths[face.ordinal()] * heights[face.ordinal()];
	}

	public int totalArea() {
		int total = 0;
		for (CanvasFace face : CanvasFace.values()) {
			total += area(face);
		}
		return total;
	}

	@Override
	public boolean equals(Object o) {
		return this == o || o instanceof CanvasLayout other && Arrays.equals(widths, other.widths) && Arrays.equals(heights, other.heights);
	}

	@Override
	public int hashCode() {
		return 31 * Arrays.hashCode(widths) + Arrays.hashCode(heights);
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder("CanvasLayout[");
		for (CanvasFace face : CanvasFace.values()) {
			if (hasFace(face)) {
				s.append(face).append('=').append(width(face)).append('x').append(height(face)).append(' ');
			}
		}
		return s.toString().trim() + "]";
	}

	public static final class Builder {
		private final int[] widths = new int[CanvasFace.count()];
		private final int[] heights = new int[CanvasFace.count()];

		private Builder() {
		}

		public Builder face(CanvasFace face, int width, int height) {
			widths[face.ordinal()] = width;
			heights[face.ordinal()] = height;
			return this;
		}

		public CanvasLayout build() {
			return new CanvasLayout(widths.clone(), heights.clone());
		}
	}
}
