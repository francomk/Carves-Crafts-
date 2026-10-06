package com.studioderiva.carves_and_crafts.model;

import com.studioderiva.carves_and_crafts.design.CanvasFace;
import com.studioderiva.carves_and_crafts.design.CanvasLayout;
import com.studioderiva.carves_and_crafts.design.PumpkinDesign;
import com.studioderiva.carves_and_crafts.geometry.CubeFace;
import com.studioderiva.carves_and_crafts.geometry.PumpkinGeometry;
import com.studioderiva.carves_and_crafts.geometry.PumpkinShape;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A customizable pumpkin model: geometry (carvable body + decorations), carvable faces and canvas densities.
 * Each model is its own block (registered in {@link PumpkinModels}).
 *
 * <p>A face of the body that is W×H model pixels gets a (W·d)×(H·d) canvas at density d.
 *
 * @param id          block/item id path, also used to match schematics to pumpkins
 * @param canvasFaces carvable faces of the body
 * @param densities   canvas pixels per model pixel offered when a blank pumpkin is first carved
 */
public record PumpkinModel(
	String id,
	PumpkinGeometry geometry,
	PumpkinShape shape,
	int canvasFaces,
	List<Integer> densities,
	Identifier fleshTexture
) {
	public PumpkinModel {
		densities = List.copyOf(densities);
		if (densities.isEmpty()) {
			throw new IllegalArgumentException("No densities for " + id);
		}
		if (canvasFaces == 0 || (canvasFaces & ~CanvasFace.ALL_MASK) != 0) {
			throw new IllegalArgumentException("Bad canvas faces for " + id);
		}
		for (int density : densities) {
			layout(shape, canvasFaces, density); // throws if a canvas would be too big
		}
	}

	/** Texture of the skin and decorations, e.g. carves_and_crafts:block/classic_pumpkin. */
	public Identifier texture() {
		return Identifier.parse(geometry.texture());
	}

	public boolean hasFace(CanvasFace face) {
		return (canvasFaces & face.bit()) != 0;
	}

	/** Canvas sizes of a design at the given density. */
	public CanvasLayout layout(int density) {
		return layout(shape, canvasFaces, density);
	}

	private static CanvasLayout layout(PumpkinShape shape, int canvasFaces, int density) {
		CanvasLayout.Builder builder = CanvasLayout.builder();
		CubeFace[] faces = CubeFace.values();
		for (CanvasFace canvas : CanvasFace.values()) {
			if ((canvasFaces & canvas.bit()) != 0) {
				CubeFace face = faces[canvas.ordinal()];
				builder.face(canvas, shape.uPixels(face) * density, shape.vPixels(face) * density);
			}
		}
		return builder.build();
	}

	/** Density the design was made at, or null if it doesn't fit this model. */
	public @Nullable Integer density(PumpkinDesign design) {
		for (int density : densities) {
			if (layout(density).equals(design.layout())) {
				return density;
			}
		}
		return null;
	}

	/** True if a design fits this model: same faces, canvas sizes of an allowed density. */
	public boolean fits(PumpkinDesign design) {
		return density(design) != null;
	}
}
