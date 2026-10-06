package com.studioderiva.carves_and_crafts.schematic;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.model.PumpkinModel;
import com.studioderiva.carves_and_crafts.model.PumpkinModels;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Ready-made designs from data packs: data/&lt;namespace&gt;/preset/&lt;name&gt;.pumpkin, exported from the game
 * with the bench's Export button. Listed in the bench (Presets view), sorted by file id; players can copy them onto
 * a blank pumpkin or save them to their library. Files for models that aren't installed are skipped.
 */
public final class PumpkinPresets extends SimplePreparableReloadListener<List<Schematic>> {
	public static final Identifier ID = CarvesAndCrafts.id("preset");
	private static final FileToIdConverter FILES = new FileToIdConverter("preset", PumpkinFile.EXTENSION);

	private static volatile List<Schematic> presets = List.of();

	/** Presets as schematics whose id is their index in this list. */
	public static List<Schematic> all() {
		return presets;
	}

	@Override
	protected List<Schematic> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
		Map<Identifier, PumpkinFile> files = new TreeMap<>();
		FILES.listMatchingResources(resourceManager).forEach((path, resource) -> {
			Identifier id = FILES.fileToId(path);
			try {
				PumpkinFile file = read(resource);
				PumpkinModel model = PumpkinModels.byId(file.model());
				if (model == null) {
					CarvesAndCrafts.LOGGER.warn("Preset {} is for a pumpkin model that isn't installed: {}", id, file.model());
				} else if (!model.fits(file.design().decode())) {
					CarvesAndCrafts.LOGGER.warn("Preset {} doesn't fit the {} model", id, file.model());
				} else {
					files.put(id, file);
				}
			} catch (IOException | IllegalArgumentException e) {
				CarvesAndCrafts.LOGGER.error("Unreadable pumpkin preset {}: {}", id, e.getMessage());
			}
		});
		List<Schematic> loaded = new ArrayList<>(files.size());
		files.forEach((id, file) -> {
			String name = file.name().isEmpty() ? Schematic.sanitizeName(id.getPath()) : file.name();
			loaded.add(new Schematic(loaded.size(), name, file.createdAt(), file.model(), file.design(), file.authors()));
		});
		return List.copyOf(loaded);
	}

	private static PumpkinFile read(Resource resource) throws IOException {
		try (InputStream in = resource.open()) {
			byte[] bytes = in.readNBytes(PumpkinFile.MAX_FILE_BYTES + 1);
			return PumpkinFile.read(bytes);
		}
	}

	@Override
	protected void apply(List<Schematic> loaded, ResourceManager resourceManager, ProfilerFiller profiler) {
		presets = loaded;
		CarvesAndCrafts.LOGGER.info("Loaded {} pumpkin presets", loaded.size());
	}
}
