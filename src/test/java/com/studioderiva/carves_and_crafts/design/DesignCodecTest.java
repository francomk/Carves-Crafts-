package com.studioderiva.carves_and_crafts.design;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DesignCodecTest {
	private static final int ALL = CanvasFace.ALL_MASK;

	@Test
	void emptyDesignIsTiny() {
		byte[] bytes = DesignCodec.encode(new PumpkinDesign(CanvasLayout.uniform(ALL, 64, 64)));
		// magic, version, mask (4) + 2 size bytes × 5 faces + palette size (1) + width (1) + format (1) + sparse count (1)
		assertTrue(bytes.length <= 18, "was " + bytes.length);
		assertEquals(new PumpkinDesign(CanvasLayout.uniform(ALL, 64, 64)), DesignCodec.decode(bytes));
	}

	@Test
	void roundTripKeepsCutsAndColors() {
		PumpkinDesign design = new PumpkinDesign(CanvasLayout.uniform(ALL, 16, 16));
		design.cut(CanvasFace.NORTH, 3, 4);
		design.paint(CanvasFace.NORTH, 5, 5, 0x123456);
		design.paint(CanvasFace.UP, 0, 15, 0xFF8800);

		PumpkinDesign decoded = DesignCodec.decode(DesignCodec.encode(design));
		assertTrue(decoded.isCut(CanvasFace.NORTH, 3, 4));
		assertEquals(0x123456, decoded.colorAt(CanvasFace.NORTH, 5, 5));
		assertEquals(0xFF8800, decoded.colorAt(CanvasFace.UP, 0, 15));
		assertEquals(-1, decoded.colorAt(CanvasFace.SOUTH, 0, 0));
		assertEquals(design, decoded);
	}

	@Test
	void smallEditsUseSparseFormat() {
		PumpkinDesign design = new PumpkinDesign(CanvasLayout.uniform(ALL, 64, 64));
		// scattered pixels: one run each would make RLE bigger than sparse
		for (int i = 0; i < 10; i++) {
			design.cut(CanvasFace.NORTH, i * 5, i * 6);
		}
		byte[] bytes = DesignCodec.encode(design);
		assertEquals(DesignCodec.FORMAT_SPARSE, formatByte(bytes));
		assertEquals(design, DesignCodec.decode(bytes));
	}

	@Test
	void fullyPaintedFaceUsesRle() {
		PumpkinDesign design = new PumpkinDesign(CanvasLayout.uniform(ALL, 64, 64));
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				design.paint(CanvasFace.NORTH, x, y, 0x00FF00);
			}
		}
		byte[] bytes = DesignCodec.encode(design);
		assertEquals(DesignCodec.FORMAT_RLE, formatByte(bytes));
		assertTrue(bytes.length < 32, "was " + bytes.length);
		assertEquals(design, DesignCodec.decode(bytes));
	}

	@Test
	void encodingIsCanonical() {
		// same look, different paint order and an unused color left in the palette
		PumpkinDesign a = new PumpkinDesign(CanvasLayout.uniform(ALL, 16, 16));
		a.paint(CanvasFace.EAST, 1, 1, 0xAAAAAA);
		a.paint(CanvasFace.EAST, 2, 2, 0xBBBBBB);

		PumpkinDesign b = new PumpkinDesign(CanvasLayout.uniform(ALL, 16, 16));
		b.paint(CanvasFace.EAST, 2, 2, 0xCCCCCC);
		b.paint(CanvasFace.EAST, 2, 2, 0xBBBBBB);
		b.paint(CanvasFace.EAST, 1, 1, 0xAAAAAA);

		assertArrayEquals(DesignCodec.encode(a), DesignCodec.encode(b));
		assertEquals(DesignCodec.hash(a), DesignCodec.hash(b));
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
	}

	@Test
	void differentDesignsHashDifferently() {
		PumpkinDesign a = new PumpkinDesign(CanvasLayout.uniform(ALL, 16, 16));
		PumpkinDesign b = new PumpkinDesign(CanvasLayout.uniform(ALL, 16, 16));
		b.cut(CanvasFace.WEST, 0, 0);
		assertNotEquals(DesignCodec.hash(a), DesignCodec.hash(b));
	}

	@Test
	void largePaletteSwitchesToTwoByteIndices() {
		PumpkinDesign design = new PumpkinDesign(CanvasLayout.uniform(ALL, 32, 32));
		int color = 0;
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				design.paint(CanvasFace.SOUTH, x, y, color++ * 997);
			}
		}
		design.cut(CanvasFace.SOUTH, 31, 31);
		PumpkinDesign decoded = DesignCodec.decode(DesignCodec.encode(design));
		assertEquals(design, decoded);
		assertTrue(decoded.isCut(CanvasFace.SOUTH, 31, 31));
		assertEquals(997 * 5, decoded.colorAt(CanvasFace.SOUTH, 5, 0));
	}

	@Test
	void randomDesignsRoundTrip() {
		Random random = new Random(42);
		for (int maxSize : new int[] {4, 16, CanvasLayout.MAX_SIZE}) {
			for (int iteration = 0; iteration < 20; iteration++) {
				int mask = 1 + random.nextInt(CanvasFace.ALL_MASK);
				// every face its own rectangular size
				CanvasLayout.Builder builder = CanvasLayout.builder();
				for (CanvasFace face : CanvasFace.values()) {
					if ((mask & face.bit()) != 0) {
						builder.face(face, 1 + random.nextInt(maxSize), 1 + random.nextInt(maxSize));
					}
				}
				PumpkinDesign design = new PumpkinDesign(builder.build());
				int edits = random.nextInt(design.layout().totalArea() * 2);
				for (int i = 0; i < edits; i++) {
					CanvasFace face = CanvasFace.byOrdinal(random.nextInt(CanvasFace.count()));
					if (!design.hasFace(face)) {
						continue;
					}
					int x = random.nextInt(design.width(face));
					int y = random.nextInt(design.height(face));
					switch (random.nextInt(3)) {
						case 0 -> design.cut(face, x, y);
						case 1 -> design.paint(face, x, y, random.nextInt(1 << 24));
						default -> design.erase(face, x, y);
					}
				}
				byte[] bytes = DesignCodec.encode(design);
				PumpkinDesign decoded = DesignCodec.decode(bytes);
				assertEquals(design, decoded);
				assertArrayEquals(bytes, DesignCodec.encode(decoded));
			}
		}
	}

	@Test
	void cutPixelsCannotBePaintedOrErased() {
		PumpkinDesign design = new PumpkinDesign(CanvasLayout.uniform(ALL, 8, 8));
		design.cut(CanvasFace.NORTH, 0, 0);
		assertFalse(design.paint(CanvasFace.NORTH, 0, 0, 0xFFFFFF));
		assertFalse(design.erase(CanvasFace.NORTH, 0, 0));
		assertTrue(design.isCut(CanvasFace.NORTH, 0, 0));
	}

	@Test
	void countDifferencesComparesVisibleContent() {
		PumpkinDesign before = new PumpkinDesign(CanvasLayout.uniform(ALL, 8, 8));
		before.paint(CanvasFace.NORTH, 1, 1, 0x111111);
		PumpkinDesign after = before.copy();
		after.paint(CanvasFace.NORTH, 1, 1, 0x111111); // no change
		after.paint(CanvasFace.NORTH, 2, 2, 0x222222);
		after.cut(CanvasFace.UP, 0, 0);
		assertEquals(2, before.countDifferences(after));
	}

	@Test
	void rejectsMalformedInput() {
		byte[] valid = DesignCodec.encode(sample());
		assertThrows(DesignFormatException.class, () -> DesignCodec.decode(new byte[0]));
		assertThrows(DesignFormatException.class, () -> DesignCodec.decode(Arrays.copyOf(valid, valid.length - 1)));
		assertThrows(DesignFormatException.class, () -> DesignCodec.decode(Arrays.copyOf(valid, valid.length + 1)));

		byte[] oldVersion = valid.clone();
		oldVersion[2] = 1;
		assertThrows(DesignFormatException.class, () -> DesignCodec.decode(oldVersion));

		byte[] badMask = valid.clone();
		badMask[3] = 0;
		assertThrows(DesignFormatException.class, () -> DesignCodec.decode(badMask));

		byte[] zeroWidth = valid.clone();
		zeroWidth[4] = 0;
		assertThrows(DesignFormatException.class, () -> DesignCodec.decode(zeroWidth));

		byte[] tooTall = valid.clone();
		tooTall[5] = (byte) (CanvasLayout.MAX_SIZE + 1);
		assertThrows(DesignFormatException.class, () -> DesignCodec.decode(tooTall));

		assertThrows(DesignFormatException.class, () -> DesignCodec.decode(new byte[DesignCodec.MAX_ENCODED_BYTES + 1]));
	}

	@Test
	void rejectsPaletteIndexOutOfRange() {
		// one 8x8 face, empty palette, width 1, sparse, one entry at position 0 with index 1
		byte[] bytes = {'D', 'P', 2, 1, 8, 8, 0, 1, DesignCodec.FORMAT_SPARSE, 1, 1, 1};
		assertThrows(DesignFormatException.class, () -> DesignCodec.decode(bytes));
	}

	@Test
	void rejectsPositionsPastIntegerOverflow() {
		// 8x8 front face, one color, sparse: a pixel at 1, then a jump of Integer.MAX_VALUE
		byte[] sparse = {'D', 'P', 2, 1, 8, 8, 1, (byte) 0xFF, 0, 0, 1, 0,
			2, 2, 1, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x07, 1};
		assertThrows(DesignFormatException.class, () -> DesignCodec.decode(sparse));
		// same canvas, RLE: a run of 1, then a run of Integer.MAX_VALUE
		byte[] rle = {'D', 'P', 2, 1, 8, 8, 1, (byte) 0xFF, 0, 0, 1, 1,
			1, 1, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x07, 1};
		assertThrows(DesignFormatException.class, () -> DesignCodec.decode(rle));
	}

	private static PumpkinDesign sample() {
		PumpkinDesign design = new PumpkinDesign(CanvasLayout.uniform(ALL, 16, 16));
		design.cut(CanvasFace.NORTH, 1, 1);
		design.paint(CanvasFace.NORTH, 2, 2, 0xABCDEF);
		return design;
	}

	@Test
	void rectangularFacesRoundTrip() {
		CanvasLayout layout = CanvasLayout.builder()
			.face(CanvasFace.NORTH, 20, 18)
			.face(CanvasFace.EAST, 7, 33)
			.build();
		PumpkinDesign design = new PumpkinDesign(layout);
		design.cut(CanvasFace.NORTH, 19, 17);
		design.paint(CanvasFace.EAST, 6, 32, 0x123456);
		design.paint(CanvasFace.EAST, 0, 0, 0x654321);
		PumpkinDesign decoded = DesignCodec.decode(DesignCodec.encode(design));
		assertEquals(layout, decoded.layout());
		assertEquals(design, decoded);
		assertTrue(decoded.isCut(CanvasFace.NORTH, 19, 17));
		assertEquals(0x123456, decoded.colorAt(CanvasFace.EAST, 6, 32));
		assertThrows(IndexOutOfBoundsException.class, () -> decoded.isCut(CanvasFace.NORTH, 20, 0));
	}

	/** Format byte: after magic, version, mask, two size bytes per face, palette and index width. */
	private static int formatByte(byte[] bytes) {
		int paletteAt = 4 + 2 * Integer.bitCount(bytes[3]);
		int paletteSize = bytes[paletteAt]; // single-byte varint in these tests
		return bytes[paletteAt + 1 + paletteSize * 3 + 1];
	}
}
