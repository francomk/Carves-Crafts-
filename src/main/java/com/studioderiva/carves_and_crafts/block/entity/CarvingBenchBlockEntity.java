package com.studioderiva.carves_and_crafts.block.entity;

import com.studioderiva.carves_and_crafts.menu.CarvingBenchMenu;
import com.studioderiva.carves_and_crafts.registry.ModBlockEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Carving bench storage (see {@link BenchSlots}): pumpkin, tools and palette crafting inputs.
 * Keeps its content when the GUI closes (like a furnace).
 * Only one player may use it at a time. Exposes no slots to hoppers.
 */
public class CarvingBenchBlockEntity extends BlockEntity implements WorldlyContainer {
	public static final Component TITLE = Component.translatable("container.carves_and_crafts.carving_bench");
	private static final int[] NO_SLOTS = new int[0];

	private final NonNullList<ItemStack> items = NonNullList.withSize(BenchSlots.COUNT, ItemStack.EMPTY);
	/** Player currently using the bench; not saved, re-validated on every open attempt. */
	private @Nullable UUID user;

	public CarvingBenchBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CARVING_BENCH, pos, state);
	}

	/** True if another player still has this bench open. */
	public boolean isInUseByOther(ServerPlayer player) {
		if (user == null || user.equals(player.getUUID()) || level == null || level.getServer() == null) {
			return false;
		}
		ServerPlayer other = level.getServer().getPlayerList().getPlayer(user);
		boolean stillOpen = other != null && other.containerMenu instanceof CarvingBenchMenu menu && menu.isFor(this);
		if (!stillOpen) {
			user = null;
		}
		return stillOpen;
	}

	public void claim(Player player) {
		user = player.getUUID();
	}

	public void release(Player player) {
		if (player.getUUID().equals(user)) {
			user = null;
		}
	}

	public static boolean isValidPumpkin(ItemStack stack) {
		return BenchSlots.accepts(BenchSlots.PUMPKIN, stack);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items.clear();
		ContainerHelper.loadAllItems(input, items);
	}

	// --- Container ---

	@Override
	public int getContainerSize() {
		return items.size();
	}

	@Override
	public boolean isEmpty() {
		return items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack taken = ContainerHelper.removeItem(items, slot, amount);
		if (!taken.isEmpty()) {
			setChanged();
		}
		return taken;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (slot >= 0 && slot < items.size()) {
			items.set(slot, stack);
			setChanged();
		}
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return BenchSlots.accepts(slot, stack);
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		items.clear();
		setChanged();
	}

	// --- no hopper access ---

	@Override
	public int[] getSlotsForFace(Direction side) {
		return NO_SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
		return false;
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return false;
	}
}
