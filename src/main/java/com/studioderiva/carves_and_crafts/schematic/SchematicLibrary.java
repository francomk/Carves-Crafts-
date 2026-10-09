package com.studioderiva.carves_and_crafts.schematic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.studioderiva.carves_and_crafts.design.AuthorList;
import com.studioderiva.carves_and_crafts.design.EncodedDesign;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A player's schematic library. Immutable: every change returns a new instance, which is then stored back on
 * the player, so the saved copy can never be half-updated. Newest schematics come first.
 */
public record SchematicLibrary(int nextId, List<Schematic> entries) {
	public static final SchematicLibrary EMPTY = new SchematicLibrary(1, List.of());
	/**
	 * Total bytes a library may hold, whatever the server config says: the whole library lives in memory with the
	 * player and is rewritten on every player save. Thousands of ordinary designs still fit.
	 */
	public static final long MAX_STORED_BYTES = 4L * 1024 * 1024;
	/**
	 * What an entry costs besides its design: name, model and up to 8 authors. Without it, near-empty designs
	 * (18 bytes) would let a library grow to hundreds of thousands of entries.
	 */
	public static final int ENTRY_OVERHEAD_BYTES = 1024;

	public static final Codec<SchematicLibrary> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("next_id").forGetter(SchematicLibrary::nextId),
		Schematic.CODEC.listOf().fieldOf("entries").forGetter(SchematicLibrary::entries)
	).apply(i, SchematicLibrary::new));

	public SchematicLibrary {
		entries = List.copyOf(entries);
	}

	/** Result of {@link #add}: the updated library and the new entry. */
	public record Added(SchematicLibrary library, Schematic schematic) {
	}

	/**
	 * @param maxEntries 0 = no count limit ({@link #MAX_STORED_BYTES} still applies)
	 * @return empty if the library is full
	 */
	public Optional<Added> add(String rawName, String model, EncodedDesign design, AuthorList authors, long now, int maxEntries) {
		if (maxEntries > 0 && entries.size() >= maxEntries || storedBytes() + entryBytes(design) > MAX_STORED_BYTES) {
			return Optional.empty();
		}
		String name = Schematic.sanitizeName(rawName);
		if (name.isEmpty()) {
			name = "Design #" + nextId;
		}
		Schematic schematic = new Schematic(nextId, name, now, model, design, authors);
		List<Schematic> next = new ArrayList<>(entries.size() + 1);
		next.add(schematic);
		next.addAll(entries);
		return Optional.of(new Added(new SchematicLibrary(nextId + 1, next), schematic));
	}

	public long storedBytes() {
		return entries.stream().mapToLong(s -> entryBytes(s.design())).sum();
	}

	private static long entryBytes(EncodedDesign design) {
		return design.size() + ENTRY_OVERHEAD_BYTES;
	}

	public Optional<Schematic> find(int id) {
		return entries.stream().filter(s -> s.id() == id).findFirst();
	}

	/** @return empty if no such schematic or the name is unusable */
	public Optional<SchematicLibrary> rename(int id, String rawName) {
		String name = Schematic.sanitizeName(rawName);
		if (name.isEmpty() || find(id).isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new SchematicLibrary(nextId, entries.stream().map(s -> s.id() == id ? s.withName(name) : s).toList()));
	}

	public Optional<SchematicLibrary> delete(int id) {
		if (find(id).isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new SchematicLibrary(nextId, entries.stream().filter(s -> s.id() != id).toList()));
	}

	public int pageCount(int pageSize) {
		return Math.max(1, (entries.size() + pageSize - 1) / pageSize);
	}

	/** Entries of a page; out-of-range pages are clamped. */
	public List<Schematic> page(int page, int pageSize) {
		int clamped = Math.max(0, Math.min(page, pageCount(pageSize) - 1));
		int from = clamped * pageSize;
		return entries.subList(Math.min(from, entries.size()), Math.min(from + pageSize, entries.size()));
	}
}
