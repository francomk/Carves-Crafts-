package com.studioderiva.carves_and_crafts.registry;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.block.CarvingBenchBlock;
import com.studioderiva.carves_and_crafts.block.AttachedPumpkinStemBlock;
import com.studioderiva.carves_and_crafts.block.CustomPumpkinBlock;
import com.studioderiva.carves_and_crafts.block.PumpkinStemBlock;
import com.studioderiva.carves_and_crafts.model.PumpkinModel;
import com.studioderiva.carves_and_crafts.model.PumpkinModels;
import com.studioderiva.carves_and_crafts.variety.PumpkinVarieties;
import com.studioderiva.carves_and_crafts.variety.PumpkinVariety;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
	/** One block per pumpkin model, in {@link PumpkinModels#ALL} order. */
	public static final Map<PumpkinModel, Block> PUMPKINS = registerPumpkins();
	public static final Block CLASSIC_PUMPKIN = PUMPKINS.get(PumpkinModels.CLASSIC);

	public static final Block CARVING_BENCH = Blocks.register(
		key("carving_bench"),
		CarvingBenchBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD)
			.strength(2.5F)
			.sound(SoundType.WOOD)
			.noOcclusion() // the model is open and reaches into the next block: don't hide neighbor faces
	);

	/** Stems of each variety, like vanilla's pumpkin stem (properties copied from it). */
	public static final Map<PumpkinVariety, Block> STEMS = registerVarietyBlocks(variety -> Blocks.register(
		key(PumpkinStemBlock.stemId(variety)),
		properties -> new PumpkinStemBlock(variety, properties),
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.PLANT)
			.noCollision()
			.randomTicks()
			.instabreak()
			.sound(SoundType.HARD_CROP)
			.pushReaction(PushReaction.DESTROY)
	));
	public static final Map<PumpkinVariety, Block> ATTACHED_STEMS = registerVarietyBlocks(variety -> Blocks.register(
		key(PumpkinStemBlock.attachedId(variety)),
		properties -> new AttachedPumpkinStemBlock(variety, properties),
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.PLANT)
			.noCollision()
			.instabreak()
			.sound(SoundType.WOOD)
			.pushReaction(PushReaction.DESTROY)
	));

	private ModBlocks() {
	}

	private static Map<PumpkinVariety, Block> registerVarietyBlocks(Function<PumpkinVariety, Block> register) {
		Map<PumpkinVariety, Block> blocks = new LinkedHashMap<>();
		for (PumpkinVariety variety : PumpkinVarieties.ALL) {
			blocks.put(variety, register.apply(variety));
		}
		return Collections.unmodifiableMap(blocks);
	}

	private static Map<PumpkinModel, Block> registerPumpkins() {
		Map<PumpkinModel, Block> blocks = new LinkedHashMap<>();
		for (PumpkinModel model : PumpkinModels.ALL) {
			blocks.put(model, Blocks.register(
				key(model.id()),
				properties -> new CustomPumpkinBlock(model, properties),
				BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_ORANGE)
					.strength(1.0F)
					.sound(SoundType.WOOD)
					// holes show what is behind the pumpkin, and the renderer needs real light inside the block
					.noOcclusion()
					.lightLevel(state -> state.getValue(CustomPumpkinBlock.LIGHT).lightLevel())
					.pushReaction(PushReaction.DESTROY)
			));
		}
		return Collections.unmodifiableMap(blocks);
	}

	private static ResourceKey<Block> key(String path) {
		return ResourceKey.create(Registries.BLOCK, CarvesAndCrafts.id(path));
	}

	public static void init() {
	}
}
