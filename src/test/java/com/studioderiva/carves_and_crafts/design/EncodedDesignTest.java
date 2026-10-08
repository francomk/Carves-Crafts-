package com.studioderiva.carves_and_crafts.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.hash.HashCode;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.util.HashOps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EncodedDesignTest {
	@AfterEach
	void clearRefs() {
		DesignRefs.clear();
	}

	/** Every pixel a different color: a design that compresses badly, the worst case for packet size. */
	private static EncodedDesign noisy(int seed) {
		PumpkinDesign design = new PumpkinDesign(CanvasLayout.uniform(CanvasFace.ALL_MASK, 40, 36));
		int color = seed;
		for (CanvasFace face : CanvasFace.values()) {
			for (int y = 0; y < 36; y++) {
				for (int x = 0; x < 40; x++) {
					design.paint(face, x, y, color++ & 0xFFFFFF);
				}
			}
		}
		return EncodedDesign.of(design);
	}

	private static EncodedDesign small() {
		PumpkinDesign design = new PumpkinDesign(CanvasLayout.uniform(CanvasFace.ALL_MASK, 10, 9));
		design.cut(CanvasFace.NORTH, 2, 3);
		return EncodedDesign.of(design);
	}

	@Test
	void referenceEqualsTheDesignAndCantBeDecoded() {
		EncodedDesign full = noisy(1);
		EncodedDesign ref = full.toRef();
		assertFalse(ref.isComplete());
		assertEquals(full, ref);
		assertEquals(full.hashCode(), ref.hashCode());
		assertEquals(full.hash(), ref.hash());
		assertEquals(EncodedDesign.REF_BYTES, ref.size());
		assertThrows(DesignFormatException.class, ref::decode);
	}

	@Test
	void itemStreamCodecSendsOnlyTheReference() {
		EncodedDesign full = noisy(2);
		assertTrue(full.size() > 10_000, "was " + full.size());
		ByteBuf buf = Unpooled.buffer();
		EncodedDesign.ITEM_STREAM_CODEC.encode(buf, full);
		assertTrue(buf.readableBytes() <= EncodedDesign.REF_BYTES + 1, "was " + buf.readableBytes());

		// the sender remembered it, so the reference comes back as the full design
		EncodedDesign back = EncodedDesign.ITEM_STREAM_CODEC.decode(buf);
		assertTrue(back.isComplete());
		assertEquals(full, back);
	}

	@Test
	void unknownReferenceStaysAReference() {
		ByteBuf buf = Unpooled.buffer();
		EncodedDesign.ITEM_STREAM_CODEC.encode(buf, noisy(3));
		DesignRefs.clear();
		EncodedDesign back = EncodedDesign.ITEM_STREAM_CODEC.decode(buf);
		assertFalse(back.isComplete());
	}

	@Test
	void slotHashIsTheSameForDesignAndReference() {
		EncodedDesign full = small();
		HashCode fullHash = EncodedDesign.ITEM_CODEC.encodeStart(HashOps.CRC32C_INSTANCE, full).getOrThrow();
		HashCode refHash = EncodedDesign.ITEM_CODEC.encodeStart(HashOps.CRC32C_INSTANCE, full.toRef()).getOrThrow();
		assertEquals(fullHash, refHash);
		assertTrue(DesignRefs.size() == 0, "hashing must not fill the cache");
	}

	@Test
	void itemCodecSavesTheFullDesignButSyncsAReference() {
		EncodedDesign full = small();
		var saved = EncodedDesign.ITEM_CODEC.encodeStart(JsonOps.INSTANCE, full).getOrThrow();
		var synced = DesignRefs.syncing(() -> EncodedDesign.ITEM_CODEC.encodeStart(JsonOps.INSTANCE, full).getOrThrow());
		assertTrue(saved.getAsJsonArray().size() > synced.getAsJsonArray().size());
		assertEquals(EncodedDesign.REF_BYTES, synced.getAsJsonArray().size());

		EncodedDesign fromSave = EncodedDesign.ITEM_CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
		assertTrue(fromSave.isComplete());
		assertEquals(full, fromSave);
		EncodedDesign fromSync = EncodedDesign.ITEM_CODEC.parse(JsonOps.INSTANCE, synced).getOrThrow();
		assertEquals(full, fromSync);
	}

	@Test
	void probeFindsOnlyUnknownReferences() {
		EncodedDesign full = small();
		assertFalse(DesignRefs.writesUnresolvedRef(() -> EncodedDesign.ITEM_CODEC.encodeStart(JsonOps.INSTANCE, full)));
		assertTrue(DesignRefs.writesUnresolvedRef(() -> EncodedDesign.ITEM_CODEC.encodeStart(JsonOps.INSTANCE, full.toRef())));
	}

	@Test
	void completeCodecRefusesReferences() {
		assertThrows(DesignFormatException.class, () -> EncodedDesign.CODEC.encodeStart(JsonOps.INSTANCE, small().toRef()));
	}

	@Test
	void expiredAndOversizedEntriesArePruned() {
		EncodedDesign a = small();
		DesignRefs.remember(a);
		assertSame(a, DesignRefs.resolve(a.toRef()));
		DesignRefs.prune(System.currentTimeMillis() + DesignRefs.TTL_MS + 1);
		assertFalse(DesignRefs.resolve(a.toRef()).isComplete());
	}

	@Test
	void colorCountIgnoresUnusedPaletteEntries() {
		PumpkinDesign design = new PumpkinDesign(CanvasLayout.uniform(CanvasFace.ALL_MASK, 10, 9));
		design.paint(CanvasFace.NORTH, 0, 0, 0xFF0000);
		design.paint(CanvasFace.NORTH, 0, 0, 0x00FF00);
		design.paint(CanvasFace.NORTH, 1, 0, 0x00FF00);
		design.cut(CanvasFace.NORTH, 2, 0);
		assertEquals(1, DesignCodec.colorCount(design));
	}
}
