package com.studioderiva.carves_and_crafts.geometry;

import com.studioderiva.carves_and_crafts.design.CanvasFace;
import com.studioderiva.carves_and_crafts.design.PumpkinDesign;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * Builds the geometry of a pumpkin: the hollow body whose CUT pixels pierce the wall, and the decorations around it.
 *
 * <p>Body layers: outer skin (one quad per face, holes come from texture alpha), inner flesh shell (one quad per face,
 * same alpha holes), and tunnel walls along the border of every cut region. A cut pixel whose tunnel would end
 * inside a neighbouring wall (near the face border) gets a flesh cap at the bottom instead of opening into the cavity.
 * Body UVs point into the per-design texture described by {@link AtlasLayout}.
 *
 * <p>Decorations use the model's own texture. A decoration sticking out of a carvable face (a wart) is left out
 * when any cut pixel lies under it.
 *
 * <p>Pure Java, no Minecraft classes, so it can be unit tested.
 * Vertex layout: x, y, z, u, v, nx, ny, nz — 4 vertices per quad, counter-clockwise seen from the visible side.
 * Outside quads (skin, decorations) come first, then the inside ones (flesh shell, tunnel walls, caps), which glow
 * when the pumpkin is lit.
 */
public final class PumpkinMesh {
	public static final int STRIDE = 8;
	public static final int VERTICES_PER_QUAD = 4;
	private static final float EPSILON = 1.0E-4F;

	private final float[] vertices;
	private final int quadCount;
	private final int outsideQuads;

	private PumpkinMesh(float[] vertices, int quadCount, int outsideQuads) {
		this.vertices = vertices;
		this.quadCount = quadCount;
		this.outsideQuads = outsideQuads;
	}

	public float[] vertices() {
		return vertices;
	}

	public int quadCount() {
		return quadCount;
	}

	/** Quads [0, outsideQuads) are the outside; the rest is the inside of the pumpkin. */
	public int outsideQuads() {
		return outsideQuads;
	}

	public static PumpkinMesh build(PumpkinDesign design, PumpkinShape shape) {
		return build(design, shape, AtlasLayout.of(design, shape));
	}

