package com.studioderiva.carves_and_crafts.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Per-player cap on the bytes of designs the server sends on request (library pages, placed pumpkin designs).
 * A tiny request can trigger a reply of hundreds of KiB, so without a cap one client could flood the server's
 * outgoing buffers. Server thread only.
 */
public final class OutboundBudget {
	/** Burst allowance: a full library page of the largest designs fits several times. */
	static final long CAPACITY = 4L * 1024 * 1024;
	static final long REFILL_PER_SECOND = 1024L * 1024;

	private static final Map<UUID, Bucket> buckets = new HashMap<>();

	private OutboundBudget() {
	}

	/** @return false if sending {@code bytes} now would exceed the player's budget; nothing is consumed then */
	public static boolean tryConsume(ServerPlayer player, long bytes) {
		return buckets.computeIfAbsent(player.getUUID(), id -> new Bucket(CAPACITY, REFILL_PER_SECOND, System.nanoTime()))
			.tryConsume(bytes, System.nanoTime());
	}

	public static void forget(UUID player) {
		buckets.remove(player);
	}

	/** Token bucket in bytes; time is passed in so it can be tested without waiting. */
	static final class Bucket {
		private final long capacity;
		private final long refillPerSecond;
		private double available;
		private long lastNanos;

		Bucket(long capacity, long refillPerSecond, long nowNanos) {
			this.capacity = capacity;
			this.refillPerSecond = refillPerSecond;
			this.available = capacity;
			this.lastNanos = nowNanos;
		}

		boolean tryConsume(long bytes, long nowNanos) {
			long elapsed = Math.max(0, nowNanos - lastNanos);
			available = Math.min(capacity, available + elapsed * (refillPerSecond / 1e9));
			lastNanos = nowNanos;
			if (bytes > available) {
				return false;
			}
			available -= bytes;
			return true;
		}
	}
}
