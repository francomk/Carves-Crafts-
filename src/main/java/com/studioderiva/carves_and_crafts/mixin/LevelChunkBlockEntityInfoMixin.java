package com.studioderiva.carves_and_crafts.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.studioderiva.carves_and_crafts.design.DesignRefs;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Chunk data: pumpkin items inside block entities (shelves, campfires...) go to clients as design references. */
@Mixin(targets = "net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData$BlockEntityInfo")
abstract class LevelChunkBlockEntityInfoMixin {
	@WrapOperation(method = "create", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/block/entity/BlockEntity;getUpdateTag(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;"))
	private static CompoundTag carves_and_crafts$syncRefs(BlockEntity blockEntity, HolderLookup.Provider registries, Operation<CompoundTag> original) {
		return DesignRefs.syncing(() -> original.call(blockEntity, registries));
	}
}
