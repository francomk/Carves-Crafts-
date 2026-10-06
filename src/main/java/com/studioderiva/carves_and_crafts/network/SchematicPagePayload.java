package com.studioderiva.carves_and_crafts.network;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.schematic.Schematic;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: one page of the player's schematic library, or of the presets.
 *
 * @param presets the entries are presets (their id is the preset index), not library schematics
 */
public record SchematicPagePayload(int page, int pageCount, int total, List<Schematic> entries, boolean presets) implements CustomPacketPayload {
	public static final int PAGE_SIZE = 6;
	public static final Type<SchematicPagePayload> TYPE = new Type<>(CarvesAndCrafts.id("schematic_page"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SchematicPagePayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, SchematicPagePayload::page,
		ByteBufCodecs.VAR_INT, SchematicPagePayload::pageCount,
		ByteBufCodecs.VAR_INT, SchematicPagePayload::total,
		Schematic.STREAM_CODEC.apply(ByteBufCodecs.list(PAGE_SIZE)), SchematicPagePayload::entries,
		ByteBufCodecs.BOOL, SchematicPagePayload::presets,
		SchematicPagePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
