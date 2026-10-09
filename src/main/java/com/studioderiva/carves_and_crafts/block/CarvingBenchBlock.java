package com.studioderiva.carves_and_crafts.block;

import com.mojang.serialization.MapCodec;
import com.studioderiva.carves_and_crafts.block.entity.CarvingBenchBlockEntity;
import com.studioderiva.carves_and_crafts.menu.CarvingBenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The carving bench: a table two blocks wide, built like a bed. The main part holds the block entity and draws the
 * whole model; the side part (one block clockwise from the facing) only gives the rest of the table its hitbox.
 * Breaking either part removes both; only the main part drops the bench (loot table condition).
 */
public class CarvingBenchBlock extends BaseEntityBlock {
	public static final MapCodec<CarvingBenchBlock> CODEC = simpleCodec(CarvingBenchBlock::new);
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<BenchPart> PART = EnumProperty.create("part", BenchPart.class);
	/** Solid up to the table top (14 px), on both parts. */
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14, 16);
	private static final Component IN_USE = Component.translatable("message.carves_and_crafts.carving_bench.in_use");

	public CarvingBenchBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BenchPart.MAIN));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	/** Direction from this part to the other one. */
	private static Direction towardOtherPart(BlockState state) {
		Direction side = state.getValue(FACING).getClockWise();
		return state.getValue(PART) == BenchPart.MAIN ? side : side.getOpposite();
	}

	/** Position of the main part (the one with the block entity) of the bench at pos. */
	public static BlockPos mainPos(BlockState state, BlockPos pos) {
		return state.getValue(PART) == BenchPart.MAIN ? pos : pos.relative(towardOtherPart(state));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer
			&& level.getBlockEntity(mainPos(state, pos)) instanceof CarvingBenchBlockEntity bench) {
			if (bench.isInUseByOther(serverPlayer)) {
				serverPlayer.displayClientMessage(IN_USE, true);
				return InteractionResult.CONSUME;
			}
			// claimed after opening: opening closes the player's previous menu, which may be this same bench and
			// releases it on close
			if (serverPlayer.openMenu(new SimpleMenuProvider(
				(containerId, inventory, p) -> new CarvingBenchMenu(containerId, inventory, bench),
				CarvingBenchBlockEntity.TITLE
			)).isPresent()) {
				bench.claim(serverPlayer);
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** Needs room for the side part too, like a bed. */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = context.getHorizontalDirection().getOpposite();
		BlockPos side = context.getClickedPos().relative(facing.getClockWise());
		Level level = context.getLevel();
		return level.getBlockState(side).canBeReplaced(context) && level.getWorldBorder().isWithinBounds(side)
			? defaultBlockState().setValue(FACING, facing)
			: null;
	}

	/** Adds the side part whenever a main part appears: placed by a player, by /setblock or by a structure. */
	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!level.isClientSide() && state.getValue(PART) == BenchPart.MAIN && !oldState.is(this)) {
			BlockPos side = pos.relative(towardOtherPart(state));
			if (level.getBlockState(side).canBeReplaced()) {
				level.setBlock(side, state.setValue(PART, BenchPart.SIDE), Block.UPDATE_ALL);
			}
		}
	}

	/** A part whose other half is gone disappears too (dropping the bench if it was the main part). */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighborPos, BlockState neighbor, RandomSource random) {
		if (direction == towardOtherPart(state)) {
			boolean partner = neighbor.is(this) && neighbor.getValue(PART) != state.getValue(PART)
				&& neighbor.getValue(FACING) == state.getValue(FACING);
			return partner ? state : Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
	}

	/** In creative, breaking the side part removes the main part without dropping the bench (as beds do). */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && player.preventsBlockDrops() && state.getValue(PART) == BenchPart.SIDE) {
			BlockPos main = pos.relative(towardOtherPart(state));
			BlockState mainState = level.getBlockState(main);
			if (mainState.is(this) && mainState.getValue(PART) == BenchPart.MAIN) {
				level.setBlock(main, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
				level.levelEvent(player, LevelEvent.PARTICLES_DESTROY_BLOCK, main, Block.getId(mainState));
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, PART);
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
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return state.getValue(PART) == BenchPart.MAIN ? new CarvingBenchBlockEntity(pos, state) : null;
	}
}
