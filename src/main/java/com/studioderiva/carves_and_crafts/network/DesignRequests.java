package com.studioderiva.carves_and_crafts.network;

import com.studioderiva.carves_and_crafts.block.entity.CustomPumpkinBlockEntity;
import com.studioderiva.carves_and_crafts.design.EncodedDesign;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Server side of {@link RequestDesignsPayload}. Runs on the server thread. */
public final class DesignRequests {
	private DesignRequests() {
	}

	public static void handle(ServerPlayer player, RequestDesignsPayload payload) {
		ServerLevel level = player.level();
		// a client may only read pumpkins it could have been sent in chunk data
		double maxDistance = (level.getServer().getPlayerList().getViewDistance() + 1) * 16.0;
		Set<String> sent = new HashSet<>();
		for (BlockPos pos : payload.positions()) {
			if (!level.isLoaded(pos) || !pos.closerToCenterThan(player.position(), maxDistance)
				|| !(level.getBlockEntity(pos) instanceof CustomPumpkinBlockEntity pumpkin)) {
				continue;
			}
			EncodedDesign design = pumpkin.encodedDesign();
			if (design == null || !sent.add(design.hash())) {
				continue;
			}
			if (!OutboundBudget.tryConsume(player, design.size())) {
				return; // the client asks again later
			}
			ServerPlayNetworking.send(player, new DesignDataPayload(design));
		}
	}
}
