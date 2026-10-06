package com.studioderiva.carves_and_crafts.variety;

import com.studioderiva.carves_and_crafts.model.PumpkinModel;
import com.studioderiva.carves_and_crafts.model.PumpkinModels;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The three seeds. Each model belongs to exactly one variety. */
public final class PumpkinVarieties {
	public static final PumpkinVariety FIELD = new PumpkinVariety("field_pumpkin",
		List.of(PumpkinModels.CLASSIC, PumpkinModels.MINI_YELLOW, PumpkinModels.WARTY));
	public static final PumpkinVariety HEIRLOOM = new PumpkinVariety("heirloom_pumpkin",
		List.of(PumpkinModels.WHITE, PumpkinModels.BLUE, PumpkinModels.CINDERELLA));
	public static final PumpkinVariety WINTER = new PumpkinVariety("winter_squash",
		List.of(PumpkinModels.BUTTERNUT, PumpkinModels.KABOCHA, PumpkinModels.TURBAN));

	public static final List<PumpkinVariety> ALL = List.of(FIELD, HEIRLOOM, WINTER);

	private PumpkinVarieties() {
	}

	public static @Nullable PumpkinVariety byId(String id) {
		for (PumpkinVariety variety : ALL) {
			if (variety.id().equals(id)) {
				return variety;
			}
		}
		return null;
	}

	public static @Nullable PumpkinVariety of(PumpkinModel model) {
		for (PumpkinVariety variety : ALL) {
			if (variety.grows(model)) {
				return variety;
			}
		}
		return null;
	}
}
