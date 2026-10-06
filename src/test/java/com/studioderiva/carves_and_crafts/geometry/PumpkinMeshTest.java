package com.studioderiva.carves_and_crafts.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studioderiva.carves_and_crafts.design.CanvasFace;
import com.studioderiva.carves_and_crafts.design.CanvasLayout;
import com.studioderiva.carves_and_crafts.design.PumpkinDesign;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PumpkinMeshTest {
	private static final int SHELL_QUADS = 12; // 6 outer + 6 inner
	private static final PumpkinShape CUBE = PumpkinShape.centered(16, 16, 16, 2);
	private static final int SIDES = CanvasFace.NORTH.bit() | CanvasFace.SOUTH.bit() | CanvasFace.EAST.bit() | CanvasFace.WEST.bit();
	/** Models generated from zucche/ by convertPumpkinModels. */
	private static final List<String> MODELS = List.of("classic_pumpkin", "white_pumpkin", "blue_pumpkin", "butternut_squash",
		"cinderella_pumpkin", "kabocha_squash", "mini_yellow_pumpkin", "turban_squash", "warty_pumpkin");

	private static PumpkinDesign square(int n) {
		return new PumpkinDesign(CanvasLayout.uniform(CanvasFace.ALL_MASK, n, n));
	}

	@Test
	void uncarvedPumpkinIsJustTheShell() {
		PumpkinMesh mesh = PumpkinMesh.build(square(16), CUBE);
		assertEquals(SHELL_QUADS, mesh.quadCount());
		assertValid(mesh);
	}

	@Test
	void singleCutInTheMiddleOpensIntoTheCavity() {
		PumpkinDesign design = square(16);
		design.cut(CanvasFace.NORTH, 8, 8);
		PumpkinMesh mesh = PumpkinMesh.build(design, CUBE);
		assertEquals(SHELL_QUADS + 4, mesh.quadCount()); // 4 tunnel walls, no cap
		assertValid(mesh);
	}

	@Test
	void outsideQuadsComeBeforeTheGlowingInside() {
		PumpkinDesign design = square(16);
		design.cut(CanvasFace.NORTH, 8, 8);
		design.cut(CanvasFace.NORTH, 0, 8); // blind: tunnel + cap
		PumpkinMesh mesh = PumpkinMesh.build(design, CUBE);
		assertEquals(6, mesh.outsideQuads()); // only the skin
		assertEquals(SHELL_QUADS + 4 + 4 + 1, mesh.quadCount());
		float[] v = mesh.vertices();
		// skin quads face outward: normal points away from the block center
		for (int q = 0; q < mesh.outsideQuads(); q++) {
			int o = q * 4 * PumpkinMesh.STRIDE;
			float dot = (v[o] - 0.5F) * v[o + 5] + (v[o + 1] - 0.5F) * v[o + 6] + (v[o + 2] - 0.5F) * v[o + 7];
			assertTrue(dot > 0, "skin quad " + q + " faces inward");
		}
	}

	@Test
	void cutNearTheBorderIsBlind() {
		PumpkinDesign design = square(16);
		design.cut(CanvasFace.NORTH, 0, 8); // within the wall thickness of the side wall
		PumpkinMesh mesh = PumpkinMesh.build(design, CUBE);
		assertEquals(SHELL_QUADS + 4 + 1, mesh.quadCount());
		assertValid(mesh);
	}

	@Test
	void adjacentCutsShareNoInnerWalls() {
		PumpkinDesign design = square(16);
		design.cut(CanvasFace.SOUTH, 7, 7);
		design.cut(CanvasFace.SOUTH, 8, 7);
		PumpkinMesh mesh = PumpkinMesh.build(design, CUBE);
		assertEquals(SHELL_QUADS + 6, mesh.quadCount()); // 2x1 hole perimeter = 6 edges
		assertValid(mesh);
	}

	@Test
	void tunnelWallsStayInsideTheBlockAndFaceTheHole() {
		PumpkinDesign design = square(32);
		design.cut(CanvasFace.EAST, 10, 12);
		PumpkinMesh mesh = PumpkinMesh.build(design, CUBE);
		float[] v = mesh.vertices();
		for (int i = 0; i < v.length; i += PumpkinMesh.STRIDE) {
			for (int axis = 0; axis < 3; axis++) {
				assertTrue(v[i + axis] >= -1e-6F && v[i + axis] <= 1 + 1e-6F, "vertex outside block");
			}
		}
		// tunnel walls of an EAST hole lie in x ∈ [1 - t, 1]
		for (int q = SHELL_QUADS; q < mesh.quadCount(); q++) {
			for (int k = 0; k < 4; k++) {
				float x = v[(q * 4 + k) * PumpkinMesh.STRIDE];
				assertTrue(x >= 1 - CUBE.wall() - 1e-6F, "tunnel wall too deep");
			}
		}
	}

	@Test
	void randomRectangularDesignsProduceValidMeshes() {
		Random random = new Random(7);
		PumpkinShape shape = new PumpkinShape(2.5F, 0.6F, 2.5F, 11, 7, 11, 1);
		for (int density : new int[] {1, 2, 4}) {
			PumpkinDesign design = new PumpkinDesign(CanvasLayout.builder()
				.face(CanvasFace.NORTH, 11 * density, 7 * density)
				.face(CanvasFace.SOUTH, 11 * density, 7 * density)
				.face(CanvasFace.EAST, 11 * density, 7 * density)
				.face(CanvasFace.WEST, 11 * density, 7 * density)
				.build());
			for (int i = 0; i < 300; i++) {
				CanvasFace face = CanvasFace.byOrdinal(random.nextInt(4));
				design.cut(face, random.nextInt(design.width(face)), random.nextInt(design.height(face)));
			}
			assertValid(PumpkinMesh.build(design, shape));
		}
	}

	/** Unit normals, counter-clockwise winding seen from the normal side, UVs within the texture. */
	private static void assertValid(PumpkinMesh mesh) {
		float[] v = mesh.vertices();
		assertEquals(mesh.quadCount() * 4 * PumpkinMesh.STRIDE, v.length);
		for (int q = 0; q < mesh.quadCount(); q++) {
			int base = q * 4 * PumpkinMesh.STRIDE;
			float nx = v[base + 5];
			float ny = v[base + 6];
			float nz = v[base + 7];
			assertEquals(1.0F, nx * nx + ny * ny + nz * nz, 1e-5F, "normal not unit");
			float[] a = pos(v, base, 0);
			float[] b = pos(v, base, 1);
			float[] c = pos(v, base, 2);
			float[] e1 = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
			float[] e2 = {c[0] - a[0], c[1] - a[1], c[2] - a[2]};
			float dot = (e1[1] * e2[2] - e1[2] * e2[1]) * nx + (e1[2] * e2[0] - e1[0] * e2[2]) * ny + (e1[0] * e2[1] - e1[1] * e2[0]) * nz;
			assertTrue(dot > 0, "quad " + q + " winding faces away from its normal");
			for (int k = 0; k < 4; k++) {
				int o = base + k * PumpkinMesh.STRIDE;
				assertTrue(v[o + 3] >= -1e-6F && v[o + 3] <= 1 + 1e-6F && v[o + 4] >= -1e-6F && v[o + 4] <= 1 + 1e-6F, "uv outside texture");
			}
		}
	}

	private static float[] pos(float[] v, int base, int vertex) {
		int o = base + vertex * PumpkinMesh.STRIDE;
		return new float[] {v[o], v[o + 1], v[o + 2]};
	}

	@Test
	void bodyCanSitAboveTheFloorAndOffCenter() {
		PumpkinShape body = new PumpkinShape(3, 0.6F, 3, 10, 9, 10, 1);
		PumpkinDesign design = new PumpkinDesign(CanvasLayout.uniform(SIDES, 20, 18));
		design.cut(CanvasFace.NORTH, 10, 9);
		PumpkinMesh mesh = PumpkinMesh.build(design, body);
		assertEquals(SHELL_QUADS + 4, mesh.quadCount());
		assertValid(mesh);
		float minY = Float.MAX_VALUE;
		float minZ = Float.MAX_VALUE;
		float[] v = mesh.vertices();
		for (int i = 0; i < v.length; i += PumpkinMesh.STRIDE) {
			minY = Math.min(minY, v[i + 1]);
			minZ = Math.min(minZ, v[i + 2]);
		}
		assertEquals(0.6F / 16, minY, 1e-6F);
		assertEquals(3 / 16.0F, minZ, 1e-6F);
	}

	@Test
	void blindHolesFollowTheWallOnRectangularFaces() {
		// 8×32 px face, 2 px wall, 8×8 canvas: pixels are 1 px wide and 4 px tall
		PumpkinShape tall = PumpkinShape.centered(8, 32, 8, 2);
		PumpkinDesign design = square(8);
		design.cut(CanvasFace.NORTH, 4, 0); // top row: centre 2 px from the top, exactly on the wall edge -> open
		design.cut(CanvasFace.NORTH, 4, 4); // middle: opens into the cavity
		design.cut(CanvasFace.NORTH, 0, 4); // left column: centre 0.5 px from the side, over the side wall -> blind
		PumpkinMesh mesh = PumpkinMesh.build(design, tall);
		assertValid(mesh);
		// 3 isolated holes = 12 tunnel walls, 1 cap
		assertEquals(SHELL_QUADS + 12 + 1, mesh.quadCount());
	}

	@Test
	void invalidShapesAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> PumpkinShape.centered(16, 16, 16, 8));
		assertThrows(IllegalArgumentException.class, () -> PumpkinShape.centered(64, 16, 16, 2));
		assertThrows(IllegalArgumentException.class, () -> PumpkinShape.centered(2, 16, 16, 1));
	}

	@Test
	void canvasPointIsTheInverseOfPoint() {
		PumpkinShape body = new PumpkinShape(3, 0.6F, 3, 10, 9, 10, 1);
		for (CubeFace face : CubeFace.values()) {
			float[] p = body.point(face, 0.3F, 0.8F, 0);
			float[] uv = body.canvasPoint(face, p);
			assertEquals(0.3F, uv[0], 1e-5F, face.name());
			assertEquals(0.8F, uv[1], 1e-5F, face.name());
		}
	}

	// ------------------------------------------------------------------ artists' models

	private static PumpkinGeometry geometry(String id) {
		try (InputStream in = PumpkinMeshTest.class.getResourceAsStream("/carves_and_crafts/geometry/" + id + ".json")) {
			assertNotNull(in, "missing geometry " + id);
			return PumpkinGeometry.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static PumpkinDesign sides(PumpkinShape shape, int density) {
		CanvasLayout.Builder builder = CanvasLayout.builder();
		CubeFace[] faces = CubeFace.values();
		for (int i = 0; i < 4; i++) {
			builder.face(CanvasFace.byOrdinal(i), shape.uPixels(faces[i]) * density, shape.vPixels(faces[i]) * density);
		}
		return new PumpkinDesign(builder.build());
	}

	@Test
	void everyModelBuildsValidMeshesAtEveryDensity() {
		for (String id : MODELS) {
			PumpkinGeometry geometry = geometry(id);
			PumpkinShape shape = geometry.shape(1);
			for (int density : new int[] {1, 2, 4}) {
				PumpkinDesign design = sides(shape, density);
				for (CanvasFace face : List.of(CanvasFace.NORTH, CanvasFace.SOUTH, CanvasFace.EAST, CanvasFace.WEST)) {
					assertTrue(design.width(face) <= CanvasLayout.MAX_SIZE && design.height(face) <= CanvasLayout.MAX_SIZE, id);
					design.cut(face, design.width(face) / 2, design.height(face) / 2);
				}
				assertValid(PumpkinMesh.build(design, shape));
				PumpkinMesh decor = PumpkinMesh.decor(geometry, shape, design);
				assertValid(decor);
				assertTrue(decor.quadCount() > 0, id + " has decorations");
			}
		}
	}

	@Test
	void wartOverACutIsLeftOut() {
		PumpkinGeometry warty = geometry("warty_pumpkin");
		PumpkinShape shape = warty.shape(1);
		PumpkinDesign design = sides(shape, 1); // 9×11 canvases
		int before = PumpkinMesh.decor(warty, shape, design).quadCount();
		// bitorzolo5 sits on the north face over x 6..7, y 8..9 → canvas pixel (5, 2) at density 1
		PumpkinGeometry.Element wart = warty.decor().stream().filter(e -> e.name().equals("bitorzolo5")).findFirst().orElseThrow();
		assertFalse(PumpkinMesh.coveredCut(wart, shape, design));
		design.cut(CanvasFace.NORTH, 5, 2);
		assertTrue(PumpkinMesh.coveredCut(wart, shape, design));
		assertEquals(before - wart.faces().size(), PumpkinMesh.decor(warty, shape, design).quadCount());
		// paint doesn't hide it, and a cut elsewhere doesn't hide the other warts
		PumpkinDesign painted = sides(shape, 1);
		painted.paint(CanvasFace.NORTH, 5, 2, 0x123456);
		painted.cut(CanvasFace.NORTH, 0, 10);
		assertEquals(before, PumpkinMesh.decor(warty, shape, painted).quadCount());
	}

	@Test
	void capsAboveTheBodyNeverHide() {
		// caps stick out of the top, which is not carvable: cutting every side pixel keeps them
		PumpkinGeometry classic = geometry("classic_pumpkin");
		PumpkinShape shape = classic.shape(1);
		PumpkinDesign design = sides(shape, 1);
		int before = PumpkinMesh.decor(classic, shape, design).quadCount();
		for (CanvasFace face : List.of(CanvasFace.NORTH, CanvasFace.SOUTH, CanvasFace.EAST, CanvasFace.WEST)) {
			for (int y = 0; y < design.height(face); y++) {
				for (int x = 0; x < design.width(face); x++) {
					design.cut(face, x, y);
				}
			}
		}
		assertEquals(before, PumpkinMesh.decor(classic, shape, design).quadCount());
	}

	@Test
	void rotatedStemTurnsAroundItsOrigin() {
		PumpkinGeometry classic = geometry("classic_pumpkin");
		PumpkinGeometry.Element tip = classic.decor().stream().filter(e -> e.name().equals("gambo_punta")).findFirst().orElseThrow();
		PumpkinGeometry.Rotation rotation = tip.rotation();
		assertNotNull(rotation);
		PumpkinGeometry onlyTip = new PumpkinGeometry(classic.texture(), classic.body(), List.of(tip));
		PumpkinShape shape = classic.shape(1);
		float[] v = PumpkinMesh.decor(onlyTip, shape, sides(shape, 1)).vertices();
		// a rotation keeps every corner's distance from the origin, and moves the corners off their box
		float[] o = {rotation.origin()[0] / 16, rotation.origin()[1] / 16, rotation.origin()[2] / 16};
		boolean moved = false;
		for (int i = 0; i < v.length; i += PumpkinMesh.STRIDE) {
			float[] p = {v[i], v[i + 1], v[i + 2]};
			float[] corner = nearestCorner(tip, p);
			assertEquals(dist(corner, o), dist(p, o), 1e-5F);
			moved |= dist(corner, p) > 1e-3F;
		}
		assertTrue(moved);
	}

	@Test
	void rotationMatchesVanillaMath() {
		// vanilla BlockElementRotation.SingleAxisRotation: new Matrix4f().rotation(angle, axis unit vector) around the origin
		for (String id : MODELS) {
			PumpkinGeometry geometry = geometry(id);
			for (PumpkinGeometry.Element e : geometry.decor()) {
				PumpkinGeometry.Rotation r = e.rotation();
				if (r == null) {
					continue;
				}
				Vector3f axis = new Vector3f(r.axis() == 0 ? 1 : 0, r.axis() == 1 ? 1 : 0, r.axis() == 2 ? 1 : 0);
				Matrix4f matrix = new Matrix4f().rotation((float) Math.toRadians(r.angle()), axis);
				Vector3f origin = new Vector3f(r.origin()).div(16);
				PumpkinGeometry single = new PumpkinGeometry(geometry.texture(), geometry.body(), List.of(e));
				PumpkinShape shape = geometry.shape(1);
				float[] v = PumpkinMesh.decor(single, shape, sides(shape, 1)).vertices();
				for (int i = 0; i < v.length; i += PumpkinMesh.STRIDE) {
					// find the unrotated corner whose vanilla rotation lands on this vertex
					boolean found = false;
					for (int c = 0; c < 8 && !found; c++) {
						Vector3f corner = new Vector3f(
							((c & 1) == 0 ? e.min(0) : e.max(0)) / 16,
							((c & 2) == 0 ? e.min(1) : e.max(1)) / 16,
							((c & 4) == 0 ? e.min(2) : e.max(2)) / 16);
						matrix.transformPosition(corner.sub(origin)).add(origin);
						found = Math.abs(corner.x - v[i]) < 1e-5F && Math.abs(corner.y - v[i + 1]) < 1e-5F && Math.abs(corner.z - v[i + 2]) < 1e-5F;
					}
					assertTrue(found, id + " " + e.name() + " vertex differs from vanilla");
				}
			}
		}
	}

	private static float[] nearestCorner(PumpkinGeometry.Element e, float[] p) {
		float[] best = null;
		for (int i = 0; i < 8; i++) {
			float[] c = {
				((i & 1) == 0 ? e.min(0) : e.max(0)) / 16,
				((i & 2) == 0 ? e.min(1) : e.max(1)) / 16,
				((i & 4) == 0 ? e.min(2) : e.max(2)) / 16,
			};
			if (best == null || dist(c, p) < dist(best, p)) {
				best = c;
			}
		}
		return best;
	}

	private static float dist(float[] a, float[] b) {
		float dx = a[0] - b[0];
		float dy = a[1] - b[1];
		float dz = a[2] - b[2];
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
