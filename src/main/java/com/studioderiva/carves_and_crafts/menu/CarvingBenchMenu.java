package com.studioderiva.carves_and_crafts.menu;

import com.studioderiva.carves_and_crafts.block.entity.BenchSlots;
import com.studioderiva.carves_and_crafts.block.entity.CarvingBenchBlockEntity;
import com.studioderiva.carves_and_crafts.config.ServerConfig;
import com.studioderiva.carves_and_crafts.design.EncodedDesign;
import com.studioderiva.carves_and_crafts.item.PaintbrushItem;
import com.studioderiva.carves_and_crafts.item.ToolBalance;
import com.studioderiva.carves_and_crafts.network.DesignDataPayload;
import com.studioderiva.carves_and_crafts.network.OutboundBudget;
import com.studioderiva.carves_and_crafts.registry.ModComponents;
import com.studioderiva.carves_and_crafts.registry.ModItems;
import com.studioderiva.carves_and_crafts.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Carving bench menu with three tabs: CARVE (pumpkin, knife, brush, palette for recharging),
 * PALETTE (16 dyes + 1 plank → one color palette) and SCHEMATICS (library; the pumpkin slot is shown again there
 * and the player inventory is hidden to make room for the list).
 */
public class CarvingBenchMenu extends AbstractContainerMenu {
	public static final int TAB_CARVE = 0;
	public static final int TAB_PALETTE = 1;
	public static final int TAB_SCHEMATICS = 2;
	public static final int BUTTON_TAB_CARVE = 0;
	public static final int BUTTON_TAB_PALETTE = 1;
	public static final int BUTTON_RECHARGE = 2;
	public static final int BUTTON_TAB_SCHEMATICS = 3;

	public static final int RESULT_SLOT = BenchSlots.COUNT;
	/** Second view of the pumpkin slot, placed for the schematics tab. */
	public static final int SCHEMATIC_PUMPKIN_SLOT = RESULT_SLOT + 1;
	private static final int PLAYER_INV_START = SCHEMATIC_PUMPKIN_SLOT + 1;
	private static final int PLAYER_INV_END = PLAYER_INV_START + 36;

	private final Container container;
	private final ResultContainer result = new ResultContainer();
	private final DataSlot tab = DataSlot.standalone();
	/** Server's max colors per design (0 = no limit), shown in the editor. */
	private final DataSlot maxColors = DataSlot.standalone();
	private final Player player;
	/** Server side: hash of the pumpkin design last sent to the client, which only holds a reference to it. */
	private @Nullable String sentDesignHash;
	/** Server side only. */
	private final @Nullable CarvingBenchBlockEntity bench;

