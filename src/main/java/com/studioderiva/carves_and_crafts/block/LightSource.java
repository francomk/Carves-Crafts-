package com.studioderiva.carves_and_crafts.block;

import java.util.Locale;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What lights a pumpkin from inside. Light levels are the vanilla ones of the source block (torch 14, soul torch 10,
 * copper torch 14, lit redstone torch 7, one lit candle 3). The tint only colors the glowing inside; it doesn't change world light.
 * Every candle color counts as the same candle and glows like a torch.
 */
public enum LightSource implements StringRepresentable {
	NONE(0, 0xFFFFFFFF),
	TORCH(14, 0xFFFFD28A),
	SOUL_TORCH(10, 0xFF8AEBFF),
	REDSTONE_TORCH(7, 0xFFFF6A55),
	COPPER_TORCH(14, 0xFF8CFF7A),
	CANDLE(3, 0xFFFFD28A);

	private final int lightLevel;
	private final int tint;

	LightSource(int lightLevel, int tint) {
		this.lightLevel = lightLevel;
		this.tint = tint;
	}

	public int lightLevel() {
		return lightLevel;
	}

	/** ARGB color multiplied onto the glowing inside. */
	public int tint() {
		return tint;
	}

	/** Source an item can be used as, or NONE. */
	public static LightSource of(ItemStack stack) {
		if (stack.is(Items.TORCH)) {
			return TORCH;
		}
		if (stack.is(Items.SOUL_TORCH)) {
			return SOUL_TORCH;
		}
		if (stack.is(Items.REDSTONE_TORCH)) {
			return REDSTONE_TORCH;
		}
		if (stack.is(Items.COPPER_TORCH)) {
			return COPPER_TORCH;
		}
		if (stack.is(ItemTags.CANDLES)) {
			return CANDLE;
		}
		return NONE;
	}

	@Override
	public String getSerializedName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
