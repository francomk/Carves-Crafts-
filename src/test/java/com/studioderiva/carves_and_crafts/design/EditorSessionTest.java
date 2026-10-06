package com.studioderiva.carves_and_crafts.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studioderiva.carves_and_crafts.design.EditorSession.Tool;
import org.junit.jupiter.api.Test;

class EditorSessionTest {
	private static final int ALL = CanvasFace.ALL_MASK;
	private static final CanvasFace F = CanvasFace.NORTH;

	@Test
	void virginNeedsLayoutUntilChosen() {
		EditorSession session = new EditorSession(null);
		assertTrue(session.needsLayout());
		session.chooseLayout(CanvasLayout.uniform(ALL, 40, 36));
		assertEquals(40, session.working().width(F));
		session.chooseLayout(CanvasLayout.uniform(ALL, 20, 18)); // still clean, may change
		session.beginStroke();
		session.apply(Tool.CUT, F, 0, 0, 0);
		session.endStroke();
		assertThrows(IllegalStateException.class, () -> session.chooseLayout(CanvasLayout.uniform(ALL, 10, 9)));
	}

	@Test
	void mirrorAndFillFollowTheFaceWidth() {
		EditorSession session = new EditorSession(new PumpkinDesign(CanvasLayout.uniform(ALL, 20, 6)));
		session.setMirror(true);
		session.beginStroke();
		session.apply(Tool.CUT, F, 2, 5, 0);
		session.endStroke();
		assertTrue(session.working().isCut(F, 17, 5));
		session.setMirror(false);
		session.beginStroke();
		assertTrue(session.fill(F, 0, 0, 0x00FF00));
		session.endStroke();
		// the two holes, plus every other pixel painted
		assertEquals(2 + (20 * 6 - 2), session.changedPixels());
		assertEquals(0x00FF00, session.working().colorAt(F, 19, 0));
	}

	@Test
	void undoAndRedoWorkPerStroke() {
		EditorSession session = new EditorSession(new PumpkinDesign(CanvasLayout.uniform(ALL, 16, 16)));
		session.beginStroke();
		session.apply(Tool.CUT, F, 1, 1, 0);
		session.apply(Tool.CUT, F, 2, 1, 0);
		session.endStroke();
		session.beginStroke();
		session.apply(Tool.PAINT, F, 5, 5, 0xFF0000);
		session.endStroke();
		assertEquals(3, session.changedPixels());

		assertTrue(session.undo());
		assertEquals(2, session.changedPixels());
		assertTrue(session.undo());
		assertEquals(0, session.changedPixels());
		assertFalse(session.undo());

		assertTrue(session.redo());
		assertTrue(session.working().isCut(F, 2, 1));
		assertEquals(2, session.changedPixels());
	}

	@Test
	void emptyStrokeLeavesNoUndoStep() {
		EditorSession session = new EditorSession(new PumpkinDesign(CanvasLayout.uniform(ALL, 16, 16)));
		session.beginStroke();
		session.apply(Tool.ERASE, F, 1, 1, 0);
		session.endStroke();
		assertFalse(session.canUndo());
	}

	@Test
	void newStrokeClearsRedo() {
		EditorSession session = new EditorSession(new PumpkinDesign(CanvasLayout.uniform(ALL, 16, 16)));
		session.beginStroke();
		session.apply(Tool.CUT, F, 1, 1, 0);
		session.endStroke();
		session.undo();
		session.beginStroke();
		session.apply(Tool.CUT, F, 3, 3, 0);
		session.endStroke();
		assertFalse(session.canRedo());
	}

	@Test
	void mirrorAppliesToTheTwinPixel() {
		EditorSession session = new EditorSession(new PumpkinDesign(CanvasLayout.uniform(ALL, 16, 16)));
		session.setMirror(true);
		session.apply(Tool.CUT, F, 2, 7, 0);
		assertTrue(session.working().isCut(F, 13, 7));
		assertEquals(2, session.changedPixels());
	}

	@Test
	void fillStopsAtCutsAndOtherColors() {
		EditorSession session = new EditorSession(new PumpkinDesign(CanvasLayout.uniform(ALL, 8, 8)));
		for (int y = 0; y < 8; y++) {
			session.apply(Tool.CUT, F, 3, y, 0); // vertical wall of cuts
		}
		assertTrue(session.fill(F, 0, 0, 0x00FF00));
		assertEquals(0x00FF00, session.working().colorAt(F, 2, 7));
		assertEquals(-1, session.working().colorAt(F, 4, 0));
		assertFalse(session.fill(F, 3, 3, 0x0000FF)); // cut pixels can't be filled
		assertEquals(8 + 3 * 8, session.changedPixels());
	}

	@Test
	void undoneCutsComeBackAsSkin() {
		PumpkinDesign original = new PumpkinDesign(CanvasLayout.uniform(ALL, 16, 16));
		original.cut(F, 0, 0);
		EditorSession session = new EditorSession(original);
		session.beginStroke();
		session.apply(Tool.CUT, F, 1, 0, 0);
		session.endStroke();
		session.undo();
		assertTrue(session.working().isCut(F, 0, 0)); // original cut stays
		assertFalse(session.working().isCut(F, 1, 0));
	}
}
