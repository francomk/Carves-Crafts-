package com.studioderiva.carves_and_crafts.model;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.block.CustomPumpkinBlock;
import com.studioderiva.carves_and_crafts.design.CanvasFace;
import com.studioderiva.carves_and_crafts.geometry.PumpkinGeometry;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * All customizable pumpkin models. Adding a model means adding it to zucche/pumpkins.json, running
 * {@code ./gradlew convertPumpkinModels}, adding an entry here and its name in the lang file.
 *
 * <p>Geometry is read from the mod jar (carves_and_crafts/geometry/&lt;id&gt;.json) at startup on both sides:
 * the server needs it for hitboxes and canvas sizes, before any resource or data pack is loaded.
 */
public final class PumpkinModels {
	private static final Identifier FLESH = CarvesAndCrafts.id("block/pumpkin_flesh");
	private static final List<Integer> DENSITIES = List.of(1, 2, 4);
	/** Sides only: caps and stem sit on top of the body. */
	private static final int SIDE_FACES = CanvasFace.NORTH.bit() | CanvasFace.SOUTH.bit() | CanvasFace.EAST.bit() | CanvasFace.WEST.bit();
	private static final int WALL_PX = 1;

	public static final PumpkinModel CLASSIC = model("classic_pumpkin");
	public static final PumpkinModel WHITE = model("white_pumpkin");
	public static final PumpkinModel BLUE = model("blue_pumpkin");
	public static final PumpkinModel BUTTERNUT = model("butternut_squash");
	public static final PumpkinModel CINDERELLA = model("cinderella_pumpkin");
	public static final PumpkinModel KABOCHA = model("kabocha_squash");
	public static final PumpkinModel MINI_YELLOW = model("mini_yellow_pumpkin");
	public static final PumpkinModel TURBAN = model("turban_squash");
	public static final PumpkinModel WARTY = model("warty_pumpkin");

	public static final List<PumpkinModel> ALL = List.of(CLASSIC, WHITE, BLUE, BUTTERNUT, CINDERELLA, KABOCHA, MINI_YELLOW, TURBAN, WARTY);
	private static final Map<String, PumpkinModel> BY_ID = new LinkedHashMap<>();

	static {
		for (PumpkinModel model : ALL) {
			BY_ID.put(model.id(), model);
		}
	}

	private PumpkinModels() {
	}

	private static PumpkinModel model(String id) {
		PumpkinGeometry geometry = loadGeometry(id);
		return new PumpkinModel(id, geometry, geometry.shape(WALL_PX), SIDE_FACES, DENSITIES, FLESH);
	}

	private static PumpkinGeometry loadGeometry(String id) {
		String path = "/" + CarvesAndCrafts.MOD_ID + "/geometry/" + id + ".json";
		try (InputStream in = PumpkinModels.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("Missing pumpkin geometry " + path + " (run ./gradlew convertPumpkinModels)");
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				return PumpkinGeometry.parse(reader);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Can't read pumpkin geometry " + path, e);
		}
	}

	public static @Nullable PumpkinModel byId(String id) {
		return BY_ID.get(id);
	}

	/** Model of a customizable pumpkin item, or null for any other item. */
	public static @Nullable PumpkinModel of(ItemStack stack) {
		return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CustomPumpkinBlock block ? block.model() : null;
	}
}
