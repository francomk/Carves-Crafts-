package com.studioderiva.carves_and_crafts.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.studioderiva.carves_and_crafts.design.DesignRefs;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Every outgoing packet: pumpkin items serialized as NBT while encoding (e.g. the item hover of a death message,
 * which carries whole items including shulker and bundle contents) go out as design references.
 */
@Mixin(PacketEncoder.class)
abstract class PacketEncoderMixin {
	@WrapOperation(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/StreamCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V"))
	private void carves_and_crafts$syncRefs(StreamCodec<Object, Object> codec, Object buffer, Object packet, Operation<Void> original) {
		DesignRefs.syncing(() -> original.call(codec, buffer, packet));
	}
}
