package com.studioderiva.carves_and_crafts.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OutboundBudgetTest {
	private static final long SECOND = 1_000_000_000L;

	@Test
	void burstUpToCapacityThenRefusesWithoutConsuming() {
		OutboundBudget.Bucket bucket = new OutboundBudget.Bucket(1000, 100, 0);
		assertTrue(bucket.tryConsume(600, 0));
		assertFalse(bucket.tryConsume(500, 0));
		assertTrue(bucket.tryConsume(400, 0));
		assertFalse(bucket.tryConsume(1, 0));
	}

	@Test
	void refillsOverTimeUpToCapacity() {
		OutboundBudget.Bucket bucket = new OutboundBudget.Bucket(1000, 100, 0);
		assertTrue(bucket.tryConsume(1000, 0));
		assertFalse(bucket.tryConsume(300, 2 * SECOND));
		assertTrue(bucket.tryConsume(300, 3 * SECOND));
		assertFalse(bucket.tryConsume(1001, 100 * SECOND));
		assertTrue(bucket.tryConsume(1000, 100 * SECOND));
	}
}
