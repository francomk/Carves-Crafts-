package com.studioderiva.carves_and_crafts.network;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.design.EncodedDesign;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: one design asked for with {@link RequestDesignsPayload}; the client files it under its hash. */
public record DesignDataPayload(EncodedDesign design) implements CustomPacketPayload {
	public static final Type<DesignDataPayload> TYPE = new Type<>(CarvesAndCrafts.id("design_data"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DesignDataPayload> STREAM_CODEC =
		EncodedDesign.STREAM_CODEC.<RegistryFriendlyByteBuf>cast().map(DesignDataPayload::new, DesignDataPayload::design);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
