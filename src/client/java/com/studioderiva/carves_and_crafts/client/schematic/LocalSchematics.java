package com.studioderiva.carves_and_crafts.client.schematic;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.schematic.PumpkinFile;
import com.studioderiva.carves_and_crafts.schematic.Schematic;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.FileUtil;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The player's local schematic library: one {@code .pumpkin} file per design in
 * {@code <game dir>/carves_and_crafts/schematics}. Files can be added, shared and deleted by hand outside the game.
 */
public final class LocalSchematics {
	/** A file in the folder; {@code data} is null if the file can't be read. */
	public record Entry(Path file, long modified, @Nullable PumpkinFile data) {
		public String fileName() {
			return file.getFileName().toString();
		}
	}

	private record Cached(long modified, long size, Entry entry) {
	}

	private static final Map<Path, Cached> CACHE = new HashMap<>();

	private LocalSchematics() {
	}

	public static Path folder() {
		return FabricLoader.getInstance().getGameDir().resolve(CarvesAndCrafts.MOD_ID).resolve("schematics");
	}

	/** Every {@code .pumpkin} file in the folder, newest first. Unchanged files are not read again. */
	public static List<Entry> list() {
		Path folder = folder();
		List<Entry> entries = new ArrayList<>();
		if (!Files.isDirectory(folder)) {
			CACHE.clear();
			return entries;
		}
		try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*" + PumpkinFile.EXTENSION)) {
			for (Path file : files) {
				BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
				if (attributes.isRegularFile()) {
					entries.add(entry(file, attributes));
				}
			}
		} catch (IOException e) {
			CarvesAndCrafts.LOGGER.warn("Could not list {}", folder, e);
		}
		CACHE.keySet().retainAll(entries.stream().map(Entry::file).toList());
		entries.sort(Comparator.comparingLong(Entry::modified).reversed().thenComparing(Entry::fileName));
		return entries;
	}

	private static Entry entry(Path file, BasicFileAttributes attributes) {
		long modified = attributes.lastModifiedTime().toMillis();
		long size = attributes.size();
		Cached cached = CACHE.get(file);
		if (cached != null && cached.modified() == modified && cached.size() == size) {
			return cached.entry();
		}
		PumpkinFile data = null;
		if (size <= PumpkinFile.MAX_FILE_BYTES) {
			try {
				data = PumpkinFile.read(Files.readAllBytes(file));
			} catch (IOException | IllegalArgumentException e) {
				CarvesAndCrafts.LOGGER.warn("Unreadable pumpkin file {}: {}", file.getFileName(), e.getMessage());
			}
		}
		Entry entry = new Entry(file, modified, data);
		CACHE.put(file, new Cached(modified, size, entry));
		return entry;
	}

	/** Writes a schematic to a new file named after it (never overwrites). @return the file name */
	public static String export(Schematic schematic) throws IOException {
		Path folder = folder();
		Files.createDirectories(folder);
		String base = schematic.name().isBlank() ? "design" : schematic.name();
		String fileName = FileUtil.findAvailableName(folder, base, PumpkinFile.EXTENSION);
		Files.write(folder.resolve(fileName), PumpkinFile.of(schematic).write(), StandardOpenOption.CREATE_NEW);
		return fileName;
	}

	/** Deletes a file of the folder; refuses anything outside it. */
	public static void delete(Entry entry) throws IOException {
		Path file = entry.file().toAbsolutePath().normalize();
		if (!folder().toAbsolutePath().normalize().equals(file.getParent())) {
			throw new IOException("Not in the schematics folder: " + file);
		}
		Files.deleteIfExists(file);
		CACHE.remove(entry.file());
	}

	public static void openFolder() {
		Path folder = folder();
		try {
			Files.createDirectories(folder);
		} catch (IOException e) {
			CarvesAndCrafts.LOGGER.warn("Could not create {}", folder, e);
		}
		Util.getPlatform().openPath(folder);
	}
}
