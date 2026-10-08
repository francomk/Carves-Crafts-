package com.studioderiva.carves_and_crafts.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.studioderiva.carves_and_crafts.design.DesignRefs;
import java.util.function.BiFunction;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Block entity updates: pumpkin items inside block entities go to clients as design references. */
@Mixin(ClientboundBlockEntityDataPacket.class)
abstract class BlockEntityDataPacketMixin {
	@WrapOperation(method = "create(Lnet/minecraft/world/level/block/entity/BlockEntity;Ljava/util/function/BiFunction;)Lnet/minecraft/network/protocol/game/ClientboundBlockEntityDataPacket;",
		at = @At(value = "INVOKE", target = "Ljava/util/function/BiFunction;apply(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
	private static Object carves_and_crafts$syncRefs(BiFunction<Object, Object, Object> function, Object blockEntity, Object registries, Operation<Object> original) {
		return DesignRefs.syncing(() -> original.call(function, blockEntity, registries));
	}
}
