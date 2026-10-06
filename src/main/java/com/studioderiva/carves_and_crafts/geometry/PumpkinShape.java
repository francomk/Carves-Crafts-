package com.studioderiva.carves_and_crafts.geometry;

/**
 * Carvable body of a pumpkin model: an axis-aligned box in model pixels (1/16 block), hollowed by a wall.
 * Sizes are whole pixels so that canvases at every density have whole-pixel sizes.
 *
 * @param x0 west edge in model pixels
 * @param y0 bottom in model pixels
 * @param z0 north edge in model pixels
 * @param wallPx wall thickness; the cavity is the box shrunk by this on every side
 */
public record PumpkinShape(float x0, float y0, float z0, int widthPx, int heightPx, int depthPx, int wallPx) {
	/** Item and block models only accept coordinates in [-16, 32]. */
	public static final int MAX_SIZE_PX = 48;

	public PumpkinShape {
		if (widthPx < 3 || heightPx < 3 || depthPx < 3 || Math.max(Math.max(widthPx, depthPx), heightPx) > MAX_SIZE_PX) {
			throw new IllegalArgumentException("Pumpkin size out of range: " + widthPx + "x" + heightPx + "x" + depthPx);
		}
		if (wallPx < 1 || wallPx * 2 >= Math.min(Math.min(widthPx, heightPx), depthPx)) {
			throw new IllegalArgumentException("Wall thickness " + wallPx + " leaves no cavity");
		}
	}

	/** Box of the given size centered horizontally on the block and resting on its floor. */
	public static PumpkinShape centered(int widthPx, int heightPx, int depthPx, int wallPx) {
		return new PumpkinShape((16 - widthPx) / 2.0F, 0, (16 - depthPx) / 2.0F, widthPx, heightPx, depthPx, wallPx);
	}

	public float sizeX() {
		return widthPx / 16.0F;
	}

	public float sizeY() {
		return heightPx / 16.0F;
	}

	public float sizeZ() {
		return depthPx / 16.0F;
	}

	public float wall() {
		return wallPx / 16.0F;
	}

	private float min(int axis) {
		return (axis == 0 ? x0 : axis == 1 ? y0 : z0) / 16.0F;
	}

	private float size(int axis) {
		return axis == 0 ? sizeX() : axis == 1 ? sizeY() : sizeZ();
	}

	/** Block-local position of a canvas point (u, v in 0..1) on a face, {@code depth} blocks into the wall. */
	public float[] point(CubeFace face, float u, float v, float depth) {
		float[] p = new float[3];
		for (int i = 0; i < 3; i++) {
			float unit = face.origin[i] + u * face.uAxis[i] + v * face.vAxis[i];
			p[i] = min(i) + unit * size(i) - depth * face.normal[i];
		}
		return p;
	}

	/**
	 * Canvas coordinates (u, v in 0..1, possibly outside) of a block-local point projected on a face.
	 * Inverse of {@link #point} for points on the face plane.
	 */
	public float[] canvasPoint(CubeFace face, float[] blockPos) {
		float u = 0;
		float v = 0;
		for (int i = 0; i < 3; i++) {
			float unit = (blockPos[i] - min(i)) / size(i) - face.origin[i];
			u += unit * face.uAxis[i];
			v += unit * face.vAxis[i];
		}
		return new float[] {u, v};
	}

	/** Face length along its u (horizontal) direction, in blocks. */
	public float uExtent(CubeFace face) {
		return extent(face.uAxis);
	}

	/** Face length along its v (vertical) direction, in blocks. */
	public float vExtent(CubeFace face) {
		return extent(face.vAxis);
	}

	/** Face length along u, in model pixels. */
	public int uPixels(CubeFace face) {
		return Math.round(uExtent(face) * 16);
	}

	/** Face length along v, in model pixels. */
	public int vPixels(CubeFace face) {
		return Math.round(vExtent(face) * 16);
	}

	/** Position of the face plane along its normal axis, in blocks. */
	public float plane(CubeFace face) {
		int axis = face.axis();
		return face.normal[axis] > 0 ? min(axis) + size(axis) : min(axis);
	}

	private float extent(float[] axis) {
		float total = 0;
		for (int i = 0; i < 3; i++) {
			total += Math.abs(axis[i]) * size(i);
		}
		return total;
	}
}
