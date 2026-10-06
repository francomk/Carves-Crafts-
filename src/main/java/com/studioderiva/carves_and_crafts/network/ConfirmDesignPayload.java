package com.studioderiva.carves_and_crafts.network;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.design.DesignCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: the player confirms the edited design of the pumpkin in the open carving bench.
 *
 * @param containerId  id of the bench menu the edit belongs to
 * @param originalHash hash of the design the editor started from ("" for a virgin pumpkin); rejects stale edits
 * @param design       encoded edited design
 * @param saveSchematic also store the result in the player's schematic library
 */
public record ConfirmDesignPayload(int containerId, String originalHash, byte[] design, boolean saveSchematic) implements CustomPacketPayload {
	public static final Type<ConfirmDesignPayload> TYPE = new Type<>(CarvesAndCrafts.id("confirm_design"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ConfirmDesignPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, ConfirmDesignPayload::containerId,
		ByteBufCodecs.stringUtf8(64), ConfirmDesignPayload::originalHash,
		ByteBufCodecs.byteArray(DesignCodec.MAX_ENCODED_BYTES), ConfirmDesignPayload::design,
		ByteBufCodecs.BOOL, ConfirmDesignPayload::saveSchematic,
		ConfirmDesignPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