	public static PumpkinMesh build(PumpkinDesign design, PumpkinShape shape, AtlasLayout atlas) {
		Builder b = new Builder(shape, atlas);
		float t = shape.wall();

		// shell first: outer skin and inner flesh of every face (12 quads)
		for (CubeFace face : CubeFace.values()) {
			CanvasFace canvas = face.canvas();
			boolean carved = canvas != null && design.hasFace(canvas);
			b.faceQuad(face, 0, 0, 1, 1, 0, AtlasLayout.OUTER_FIRST + face.ordinal(), false, false);
			int innerSlot = carved ? AtlasLayout.INNER_FIRST + canvas.ordinal() : AtlasLayout.INNER_PLAIN;
			float tu = t / shape.uExtent(face);
			float tv = t / shape.vExtent(face);
			b.faceQuad(face, tu, tv, 1 - tu, 1 - tv, t, innerSlot, true, true);
		}

		// then the holes
		for (CubeFace face : CubeFace.values()) {
			CanvasFace canvas = face.canvas();
			if (canvas == null || !design.hasFace(canvas)) {
				continue;
			}
			int w = design.width(canvas);
			int h = design.height(canvas);
			float pu = 1.0F / w;
			float pv = 1.0F / h;
			// wall thickness in this face's canvas units
			float tu = t / shape.uExtent(face);
			float tv = t / shape.vExtent(face);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					if (!design.isCut(canvas, x, y)) {
						continue;
					}
					float u0 = x * pu;
					float v0 = y * pv;
					float u1 = u0 + pu;
					float v1 = v0 + pv;
					// tunnel walls toward every non-cut neighbour (the face border counts as non-cut)
					if (x == w - 1 || !design.isCut(canvas, x + 1, y)) {
						b.tunnelWall(face, u1, v0, u1, v1, t, -1, 0);
					}
					if (x == 0 || !design.isCut(canvas, x - 1, y)) {
						b.tunnelWall(face, u0, v1, u0, v0, t, 1, 0);
					}
					if (y == h - 1 || !design.isCut(canvas, x, y + 1)) {
						b.tunnelWall(face, u1, v1, u0, v1, t, 0, -1);
					}
					if (y == 0 || !design.isCut(canvas, x, y - 1)) {
						b.tunnelWall(face, u0, v0, u1, v0, t, 0, 1);
					}
					// blind hole: the pixel lies over a neighbouring wall, not over the cavity
					float cu = u0 + pu / 2;
					float cv = v0 + pv / 2;
					if (cu < tu || cu > 1 - tu || cv < tv || cv > 1 - tv) {
						b.fleshCap(face, u0, v0, u1, v1, t);
					}
				}
			}
		}
		return b.build();
	}

	/** Decorations of a model, UVs in its texture (0..1). Warts over cut pixels are left out. */
	public static PumpkinMesh decor(PumpkinGeometry geometry, PumpkinShape shape, PumpkinDesign design) {
		Builder b = new Builder(shape, null);
		for (PumpkinGeometry.Element element : geometry.decor()) {
			if (!coveredCut(element, shape, design)) {
				b.element(element);
			}
		}
		return b.build();
	}

	/** True if the element sticks out of a carvable face of the body and covers at least one cut pixel there. */
	static boolean coveredCut(PumpkinGeometry.Element element, PumpkinShape shape, PumpkinDesign design) {
		if (element.rotation() != null) {
			return false;
		}
		for (CubeFace face : CubeFace.values()) {
			CanvasFace canvas = face.canvas();
			if (canvas == null || !design.hasFace(canvas)) {
				continue;
			}
			int axis = face.axis();
			float plane = shape.plane(face) * 16;
			float outward = face.normal()[axis];
			// must reach the face plane and stick out of it
			float inner = outward > 0 ? element.min(axis) : element.max(axis);
			float outer = outward > 0 ? element.max(axis) : element.min(axis);
			if ((inner - plane) * outward > EPSILON || (outer - plane) * outward <= EPSILON) {
				continue;
			}
			float[] a = shape.canvasPoint(face, scaled(element.from(), 1 / 16.0F));
			float[] c = shape.canvasPoint(face, scaled(element.to(), 1 / 16.0F));
			float uMin = Math.min(a[0], c[0]);
			float uMax = Math.max(a[0], c[0]);
			float vMin = Math.min(a[1], c[1]);
			float vMax = Math.max(a[1], c[1]);
			int w = design.width(canvas);
			int h = design.height(canvas);
			int x0 = Math.max(0, (int) Math.floor(uMin * w + EPSILON));
			int x1 = Math.min(w - 1, (int) Math.ceil(uMax * w - EPSILON) - 1);
			int y0 = Math.max(0, (int) Math.floor(vMin * h + EPSILON));
			int y1 = Math.min(h - 1, (int) Math.ceil(vMax * h - EPSILON) - 1);
			for (int y = y0; y <= y1; y++) {
				for (int x = x0; x <= x1; x++) {
					if (design.isCut(canvas, x, y)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static float[] scaled(float[] v, float s) {
		return new float[] {v[0] * s, v[1] * s, v[2] * s};
	}

	private static float[] sub(float[] a, float[] b) {
		return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
	}

	private static void scale(float[] v, float s) {
		for (int i = 0; i < 3; i++) {
			v[i] *= s;
		}
	}

	private static final class Builder {
		private final PumpkinShape shape;
		private final @Nullable AtlasLayout atlas;
		private final Buffer outside = new Buffer();
		private final Buffer inside = new Buffer();

		Builder(PumpkinShape shape, @Nullable AtlasLayout atlas) {
			this.shape = shape;
			this.atlas = atlas;
		}

		/**
		 * Quad on a face plane at a fixed depth, covering canvas rect [u0,u1]×[v0,v1].
		 * Texture uses the same canvas coordinates inside the given atlas slot.
		 */
		void faceQuad(CubeFace face, float u0, float v0, float u1, float v1, float depth, int slot, boolean inward, boolean isInside) {
			float[] normal = face.normal();
			if (inward) {
				scale(normal, -1);
			}
			float[][] corners = {
				shape.point(face, u0, v0, depth),
				shape.point(face, u0, v1, depth),
				shape.point(face, u1, v1, depth),
				shape.point(face, u1, v0, depth),
			};
			float[][] uvs = {
				{atlas.u(slot, u0), atlas.v(slot, v0)},
				{atlas.u(slot, u0), atlas.v(slot, v1)},
				{atlas.u(slot, u1), atlas.v(slot, v1)},
				{atlas.u(slot, u1), atlas.v(slot, v0)},
			};
			(isInside ? inside : outside).quad(corners, uvs, normal);
		}

		/** Flesh at the bottom of a blind hole, facing out; flesh texture at its real size. */
		void fleshCap(CubeFace face, float u0, float v0, float u1, float v1, float depth) {
			float[][] corners = {
				shape.point(face, u0, v0, depth),
				shape.point(face, u0, v1, depth),
				shape.point(face, u1, v1, depth),
				shape.point(face, u1, v0, depth),
			};
			float su = shape.uExtent(face);
			float sv = shape.vExtent(face);
			int slot = AtlasLayout.FLESH;
			float[][] uvs = {
				{atlas.u(slot, u0 * su), atlas.v(slot, v0 * sv)},
				{atlas.u(slot, u0 * su), atlas.v(slot, v1 * sv)},
				{atlas.u(slot, u1 * su), atlas.v(slot, v1 * sv)},
				{atlas.u(slot, u1 * su), atlas.v(slot, v0 * sv)},
			};
			inside.quad(corners, uvs, face.normal());
		}

		/** Every textured face of a decoration box, rotated like vanilla does. */
		void element(PumpkinGeometry.Element element) {
			for (var entry : element.faces().entrySet()) {
				CubeFace face = entry.getKey();
				float[] uv = entry.getValue();
				float[][] corners = {
					elementPoint(element, face, 0, 0),
					elementPoint(element, face, 0, 1),
					elementPoint(element, face, 1, 1),
					elementPoint(element, face, 1, 0),
				};
				float[] normal = face.normal();
				PumpkinGeometry.Rotation rotation = element.rotation();
				if (rotation != null) {
					float[] origin = scaled(rotation.origin(), 1 / 16.0F);
					for (float[] corner : corners) {
						rotate(corner, origin, rotation);
					}
					rotate(normal, new float[3], rotation);
				}
				float[][] uvs = {
					{uv[0] / 16, uv[1] / 16},
					{uv[0] / 16, uv[3] / 16},
					{uv[2] / 16, uv[3] / 16},
					{uv[2] / 16, uv[1] / 16},
				};
				outside.quad(corners, uvs, normal);
			}
		}

		/** Corner of an element face at canvas-style (u, v) in 0..1, same face orientation as the body. */
		private static float[] elementPoint(PumpkinGeometry.Element element, CubeFace face, float u, float v) {
			float[] p = new float[3];
			for (int i = 0; i < 3; i++) {
				float unit = face.origin[i] + u * face.uAxis[i] + v * face.vAxis[i];
				p[i] = (element.min(i) + unit * (element.max(i) - element.min(i))) / 16.0F;
			}
			return p;
		}

		/** Right-handed rotation around one axis through origin (vanilla BlockElementRotation.SingleAxisRotation). */
		private static void rotate(float[] p, float[] origin, PumpkinGeometry.Rotation rotation) {
			double angle = Math.toRadians(rotation.angle());
			float cos = (float) Math.cos(angle);
			float sin = (float) Math.sin(angle);
			float x = p[0] - origin[0];
			float y = p[1] - origin[1];
			float z = p[2] - origin[2];
			switch (rotation.axis()) {
				case 0 -> {
					p[1] = y * cos - z * sin + origin[1];
					p[2] = y * sin + z * cos + origin[2];
				}
				case 1 -> {
					p[0] = x * cos + z * sin + origin[0];
					p[2] = -x * sin + z * cos + origin[2];
				}
				default -> {
					p[0] = x * cos - y * sin + origin[0];
					p[1] = x * sin + y * cos + origin[1];
				}
			}
		}

		/**
		 * Wall of a hole, from the outer surface down to the given depth, along the pixel edge (ua,va)-(ub,vb).
		 * (du, dv) points from the edge toward the inside of the hole, which the wall faces.
		 */
		void tunnelWall(CubeFace face, float ua, float va, float ub, float vb, float depth, int du, int dv) {
			float[] normal = new float[3];
			for (int i = 0; i < 3; i++) {
				normal[i] = du * face.uAxis[i] + dv * face.vAxis[i];
			}
			float[][] corners = {
				shape.point(face, ua, va, 0),
				shape.point(face, ua, va, depth),
				shape.point(face, ub, vb, depth),
				shape.point(face, ub, vb, 0),
			};
			// flesh texture at its real size: along-edge distance horizontally, depth vertically
			float along = du != 0 ? shape.vExtent(face) : shape.uExtent(face);
			float along0 = (du != 0 ? va : ua) * along;
			float along1 = (du != 0 ? vb : ub) * along;
			int slot = AtlasLayout.FLESH;
			float[][] uvs = {
				{atlas.u(slot, along0), atlas.v(slot, 0)},
				{atlas.u(slot, along0), atlas.v(slot, depth)},
				{atlas.u(slot, along1), atlas.v(slot, depth)},
				{atlas.u(slot, along1), atlas.v(slot, 0)},
			};
			inside.quad(corners, uvs, normal);
		}

		PumpkinMesh build() {
			float[] all = Arrays.copyOf(outside.data, outside.floats + inside.floats);
			System.arraycopy(inside.data, 0, all, outside.floats, inside.floats);
			return new PumpkinMesh(all, outside.quads + inside.quads, outside.quads);
		}
	}

	private static final class Buffer {
		private float[] data = new float[STRIDE * VERTICES_PER_QUAD * 64];
		private int floats;
		private int quads;

		/** Emits the quad, flipping the winding if needed so it is counter-clockwise seen from the normal side. */
		void quad(float[][] corners, float[][] uvs, float[] normal) {
			float[] e1 = sub(corners[1], corners[0]);
			float[] e2 = sub(corners[2], corners[0]);
			float[] cross = {
				e1[1] * e2[2] - e1[2] * e2[1],
				e1[2] * e2[0] - e1[0] * e2[2],
				e1[0] * e2[1] - e1[1] * e2[0],
			};
			boolean flip = cross[0] * normal[0] + cross[1] * normal[1] + cross[2] * normal[2] < 0;
			ensure(STRIDE * VERTICES_PER_QUAD);
			for (int k = 0; k < 4; k++) {
				int i = flip ? 3 - k : k;
				float[] c = corners[i];
				data[floats++] = c[0];
				data[floats++] = c[1];
				data[floats++] = c[2];
				data[floats++] = uvs[i][0];
				data[floats++] = uvs[i][1];
				data[floats++] = normal[0];
				data[floats++] = normal[1];
				data[floats++] = normal[2];
			}
			quads++;
		}

		private void ensure(int extra) {
			if (floats + extra > data.length) {
				data = Arrays.copyOf(data, data.length * 2);
			}
		}
	}
}
