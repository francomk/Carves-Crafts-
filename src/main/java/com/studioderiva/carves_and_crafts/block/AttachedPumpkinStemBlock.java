package com.studioderiva.carves_and_crafts.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.studioderiva.carves_and_crafts.registry.ModBlocks;
import com.studioderiva.carves_and_crafts.registry.ModItems;
import com.studioderiva.carves_and_crafts.variety.PumpkinVariety;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Stem bent toward the pumpkin it grew, like vanilla's attached pumpkin stem. A variety grows several models, so
 * (unlike vanilla AttachedStemBlock, which knows one fruit) it stays attached to any pumpkin of its variety and turns
 * back into a fully grown stem when that pumpkin goes away.
 */
public class AttachedPumpkinStemBlock extends VegetationBlock {
	public static final MapCodec<AttachedPumpkinStemBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		PumpkinStemBlock.varietyCodec().fieldOf("variety").forGetter(AttachedPumpkinStemBlock::variety),
		propertiesCodec()
	).apply(i, AttachedPumpkinStemBlock::new));
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	/** Same shapes as vanilla's attached stem. */
	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.boxZ(4.0, 0.0, 10.0, 0.0, 10.0));

	private final PumpkinVariety variety;

	public AttachedPumpkinStemBlock(PumpkinVariety variety, Properties properties) {
		super(properties);
		this.variety = variety;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	public PumpkinVariety variety() {
		return variety;
	}

	@Override
	protected MapCodec<AttachedPumpkinStemBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighborPos, BlockState neighbor, RandomSource random) {
		if (direction == state.getValue(FACING) && !(neighbor.getBlock() instanceof CustomPumpkinBlock pumpkin && variety.grows(pumpkin.model()))) {
			return ModBlocks.STEMS.get(variety).defaultBlockState().setValue(StemBlock.AGE, StemBlock.MAX_AGE);
		}
		return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.is(Blocks.FARMLAND);
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(ModItems.SEEDS.get(variety));
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}
}
