package com.studioderiva.carves_and_crafts.geometry;

import com.studioderiva.carves_and_crafts.design.CanvasFace;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Faces of the unrotated unit cube, front = NORTH.
 *
 * <p>Each face maps canvas coordinates (u right, v down, as seen from outside, both 0..1) onto the unit cube:
 * {@code origin + u·uAxis + v·vAxis}; {@link PumpkinShape#point} scales that to the model box and adds depth.
 */
public enum CubeFace {
	NORTH(CanvasFace.NORTH, v(1, 1, 0), v(-1, 0, 0), v(0, -1, 0), v(0, 0, -1)),
	SOUTH(CanvasFace.SOUTH, v(0, 1, 1), v(1, 0, 0), v(0, -1, 0), v(0, 0, 1)),
	EAST(CanvasFace.EAST, v(1, 1, 1), v(0, 0, -1), v(0, -1, 0), v(1, 0, 0)),
	WEST(CanvasFace.WEST, v(0, 1, 0), v(0, 0, 1), v(0, -1, 0), v(-1, 0, 0)),
	UP(CanvasFace.UP, v(0, 1, 0), v(1, 0, 0), v(0, 0, 1), v(0, 1, 0)),
	DOWN(null, v(0, 0, 1), v(1, 0, 0), v(0, 0, -1), v(0, -1, 0));

	private final @Nullable CanvasFace canvas;
	final float[] origin;
	final float[] uAxis;
	final float[] vAxis;
	final float[] normal;

	CubeFace(@Nullable CanvasFace canvas, float[] origin, float[] uAxis, float[] vAxis, float[] normal) {
		this.canvas = canvas;
		this.origin = origin;
		this.uAxis = uAxis;
		this.vAxis = vAxis;
		this.normal = normal;
	}

	/** Canvas carried by this face, or null for the bottom. */
	public @Nullable CanvasFace canvas() {
		return canvas;
	}

	public float[] normal() {
		return normal.clone();
	}

	/** Axis of the normal: 0 = x, 1 = y, 2 = z. */
	public int axis() {
		return normal[0] != 0 ? 0 : normal[1] != 0 ? 1 : 2;
	}

	/** Lower-case name as used in model JSON ("north", "up", ...). */
	public String jsonName() {
		return name().toLowerCase(Locale.ROOT);
	}

	private static float[] v(float x, float y, float z) {
		return new float[] {x, y, z};
	}
}
