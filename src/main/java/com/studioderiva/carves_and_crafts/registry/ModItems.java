package com.studioderiva.carves_and_crafts.registry;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.block.PumpkinStemBlock;
import com.studioderiva.carves_and_crafts.item.PaintbrushItem;
import com.studioderiva.carves_and_crafts.item.ToolBalance;
import com.studioderiva.carves_and_crafts.variety.PumpkinVarieties;
import com.studioderiva.carves_and_crafts.variety.PumpkinVariety;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModItems {
	/** One item per pumpkin model; CLASSIC_PUMPKIN is the classic one. */
	public static final List<Item> PUMPKINS = ModBlocks.PUMPKINS.values().stream().map(Items::registerBlock).toList();
	public static final Item CLASSIC_PUMPKIN = ModBlocks.CLASSIC_PUMPKIN.asItem();
	public static final Item CARVING_BENCH = Items.registerBlock(ModBlocks.CARVING_BENCH);
	/** Seeds plant the variety's stem; named like vanilla seeds (item.*), not after the stem block. */
	public static final Map<PumpkinVariety, Item> SEEDS = registerSeeds();
	public static final Item CARVING_KNIFE = Items.registerItem(key("carving_knife"), Item::new,
		new Item.Properties().durability(ToolBalance.KNIFE_DURABILITY));
	/** Crafted empty: charge 0 until recharged with a palette. */
	public static final Item PAINTBRUSH = Items.registerItem(key("paintbrush"), PaintbrushItem::new,
		new Item.Properties().stacksTo(1).component(ModComponents.PAINT_CHARGE, 0));
	/** Single-use: fully recharges one paintbrush. */
	public static final Item COLOR_PALETTE = Items.registerItem(key("color_palette"), Item::new,
		new Item.Properties().stacksTo(16));

	private ModItems() {
	}

	private static Map<PumpkinVariety, Item> registerSeeds() {
		Map<PumpkinVariety, Item> seeds = new LinkedHashMap<>();
		for (PumpkinVariety variety : PumpkinVarieties.ALL) {
			Block stem = ModBlocks.STEMS.get(variety);
			seeds.put(variety, Items.registerItem(key(PumpkinStemBlock.seedsId(variety)),
				properties -> new BlockItem(stem, properties), new Item.Properties().useItemDescriptionPrefix()));
		}
		return Collections.unmodifiableMap(seeds);
	}

	private static ResourceKey<Item> key(String path) {
		return ResourceKey.create(Registries.ITEM, CarvesAndCrafts.id(path));
	}

	public static void init() {
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.NATURAL_BLOCKS).register(entries -> {
			PUMPKINS.forEach(entries::accept);
			SEEDS.values().forEach(entries::accept);
		});
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(CARVING_BENCH));
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
			entries.accept(CARVING_KNIFE);
			entries.accept(PAINTBRUSH);
			entries.accept(COLOR_PALETTE);
		});
	}
}
