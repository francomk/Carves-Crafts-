package com.studioderiva.carves_and_crafts.design;

/**
 * A carvable face of a pumpkin model. The bottom is never carvable.
 * Pixel (0,0) is the top-left corner as seen from outside the pumpkin.
 */
public enum CanvasFace {
	NORTH,
	SOUTH,
	EAST,
	WEST,
	UP;

	public static final int ALL_MASK = (1 << values().length) - 1;

	private static final CanvasFace[] VALUES = values();

	public int bit() {
		return 1 << ordinal();
	}

	public static CanvasFace byOrdinal(int ordinal) {
		return VALUES[ordinal];
	}

	public static int count() {
		return VALUES.length;
	}
}
