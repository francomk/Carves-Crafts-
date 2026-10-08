package com.studioderiva.carves_and_crafts.design;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Designs behind the references sent to clients (see {@link EncodedDesign}). Clients only ever hold references to
 * pumpkin item designs; when one comes back (the creative inventory sends whole items to the server) it is turned
 * into the complete design again from here.
 *
 * <p>Only designs the server sent recently are kept: references are remembered while being written, entries
 * expire after {@link #TTL_MS} without being sent and the total is capped at {@link #MAX_BYTES}. A reference that is
 * no longer known stays a reference; the creative slot handler refuses items holding one.
 *
 * <p>Written from network threads and the server thread.
 */
public final class DesignRefs {
	static final long TTL_MS = 10 * 60_000;
	static final long MAX_BYTES = 64L * 1024 * 1024;

	private static final Map<String, Entry> entries = new ConcurrentHashMap<>();
	private static final AtomicLong totalBytes = new AtomicLong();
	private static final ThreadLocal<int[]> syncDepth = ThreadLocal.withInitial(() -> new int[1]);
	private static final ThreadLocal<boolean[]> probe = new ThreadLocal<>();

	private DesignRefs() {
	}

	private static final class Entry {
		final EncodedDesign design;
		volatile long lastSent;

		Entry(EncodedDesign design, long lastSent) {
			this.design = design;
			this.lastSent = lastSent;
		}
	}

	/** Remembers a complete design that is about to be sent as a reference. */
	public static void remember(EncodedDesign design) {
		if (!design.isComplete()) {
			return;
		}
		long now = System.currentTimeMillis();
		Entry existing = entries.get(design.hash());
		if (existing != null) {
			existing.lastSent = now;
			return;
		}
		if (entries.putIfAbsent(design.hash(), new Entry(design, now)) == null
			&& totalBytes.addAndGet(design.size()) > MAX_BYTES) {
			prune(now);
		}
	}

	/** The complete design behind a reference if it is known, else the reference itself. */
	static EncodedDesign resolve(EncodedDesign ref) {
		Entry entry = entries.get(ref.hash());
		return entry != null ? entry.design : ref;
	}

	/** Runs {@code action} with item designs in NBT written as references (block entity data sent to clients). */
	public static <T> T syncing(Supplier<T> action) {
		int[] depth = syncDepth.get();
		depth[0]++;
		try {
			return action.get();
		} finally {
			depth[0]--;
		}
	}

	static boolean isSyncing() {
		return syncDepth.get()[0] > 0;
	}

	/**
	 * Runs {@code action} (which should encode something) and tells whether it wrote a reference it could not
	 * turn back into a design, i.e. whether the encoded data would lose a design if saved.
	 */
	public static boolean writesUnresolvedRef(Runnable action) {
		boolean[] found = {false};
		boolean[] previous = probe.get();
		probe.set(found);
		try {
			action.run();
		} finally {
			probe.set(previous);
		}
		return found[0];
	}

	static void noteReference(EncodedDesign design) {
		boolean[] found = probe.get();
		if (found != null && !design.isComplete()) {
			found[0] = true;
		}
	}

	/** Drops expired entries, then the oldest ones while over {@link #MAX_BYTES}. */
	public static synchronized void prune(long now) {
		entries.entrySet().removeIf(e -> {
			if (now - e.getValue().lastSent > TTL_MS) {
				totalBytes.addAndGet(-e.getValue().design.size());
				return true;
			}
			return false;
		});
		if (totalBytes.get() <= MAX_BYTES) {
			return;
		}
		List<Map.Entry<String, Entry>> oldest = new ArrayList<>(entries.entrySet());
		oldest.sort(Comparator.comparingLong(e -> e.getValue().lastSent));
		for (Map.Entry<String, Entry> e : oldest) {
			if (totalBytes.get() <= MAX_BYTES) {
				break;
			}
			if (entries.remove(e.getKey(), e.getValue())) {
				totalBytes.addAndGet(-e.getValue().design.size());
			}
		}
	}

	public static synchronized void clear() {
		entries.clear();
		totalBytes.set(0);
	}

	static int size() {
		return entries.size();
	}
}
