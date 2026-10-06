package com.studioderiva.carves_and_crafts.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.studioderiva.carves_and_crafts.block.CustomPumpkinBlock;
import com.studioderiva.carves_and_crafts.registry.ModBlocks;
import com.studioderiva.carves_and_crafts.variety.PumpkinVarieties;
import com.studioderiva.carves_and_crafts.variety.PumpkinVariety;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Places one blank pumpkin of a variety (weighted model, random direction). Wrapped in a vanilla random_patch in the
 * data pack (worldgen/configured_feature/patch_&lt;variety&gt;.json), like vanilla pumpkin patches.
 */
public class PumpkinFeature extends Feature<PumpkinFeature.Config> {
	public record Config(String variety) implements FeatureConfiguration {
		public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("variety").forGetter(Config::variety)
		).apply(i, Config::new));
	}

	public PumpkinFeature() {
		super(Config.CODEC);
	}

	@Override
	public boolean place(FeaturePlaceContext<Config> context) {
		PumpkinVariety variety = PumpkinVarieties.byId(context.config().variety());
		if (variety == null) {
			return false;
		}
		RandomSource random = context.random();
		Block block = ModBlocks.PUMPKINS.get(variety.pick(random));
		return context.level().setBlock(context.origin(),
			block.defaultBlockState().setValue(CustomPumpkinBlock.ROTATION, random.nextInt(CustomPumpkinBlock.ROTATIONS)), Block.UPDATE_CLIENTS);
	}
}