	/** Client constructor. */
	public CarvingBenchMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(BenchSlots.COUNT), null);
	}

	/** Server constructor. */
	public CarvingBenchMenu(int containerId, Inventory inventory, CarvingBenchBlockEntity bench) {
		this(containerId, inventory, bench, bench);
	}

	private CarvingBenchMenu(int containerId, Inventory inventory, Container container, @Nullable CarvingBenchBlockEntity bench) {
		super(ModMenus.CARVING_BENCH, containerId);
		this.container = container;
		this.bench = bench;
		this.player = inventory.player;
		addDataSlot(tab);
		addDataSlot(maxColors);

		// carve tab
		addSlot(new BenchSlot(container, BenchSlots.PUMPKIN, 80, 35, TAB_CARVE));
		addSlot(new BenchSlot(container, BenchSlots.KNIFE, 8, 20, TAB_CARVE));
		addSlot(new BenchSlot(container, BenchSlots.BRUSH, 8, 44, TAB_CARVE));
		addSlot(new BenchSlot(container, BenchSlots.PALETTE, 30, 44, TAB_CARVE));
		// palette tab
		for (int i = 0; i < BenchSlots.DYE_COUNT; i++) {
			addSlot(new BenchSlot(container, BenchSlots.FIRST_DYE + i, 8 + (i % 8) * 18, 17 + (i / 8) * 18, TAB_PALETTE));
		}
		addSlot(new BenchSlot(container, BenchSlots.PLANK, 8, 55, TAB_PALETTE));
		addSlot(new PaletteResultSlot(152, 55));
		// schematics tab
		addSlot(new BenchSlot(container, BenchSlots.PUMPKIN, 8, 18, TAB_SCHEMATICS));

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new PlayerSlot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new PlayerSlot(inventory, col, 8 + col * 18, 142));
		}
	}

	public boolean isFor(CarvingBenchBlockEntity blockEntity) {
		return bench == blockEntity;
	}

	public int tab() {
		return tab.get();
	}

	public int maxColors() {
		return maxColors.get();
	}

	public ItemStack getPumpkin() {
		return getSlot(BenchSlots.PUMPKIN).getItem();
	}

	public ItemStack getKnife() {
		return getSlot(BenchSlots.KNIFE).getItem();
	}

	public ItemStack getBrush() {
		return getSlot(BenchSlots.BRUSH).getItem();
	}

	/** Server side: replaces the pumpkin in the bench and syncs it. */
	public void setPumpkin(ItemStack stack) {
		getSlot(BenchSlots.PUMPKIN).set(stack);
		broadcastChanges();
	}

	/** True when the brush can be recharged right now. */
	public boolean canRecharge() {
		ItemStack brush = getBrush();
		return brush.is(ModItems.PAINTBRUSH)
			&& PaintbrushItem.charge(brush) < ToolBalance.BRUSH_CAPACITY
			&& getSlot(BenchSlots.PALETTE).getItem().is(ModItems.COLOR_PALETTE);
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		switch (id) {
			case BUTTON_TAB_CARVE -> tab.set(TAB_CARVE);
			case BUTTON_TAB_PALETTE -> tab.set(TAB_PALETTE);
			case BUTTON_TAB_SCHEMATICS -> tab.set(TAB_SCHEMATICS);
			case BUTTON_RECHARGE -> {
				if (!canRecharge()) {
					return false;
				}
				Slot paletteSlot = getSlot(BenchSlots.PALETTE);
				paletteSlot.remove(1);
				ItemStack brush = getBrush().copy();
				PaintbrushItem.setCharge(brush, ToolBalance.BRUSH_CAPACITY);
				getSlot(BenchSlots.BRUSH).set(brush);
			}
			default -> {
				return false;
			}
		}
		broadcastChanges();
		return true;
	}

	/** All 16 dyes and a plank present. */
	private boolean canCraftPalette() {
		for (int i = BenchSlots.FIRST_DYE; i <= BenchSlots.PLANK; i++) {
			if (getSlot(i).getItem().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private void consumePaletteInputs() {
		for (int i = BenchSlots.FIRST_DYE; i <= BenchSlots.PLANK; i++) {
			getSlot(i).remove(1);
		}
	}

	@Override
	public void broadcastChanges() {
		// the bench container doesn't notify menus, so refresh the craft preview every sync
		ItemStack preview = canCraftPalette() ? new ItemStack(ModItems.COLOR_PALETTE) : ItemStack.EMPTY;
		if (!ItemStack.matches(preview, result.getItem(0))) {
			result.setItem(0, preview);
		}
		if (bench != null) {
			maxColors.set(ServerConfig.get().colorLimit());
		}
		super.broadcastChanges();
		if (player instanceof ServerPlayer serverPlayer) {
			sendPumpkinDesign(serverPlayer);
		}
	}

	/**
	 * The editor needs the full design of the pumpkin in the bench, and the client's copy of the item only holds a
	 * reference. Sent once per design, within the player's send budget (retried on the next sync if over it).
	 */
	private void sendPumpkinDesign(ServerPlayer serverPlayer) {
		EncodedDesign design = getPumpkin().get(ModComponents.DESIGN);
		if (design == null || !design.isComplete()) {
			sentDesignHash = null;
			return;
		}
		if (design.hash().equals(sentDesignHash) || !OutboundBudget.tryConsume(serverPlayer, design.size())) {
			return;
		}
		sentDesignHash = design.hash();
		ServerPlayNetworking.send(serverPlayer, new DesignDataPayload(design));
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (bench != null) {
			bench.release(player);
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem() || !slot.isActive()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index == RESULT_SLOT) {
			if (!slot.mayPickup(player) || !moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_END, true)) {
				return ItemStack.EMPTY;
			}
			slot.onTake(player, original);
			return ItemStack.EMPTY; // one palette per shift-click
		}
		if (index < BenchSlots.COUNT || index == SCHEMATIC_PUMPKIN_SLOT) {
			if (!moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveIntoBench(stack)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}

	/** Moves into the first active bench slot that accepts the item. */
	private boolean moveIntoBench(ItemStack stack) {
		for (int i = 0; i < BenchSlots.COUNT; i++) {
			Slot target = getSlot(i);
			if (target.isActive() && target.mayPlace(stack) && moveItemStackTo(stack, i, i + 1, false)) {
				return true;
			}
		}
		return false;
	}

	private class BenchSlot extends Slot {
		private final int slotTab;

		BenchSlot(Container container, int index, int x, int y, int slotTab) {
			super(container, index, x, y);
			this.slotTab = slotTab;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return BenchSlots.accepts(getContainerSlot(), stack);
		}

		@Override
		public int getMaxStackSize() {
			return BenchSlots.maxStackSize(getContainerSlot());
		}

		@Override
		public boolean isActive() {
			return tab.get() == slotTab;
		}
	}

	private class PlayerSlot extends Slot {
		PlayerSlot(Inventory inventory, int index, int x, int y) {
			super(inventory, index, x, y);
		}

		@Override
		public boolean isActive() {
			return tab.get() != TAB_SCHEMATICS;
		}
	}

	private class PaletteResultSlot extends Slot {
		PaletteResultSlot(int x, int y) {
			super(result, 0, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		/** Re-checked at take time: inputs may have been removed since the preview was computed. */
		@Override
		public boolean mayPickup(Player player) {
			return canCraftPalette();
		}

		@Override
		public void onTake(Player player, ItemStack stack) {
			consumePaletteInputs();
			super.onTake(player, stack);
		}

		@Override
		public boolean isActive() {
			return tab.get() == TAB_PALETTE;
		}
	}
}
