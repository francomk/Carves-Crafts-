package com.studioderiva.carves_and_crafts.variety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.studioderiva.carves_and_crafts.model.PumpkinModel;
import com.studioderiva.carves_and_crafts.model.PumpkinModels;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VarietyTest {
	@Test
	void weightTablePicksInProportion() {
		WeightTable<String> table = new WeightTable<>(List.of("a", "b", "c"), List.of(70, 20, 10));
		assertEquals(100, table.totalWeight());
		Map<String, Integer> counts = new HashMap<>();
		for (int roll = 0; roll < 100; roll++) {
			counts.merge(table.pick(roll), 1, Integer::sum);
		}
		assertEquals(Map.of("a", 70, "b", 20, "c", 10), counts);
		assertEquals("a", table.pick(100)); // wraps around
	}

	@Test
	void zeroWeightNeverComesOut() {
		WeightTable<String> table = new WeightTable<>(List.of("a", "b"), List.of(0, 5));
		for (int roll = 0; roll < 50; roll++) {
			assertEquals("b", table.pick(roll));
		}
		assertThrows(IllegalArgumentException.class, () -> new WeightTable<>(List.of("a"), List.of(0)));
		assertThrows(IllegalArgumentException.class, () -> new WeightTable<>(List.of("a"), List.of(-1)));
	}

	@Test
	void everyModelBelongsToExactlyOneVariety() {
		for (PumpkinModel model : PumpkinModels.ALL) {
			long owners = PumpkinVarieties.ALL.stream().filter(v -> v.grows(model)).count();
			assertEquals(1, owners, model.id());
		}
	}

	@Test
	void bundledWeightsMatchTheVarieties() throws Exception {
		for (PumpkinVariety variety : PumpkinVarieties.ALL) {
			String path = "/data/carves_and_crafts/pumpkin_variety/" + variety.id() + ".json";
			try (InputStream in = VarietyTest.class.getResourceAsStream(path)) {
				assertNotNull(in, path);
				VarietyWeights.File file = VarietyWeights.File.CODEC
					.parse(JsonOps.INSTANCE, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
					.getOrThrow();
				WeightTable<PumpkinModel> table = VarietyWeights.table(variety, file);
				assertEquals(100, table.totalWeight(), variety.id());
				assertEquals(variety.models().size(), file.variants().size(), variety.id());
			}
		}
		// a model of another variety is refused
		VarietyWeights.File wrong = new VarietyWeights.File(List.of(new VarietyWeights.Variant("blue_pumpkin", 1)));
		assertThrows(IllegalArgumentException.class, () -> VarietyWeights.table(PumpkinVarieties.FIELD, wrong));
	}
}
