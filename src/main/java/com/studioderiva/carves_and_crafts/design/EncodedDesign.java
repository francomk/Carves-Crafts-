package com.studioderiva.carves_and_crafts.design;

import com.google.common.hash.HashCode;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HexFormat;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

/**
 * Immutable, validated encoded design. Used where Minecraft needs value semantics:
 * data components, NBT and network.
 *
 * <p>A value is either <em>complete</em> (the encoded bytes) or a <em>reference</em>: only the design's hash. Pumpkin
 * items send references to clients ({@link #ITEM_STREAM_CODEC}, {@link #ITEM_CODEC}), so an inventory or a shulker
 * box full of large designs can't make a packet too big; {@link DesignRefs} turns references sent back by clients
 * into complete designs again. Two values are equal when their hashes are, whatever their form.
 */
public final class EncodedDesign {
	/** Reference layout: magic 'D','R' then the 16 hash bytes. */
	static final int REF_BYTES = 18;
	private static final int HASH_BYTES = 16;

	/** Complete designs only: saves, schematics and the mod's own payloads. */
	public static final Codec<EncodedDesign> CODEC = Codec.BYTE_BUFFER.comapFlatMap(EncodedDesign::fromBuffer, e -> ByteBuffer.wrap(e.completeBytes()));
	/** Complete designs only, for the mod's own payloads. */
	public static final StreamCodec<ByteBuf, EncodedDesign> STREAM_CODEC = ByteBufCodecs.byteArray(DesignCodec.MAX_ENCODED_BYTES).map(bytes -> {
		try {
			return of(bytes);
		} catch (DesignFormatException e) {
			throw new DecoderException("Invalid pumpkin design: " + e.getMessage());
		}
	}, design -> {
		if (!design.isComplete()) {
			throw new EncoderException("Pumpkin design reference where the full design is needed");
		}
		return design.bytes;
	});

	/** Network codec of the item component: always a reference. */
	public static final StreamCodec<ByteBuf, EncodedDesign> ITEM_STREAM_CODEC = ByteBufCodecs.byteArray(REF_BYTES).map(bytes -> {
		try {
			return DesignRefs.resolve(ref(bytes));
		} catch (DesignFormatException e) {
			throw new DecoderException("Invalid pumpkin design reference: " + e.getMessage());
		}
	}, design -> {
		DesignRefs.remember(design);
		return design.refBytes();
	});

	/**
	 * Persistent codec of the item component. Writes the complete design, except for a reference when hashing
	 * (slot sync compares client and server hashes, the client only has references) or while
	 * {@link DesignRefs#syncing} (packets being encoded, block entity data sent to clients, e.g. pumpkins on a shelf).
	 * Reads both forms.
	 */
	public static final Codec<EncodedDesign> ITEM_CODEC = new Codec<>() {
		@Override
		public <T> DataResult<T> encode(EncodedDesign input, DynamicOps<T> ops, T prefix) {
			boolean hashing = ops.empty() instanceof HashCode;
			if (input.isComplete() && !hashing && !DesignRefs.isSyncing()) {
				return Codec.BYTE_BUFFER.encode(ByteBuffer.wrap(input.bytes), ops, prefix);
			}
			if (!hashing) {
				DesignRefs.remember(input);
				DesignRefs.noteReference(input);
			}
			return Codec.BYTE_BUFFER.encode(ByteBuffer.wrap(input.refBytes()), ops, prefix);
		}

		@Override
		public <T> DataResult<Pair<EncodedDesign, T>> decode(DynamicOps<T> ops, T input) {
			return Codec.BYTE_BUFFER.decode(ops, input).flatMap(pair -> {
				byte[] data = toArray(pair.getFirst());
				try {
					EncodedDesign design = isRef(data) ? DesignRefs.resolve(ref(data)) : of(data);
					return DataResult.success(Pair.of(design, pair.getSecond()));
				} catch (DesignFormatException e) {
					return DataResult.error(() -> "Invalid pumpkin design: " + e.getMessage());
				}
			});
		}

		@Override
		public String toString() {
			return "EncodedDesign.ITEM_CODEC";
		}
	};

	/** Encoded bytes, or null for a reference. */
	private final byte @Nullable [] bytes;
	private final byte[] hashBytes;
	private final int hashCode;
	private @Nullable String hash;

	private EncodedDesign(byte @Nullable [] bytes, byte[] hashBytes) {
		this.bytes = bytes;
		this.hashBytes = hashBytes;
		this.hashCode = Arrays.hashCode(hashBytes);
	}

	public static EncodedDesign of(PumpkinDesign design) {
		return complete(DesignCodec.encode(design));
	}

	/** Validates and canonicalizes untrusted bytes. */
	public static EncodedDesign of(byte[] bytes) {
		return of(DesignCodec.decode(bytes));
	}

	private static EncodedDesign complete(byte[] canonical) {
		return new EncodedDesign(canonical, HexFormat.of().parseHex(DesignCodec.hash(canonical)));
	}

	static boolean isRef(byte[] data) {
		return data.length == REF_BYTES && data[0] == 'D' && data[1] == 'R';
	}

	static EncodedDesign ref(byte[] data) {
		if (!isRef(data)) {
			throw new DesignFormatException("Not a design reference");
		}
		return new EncodedDesign(null, Arrays.copyOfRange(data, 2, REF_BYTES));
	}

	/** False for a reference: only the hash is known, the design itself is on the server. */
	public boolean isComplete() {
		return bytes != null;
	}

	public EncodedDesign toRef() {
		return isComplete() ? new EncodedDesign(null, hashBytes) : this;
	}

	public PumpkinDesign decode() {
		return DesignCodec.decode(completeBytes());
	}

	public String hash() {
		if (hash == null) {
			hash = HexFormat.of().formatHex(hashBytes);
		}
		return hash;
	}

	/** Bytes this value takes when sent: the full design, or a reference. */
	public int size() {
		return bytes != null ? bytes.length : REF_BYTES;
	}

	public int colorCount() {
		return DesignCodec.colorCount(decode());
	}

	private byte[] completeBytes() {
		if (bytes == null) {
			throw new DesignFormatException("Only the reference of this design is known");
		}
		return bytes;
	}

	byte[] refBytes() {
		byte[] ref = new byte[REF_BYTES];
		ref[0] = 'D';
		ref[1] = 'R';
		System.arraycopy(hashBytes, 0, ref, 2, HASH_BYTES);
		return ref;
	}

	/** Copy of the encoded bytes, for writing to a file. */
	public byte[] toByteArray() {
		return completeBytes().clone();
	}

	private static byte[] toArray(ByteBuffer buffer) {
		byte[] data = new byte[buffer.remaining()];
		buffer.duplicate().get(data);
		return data;
	}

	private static DataResult<EncodedDesign> fromBuffer(ByteBuffer buffer) {
		try {
			return DataResult.success(of(toArray(buffer)));
		} catch (DesignFormatException e) {
			return DataResult.error(() -> "Invalid pumpkin design: " + e.getMessage());
		}
	}

	@Override
	public boolean equals(Object o) {
		return this == o || o instanceof EncodedDesign other && Arrays.equals(hashBytes, other.hashBytes);
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	@Override
	public String toString() {
		return bytes != null ? "EncodedDesign[" + bytes.length + " bytes]" : "EncodedDesign[ref " + hash() + "]";
	}
}
