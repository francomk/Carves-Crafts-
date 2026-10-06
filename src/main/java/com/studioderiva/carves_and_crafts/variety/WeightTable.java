package com.studioderiva.carves_and_crafts.variety;

import java.util.List;

/** Weighted choice among entries. Pure Java so it can be unit tested. */
public final class WeightTable<T> {
	private final List<T> entries;
	private final int[] cumulative;

	/** Entries with weight 0 never come out; at least one weight must be positive. */
	public WeightTable(List<T> entries, List<Integer> weights) {
		if (entries.size() != weights.size() || entries.isEmpty()) {
			throw new IllegalArgumentException("Entries and weights differ");
		}
		this.entries = List.copyOf(entries);
		this.cumulative = new int[weights.size()];
		long total = 0;
		for (int i = 0; i < weights.size(); i++) {
			int w = weights.get(i);
			if (w < 0) {
				throw new IllegalArgumentException("Negative weight " + w);
			}
			total += w;
			if (total > Integer.MAX_VALUE) {
				throw new IllegalArgumentException("Weights too large");
			}
			cumulative[i] = (int) total;
		}
		if (total == 0) {
			throw new IllegalArgumentException("All weights are zero");
		}
	}

	public int totalWeight() {
		return cumulative[cumulative.length - 1];
	}

	/** Entry for a non-negative roll (taken modulo the total weight). */
	public T pick(int roll) {
		int r = Math.floorMod(roll, totalWeight());
		for (int i = 0; i < cumulative.length; i++) {
			if (r < cumulative[i]) {
				return entries.get(i);
			}
		}
		throw new IllegalStateException("unreachable");
	}
}
