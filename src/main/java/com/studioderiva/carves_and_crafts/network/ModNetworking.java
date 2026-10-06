package com.studioderiva.carves_and_crafts.network;

import com.studioderiva.carves_and_crafts.bench.BenchService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class ModNetworking {
	private ModNetworking() {
	}

	public static void init() {
		// designs above 32 KiB are fine: that vanilla limit only applies to unknown payloads (checked on a dedicated
		// server with DevServerScenario)
		PayloadTypeRegistry.playC2S().register(ConfirmDesignPayload.TYPE, ConfirmDesignPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(SchematicActionPayload.TYPE, SchematicActionPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(ImportSchematicPayload.TYPE, ImportSchematicPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(RequestDesignsPayload.TYPE, RequestDesignsPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(SchematicPagePayload.TYPE, SchematicPagePayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(DesignDataPayload.TYPE, DesignDataPayload.STREAM_CODEC);

		// handlers run on the server thread
		ServerPlayNetworking.registerGlobalReceiver(ConfirmDesignPayload.TYPE, (payload, context) -> BenchService.confirmDesign(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(SchematicActionPayload.TYPE, (payload, context) -> BenchService.schematicAction(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(ImportSchematicPayload.TYPE, (payload, context) -> BenchService.importSchematic(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(RequestDesignsPayload.TYPE, (payload, context) -> DesignRequests.handle(context.player(), payload));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			BenchService.forget(handler.getPlayer().getUUID());
			OutboundBudget.forget(handler.getPlayer().getUUID());
		});
	}
}
