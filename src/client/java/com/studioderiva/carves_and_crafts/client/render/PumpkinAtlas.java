package com.studioderiva.carves_and_crafts.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.design.CanvasFace;
import com.studioderiva.carves_and_crafts.design.PumpkinDesign;
import com.studioderiva.carves_and_crafts.geometry.AtlasLayout;
import com.studioderiva.carves_and_crafts.geometry.CubeFace;
import com.studioderiva.carves_and_crafts.model.PumpkinModel;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Paints the per-design texture strip described by {@link AtlasLayout}. Render thread only. */
public final class PumpkinAtlas {
	/** Inner faces are darker than freshly cut edges: a cheap depth cue. */
	private static final float INNER_SHADE = 0.6F;
	private static final CubeFace[] FACES = CubeFace.values();

	/** Source textures by texture id (e.g. carves_and_crafts:block/classic_pumpkin). */
	private static final Map<Identifier, Source> SOURCES = new HashMap<>();

	private PumpkinAtlas() {
	}

	/**
	 * @param glow lit pumpkin: the inside is painted in pale, nearly neutral flesh so the source's tint
	 *             (multiplied on top by the renderer) shows its own color instead of mixing with orange
	 */
	public static NativeImage build(PumpkinDesign design, PumpkinModel model, AtlasLayout atlas, boolean glow) {
		Source flesh = source(model.fleshTexture());
		float innerShade = glow ? 1.0F : INNER_SHADE;
		NativeImage image = new NativeImage(atlas.width(), atlas.height(), false);

		for (CubeFace face : FACES) {
			CanvasFace canvas = face.canvas();
			boolean carved = canvas != null && design.hasFace(canvas);
			int slot = AtlasLayout.OUTER_FIRST + face.ordinal();
			int sx = atlas.slotX(slot);
			int sw = atlas.slotWidth(slot);
			int sh = atlas.slotHeight(slot);
			for (int ty = 0; ty < sh; ty++) {
				for (int tx = 0; tx < sw; tx++) {
					float u = (tx + 0.5F) / sw;
					float v = (ty + 0.5F) / sh;
					int argb = skinAt(model, face, u, v);
					if (carved) {
						int cx = tx * design.width(canvas) / sw;
						int cy = ty * design.height(canvas) / sh;
						if (design.isCut(canvas, cx, cy)) {
							argb = 0;
						} else {
							int color = design.colorAt(canvas, cx, cy);
							if (color != -1) {
								argb = 0xFF000000 | color;
							}
						}
					}
					image.setPixel(sx + tx, ty, argb);
				}
			}
		}

		for (CanvasFace canvas : CanvasFace.values()) {
			int slot = AtlasLayout.INNER_FIRST + canvas.ordinal();
			int sx = atlas.slotX(slot);
			int sw = atlas.slotWidth(slot);
			int sh = atlas.slotHeight(slot);
			boolean carved = design.hasFace(canvas);
			for (int ty = 0; ty < sh; ty++) {
				for (int tx = 0; tx < sw; tx++) {
					boolean hole = carved && design.isCut(canvas, tx * design.width(canvas) / sw, ty * design.height(canvas) / sh);
					int argb = flesh.sample((tx + 0.5F) / sw, (ty + 0.5F) / sh);
					image.setPixel(sx + tx, ty, hole ? 0 : shade(glow ? pale(argb) : argb, innerShade));
				}
			}
		}

		int fleshX = atlas.slotX(AtlasLayout.FLESH);
		int plainX = atlas.slotX(AtlasLayout.INNER_PLAIN);
		int size = AtlasLayout.FLESH_SIZE;
		for (int ty = 0; ty < size; ty++) {
			for (int tx = 0; tx < size; tx++) {
				int argb = flesh.sample((tx + 0.5F) / size, (ty + 0.5F) / size);
				if (glow) {
					argb = pale(argb);
				}
				image.setPixel(fleshX + tx, ty, argb);
				image.setPixel(plainX + tx, ty, shade(argb, innerShade));
			}
		}
		return image;
	}

	/** Skin color under the center of canvas pixel (x, y) of a design, for the editor. */
	public static int skinColor(PumpkinModel model, PumpkinDesign design, CanvasFace face, int x, int y) {
		return skinAt(model, FACES[face.ordinal()], (x + 0.5F) / design.width(face), (y + 0.5F) / design.height(face));
	}

	/** Flesh color under the center of canvas pixel (x, y), for the editor. */
	public static int fleshColor(PumpkinModel model, PumpkinDesign design, CanvasFace face, int x, int y) {
		return source(model.fleshTexture()).sample((x + 0.5F) / design.width(face), (y + 0.5F) / design.height(face));
	}

	/** Skin of a body face at canvas coordinates (u, v in 0..1), from the face's UV rectangle in the model texture. */
	private static int skinAt(PumpkinModel model, CubeFace face, float u, float v) {
		float[] uv = model.geometry().body().faces().get(face);
		float tu = (uv[0] + u * (uv[2] - uv[0])) / 16.0F;
		float tv = (uv[1] + v * (uv[3] - uv[1])) / 16.0F;
		return source(model.texture()).sample(tu, tv);
	}

	/** Forgets cached source textures, e.g. after a resource reload. */
	static void invalidate() {
		SOURCES.clear();
	}

	private static Source source(Identifier texture) {
		return SOURCES.computeIfAbsent(texture, id -> Source.load(id.withPrefix("textures/").withSuffix(".png")));
	}

	/** Light gray from the pixel's brightness: keeps the flesh grain, drops its color. */
	private static int pale(int argb) {
		int luma = (((argb >> 16) & 0xFF) * 3 + ((argb >> 8) & 0xFF) * 6 + (argb & 0xFF)) / 10;
		int g = 190 + luma * 65 / 255;
		return (argb & 0xFF000000) | (g << 16) | (g << 8) | g;
	}

	private static int shade(int argb, float factor) {
		int r = (int) (((argb >> 16) & 0xFF) * factor);
		int g = (int) (((argb >> 8) & 0xFF) * factor);
		int b = (int) ((argb & 0xFF) * factor);
		return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
	}

	/** ARGB source texture sampled with nearest-neighbour at normalized coordinates. */
	private record Source(int width, int height, int[] argb) {
		static Source load(Identifier id) {
			try (InputStream in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(id).open();
				NativeImage image = NativeImage.read(in)) {
				int w = image.getWidth();
				int h = image.getHeight();
				int[] pixels = new int[w * h];
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						pixels[y * w + x] = image.getPixel(x, y);
					}
				}
				return new Source(w, h, pixels);
			} catch (IOException e) {
				CarvesAndCrafts.LOGGER.error("Missing pumpkin texture {}", id, e);
				return new Source(1, 1, new int[] {0xFFFF00FF});
			}
		}

		/** Pixel containing (u, v); coordinates outside 0..1 are clamped. */
		int sample(float u, float v) {
			int x = Math.min(width - 1, Math.max(0, (int) Math.floor(u * width)));
			int y = Math.min(height - 1, Math.max(0, (int) Math.floor(v * height)));
			return argb[y * width + x];
		}
	}
}
