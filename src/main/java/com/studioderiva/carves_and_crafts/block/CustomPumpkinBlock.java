package com.studioderiva.carves_and_crafts.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.studioderiva.carves_and_crafts.block.entity.CustomPumpkinBlockEntity;
import com.studioderiva.carves_and_crafts.geometry.PumpkinGeometry;
import com.studioderiva.carves_and_crafts.model.PumpkinModel;
import com.studioderiva.carves_and_crafts.model.PumpkinModels;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Carvable pumpkin. Faces one of 8 directions (cardinals + diagonals).
 * Rotation 0 faces south, increasing clockwise in 45° steps (same convention as banners, at half resolution).
 */
public class CustomPumpkinBlock extends BaseEntityBlock {
	public static final MapCodec<CustomPumpkinBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Codec.STRING.comapFlatMap(
			id -> Optional.ofNullable(PumpkinModels.byId(id)).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown pumpkin model " + id)),
			PumpkinModel::id
		).fieldOf("model").forGetter(CustomPumpkinBlock::model),
		propertiesCodec()
	).apply(i, CustomPumpkinBlock::new));
	public static final int ROTATIONS = 8;
	public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, ROTATIONS - 1);
	/** What lights the pumpkin from inside; drives the world light level. The exact item is kept by the block entity. */
	public static final EnumProperty<LightSource> LIGHT = EnumProperty.create("light", LightSource.class);

	private final PumpkinModel model;
	/** Hitbox per rotation; diagonals reuse the previous cardinal one (a hitbox can't be turned 45°). */
	private final VoxelShape[] shapes = new VoxelShape[ROTATIONS];

	public CustomPumpkinBlock(PumpkinModel model, Properties properties) {
		super(properties);
		this.model = model;
		for (int rotation = 0; rotation < ROTATIONS; rotation += 2) {
			shapes[rotation] = hitbox(model, rotation);
			shapes[rotation + 1] = shapes[rotation];
		}
		registerDefaultState(stateDefinition.any().setValue(ROTATION, 0).setValue(LIGHT, LightSource.NONE));
	}

	/**
	 * Union of the model's unrotated boxes (body, base, caps, stem, warts...), turned like the renderer turns the
	 * model for a cardinal rotation and kept inside the block.
	 */
	private static VoxelShape hitbox(PumpkinModel model, int rotation) {
		// same turn as CustomPumpkinRenderer: -(rotation * 45 + 180) degrees around the block's vertical axis
		int quarterTurns = Math.floorMod(-(rotation * 45 + 180) / 90, 4);
		VoxelShape result = Shapes.empty();
		for (PumpkinGeometry.Element e : model.geometry().axisAlignedElements()) {
			double[] min = {e.min(0), e.min(1), e.min(2)};
			double[] max = {e.max(0), e.max(1), e.max(2)};
			for (int q = 0; q < quarterTurns; q++) {
				// +90° around Y through the block center: (x, z) -> (z, 16 - x)
				double x0 = min[0];
				double x1 = max[0];
				min[0] = min[2];
				max[0] = max[2];
				min[2] = 16 - x1;
				max[2] = 16 - x0;
			}
			for (int i = 0; i < 3; i++) {
				min[i] = Mth.clamp(min[i], 0, 16);
				max[i] = Mth.clamp(max[i], 0, 16);
			}
			if (min[0] < max[0] && min[1] < max[1] && min[2] < max[2]) {
				result = Shapes.or(result, Block.box(min[0], min[1], min[2], max[0], max[1], max[2]));
			}
		}
		return result.optimize();
	}

	public PumpkinModel model() {
		return model;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes[state.getValue(ROTATION)];
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	/** Drawn entirely by the block entity renderer; the JSON model only provides the particle texture. */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// face the player
		int segment = Mth.floor((context.getRotation() + 180.0F) * ROTATIONS / 360.0F + 0.5F) & (ROTATIONS - 1);
		return defaultBlockState().setValue(ROTATION, segment);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ROTATION, LIGHT);
	}

	// Like vanilla banners, mirroring a structure doesn't mirror the design itself.
	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(ROTATION, rotation.rotate(state.getValue(ROTATION), ROTATIONS));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.setValue(ROTATION, mirror.mirror(state.getValue(ROTATION), ROTATIONS));
	}

	/**
	 * Right click with a torch, soul torch, redstone torch or candle puts it inside: the pumpkin becomes a lantern.
	 * Once lit it stays lit (more sources are refused); the source only comes back out when the pumpkin is broken.
	 * Sneaking with the item skips this (vanilla), so a torch can still be placed on the pumpkin's side.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		LightSource source = LightSource.of(stack);
		if (source == LightSource.NONE || !(level.getBlockEntity(pos) instanceof CustomPumpkinBlockEntity pumpkin)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (state.getValue(LIGHT) != LightSource.NONE) {
			return InteractionResult.CONSUME; // already lit: don't fall through to placing the torch on its side
		}
		if (!level.isClientSide()) {
			pumpkin.setLightItem(stack.copyWithCount(1));
			level.setBlock(pos, state.setValue(LIGHT, source), Block.UPDATE_ALL);
			if (stack.getItem() instanceof BlockItem item) {
				SoundType sound = item.getBlock().defaultBlockState().getSoundType();
				level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
			}
			level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
			stack.consume(1, player); // no-op in creative
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CustomPumpkinBlockEntity(pos, state);
	}
}
