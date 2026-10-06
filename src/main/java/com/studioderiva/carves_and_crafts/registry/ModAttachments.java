package com.studioderiva.carves_and_crafts.registry;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.schematic.SchematicLibrary;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class ModAttachments {
	/** Each player's schematic library, saved in their player data and kept on death. */
	public static final AttachmentType<SchematicLibrary> SCHEMATICS = AttachmentRegistry.create(
		CarvesAndCrafts.id("schematics"),
		builder -> builder.persistent(SchematicLibrary.CODEC).copyOnDeath().initializer(() -> SchematicLibrary.EMPTY)
	);

	private ModAttachments() {
	}

	public static void init() {
	}
}
