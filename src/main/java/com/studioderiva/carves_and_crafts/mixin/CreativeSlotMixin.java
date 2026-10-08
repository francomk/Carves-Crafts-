package com.studioderiva.carves_and_crafts.mixin;

import com.mojang.serialization.Codec;
import com.studioderiva.carves_and_crafts.design.DesignRefs;
import com.studioderiva.carves_and_crafts.network.HeldDesigns;
import java.util.Optional;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.NullOps;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The creative inventory sends whole items back to the server, and clients only hold references to pumpkin designs.
 * References the server still knows were turned back into designs while decoding; the others are looked up again
 * after refreshing the designs in the player's inventory. An item that still holds an unknown one (e.g. from a saved
 * hotbar, long after) is refused and the client's inventory is resynced.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class CreativeSlotMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleSetCreativeModeSlot", cancellable = true, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
		shift = At.Shift.AFTER))
	private void carves_and_crafts$refuseUnknownDesigns(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
		ItemStack stack = packet.itemStack();
		if (stack.isEmpty() || !player.hasInfiniteMaterials()) {
			return;
		}
		if (!holdsUnresolvedRef(stack)) {
			return;
		}
		HeldDesigns.rememberInventory(player);
		Optional<ItemStack> resolved = HeldDesigns.resolve(stack, player.registryAccess());
		if (resolved.isPresent() && !holdsUnresolvedRef(resolved.get())) {
			stack.applyComponents(resolved.get().getComponentsPatch());
			return;
		}
		player.inventoryMenu.sendAllDataToRemote();
		ci.cancel();
	}

	private boolean holdsUnresolvedRef(ItemStack stack) {
		Codec<ItemStack> codec = ItemStack.CODEC;
		var ops = player.registryAccess().createSerializationContext(NullOps.INSTANCE);
		return DesignRefs.writesUnresolvedRef(() -> codec.encodeStart(ops, stack));
	}
}
