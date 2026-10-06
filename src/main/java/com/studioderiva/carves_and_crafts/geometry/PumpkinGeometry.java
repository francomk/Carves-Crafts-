package com.studioderiva.carves_and_crafts.geometry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Geometry of a pumpkin model, generated from the artists' Blockbench files by {@code ./gradlew convertPumpkinModels}.
 * The body is the carvable box; decorations (base, caps, stem, leaves, warts...) are drawn as they are.
 * Coordinates are model pixels (0..16 = one block), UVs are vanilla model UVs (0..16 over the texture).
 *
 * @param texture texture id, e.g. "carves_and_crafts:block/classic_pumpkin"
 */
public record PumpkinGeometry(String texture, Element body, List<Element> decor) {
	public PumpkinGeometry {
		decor = List.copyOf(decor);
		if (body.rotation() != null) {
			throw new IllegalArgumentException("The body can't be rotated");
		}
	}

	/** Rotation of an element around one axis (0 = x, 1 = y, 2 = z), right-handed like vanilla models. */
	public record Rotation(int axis, float angle, float[] origin) {
	}

	/**
	 * @param faces UV rectangle (u0, v0, u1, v1) of every textured face; missing faces are not drawn
	 */
	public record Element(String name, float[] from, float[] to, Map<CubeFace, float[]> faces, @Nullable Rotation rotation) {
		public Element {
			faces = Collections.unmodifiableMap(new EnumMap<>(faces));
		}

		public float min(int axis) {
			return from[axis];
		}

		public float max(int axis) {
			return to[axis];
		}
	}

	/** Body as a hollow box with the given wall thickness. */
	public PumpkinShape shape(int wallPx) {
		float[] f = body.from;
		float[] t = body.to;
		return new PumpkinShape(f[0], f[1], f[2], Math.round(t[0] - f[0]), Math.round(t[1] - f[1]), Math.round(t[2] - f[2]), wallPx);
	}

	/** Every element that is not rotated, body included; used for the hitbox. */
	public List<Element> axisAlignedElements() {
		List<Element> result = new ArrayList<>();
		result.add(body);
		for (Element e : decor) {
			if (e.rotation() == null) {
				result.add(e);
			}
		}
		return result;
	}

	public static PumpkinGeometry parse(Reader reader) {
		JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
		List<Element> decor = new ArrayList<>();
		for (JsonElement e : root.getAsJsonArray("decor")) {
			decor.add(element(e.getAsJsonObject()));
		}
		return new PumpkinGeometry(root.get("texture").getAsString(), element(root.getAsJsonObject("body")), decor);
	}

	private static Element element(JsonObject json) {
		Map<CubeFace, float[]> faces = new EnumMap<>(CubeFace.class);
		JsonObject facesJson = json.getAsJsonObject("faces");
		for (CubeFace face : CubeFace.values()) {
			if (facesJson.has(face.jsonName())) {
				faces.put(face, floats(facesJson.getAsJsonArray(face.jsonName()), 4));
			}
		}
		Rotation rotation = null;
		if (json.has("rotation")) {
			JsonObject r = json.getAsJsonObject("rotation");
			int axis = switch (r.get("axis").getAsString()) {
				case "x" -> 0;
				case "y" -> 1;
				case "z" -> 2;
				default -> throw new JsonParseException("Bad rotation axis " + r.get("axis"));
			};
			rotation = new Rotation(axis, r.get("angle").getAsFloat(), floats(r.getAsJsonArray("origin"), 3));
		}
		return new Element(json.get("name").getAsString(), floats(json.getAsJsonArray("from"), 3), floats(json.getAsJsonArray("to"), 3),
			faces, rotation);
	}

	private static float[] floats(JsonArray array, int size) {
		if (array.size() != size) {
			throw new JsonParseException("Expected " + size + " numbers, found " + array.size());
		}
		float[] result = new float[size];
		for (int i = 0; i < size; i++) {
			result[i] = array.get(i).getAsFloat();
		}
		return result;
	}
}
