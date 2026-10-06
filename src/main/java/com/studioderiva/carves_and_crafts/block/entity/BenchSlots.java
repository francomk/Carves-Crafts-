package com.studioderiva.carves_and_crafts.block.entity;

import com.studioderiva.carves_and_crafts.model.PumpkinModels;
import com.studioderiva.carves_and_crafts.registry.ModItems;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;

/** Slot layout of the carving bench storage and which items each slot accepts. */
public final class BenchSlots {
	public static final int PUMPKIN = 0;
	public static final int KNIFE = 1;
	public static final int BRUSH = 2;
	/** Palette waiting to recharge the brush. */
	public static final int PALETTE = 3;
	/** One slot per dye color, in {@link DyeColor} order, for crafting a palette. */
	public static final int FIRST_DYE = 4;
	public static final int DYE_COUNT = 16;
	public static final int PLANK = FIRST_DYE + DYE_COUNT;
	public static final int COUNT = PLANK + 1;

	private BenchSlots() {
	}

	public static boolean accepts(int slot, ItemStack stack) {
		if (slot >= FIRST_DYE && slot < PLANK) {
			return stack.is(DyeItem.byColor(DyeColor.byId(slot - FIRST_DYE)));
		}
		return switch (slot) {
			case PUMPKIN -> PumpkinModels.of(stack) != null;
			case KNIFE -> stack.is(ModItems.CARVING_KNIFE);
			case BRUSH -> stack.is(ModItems.PAINTBRUSH);
			case PALETTE -> stack.is(ModItems.COLOR_PALETTE);
			case PLANK -> stack.is(ItemTags.PLANKS);
			default -> false;
		};
	}

	/** Tool and pumpkin slots hold a single item; crafting inputs stack normally. */
	public static int maxStackSize(int slot) {
		return slot == PUMPKIN || slot == KNIFE || slot == BRUSH ? 1 : 64;
	}
}
