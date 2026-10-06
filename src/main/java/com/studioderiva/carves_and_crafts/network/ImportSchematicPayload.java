package com.studioderiva.carves_and_crafts.network;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.design.AuthorList;
import com.studioderiva.carves_and_crafts.design.DesignCodec;
import com.studioderiva.carves_and_crafts.schematic.PumpkinFile;
import com.studioderiva.carves_and_crafts.schematic.Schematic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: import a {@code .pumpkin} file from the player's computer into their schematic library.
 * The design bytes are validated on the server thread, like {@link ConfirmDesignPayload}.
 *
 * @param page page to send back after the import
 */
public record ImportSchematicPayload(int containerId, String name, String model, byte[] design, AuthorList authors, int page)
	implements CustomPacketPayload {
	public static final Type<ImportSchematicPayload> TYPE = new Type<>(CarvesAndCrafts.id("import_schematic"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ImportSchematicPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, ImportSchematicPayload::containerId,
		ByteBufCodecs.stringUtf8(Schematic.MAX_NAME_LENGTH * 2), ImportSchematicPayload::name,
		ByteBufCodecs.stringUtf8(64), ImportSchematicPayload::model,
		ByteBufCodecs.byteArray(DesignCodec.MAX_ENCODED_BYTES), ImportSchematicPayload::design,
		AuthorList.STREAM_CODEC, ImportSchematicPayload::authors,
		ByteBufCodecs.VAR_INT, ImportSchematicPayload::page,
		ImportSchematicPayload::new
	);

	/** @param name name for the new schematic (the file name: players rename files outside the game) */
	public static ImportSchematicPayload of(int containerId, String name, PumpkinFile file, int page) {
		return new ImportSchematicPayload(containerId, Schematic.sanitizeName(name), file.model(), file.design().toByteArray(), file.authors(), page);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
