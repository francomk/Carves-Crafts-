package com.studioderiva.carves_and_crafts.network;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.schematic.Schematic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: an action on the player's schematic library, or on the presets, from the open carving bench.
 *
 * @param schematicId target schematic (RENAME, DELETE, APPLY) or preset (PRESET_APPLY, PRESET_SAVE)
 * @param designHash  hash of the preset's design as the client saw it (PRESET_APPLY, PRESET_SAVE): preset ids are
 *                    list positions, which a data pack reload can shift onto another preset
 * @param name        new name (SAVE_FROM_PUMPKIN, RENAME)
 * @param page        page to send back after the action
 */
public record SchematicActionPayload(int containerId, Action action, int schematicId, String designHash, String name, int page) implements CustomPacketPayload {
	public enum Action {
		PAGE,
		SAVE_FROM_PUMPKIN,
		RENAME,
		DELETE,
		APPLY,
		PRESET_PAGE,
		PRESET_APPLY,
		PRESET_SAVE;

		/** Actions on the presets get a page of presets back, the others a page of the library. */
		public boolean onPresets() {
			return this == PRESET_PAGE || this == PRESET_APPLY || this == PRESET_SAVE;
		}
	}

	public static final Type<SchematicActionPayload> TYPE = new Type<>(CarvesAndCrafts.id("schematic_action"));
	private static final StreamCodec<RegistryFriendlyByteBuf, Action> ACTION_CODEC =
		ByteBufCodecs.idMapper(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal).cast();
	public static final StreamCodec<RegistryFriendlyByteBuf, SchematicActionPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, SchematicActionPayload::containerId,
		ACTION_CODEC, SchematicActionPayload::action,
		ByteBufCodecs.VAR_INT, SchematicActionPayload::schematicId,
		ByteBufCodecs.stringUtf8(64), SchematicActionPayload::designHash,
		ByteBufCodecs.stringUtf8(Schematic.MAX_NAME_LENGTH * 2), SchematicActionPayload::name,
		ByteBufCodecs.VAR_INT, SchematicActionPayload::page,
		SchematicActionPayload::new
	);

	public static SchematicActionPayload page(int containerId, int page) {
		return new SchematicActionPayload(containerId, Action.PAGE, 0, "", "", page);
	}

	public static SchematicActionPayload presetPage(int containerId, int page) {
		return new SchematicActionPayload(containerId, Action.PRESET_PAGE, 0, "", "", page);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
