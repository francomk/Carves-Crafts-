package com.studioderiva.carves_and_crafts.design;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Immutable, validated encoded design. Used where Minecraft needs value semantics:
 * data components, NBT and network.
 */
public final class EncodedDesign {
	public static final Codec<EncodedDesign> CODEC = Codec.BYTE_BUFFER.comapFlatMap(EncodedDesign::fromBuffer, e -> ByteBuffer.wrap(e.bytes));
	public static final StreamCodec<ByteBuf, EncodedDesign> STREAM_CODEC = ByteBufCodecs.byteArray(DesignCodec.MAX_ENCODED_BYTES).map(bytes -> {
		try {
			return of(bytes);
		} catch (DesignFormatException e) {
			throw new DecoderException("Invalid pumpkin design: " + e.getMessage());
		}
	}, EncodedDesign::bytes);

	private final byte[] bytes;
	private final int hashCode;

	private EncodedDesign(byte[] bytes) {
		this.bytes = bytes;
		this.hashCode = Arrays.hashCode(bytes);
	}

	public static EncodedDesign of(PumpkinDesign design) {
		return new EncodedDesign(DesignCodec.encode(design));
	}

	/** Validates and canonicalizes untrusted bytes. */
	public static EncodedDesign of(byte[] bytes) {
		return of(DesignCodec.decode(bytes));
	}

	public PumpkinDesign decode() {
		return DesignCodec.decode(bytes);
	}

	public String hash() {
		return DesignCodec.hash(bytes);
	}

	public int size() {
		return bytes.length;
	}

	private byte[] bytes() {
		return bytes;
	}

	/** Copy of the encoded bytes, for writing to a file. */
	public byte[] toByteArray() {
		return bytes.clone();
	}

	private static DataResult<EncodedDesign> fromBuffer(ByteBuffer buffer) {
		byte[] data = new byte[buffer.remaining()];
		buffer.duplicate().get(data);
		try {
			return DataResult.success(of(data));
		} catch (DesignFormatException e) {
			return DataResult.error(() -> "Invalid pumpkin design: " + e.getMessage());
		}
	}

	@Override
	public boolean equals(Object o) {
		return this == o || o instanceof EncodedDesign other && Arrays.equals(bytes, other.bytes);
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	@Override
	public String toString() {
		return "EncodedDesign[" + bytes.length + " bytes]";
	}
}
