package com.studioderiva.carves_and_crafts.schematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studioderiva.carves_and_crafts.design.AuthorList;
import com.studioderiva.carves_and_crafts.design.CanvasFace;
import com.studioderiva.carves_and_crafts.design.CanvasLayout;
import com.studioderiva.carves_and_crafts.design.EncodedDesign;
import com.studioderiva.carves_and_crafts.design.PumpkinDesign;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SchematicLibraryTest {
	private static final EncodedDesign DESIGN = EncodedDesign.of(new PumpkinDesign(CanvasLayout.uniform(CanvasFace.ALL_MASK, 16, 16)));

	private static SchematicLibrary withEntries(int count) {
		SchematicLibrary library = SchematicLibrary.EMPTY;
		for (int i = 0; i < count; i++) {
			library = library.add("s" + i, "custom_pumpkin", DESIGN, AuthorList.EMPTY, i, 0).orElseThrow().library();
		}
		return library;
	}

	@Test
	void addAssignsIncreasingIdsNewestFirst() {
		SchematicLibrary library = withEntries(3);
		assertEquals(4, library.nextId());
		assertEquals("s2", library.entries().get(0).name());
		assertEquals(3, library.entries().get(0).id());
	}

	@Test
	void blankNamesGetADefault() {
		SchematicLibrary.Added added = SchematicLibrary.EMPTY.add("  §c ", "custom_pumpkin", DESIGN, AuthorList.EMPTY, 0, 0).orElseThrow();
		assertEquals("Design #1", added.schematic().name());
	}

	@Test
	void namesAreSanitized() {
		assertEquals("Spooky face", Schematic.sanitizeName("  §lSpooky\u0007 face  "));
		assertEquals(Schematic.MAX_NAME_LENGTH, Schematic.sanitizeName("x".repeat(100)).length());
	}

	@Test
	void optionalLimitIsEnforced() {
		SchematicLibrary library = withEntries(2);
		assertTrue(library.add("a", "custom_pumpkin", DESIGN, AuthorList.EMPTY, 0, 2).isEmpty());
		assertTrue(library.add("a", "custom_pumpkin", DESIGN, AuthorList.EMPTY, 0, 0).isPresent());
	}

	@Test
	void renameAndDelete() {
		SchematicLibrary library = withEntries(3);
		library = library.rename(2, "Renamed").orElseThrow();
		assertEquals("Renamed", library.find(2).orElseThrow().name());
		assertTrue(library.rename(99, "x").isEmpty());
		assertTrue(library.rename(2, "   ").isEmpty());
		library = library.delete(2).orElseThrow();
		assertTrue(library.find(2).isEmpty());
		assertEquals(2, library.entries().size());
		assertTrue(library.delete(2).isEmpty());
		// ids are never reused
		assertEquals(4, library.add("new", "custom_pumpkin", DESIGN, AuthorList.EMPTY, 0, 0).orElseThrow().schematic().id());
	}

	@Test
	void paging() {
		SchematicLibrary library = withEntries(13);
		assertEquals(3, library.pageCount(6));
		assertEquals(6, library.page(0, 6).size());
		assertEquals(1, library.page(2, 6).size());
		assertEquals(1, library.page(99, 6).size()); // clamped to last page
		assertEquals(1, SchematicLibrary.EMPTY.pageCount(6));
		assertTrue(SchematicLibrary.EMPTY.page(0, 6).isEmpty());
	}

	@Test
	void authorsAreCappedAndUnique() {
		AuthorList authors = AuthorList.EMPTY;
		UUID first = UUID.randomUUID();
		authors = authors.with(first, "Alice").with(first, "Alice");
		assertEquals(1, authors.authors().size());
		for (int i = 0; i < 20; i++) {
			authors = authors.with(UUID.randomUUID(), "P" + i);
		}
		assertEquals(AuthorList.MAX_AUTHORS, authors.authors().size());
		assertEquals("Alice", authors.names().get(0));
		AuthorList full = authors;
		assertSame(full, full.with(UUID.randomUUID(), "Late"));
	}

	@Test
	void totalDesignBytesAreCappedEvenWithoutACountLimit() {
		PumpkinDesign noisy = new PumpkinDesign(CanvasLayout.uniform(CanvasFace.ALL_MASK, 64, 64));
		int color = 1;
		for (CanvasFace face : CanvasFace.values()) {
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					noisy.paint(face, x, y, color);
					color = (color * 1103515245 + 12345) & 0xFFFFFF;
				}
			}
		}
		EncodedDesign big = EncodedDesign.of(noisy);
		SchematicLibrary library = SchematicLibrary.EMPTY;
		int added = 0;
		while (true) {
			Optional<SchematicLibrary.Added> next = library.add("big", "custom_pumpkin", big, AuthorList.EMPTY, 0, 0);
			if (next.isEmpty()) {
				break;
			}
			library = next.get().library();
			added++;
		}
		assertEquals(SchematicLibrary.MAX_STORED_BYTES / (big.size() + SchematicLibrary.ENTRY_OVERHEAD_BYTES), added);
		assertTrue(library.storedBytes() <= SchematicLibrary.MAX_STORED_BYTES);
	}

	@Test
	void tinyDesignsCountTheirEntryOverhead() {
		EncodedDesign empty = EncodedDesign.of(new PumpkinDesign(CanvasLayout.uniform(CanvasFace.ALL_MASK, 10, 10)));
		SchematicLibrary library = SchematicLibrary.EMPTY;
		int added = 0;
		while (true) {
			Optional<SchematicLibrary.Added> next = library.add("empty", "custom_pumpkin", empty, AuthorList.EMPTY, 0, 0);
			if (next.isEmpty()) {
				break;
			}
			library = next.get().library();
			added++;
		}
		assertEquals(SchematicLibrary.MAX_STORED_BYTES / (empty.size() + SchematicLibrary.ENTRY_OVERHEAD_BYTES), added);
	}
}
