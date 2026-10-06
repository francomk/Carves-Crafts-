package com.studioderiva.carves_and_crafts.schematic;

import com.studioderiva.carves_and_crafts.design.AuthorList;
import com.studioderiva.carves_and_crafts.design.DesignCodec;
import com.studioderiva.carves_and_crafts.design.DesignFormatException;
import com.studioderiva.carves_and_crafts.design.EncodedDesign;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A schematic exported to a {@code .pumpkin} file, to keep on the player's computer or share with others.
 * Files are untrusted input: {@link #read} validates everything, the server validates again on import.
 *
 * <pre>
 * magic        4 bytes  'P','M','P','K'
 * version      u8       1
 * model        UTF      pumpkin model id without namespace (e.g. classic_pumpkin), so files survive a mod id rename
 * name         UTF      schematic name (sanitized on read)
 * created      i64      creation time, epoch millis
 * authorCount  u8       0..{@link AuthorList#MAX_AUTHORS}
 * authors      per author: uuid (2 × i64), name UTF (1..{@link AuthorList#MAX_NAME_LENGTH} chars)
 * designLength i32      1..{@link DesignCodec#MAX_ENCODED_BYTES}
 * design       bytes    {@link DesignCodec} encoding (canvas sizes, palette, pixels)
 * </pre>
 * Strings use {@link DataOutputStream#writeUTF} (length-prefixed modified UTF-8). Nothing may follow the design.
 */
public record PumpkinFile(String name, long createdAt, String model, EncodedDesign design, AuthorList authors) {
	public static final String EXTENSION = ".pumpkin";
	public static final int VERSION = 1;
	/** Upper bound for a file on disk: the design plus a generous header. */
	public static final int MAX_FILE_BYTES = DesignCodec.MAX_ENCODED_BYTES + 4096;
	private static final byte[] MAGIC = {'P', 'M', 'P', 'K'};
	private static final Pattern MODEL_ID = Pattern.compile("[a-z0-9_]{1,64}");

	public static PumpkinFile of(Schematic schematic) {
		return new PumpkinFile(schematic.name(), schematic.createdAt(), schematic.model(), schematic.design(), schematic.authors());
	}

	public static boolean isValidModelId(String model) {
		return MODEL_ID.matcher(model).matches();
	}

	public byte[] write() {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(design.size() + 128);
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.write(MAGIC);
			out.writeByte(VERSION);
			out.writeUTF(model);
			out.writeUTF(name);
			out.writeLong(createdAt);
			out.writeByte(authors.authors().size());
			for (AuthorList.Author author : authors.authors()) {
				out.writeLong(author.id().getMostSignificantBits());
				out.writeLong(author.id().getLeastSignificantBits());
				out.writeUTF(author.name());
			}
			byte[] encoded = design.toByteArray();
			out.writeInt(encoded.length);
			out.write(encoded);
		} catch (IOException e) {
			throw new UncheckedIOException(e); // in-memory stream: cannot happen
		}
		return bytes.toByteArray();
	}

	/** @throws DesignFormatException if the bytes are not a valid pumpkin file */
	public static PumpkinFile read(byte[] data) {
		if (data.length > MAX_FILE_BYTES) {
			throw new DesignFormatException("File too large: " + data.length + " bytes");
		}
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
			byte[] magic = new byte[MAGIC.length];
			in.readFully(magic);
			if (!Arrays.equals(magic, MAGIC)) {
				throw new DesignFormatException("Not a pumpkin file");
			}
			int version = in.readUnsignedByte();
			if (version != VERSION) {
				throw new DesignFormatException("Unsupported pumpkin file version " + version);
			}
			String model = in.readUTF();
			if (!isValidModelId(model)) {
				throw new DesignFormatException("Bad model id");
			}
			String name = Schematic.sanitizeName(in.readUTF());
			long created = in.readLong();
			int authorCount = in.readUnsignedByte();
			if (authorCount > AuthorList.MAX_AUTHORS) {
				throw new DesignFormatException("Too many authors: " + authorCount);
			}
			List<AuthorList.Author> authors = new ArrayList<>(authorCount);
			for (int i = 0; i < authorCount; i++) {
				UUID id = new UUID(in.readLong(), in.readLong());
				String authorName = in.readUTF();
				if (authorName.isEmpty() || authorName.length() > AuthorList.MAX_NAME_LENGTH) {
					throw new DesignFormatException("Bad author name");
				}
				authors.add(new AuthorList.Author(id, authorName));
			}
			int length = in.readInt();
			if (length <= 0 || length > DesignCodec.MAX_ENCODED_BYTES) {
				throw new DesignFormatException("Bad design length " + length);
			}
			byte[] encoded = new byte[length];
			in.readFully(encoded);
			if (in.read() != -1) {
				throw new DesignFormatException("Unexpected data after the design");
			}
			return new PumpkinFile(name, created, model, EncodedDesign.of(encoded), new AuthorList(authors));
		} catch (IOException e) {
			throw new DesignFormatException("Truncated pumpkin file");
		}
	}
}
