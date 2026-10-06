package com.studioderiva.carves_and_crafts.variety;

import com.studioderiva.carves_and_crafts.model.PumpkinModel;
import java.util.List;
import net.minecraft.util.RandomSource;

/**
 * A pumpkin variety: one kind of seed, whose stems (and wild patches) grow one of several models.
 * Which model comes out is a weighted draw; weights come from data packs (see {@link VarietyWeights}).
 *
 * @param models models this variety can grow, in the order of its default (equal) weights
 */
public record PumpkinVariety(String id, List<PumpkinModel> models) {
	public PumpkinVariety {
		models = List.copyOf(models);
		if (models.isEmpty()) {
			throw new IllegalArgumentException("Variety " + id + " grows nothing");
		}
	}

	/** Model for a newly grown pumpkin. */
	public PumpkinModel pick(RandomSource random) {
		return VarietyWeights.table(this).pick(random.nextInt(Integer.MAX_VALUE));
	}

	public boolean grows(PumpkinModel model) {
		return models.contains(model);
	}
}
