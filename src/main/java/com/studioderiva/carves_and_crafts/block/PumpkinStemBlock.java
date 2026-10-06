package com.studioderiva.carves_and_crafts.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.registry.ModBlocks;
import com.studioderiva.carves_and_crafts.variety.PumpkinVarieties;
import com.studioderiva.carves_and_crafts.variety.PumpkinVariety;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stem of a pumpkin variety. Grows like the vanilla pumpkin stem, but the fruit is a weighted pick among the
 * variety's models, turned to a random direction. Subclassing {@link StemBlock} keeps vanilla behaviour that looks
 * for stems (bone meal, bees, seeds as pick block).
 */
public class PumpkinStemBlock extends StemBlock {
	public static final MapCodec<PumpkinStemBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		varietyCodec().fieldOf("variety").forGetter(PumpkinStemBlock::variety),
		propertiesCodec()
	).apply(i, PumpkinStemBlock::new));

	private final PumpkinVariety variety;

	public PumpkinStemBlock(PumpkinVariety variety, Properties properties) {
		// the vanilla fruit key is only used by StemBlock's own growth, which this class replaces
		super(blockKey(variety.models().get(0).id()), blockKey(attachedId(variety)), itemKey(seedsId(variety)), properties);
		this.variety = variety;
	}

	public PumpkinVariety variety() {
		return variety;
	}

	public static String stemId(PumpkinVariety variety) {
		return variety.id() + "_stem";
	}

	public static String attachedId(PumpkinVariety variety) {
		return "attached_" + variety.id() + "_stem";
	}

	public static String seedsId(PumpkinVariety variety) {
		return variety.id() + "_seeds";
	}

	static Codec<PumpkinVariety> varietyCodec() {
		return Codec.STRING.comapFlatMap(
			id -> Optional.ofNullable(PumpkinVarieties.byId(id)).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown variety " + id)),
			PumpkinVariety::id
		);
	}

	private static ResourceKey<Block> blockKey(String path) {
		return ResourceKey.create(Registries.BLOCK, CarvesAndCrafts.id(path));
	}

	private static ResourceKey<Item> itemKey(String path) {
		return ResourceKey.create(Registries.ITEM, CarvesAndCrafts.id(path));
	}

	@SuppressWarnings("unchecked")
	@Override
	public MapCodec<StemBlock> codec() {
		return (MapCodec<StemBlock>) (MapCodec<?>) CODEC;
	}

	/** Vanilla StemBlock#randomTick with the fruit picked from the variety. */
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (level.getRawBrightness(pos, 0) < 9 || random.nextInt((int) (25.0F / growthSpeed(level, pos)) + 1) != 0) {
			return;
		}
		int age = state.getValue(AGE);
		if (age < MAX_AGE) {
			level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
			return;
		}
		Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		BlockPos fruitPos = pos.relative(direction);
		BlockState ground = level.getBlockState(fruitPos.below());
		if (level.getBlockState(fruitPos).isAir() && (ground.is(Blocks.FARMLAND) || ground.is(BlockTags.DIRT))) {
			Block fruit = ModBlocks.PUMPKINS.get(variety.pick(random));
			level.setBlockAndUpdate(fruitPos, fruit.defaultBlockState().setValue(CustomPumpkinBlock.ROTATION, random.nextInt(CustomPumpkinBlock.ROTATIONS)));
			level.setBlockAndUpdate(pos, ModBlocks.ATTACHED_STEMS.get(variety).defaultBlockState().setValue(AttachedPumpkinStemBlock.FACING, direction));
		}
	}

	/** Same as vanilla CropBlock#getGrowthSpeed (not accessible from here): wet farmland and free rows grow faster. */
	private float growthSpeed(BlockGetter level, BlockPos pos) {
		float speed = 1.0F;
		BlockPos below = pos.below();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				float bonus = 0.0F;
				BlockState soil = level.getBlockState(below.offset(dx, 0, dz));
				if (soil.is(Blocks.FARMLAND)) {
					bonus = soil.getValue(FarmBlock.MOISTURE) > 0 ? 3.0F : 1.0F;
				}
				if (dx != 0 || dz != 0) {
					bonus /= 4.0F;
				}
				speed += bonus;
			}
		}
		BlockPos north = pos.north();
		BlockPos south = pos.south();
		BlockPos west = pos.west();
		BlockPos east = pos.east();
		boolean rowX = level.getBlockState(west).is(this) || level.getBlockState(east).is(this);
		boolean rowZ = level.getBlockState(north).is(this) || level.getBlockState(south).is(this);
		if (rowX && rowZ) {
			speed /= 2.0F;
		} else if (level.getBlockState(west.north()).is(this) || level.getBlockState(east.north()).is(this)
			|| level.getBlockState(east.south()).is(this) || level.getBlockState(west.south()).is(this)) {
			speed /= 2.0F;
		}
		return speed;
	}
}
