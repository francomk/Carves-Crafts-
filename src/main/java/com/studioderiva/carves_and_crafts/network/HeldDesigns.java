package com.studioderiva.carves_and_crafts.network;

import com.studioderiva.carves_and_crafts.design.DesignRefs;
import com.studioderiva.carves_and_crafts.design.EncodedDesign;
import com.studioderiva.carves_and_crafts.registry.ModComponents;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Keeps the designs in players' inventories known to {@link DesignRefs}, so that moving an old pumpkin around the
 * creative inventory never hits an expired reference. Server thread only.
 */
public final class HeldDesigns {
	/** Shulker box in a bundle in a shulker box...: deep enough for anything vanilla allows. */
	private static final int MAX_DEPTH = 4;

	private HeldDesigns() {
	}

	public static void rememberOnlinePlayers(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			rememberInventory(player);
		}
	}

	public static void rememberInventory(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			remember(inventory.getItem(i), 0);
		}
		remember(player.containerMenu.getCarried(), 0);
	}

	private static void remember(ItemStack stack, int depth) {
		if (stack.isEmpty() || depth > MAX_DEPTH) {
			return;
		}
		EncodedDesign design = stack.get(ModComponents.DESIGN);
		if (design != null) {
			DesignRefs.remember(design);
		}
		ItemContainerContents container = stack.get(DataComponents.CONTAINER);
		if (container != null) {
			for (ItemStack inner : container.nonEmptyItems()) {
				remember(inner, depth + 1);
			}
		}
		BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
		if (bundle != null) {
			for (ItemStack inner : bundle.items()) {
				remember(inner, depth + 1);
			}
		}
	}

	/** Turns the references in {@code stack} (at any depth) into designs again, as far as they are known now. */
	public static Optional<ItemStack> resolve(ItemStack stack, RegistryAccess registries) {
		var ops = registries.createSerializationContext(NbtOps.INSTANCE);
		return ItemStack.CODEC.encodeStart(ops, stack).flatMap(tag -> ItemStack.CODEC.parse(ops, tag)).result();
	}
}
