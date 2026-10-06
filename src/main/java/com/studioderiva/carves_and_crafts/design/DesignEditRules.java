package com.studioderiva.carves_and_crafts.design;

import org.jspecify.annotations.Nullable;

/**
 * Server-side rules for accepting an edited design from the carving bench.
 * Pure logic so it can be unit tested.
 */
public final class DesignEditRules {
	private DesignEditRules() {
	}

	public enum Status {
		OK,
		/** Canvas layout differs from the pumpkin being edited (or faces not allowed for a virgin pumpkin). */
		WRONG_SHAPE,
		/** A pixel that was already cut is no longer cut: cuts are irreversible. */
		UNCUT_PIXEL
	}

	/**
	 * @param changedPixels pixels whose visible content differs from the original (all non-skin pixels for a virgin pumpkin)
	 */
	public record Result(Status status, int changedPixels) {
		public boolean ok() {
			return status == Status.OK;
		}
	}

	/**
	 * Tool usage of an edit: newly cut pixels wear the knife, newly painted pixels use brush charge.
	 * Erasing paint back to skin is free.
	 */
	public record Cost(int cuts, int paints) {
		public static final Cost NONE = new Cost(0, 0);
	}

	/** Cost of turning {@code original} (null = virgin) into {@code edited}; both must have the same shape. */
	public static Cost cost(@Nullable PumpkinDesign original, PumpkinDesign edited) {
		PumpkinDesign base = original != null ? original : new PumpkinDesign(edited.layout());
		int cuts = 0;
		int paints = 0;
		for (CanvasFace face : CanvasFace.values()) {
			if (!edited.hasFace(face)) {
				continue;
			}
			for (int y = 0; y < edited.height(face); y++) {
				for (int x = 0; x < edited.width(face); x++) {
					if (edited.isCut(face, x, y)) {
						if (!base.isCut(face, x, y)) {
							cuts++;
						}
						continue;
					}
					int color = edited.colorAt(face, x, y);
					if (color != -1 && color != base.colorAt(face, x, y)) {
						paints++;
					}
				}
			}
		}
		return new Cost(cuts, paints);
	}

	/**
	 * @param original     current design of the pumpkin, or null if virgin
	 * @param edited       design proposed by the client
	 * @param allowedFaces face mask of the pumpkin model; a virgin pumpkin's new design must use exactly these faces
	 *                     (the caller also checks that its canvas sizes fit the model)
	 */
	public static Result check(@Nullable PumpkinDesign original, PumpkinDesign edited, int allowedFaces) {
		if (original == null) {
			if (edited.faceMask() != allowedFaces) {
				return new Result(Status.WRONG_SHAPE, 0);
			}
			return new Result(Status.OK, edited.countDifferences(new PumpkinDesign(edited.layout())));
		}
		if (!edited.layout().equals(original.layout())) {
			return new Result(Status.WRONG_SHAPE, 0);
		}
		for (CanvasFace face : CanvasFace.values()) {
			if (!original.hasFace(face)) {
				continue;
			}
			for (int y = 0; y < original.height(face); y++) {
				for (int x = 0; x < original.width(face); x++) {
					if (original.isCut(face, x, y) && !edited.isCut(face, x, y)) {
						return new Result(Status.UNCUT_PIXEL, 0);
					}
				}
			}
		}
		return new Result(Status.OK, original.countDifferences(edited));
	}
}
