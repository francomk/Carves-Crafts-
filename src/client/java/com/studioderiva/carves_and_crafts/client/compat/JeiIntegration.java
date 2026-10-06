package com.studioderiva.carves_and_crafts.client.compat;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.registry.ModBlocks;
import com.studioderiva.carves_and_crafts.registry.ModItems;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import org.jspecify.annotations.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * JEI entry point (only loaded when JEI is installed): the color palette recipe of the carving bench's Palette tab
 * (it isn't a crafting table recipe, so JEI can't find it on its own) and info pages for the mod's items.
 */
@JeiPlugin
public class JeiIntegration implements IModPlugin {
	/** The palette recipe has no data: it is always the 16 dyes + 1 plank. */
	public record PaletteRecipe() {
	}

	public static final IRecipeType<PaletteRecipe> PALETTE = IRecipeType.create(CarvesAndCrafts.id("palette"), PaletteRecipe.class);
	private static @Nullable IJeiRuntime runtime;

	/** JEI's runtime while a world is open (used by the dev scenarios). */
	public static @Nullable IJeiRuntime runtime() {
		return runtime;
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
		runtime = jeiRuntime;
	}

	@Override
	public void onRuntimeUnavailable() {
		runtime = null;
	}

	@Override
	public Identifier getPluginUid() {
		return CarvesAndCrafts.id("jei");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(new PaletteCategory(registration.getJeiHelpers().getGuiHelper()));
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		registration.addRecipes(PALETTE, List.of(new PaletteRecipe()));
		info(registration, ModItems.CARVING_BENCH, "carving_bench");
		info(registration, ModItems.CARVING_KNIFE, "carving_knife");
		info(registration, ModItems.PAINTBRUSH, "paintbrush");
		info(registration, ModItems.COLOR_PALETTE, "color_palette");
		ModBlocks.PUMPKINS.values().forEach(block -> info(registration, block.asItem(), "pumpkin"));
		ModItems.SEEDS.values().forEach(seeds -> info(registration, seeds, "seeds"));
	}

	private static void info(IRecipeRegistration registration, Item item, String key) {
		registration.addIngredientInfo(item, Component.translatable("jei.carves_and_crafts.info." + key));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addCraftingStation(PALETTE, ModItems.CARVING_BENCH);
	}

	/** Same layout as the bench's Palette tab: two rows of dyes, the plank below, then the palette. */
	private static final class PaletteCategory extends AbstractRecipeCategory<PaletteRecipe> {
		PaletteCategory(IGuiHelper gui) {
			super(PALETTE, Component.translatable("jei.carves_and_crafts.palette"), gui.createDrawableItemLike(ModItems.COLOR_PALETTE),
				8 * 18, 2 * 18 + 22);
		}

		@Override
		public void setRecipe(IRecipeLayoutBuilder builder, PaletteRecipe recipe, IFocusGroup focuses) {
			for (DyeColor color : DyeColor.values()) {
				int i = color.getId();
				builder.addInputSlot((i % 8) * 18, (i / 8) * 18).setStandardSlotBackground().add(DyeItem.byColor(color));
			}
			builder.addInputSlot(0, 40).setStandardSlotBackground()
				.add(Ingredient.of(BuiltInRegistries.ITEM.getOrThrow(ItemTags.PLANKS)));
			builder.addOutputSlot(8 * 18 - 18, 40).setOutputSlotBackground().add(new ItemStack(ModItems.COLOR_PALETTE));
		}

		@Override
		public void createRecipeExtras(IRecipeExtrasBuilder builder, PaletteRecipe recipe, IFocusGroup focuses) {
			builder.addRecipeArrowWidget().setPosition(58, 40);
		}
	}
}
