package com.studioderiva.carves_and_crafts.variety;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.model.PumpkinModel;
import com.studioderiva.carves_and_crafts.model.PumpkinModels;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Harvest weights of each variety, from data packs: data/&lt;namespace&gt;/pumpkin_variety/&lt;variety id&gt;.json
 * <pre>
 * { "variants": [ { "model": "classic_pumpkin", "weight": 70 }, ... ] }
 * </pre>
 * Only models of the variety are allowed. A variety without a file grows its models with equal weights.
 */
public final class VarietyWeights extends SimpleJsonResourceReloadListener<VarietyWeights.File> {
	public static final Identifier ID = CarvesAndCrafts.id("pumpkin_variety");

	public record Variant(String model, int weight) {
		static final Codec<Variant> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("model").forGetter(Variant::model),
			ExtraCodecs.NON_NEGATIVE_INT.fieldOf("weight").forGetter(Variant::weight)
		).apply(i, Variant::new));
	}

	public record File(List<Variant> variants) {
		static final Codec<File> CODEC = RecordCodecBuilder.create(i -> i.group(
			Variant.CODEC.listOf().fieldOf("variants").forGetter(File::variants)
		).apply(i, File::new));
	}

	private static volatile Map<String, WeightTable<PumpkinModel>> tables = Map.of();

	public VarietyWeights() {
		super(File.CODEC, FileToIdConverter.json("pumpkin_variety"));
	}

	@Override
	protected void apply(Map<Identifier, File> files, ResourceManager resourceManager, ProfilerFiller profiler) {
		Map<String, WeightTable<PumpkinModel>> loaded = new HashMap<>();
		files.forEach((id, file) -> {
			PumpkinVariety variety = PumpkinVarieties.byId(id.getPath());
			if (variety == null) {
				CarvesAndCrafts.LOGGER.warn("Weights for unknown pumpkin variety {}", id);
				return;
			}
			try {
				loaded.put(variety.id(), table(variety, file));
			} catch (IllegalArgumentException e) {
				CarvesAndCrafts.LOGGER.error("Bad weights for pumpkin variety {}: {}", id, e.getMessage());
			}
		});
		tables = Collections.unmodifiableMap(loaded);
	}

	static WeightTable<PumpkinModel> table(PumpkinVariety variety, File file) {
		List<PumpkinModel> models = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		for (Variant variant : file.variants()) {
			PumpkinModel model = PumpkinModels.byId(variant.model());
			if (model == null || !variety.grows(model)) {
				throw new IllegalArgumentException(variant.model() + " is not a model of " + variety.id());
			}
			models.add(model);
			weights.add(variant.weight());
		}
		return new WeightTable<>(models, weights);
	}

	/** Weights of a variety: from data packs, or equal for every model. */
	static WeightTable<PumpkinModel> table(PumpkinVariety variety) {
		WeightTable<PumpkinModel> table = tables.get(variety.id());
		return table != null ? table : new WeightTable<>(variety.models(), Collections.nCopies(variety.models().size(), 1));
	}
}
