package com.studioderiva.carves_and_crafts.registry;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.block.entity.CarvingBenchBlockEntity;
import com.studioderiva.carves_and_crafts.block.entity.CustomPumpkinBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<CustomPumpkinBlockEntity> CUSTOM_PUMPKIN = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		CarvesAndCrafts.id("custom_pumpkin"),
		FabricBlockEntityTypeBuilder.create(CustomPumpkinBlockEntity::new, ModBlocks.PUMPKINS.values().toArray(Block[]::new)).build()
	);

	public static final BlockEntityType<CarvingBenchBlockEntity> CARVING_BENCH = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		CarvesAndCrafts.id("carving_bench"),
		FabricBlockEntityTypeBuilder.create(CarvingBenchBlockEntity::new, ModBlocks.CARVING_BENCH).build()
	);

	private ModBlockEntities() {
	}

	public static void init() {
	}
}
