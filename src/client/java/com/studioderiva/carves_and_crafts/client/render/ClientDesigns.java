package com.studioderiva.carves_and_crafts.client.render;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.design.EncodedDesign;
import com.studioderiva.carves_and_crafts.design.PumpkinDesign;
import com.studioderiva.carves_and_crafts.network.RequestDesignsPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Designs of placed pumpkins, fetched from the server by hash when first drawn (chunk data carries only the hash).
 * Main thread only.
 */
public final class ClientDesigns {
	private static final int MAX_DESIGNS = 1024;
	/** A request the server dropped (over its send budget, pumpkin changed meanwhile) is sent again after this. */
	private static final long RETRY_MS = 3_000;
	/** Requests leave at a steady pace instead of all at once when entering an area full of designs. */
	private static final int MAX_PACKETS_PER_TICK = 2;

	private static final LinkedHashMap<String, PumpkinDesign> designs = new LinkedHashMap<>(64, 0.75F, true);
	private static final Map<String, Long> requestedAt = new HashMap<>();
	private static final Map<String, BlockPos> queued = new LinkedHashMap<>();

	private ClientDesigns() {
	}

	/** The design with this hash, or null while it is on its way (it is requested from the pumpkin at pos). */
	public static @Nullable PumpkinDesign get(String hash, BlockPos pos) {
		PumpkinDesign design = designs.get(hash);
		if (design != null) {
			return design;
		}
		Long sent = requestedAt.get(hash);
		if ((sent == null || System.currentTimeMillis() - sent > RETRY_MS) && !queued.containsKey(hash)) {
			queued.put(hash, pos.immutable());
		}
		return null;
	}

	public static void receive(EncodedDesign encoded) {
		String hash = encoded.hash();
		requestedAt.remove(hash);
		queued.remove(hash);
		try {
			designs.put(hash, encoded.decode());
		} catch (IllegalArgumentException e) {
			CarvesAndCrafts.LOGGER.warn("Invalid pumpkin design from the server: {}", e.getMessage());
			return;
		}
		if (designs.size() > MAX_DESIGNS) {
			designs.pollFirstEntry();
		}
	}

	/** Sends queued requests; called every client tick. */
	public static void tick() {
		long now = System.currentTimeMillis();
		// past the retry delay an entry means the same as no entry, and pumpkins broken meanwhile never answer
		requestedAt.values().removeIf(sent -> now - sent > RETRY_MS);
		if (queued.isEmpty() || !ClientPlayNetworking.canSend(RequestDesignsPayload.TYPE)) {
			return;
		}
		for (int packet = 0; packet < MAX_PACKETS_PER_TICK && !queued.isEmpty(); packet++) {
			List<BlockPos> positions = new ArrayList<>(RequestDesignsPayload.MAX_POSITIONS);
			Iterator<Map.Entry<String, BlockPos>> it = queued.entrySet().iterator();
			while (it.hasNext() && positions.size() < RequestDesignsPayload.MAX_POSITIONS) {
				Map.Entry<String, BlockPos> next = it.next();
				positions.add(next.getValue());
				requestedAt.put(next.getKey(), now);
				it.remove();
			}
			ClientPlayNetworking.send(new RequestDesignsPayload(positions));
		}
	}

	public static void clear() {
		designs.clear();
		requestedAt.clear();
		queued.clear();
	}
}
