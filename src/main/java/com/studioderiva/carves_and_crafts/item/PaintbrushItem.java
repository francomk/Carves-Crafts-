package com.studioderiva.carves_and_crafts.item;

import com.studioderiva.carves_and_crafts.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Paintbrush: holds paint charge (1 per painted pixel). Never breaks; an empty brush can't paint until it is
 * recharged with a color palette at the carving bench.
 *
 * <p>Charge is a custom component rather than vanilla damage, so crafting-grid repair, anvils and Mending
 * can't refill it for free.
 */
public class PaintbrushItem extends Item {
	private static final int BAR_COLOR = 0xFF7B1FA2;

	public PaintbrushItem(Properties properties) {
		super(properties);
	}

	public static int charge(ItemStack stack) {
		return Mth.clamp(stack.getOrDefault(ModComponents.PAINT_CHARGE, 0), 0, ToolBalance.BRUSH_CAPACITY);
	}

	public static void setCharge(ItemStack stack, int charge) {
		stack.set(ModComponents.PAINT_CHARGE, Mth.clamp(charge, 0, ToolBalance.BRUSH_CAPACITY));
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0F * charge(stack) / ToolBalance.BRUSH_CAPACITY);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return BAR_COLOR;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		int charge = charge(stack);
		tooltip.accept(charge == 0
			? Component.translatable("item.carves_and_crafts.paintbrush.empty").withStyle(ChatFormatting.GRAY)
			: Component.translatable("item.carves_and_crafts.paintbrush.charge", charge, ToolBalance.BRUSH_CAPACITY).withStyle(ChatFormatting.GRAY));
	}
}
