package com.studioderiva.carves_and_crafts.network;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: send me the designs of these placed pumpkins. Chunk data only carries each design's hash
 * (see CustomPumpkinBlockEntity#getUpdateTag), so the client asks for the designs it is about to draw.
 */
public record RequestDesignsPayload(List<BlockPos> positions) implements CustomPacketPayload {
	public static final int MAX_POSITIONS = 16;
	public static final Type<RequestDesignsPayload> TYPE = new Type<>(CarvesAndCrafts.id("request_designs"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RequestDesignsPayload> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_POSITIONS)), RequestDesignsPayload::positions,
		RequestDesignsPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
