package com.studioderiva.carves_and_crafts.design;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.studioderiva.carves_and_crafts.design.DesignEditRules.Status;
import org.junit.jupiter.api.Test;

class DesignEditRulesTest {
	private static final int ALL = CanvasFace.ALL_MASK;

	@Test
	void virginPumpkinAcceptsItsFaces() {
		PumpkinDesign edited = new PumpkinDesign(CanvasLayout.uniform(ALL, 32, 32));
		edited.cut(CanvasFace.NORTH, 1, 1);
		edited.paint(CanvasFace.UP, 2, 2, 0x112233);
		DesignEditRules.Result result = DesignEditRules.check(null, edited, ALL);
		assertEquals(Status.OK, result.status());
		assertEquals(2, result.changedPixels());
	}

	@Test
	void virginPumpkinRejectsOtherFaces() {
		PumpkinDesign edited = new PumpkinDesign(CanvasLayout.uniform(CanvasFace.NORTH.bit(), 16, 16));
		assertEquals(Status.WRONG_SHAPE, DesignEditRules.check(null, edited, ALL).status());
	}

	@Test
	void canvasSizeCannotChange() {
		PumpkinDesign original = new PumpkinDesign(CanvasLayout.uniform(ALL, 16, 16));
		assertEquals(Status.WRONG_SHAPE, DesignEditRules.check(original, new PumpkinDesign(CanvasLayout.uniform(ALL, 32, 32)), ALL).status());
	}

	@Test
	void cutsAreIrreversible() {
		PumpkinDesign original = new PumpkinDesign(CanvasLayout.uniform(ALL, 16, 16));
		original.cut(CanvasFace.EAST, 3, 3);
		PumpkinDesign edited = new PumpkinDesign(CanvasLayout.uniform(ALL, 16, 16)); // same shape, cut missing
		assertEquals(Status.UNCUT_PIXEL, DesignEditRules.check(original, edited, ALL).status());
	}

	@Test
	void countsOnlyChangedPixels() {
		PumpkinDesign original = new PumpkinDesign(CanvasLayout.uniform(ALL, 16, 16));
		original.cut(CanvasFace.EAST, 3, 3);
		original.paint(CanvasFace.EAST, 4, 4, 0xFF0000);
		PumpkinDesign edited = original.copy();
		edited.paint(CanvasFace.EAST, 4, 4, 0x00FF00); // repaint
		edited.erase(CanvasFace.EAST, 4, 4);           // then erase: 1 change in total
		edited.cut(CanvasFace.WEST, 0, 0);             // new cut
		DesignEditRules.Result result = DesignEditRules.check(original, edited, ALL);
		assertEquals(Status.OK, result.status());
		assertEquals(2, result.changedPixels());
	}

	@Test
	void costSplitsCutsAndPaints() {
		PumpkinDesign original = new PumpkinDesign(CanvasLayout.uniform(ALL, 16, 16));
		original.paint(CanvasFace.NORTH, 0, 0, 0xFF0000);
		original.paint(CanvasFace.NORTH, 1, 0, 0xFF0000);
		original.cut(CanvasFace.NORTH, 9, 9);
		PumpkinDesign edited = original.copy();
		edited.cut(CanvasFace.NORTH, 0, 0);          // paint cut away: knife only
		edited.erase(CanvasFace.NORTH, 1, 0);        // erase: free
		edited.paint(CanvasFace.NORTH, 2, 0, 0x00FF00); // new paint
		edited.paint(CanvasFace.UP, 3, 3, 0x0000FF);    // new paint
		edited.cut(CanvasFace.SOUTH, 4, 4);             // new cut
		assertEquals(new DesignEditRules.Cost(2, 2), DesignEditRules.cost(original, edited));
	}

	@Test
	void repaintingTheSameColorIsFree() {
		PumpkinDesign original = new PumpkinDesign(CanvasLayout.uniform(ALL, 8, 8));
		original.paint(CanvasFace.EAST, 1, 1, 0x123456);
		PumpkinDesign edited = original.copy();
		edited.erase(CanvasFace.EAST, 1, 1);
		edited.paint(CanvasFace.EAST, 1, 1, 0x123456);
		assertEquals(DesignEditRules.Cost.NONE, DesignEditRules.cost(original, edited));
	}

	@Test
	void virginCostCountsEverything() {
		PumpkinDesign edited = new PumpkinDesign(CanvasLayout.uniform(ALL, 8, 8));
		edited.cut(CanvasFace.NORTH, 0, 0);
		edited.paint(CanvasFace.NORTH, 1, 0, 0xABCDEF);
		assertEquals(new DesignEditRules.Cost(1, 1), DesignEditRules.cost(null, edited));
	}
}
