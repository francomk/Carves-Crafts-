package com.studioderiva.carves_and_crafts.client.dev;

import com.studioderiva.carves_and_crafts.design.CanvasFace;
import com.studioderiva.carves_and_crafts.design.PumpkinDesign;
import com.studioderiva.carves_and_crafts.model.PumpkinModel;
import java.util.Random;

/**
 * Designs for the Modrinth gallery (see GalleryScenario). Shapes are drawn in face-relative coordinates
 * (0..1 across, 0..1 down) so the same face works on every model and density.
 */
final class GalleryDesigns {
	static final int DENSITY = 4;

	private GalleryDesigns() {
	}

	@FunctionalInterface
	interface Shape {
		boolean contains(double u, double v);
	}

	private static Shape triangleUp(double cx, double top, double halfWidth, double bottom) {
		return (u, v) -> v >= top && v <= bottom && Math.abs(u - cx) <= halfWidth * (v - top) / (bottom - top);
	}

	private static Shape triangleDown(double cx, double top, double halfWidth, double bottom) {
		return (u, v) -> v >= top && v <= bottom && Math.abs(u - cx) <= halfWidth * (bottom - v) / (bottom - top);
	}

	private static Shape ellipse(double cx, double cy, double rx, double ry) {
		return (u, v) -> sq((u - cx) / rx) + sq((v - cy) / ry) <= 1;
	}

	private static Shape rect(double u0, double v0, double u1, double v1) {
		return (u, v) -> u >= u0 && u <= u1 && v >= v0 && v <= v1;
	}

	private static double sq(double d) {
		return d * d;
	}

	private static void cut(PumpkinDesign d, CanvasFace face, Shape shape) {
		apply(d, face, shape, -1);
	}

	private static void paint(PumpkinDesign d, CanvasFace face, Shape shape, int rgb) {
		apply(d, face, shape, rgb);
	}

