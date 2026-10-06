package com.studioderiva.carves_and_crafts.client;

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

/** Client-side options, read from config/carves_and_crafts-client.json (created with defaults on first start). */
public final class ClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ClientConfig instance = new ClientConfig();

	/**
	 * Blocks within which pumpkins are drawn with their design; farther ones show the plain model (cheaper).
	 * Pumpkins are drawn up to the game's render distance either way.
	 */
	public int designRenderDistance = 64;

	public static ClientConfig get() {
		return instance;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(CarvesAndCrafts.MOD_ID + "-client.json");
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
					ClientConfig loaded = GSON.fromJson(reader, ClientConfig.class);
					if (loaded != null) {
						instance = loaded;
					}
				}
			}
			instance.designRenderDistance = Math.max(0, instance.designRenderDistance);
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(instance, writer); // writes defaults and any newly added options
			}
		} catch (IOException | JsonParseException e) {
			CarvesAndCrafts.LOGGER.error("Could not read {}, using defaults", file, e);
		}
	}
}
