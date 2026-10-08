package com.studioderiva.carves_and_crafts.design;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Binary encoding of {@link PumpkinDesign}. Output is canonical: two designs that look identical
 * always encode to the same bytes, so the bytes can be hashed to identify a design.
 *
 * <pre>
 * magic        2 bytes  'D','P'
 * version      u8       2 (version 1 had one square N×N resolution for every face; no longer read)
 * faceMask     u8       bit per {@link CanvasFace}
 * sizes        per enabled face, in {@link CanvasFace} order: width u8, height u8 (1..{@link CanvasLayout#MAX_SIZE})
 * paletteSize  varint   colors in order of first appearance, unused ones dropped
 * palette      3 bytes RGB each
 * indexWidth   u8       1 (palette <= 254, CUT = 0xFF) or 2 (CUT = 0xFFFF)
 * format       u8       0 = sparse, 1 = RLE
 * SPARSE: count varint, then count × (positionDelta varint, value)  — non-UNPAINTED pixels only
 * RLE:    runs of (length varint, value) covering every pixel
 * </pre>
 * Pixel positions run over the enabled faces in {@link CanvasFace} order, row-major inside each face.
 */
public final class DesignCodec {
	public static final int VERSION = 2;
	/** Upper bound for untrusted input; a legit worst case is ~125 KiB. */
	public static final int MAX_ENCODED_BYTES = 256 * 1024;

	static final int FORMAT_SPARSE = 0;
	static final int FORMAT_RLE = 1;
	private static final int MAX_PALETTE_ONE_BYTE = 254;
	private static final int CUT_ONE_BYTE = 0xFF;

	private DesignCodec() {
	}

	public static byte[] encode(PumpkinDesign design) {
		int[] remap = new int[design.paletteSize() + 1];
		int[] canonicalPalette = buildCanonicalPalette(design, remap);
		int paletteSize = canonicalPalette.length;
		int width = paletteSize <= MAX_PALETTE_ONE_BYTE ? 1 : 2;

		ByteWriter header = new ByteWriter(16 + paletteSize * 3);
		header.writeByte('D');
		header.writeByte('P');
		header.writeByte(VERSION);
		CanvasLayout layout = design.layout();
		header.writeByte(layout.faceMask());
		for (CanvasFace face : CanvasFace.values()) {
			if (layout.hasFace(face)) {
				header.writeByte(layout.width(face));
				header.writeByte(layout.height(face));
			}
		}
		header.writeVarInt(paletteSize);
		for (int rgb : canonicalPalette) {
			header.writeByte(rgb >> 16);
			header.writeByte(rgb >> 8);
			header.writeByte(rgb);
		}
		header.writeByte(width);

		byte[] sparse = encodeSparse(design, remap, width);
		byte[] rle = encodeRle(design, remap, width);
		boolean useSparse = sparse.length <= rle.length;
		header.writeByte(useSparse ? FORMAT_SPARSE : FORMAT_RLE);
		header.writeBytes(useSparse ? sparse : rle);
		return header.toByteArray();
	}

	public static PumpkinDesign decode(byte[] data) {
		if (data.length > MAX_ENCODED_BYTES) {
			throw new DesignFormatException("Design too large: " + data.length + " bytes");
		}
		ByteReader in = new ByteReader(data);
		if (in.readByte() != 'D' || in.readByte() != 'P') {
			throw new DesignFormatException("Bad magic");
		}
		int version = in.readByte();
		if (version != VERSION) {
			throw new DesignFormatException("Unsupported design version " + version);
		}
		int faceMask = in.readByte();
		if (faceMask == 0 || (faceMask & ~CanvasFace.ALL_MASK) != 0) {
			throw new DesignFormatException("Bad face mask " + faceMask);
		}
		CanvasLayout.Builder builder = CanvasLayout.builder();
		for (CanvasFace face : CanvasFace.values()) {
			if ((faceMask & face.bit()) != 0) {
				int w = in.readByte();
				int h = in.readByte();
				if (w < 1 || h < 1 || w > CanvasLayout.MAX_SIZE || h > CanvasLayout.MAX_SIZE) {
					throw new DesignFormatException("Bad canvas size " + w + "x" + h);
				}
				builder.face(face, w, h);
			}
		}
		CanvasLayout layout = builder.build();
		PumpkinDesign design = new PumpkinDesign(layout);
		int totalPixels = layout.totalArea();
		PixelIndex index = new PixelIndex(layout);

		int paletteSize = in.readVarInt();
		if (paletteSize > Math.min(PumpkinDesign.MAX_PALETTE_SIZE, totalPixels)) {
			throw new DesignFormatException("Palette too large: " + paletteSize);
		}
		int[] toInternal = new int[paletteSize + 1];
		for (int i = 1; i <= paletteSize; i++) {
			int rgb = (in.readByte() << 16) | (in.readByte() << 8) | in.readByte();
			toInternal[i] = design.paletteIndexFor(rgb);
		}

		int width = in.readByte();
		if (width != 1 && width != 2 || width == 1 && paletteSize > MAX_PALETTE_ONE_BYTE) {
			throw new DesignFormatException("Bad index width " + width);
		}
		int cutValue = width == 1 ? CUT_ONE_BYTE : PumpkinDesign.CUT;

		int format = in.readByte();
		if (format == FORMAT_SPARSE) {
			int count = in.readVarInt();
			if (count > totalPixels) {
				throw new DesignFormatException("Too many sparse entries");
			}
			int position = -1;
			for (int i = 0; i < count; i++) {
				int delta = in.readVarInt();
				if (delta < 1 || position + delta >= totalPixels) {
					throw new DesignFormatException("Bad sparse position");
				}
				position += delta;
				int value = mapValue(readValue(in, width), cutValue, paletteSize, toInternal);
				if (value == PumpkinDesign.UNPAINTED) {
					throw new DesignFormatException("Sparse entry stores UNPAINTED");
				}
				index.seek(position);
				design.setRawUnchecked(index.face, index.offset, value);
			}
		} else if (format == FORMAT_RLE) {
			int position = 0;
			while (position < totalPixels) {
				int length = in.readVarInt();
				if (length < 1 || position + length > totalPixels) {
					throw new DesignFormatException("Bad run length");
				}
				int value = mapValue(readValue(in, width), cutValue, paletteSize, toInternal);
				index.seek(position);
				for (int end = position + length; position < end; position++) {
					design.setRawUnchecked(index.face, index.offset, value);
					index.next();
				}
			}
		} else {
			throw new DesignFormatException("Unknown format " + format);
		}
		if (in.remaining() != 0) {
			throw new DesignFormatException("Trailing bytes");
		}
		return design;
	}

	/** Stable content id: first 128 bits of SHA-256 over the canonical encoding, as hex. */
	public static String hash(byte[] encoded) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(encoded);
			return HexFormat.of().formatHex(digest, 0, 16);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}

	public static String hash(PumpkinDesign design) {
		return hash(encode(design));
	}

	/** Distinct colors the design actually uses (unused palette entries don't count). */
	public static int colorCount(PumpkinDesign design) {
		return buildCanonicalPalette(design, new int[design.paletteSize() + 1]).length;
	}

	// --- encoding helpers ---

	/**
	 * Orders used colors by first appearance; fills remap[internalIndex] = canonicalIndex.
	 * Internal palette RGB values are unique, so each used index maps to its own canonical entry.
	 */
	private static int[] buildCanonicalPalette(PumpkinDesign design, int[] remap) {
		int[] colors = new int[design.paletteSize()];
		int count = 0;
		for (int faceOrdinal : enabledFaces(design.faceMask())) {
			for (char raw : design.gridOrNull(faceOrdinal)) {
				if (raw == PumpkinDesign.UNPAINTED || raw == PumpkinDesign.CUT || remap[raw] != 0) {
					continue;
				}
				colors[count] = design.paletteColor(raw);
				remap[raw] = ++count;
			}
		}
		return Arrays.copyOf(colors, count);
	}

	private static byte[] encodeSparse(PumpkinDesign design, int[] remap, int width) {
		int count = 0;
		int position = 0;
		int last = -1;
		ByteWriter entries = new ByteWriter(64);
		for (int faceOrdinal : enabledFaces(design.faceMask())) {
			for (char raw : design.gridOrNull(faceOrdinal)) {
				if (raw != PumpkinDesign.UNPAINTED) {
					entries.writeVarInt(position - last);
					writeValue(entries, canonicalValue(raw, remap, width), width);
					last = position;
					count++;
				}
				position++;
			}
		}
		ByteWriter body = new ByteWriter(entries.size + 5);
		body.writeVarInt(count);
		body.writeBytes(entries.toByteArray());
		return body.toByteArray();
	}

	private static byte[] encodeRle(PumpkinDesign design, int[] remap, int width) {
		ByteWriter body = new ByteWriter(64);
		int runValue = -1;
		int runLength = 0;
		for (int faceOrdinal : enabledFaces(design.faceMask())) {
			for (char raw : design.gridOrNull(faceOrdinal)) {
				int value = canonicalValue(raw, remap, width);
				if (value == runValue) {
					runLength++;
				} else {
					if (runLength > 0) {
						body.writeVarInt(runLength);
						writeValue(body, runValue, width);
					}
					runValue = value;
					runLength = 1;
				}
			}
		}
		if (runLength > 0) {
			body.writeVarInt(runLength);
			writeValue(body, runValue, width);
		}
		return body.toByteArray();
	}

	private static int canonicalValue(char raw, int[] remap, int width) {
		if (raw == PumpkinDesign.UNPAINTED) {
			return 0;
		}
		if (raw == PumpkinDesign.CUT) {
			return width == 1 ? CUT_ONE_BYTE : PumpkinDesign.CUT;
		}
		return remap[raw];
	}

	private static void writeValue(ByteWriter out, int value, int width) {
		if (width == 2) {
			out.writeByte(value >> 8);
		}
		out.writeByte(value);
	}

	// --- decoding helpers ---

	private static int readValue(ByteReader in, int width) {
		return width == 2 ? (in.readByte() << 8) | in.readByte() : in.readByte();
	}

	private static int mapValue(int stored, int cutValue, int paletteSize, int[] toInternal) {
		if (stored == 0) {
			return PumpkinDesign.UNPAINTED;
		}
		if (stored == cutValue) {
			return PumpkinDesign.CUT;
		}
		if (stored > paletteSize) {
			throw new DesignFormatException("Palette index out of range: " + stored);
		}
		return toInternal[stored];
	}

	private static int[] enabledFaces(int faceMask) {
		int[] result = new int[Integer.bitCount(faceMask)];
		int n = 0;
		for (CanvasFace face : CanvasFace.values()) {
			if ((faceMask & face.bit()) != 0) {
				result[n++] = face.ordinal();
			}
		}
		return result;
	}

	/** Walks pixel positions (enabled faces in order, row-major) as face ordinal + offset inside the face. */
	private static final class PixelIndex {
		private final int[] faces;
		private final int[] areas;
		private int slot;
		int face;
		int offset;

		PixelIndex(CanvasLayout layout) {
			faces = enabledFaces(layout.faceMask());
			areas = new int[faces.length];
			for (int i = 0; i < faces.length; i++) {
				areas[i] = layout.area(CanvasFace.byOrdinal(faces[i]));
			}
		}

		/** Positions only grow while decoding, so seeking continues from the current face. */
		void seek(int position) {
			int start = 0;
			for (int i = 0; i < slot; i++) {
				start += areas[i];
			}
			while (position - start >= areas[slot]) {
				start += areas[slot++];
			}
			face = faces[slot];
			offset = position - start;
		}

		void next() {
			if (++offset >= areas[slot] && slot + 1 < faces.length) {
				slot++;
				offset = 0;
			}
			face = faces[slot];
		}
	}

	private static final class ByteWriter {
		private byte[] buffer;
		private int size;

		ByteWriter(int capacity) {
			buffer = new byte[Math.max(16, capacity)];
		}

		void writeByte(int value) {
			ensure(1);
			buffer[size++] = (byte) value;
		}

		void writeBytes(byte[] bytes) {
			ensure(bytes.length);
			System.arraycopy(bytes, 0, buffer, size, bytes.length);
			size += bytes.length;
		}

		void writeVarInt(int value) {
			while ((value & ~0x7F) != 0) {
				writeByte((value & 0x7F) | 0x80);
				value >>>= 7;
			}
			writeByte(value);
		}

		byte[] toByteArray() {
			return Arrays.copyOf(buffer, size);
		}

		private void ensure(int extra) {
			if (size + extra > buffer.length) {
				buffer = Arrays.copyOf(buffer, Math.max(buffer.length * 2, size + extra));
			}
		}
	}

	private static final class ByteReader {
		private final byte[] data;
		private int position;

		ByteReader(byte[] data) {
			this.data = data;
		}

		int readByte() {
			if (position >= data.length) {
				throw new DesignFormatException("Unexpected end of data");
			}
			return data[position++] & 0xFF;
		}

		int readVarInt() {
			int value = 0;
			for (int shift = 0; shift < 35; shift += 7) {
				int b = readByte();
				value |= (b & 0x7F) << shift;
				if ((b & 0x80) == 0) {
					if (value < 0) {
						throw new DesignFormatException("Negative varint");
					}
					return value;
				}
			}
			throw new DesignFormatException("VarInt too long");
		}

		int remaining() {
			return data.length - position;
		}
	}
}
