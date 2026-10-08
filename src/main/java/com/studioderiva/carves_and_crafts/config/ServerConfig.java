package com.studioderiva.carves_and_crafts.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Server-side options, read from config/carves_and_crafts.json (created with defaults on first start).
 * Limits are off by default; server owners can turn them on against abuse.
 */
public final class ServerConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ServerConfig instance = new ServerConfig();

	/** Max schematics per player; 0 = unlimited. */
	public int maxSchematicsPerPlayer = 0;
	/** Max schematic library actions per player per minute; 0 = unlimited. */
	public int schematicActionsPerMinute = 0;
	/** Whether players may import .pumpkin files from their computer into their library. */
	public boolean allowSchematicImport = true;
	/**
	 * Max distinct colors in a new design (carved, imported or copied onto a pumpkin), 1 to {@link #MAX_COLORS_LIMIT};
	 * 0 = no limit. Keeps every design small. Existing pumpkins and schematics are left as they are.
	 * Set in game with /carves maxcolors.
	 */
	public int maxColorsPerDesign = 0;

	public static final int MAX_COLORS_LIMIT = 254;

	/** Clamped because the file can be edited by hand: past 254 colors designs switch to two-byte indices. */
	public int colorLimit() {
		return maxColorsPerDesign > 0 ? Math.min(maxColorsPerDesign, MAX_COLORS_LIMIT) : 0;
	}

	public static ServerConfig get() {
		return instance;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(CarvesAndCrafts.MOD_ID + ".json");
	}

	/** Writes the current options, e.g. after changing them in the config screen. */
	public static void save() {
		Path file = file();
		try (Writer writer = Files.newBufferedWriter(file)) {
			GSON.toJson(instance, writer);
		} catch (IOException e) {
			CarvesAndCrafts.LOGGER.error("Could not write {}", file, e);
		}
	}

	public static void load() {
		Path file = file();
		try {
			if (Files.exists(file)) {
				try (Reader reader = Files.newBufferedReader(file)) {
					ServerConfig loaded = GSON.fromJson(reader, ServerConfig.class);
					if (loaded != null) {
						instance = loaded;
					}
				}
			}
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(instance, writer); // writes defaults and any newly added options
			}
		} catch (IOException | JsonParseException e) {
			CarvesAndCrafts.LOGGER.error("Could not read {}, using defaults", file, e);
		}
	}
}
