package com.studioderiva.carves_and_crafts.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorListTest {
	private static final AuthorList ONE = AuthorList.EMPTY.with(new UUID(1, 2), "Ann");

	private static AuthorList roundTrip(AuthorList list) {
		JsonElement json = AuthorList.CODEC.encodeStart(JsonOps.INSTANCE, list).getOrThrow();
		return AuthorList.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
	}

	@Test
	void plainListKeepsTheOldFormat() {
		JsonElement json = AuthorList.CODEC.encodeStart(JsonOps.INSTANCE, ONE).getOrThrow();
		assertTrue(json.isJsonArray());
		assertEquals(ONE, roundTrip(ONE));
	}

	@Test
	void fromFileFlagSurvivesSaving() {
		AuthorList flagged = ONE.asFromFile();
		assertTrue(flagged.fromFile());
		assertEquals(flagged, roundTrip(flagged));
	}

	@Test
	void flagIsKeptWhenSomeoneCarvesOnTop() {
		assertTrue(ONE.asFromFile().with(new UUID(3, 4), "Bob").fromFile());
		assertFalse(AuthorList.EMPTY.asFromFile().fromFile());
	}

	@Test
	void namesThatCannotBeSavedOrShownAreInvalid() {
		assertTrue(ONE.isValid());
		assertFalse(new AuthorList(List.of(new AuthorList.Author(new UUID(1, 1), ""))).isValid());
		assertFalse(new AuthorList(List.of(new AuthorList.Author(new UUID(1, 1), "§cRed"))).isValid());
		assertFalse(new AuthorList(List.of(new AuthorList.Author(new UUID(1, 1), "a\nb"))).isValid());
		assertFalse(new AuthorList(List.of(new AuthorList.Author(new UUID(1, 1), "x".repeat(17)))).isValid());
	}
}
