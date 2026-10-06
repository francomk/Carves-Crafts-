package com.studioderiva.carves_and_crafts.worldgen;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.variety.PumpkinVarieties;
import com.studioderiva.carves_and_crafts.variety.PumpkinVariety;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Wild pumpkin patches. Each variety grows in the biomes of its tag
 * (data/carves_and_crafts/tags/worldgen/biome/grows_&lt;variety&gt;.json) through placed_feature/patch_&lt;variety&gt;.json;
 * both are data, so a data pack can move or tune them.
 */
public final class ModWorldgen {
	public static final PumpkinFeature PUMPKIN = Registry.register(BuiltInRegistries.FEATURE, CarvesAndCrafts.id("pumpkin"), new PumpkinFeature());

	private ModWorldgen() {
	}

	public static void init() {
		for (PumpkinVariety variety : PumpkinVarieties.ALL) {
			TagKey<Biome> biomes = TagKey.create(Registries.BIOME, CarvesAndCrafts.id("grows_" + variety.id()));
			ResourceKey<PlacedFeature> patch =
				ResourceKey.create(Registries.PLACED_FEATURE, CarvesAndCrafts.id("patch_" + variety.id()));
			BiomeModifications.addFeature(BiomeSelectors.tag(biomes), GenerationStep.Decoration.VEGETAL_DECORATION, patch);
		}
	}
}
