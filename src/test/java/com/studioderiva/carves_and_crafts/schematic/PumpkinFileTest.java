package com.studioderiva.carves_and_crafts.schematic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.studioderiva.carves_and_crafts.design.AuthorList;
import com.studioderiva.carves_and_crafts.design.CanvasFace;
import com.studioderiva.carves_and_crafts.design.CanvasLayout;
import com.studioderiva.carves_and_crafts.design.DesignFormatException;
import com.studioderiva.carves_and_crafts.design.EncodedDesign;
import com.studioderiva.carves_and_crafts.design.PumpkinDesign;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PumpkinFileTest {
	private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
	private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000b0b");

	private static PumpkinFile sample() {
		PumpkinDesign design = new PumpkinDesign(CanvasLayout.builder().face(CanvasFace.NORTH, 20, 18).face(CanvasFace.EAST, 20, 18).build());
		design.cut(CanvasFace.NORTH, 3, 4);
		design.paint(CanvasFace.EAST, 19, 17, 0x123456);
		AuthorList authors = AuthorList.EMPTY.with(ALICE, "Alice").with(BOB, "Bob");
		return new PumpkinFile("Spooky", 1_700_000_000_000L, "classic_pumpkin", EncodedDesign.of(design), authors);
	}

	@Test
	void roundTrip() {
		PumpkinFile file = sample();
		assertEquals(file, PumpkinFile.read(file.write()));
	}

	@Test
	void writingIsDeterministic() {
		assertArrayEquals(sample().write(), sample().write());
	}

	@Test
	void nameIsSanitizedOnRead() {
		PumpkinFile file = sample();
		PumpkinFile dirty = new PumpkinFile("  §cRed\u0007 ", file.createdAt(), file.model(), file.design(), file.authors());
		assertEquals("Red", PumpkinFile.read(dirty.write()).name());
	}

	@Test
	void rejectsBadMagicAndVersion() {
		byte[] bytes = sample().write();
		byte[] badMagic = bytes.clone();
		badMagic[0] = 'X';
		assertThrows(DesignFormatException.class, () -> PumpkinFile.read(badMagic));
		byte[] badVersion = bytes.clone();
		badVersion[4] = 99;
		assertThrows(DesignFormatException.class, () -> PumpkinFile.read(badVersion));
	}

	@Test
	void rejectsTruncatedAndTrailingData() {
		byte[] bytes = sample().write();
		for (int cut : new int[] {0, 3, 5, 20, bytes.length - 1}) {
			byte[] truncated = Arrays.copyOf(bytes, cut);
			assertThrows(DesignFormatException.class, () -> PumpkinFile.read(truncated));
		}
		byte[] trailing = Arrays.copyOf(bytes, bytes.length + 1);
		assertThrows(DesignFormatException.class, () -> PumpkinFile.read(trailing));
	}

	@Test
	void rejectsBadModelId() {
		PumpkinFile file = sample();
		PumpkinFile bad = new PumpkinFile(file.name(), file.createdAt(), "../Evil", file.design(), file.authors());
		assertThrows(DesignFormatException.class, () -> PumpkinFile.read(bad.write()));
	}

	@Test
	void rejectsCorruptDesign() {
		PumpkinFile file = sample();
		byte[] bytes = file.write();
		bytes[bytes.length - file.design().size()] = 'X'; // first byte of the design's own magic
		assertThrows(DesignFormatException.class, () -> PumpkinFile.read(bytes));
	}

	@Test
	void rejectsOversizedFiles() {
		assertThrows(DesignFormatException.class, () -> PumpkinFile.read(new byte[PumpkinFile.MAX_FILE_BYTES + 1]));
	}
}