	private static void apply(PumpkinDesign d, CanvasFace face, Shape shape, int rgb) {
		int w = d.width(face);
		int h = d.height(face);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (shape.contains((x + 0.5) / w, (y + 0.5) / h)) {
					if (rgb < 0) {
						d.cut(face, x, y);
					} else {
						d.paint(face, x, y, rgb);
					}
				}
			}
		}
	}

	private static PumpkinDesign blank(PumpkinModel model) {
		return new PumpkinDesign(model.layout(DENSITY));
	}

	// ------------------------------------------------------------------ carved faces

	/** Triangle eyes and nose, a wide grin with two teeth. */
	static PumpkinDesign classicFace(PumpkinModel model) {
		PumpkinDesign d = blank(model);
		CanvasFace f = CanvasFace.NORTH;
		cut(d, f, triangleUp(0.30, 0.18, 0.14, 0.42));
		cut(d, f, triangleUp(0.70, 0.18, 0.14, 0.42));
		cut(d, f, triangleUp(0.50, 0.44, 0.07, 0.58));
		Shape grin = (u, v) -> {
			double t = (u - 0.5) / 0.36;
			if (Math.abs(t) > 1) {
				return false;
			}
			double upper = 0.66 + 0.12 * t * t;
			double lower = 0.74 + 0.12 * (1 - t * t);
			return v >= upper && v <= lower;
		};
		Shape teeth = (u, v) -> (Math.abs(u - 0.40) < 0.04 || Math.abs(u - 0.60) < 0.04) && v < 0.74;
		cut(d, f, (u, v) -> grin.contains(u, v) && !teeth.contains(u, v));
		return d;
	}

	/** Slanted angry eyes and a jagged mouth. */
	static PumpkinDesign scaryFace(PumpkinModel model) {
		PumpkinDesign d = blank(model);
		CanvasFace f = CanvasFace.NORTH;
		cut(d, f, (u, v) -> u > 0.16 && u < 0.42 && v < 0.44 && v > 0.22 + 0.45 * (0.42 - u) * 0.6);
		cut(d, f, (u, v) -> u > 0.58 && u < 0.84 && v < 0.44 && v > 0.22 + 0.45 * (u - 0.58) * 0.6);
		Shape mouth = (u, v) -> {
			if (u < 0.18 || u > 0.82) {
				return false;
			}
			double zig = Math.abs(((u - 0.18) / 0.08) % 2 - 1); // 0..1 sawtooth
			return v >= 0.60 + 0.06 * zig && v <= 0.84 - 0.06 * (1 - zig);
		};
		cut(d, f, mouth);
		return d;
	}

	/** Round eyes and a crescent smile. */
	static PumpkinDesign happyFace(PumpkinModel model) {
		PumpkinDesign d = blank(model);
		CanvasFace f = CanvasFace.NORTH;
		cut(d, f, ellipse(0.32, 0.32, 0.11, 0.12));
		cut(d, f, ellipse(0.68, 0.32, 0.11, 0.12));
		Shape outer = ellipse(0.5, 0.52, 0.34, 0.30);
		Shape inner = ellipse(0.5, 0.44, 0.34, 0.30);
		cut(d, f, (u, v) -> v > 0.56 && outer.contains(u, v) && !inner.contains(u, v));
		return d;
	}

	/** One round eye, one winking, a lopsided smile. */
	static PumpkinDesign winkFace(PumpkinModel model) {
		PumpkinDesign d = blank(model);
		CanvasFace f = CanvasFace.NORTH;
		cut(d, f, ellipse(0.32, 0.32, 0.10, 0.13));
		cut(d, f, (u, v) -> u > 0.56 && u < 0.82 && Math.abs(v - (0.34 - 0.5 * sq(u - 0.69))) < 0.035);
		Shape outer = ellipse(0.54, 0.58, 0.28, 0.22);
		Shape inner = ellipse(0.54, 0.50, 0.28, 0.22);
		cut(d, f, (u, v) -> v > 0.6 && outer.contains(u, v) && !inner.contains(u, v));
		return d;
	}

	/** Cat face: pointed ears inside the eyes, whiskers painted. */
	static PumpkinDesign catFace(PumpkinModel model) {
		PumpkinDesign d = blank(model);
		CanvasFace f = CanvasFace.NORTH;
		cut(d, f, ellipse(0.32, 0.38, 0.10, 0.10));
		cut(d, f, ellipse(0.68, 0.38, 0.10, 0.10));
		cut(d, f, triangleUp(0.24, 0.10, 0.08, 0.30));
		cut(d, f, triangleUp(0.76, 0.10, 0.08, 0.30));
		cut(d, f, triangleDown(0.50, 0.52, 0.06, 0.62));
		for (double side : new double[] {-1, 1}) {
			for (double tilt : new double[] {-0.08, 0, 0.08}) {
				paint(d, f, (u, v) -> side * (u - 0.5) > 0.12 && side * (u - 0.5) < 0.42
					&& Math.abs(v - (0.62 + tilt * side * (u - 0.5) / 0.3)) < 0.02, 0x3B2414);
			}
		}
		return d;
	}

	// ------------------------------------------------------------------ painted designs (all four sides)

	private static void allSides(PumpkinDesign d, java.util.function.Consumer<CanvasFace> draw) {
		for (CanvasFace face : CanvasFace.values()) {
			if (d.hasFace(face)) {
				draw.accept(face);
			}
		}
	}

	/** Navy sky, a crescent moon and stars. */
	static PumpkinDesign nightSky(PumpkinModel model) {
		PumpkinDesign d = blank(model);
		Random random = new Random(7);
		allSides(d, f -> {
			paint(d, f, (u, v) -> true, 0x1B2A55);
			paint(d, f, (u, v) -> v > 0.75, 0x14204A);
			Shape moon = ellipse(0.68, 0.30, 0.16, 0.18);
			Shape bite = ellipse(0.60, 0.25, 0.15, 0.17);
			paint(d, f, (u, v) -> moon.contains(u, v) && !bite.contains(u, v), 0xFFF1B0);
			int w = d.width(f);
			int h = d.height(f);
			for (int i = 0; i < w * h / 60; i++) {
				int x = random.nextInt(w);
				int y = random.nextInt(h);
				d.paint(f, x, y, random.nextBoolean() ? 0xFFFFFF : 0xFFE27A);
			}
		});
		return d;
	}

	/** Diagonal candy stripes. */
	static PumpkinDesign candyStripes(PumpkinModel model) {
		PumpkinDesign d = blank(model);
		allSides(d, f -> {
			int w = d.width(f);
			int h = d.height(f);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int band = Math.floorMod((x + y) / Math.max(2, w / 6), 3);
					d.paint(f, x, y, band == 0 ? 0xF4F0E8 : band == 1 ? 0xD8343F : 0x2E9E6E);
				}
			}
		});
		return d;
	}

	/** Sunset bands with a dark hill line. */
	static PumpkinDesign sunset(PumpkinModel model) {
		PumpkinDesign d = blank(model);
		int[] bands = {0x3D2C6E, 0x7A3B8F, 0xC2477A, 0xEE7A4B, 0xF7B455};
		allSides(d, f -> {
			for (int i = 0; i < bands.length; i++) {
				double v0 = i / (double) bands.length;
				double v1 = (i + 1) / (double) bands.length;
				int color = bands[i];
				paint(d, f, (u, v) -> v >= v0 && v < v1 + 0.001, color);
			}
			paint(d, f, ellipse(0.5, 0.78, 0.16, 0.16), 0xFFE08A);
			paint(d, f, (u, v) -> v > 0.80 + 0.06 * Math.sin(u * 9), 0x2B1D33);
		});
		return d;
	}

	/** Flowers on stems over a pale green base. */
	static PumpkinDesign flowers(PumpkinModel model) {
		PumpkinDesign d = blank(model);
		int[] petals = {0xF25C8A, 0xFFD34E, 0x8E6CF0, 0xFF8A3D};
		allSides(d, f -> {
			paint(d, f, (u, v) -> true, 0xDCEFC9);
			for (int i = 0; i < 3; i++) {
				double cx = 0.2 + 0.3 * i;
				double cy = 0.32 + 0.12 * (i % 2);
				int petal = petals[(i + f.ordinal()) % petals.length];
				paint(d, f, (u, v) -> Math.abs(u - cx) < 0.025 && v > cy && v < 0.95, 0x3E8E41);
				paint(d, f, ellipse(cx + 0.06, cy + 0.30, 0.06, 0.03), 0x3E8E41);
				paint(d, f, ellipse(cx, cy, 0.10, 0.10), petal);
				paint(d, f, ellipse(cx, cy, 0.04, 0.04), 0xFFF3C4);
			}
		});
		return d;
	}

	/** White ghost with painted eyes over the skin, cut mouth. */
	static PumpkinDesign ghost(PumpkinModel model) {
		PumpkinDesign d = blank(model);
		CanvasFace f = CanvasFace.NORTH;
		Shape head = ellipse(0.5, 0.42, 0.28, 0.30);
		Shape skirt = (u, v) -> v >= 0.42 && v <= 0.86 - 0.05 * Math.abs(Math.sin(u * 22)) && Math.abs(u - 0.5) <= 0.28;
		paint(d, f, (u, v) -> head.contains(u, v) || skirt.contains(u, v), 0xF7F7F2);
		paint(d, f, ellipse(0.40, 0.40, 0.05, 0.07), 0x1E1E24);
		paint(d, f, ellipse(0.60, 0.40, 0.05, 0.07), 0x1E1E24);
		cut(d, f, ellipse(0.5, 0.58, 0.06, 0.06));
		return d;
	}

	/** Black bats over an orange glow, cut eyes on every bat. */
	static PumpkinDesign bats(PumpkinModel model) {
		PumpkinDesign d = blank(model);
		allSides(d, f -> {
			paint(d, f, ellipse(0.5, 0.5, 0.55, 0.55), 0xF2A33A);
			for (double[] bat : new double[][] {{0.3, 0.3}, {0.68, 0.45}, {0.42, 0.72}}) {
				double cx = bat[0];
				double cy = bat[1];
				paint(d, f, ellipse(cx, cy, 0.05, 0.06), 0x1A1420);
				paint(d, f, (u, v) -> Math.abs(u - cx) < 0.17 && Math.abs(u - cx) > 0.03
					&& v > cy - 0.05 && v < cy + 0.02 - 0.04 * Math.abs(Math.sin((u - cx) * 30)), 0x1A1420);
			}
		});
		return d;
	}
}
