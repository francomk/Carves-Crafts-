package com.studioderiva.carves_and_crafts.registry;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.design.AuthorList;
import com.studioderiva.carves_and_crafts.design.EncodedDesign;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;

public final class ModComponents {
	/**
	 * Design carried by a pumpkin item, stored inline so the pumpkin is self-contained (works across worlds).
	 * Clients only receive a reference to it (see EncodedDesign).
	 */
	public static final DataComponentType<EncodedDesign> DESIGN = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		CarvesAndCrafts.id("design"),
		DataComponentType.<EncodedDesign>builder()
			.persistent(EncodedDesign.ITEM_CODEC)
			.networkSynchronized(EncodedDesign.ITEM_STREAM_CODEC)
			.build()
	);

	/** Paint left in a paintbrush, in pixels. */
	public static final DataComponentType<Integer> PAINT_CHARGE = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		CarvesAndCrafts.id("paint_charge"),
		DataComponentType.<Integer>builder()
			.persistent(ExtraCodecs.NON_NEGATIVE_INT)
			.networkSynchronized(ByteBufCodecs.VAR_INT)
			.build()
	);

	/** Players who carved the pumpkin; copies keep it. */
	public static final DataComponentType<AuthorList> AUTHORS = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		CarvesAndCrafts.id("authors"),
		DataComponentType.<AuthorList>builder()
			.persistent(AuthorList.CODEC)
			.networkSynchronized(AuthorList.STREAM_CODEC)
			.build()
	);

	private ModComponents() {
	}

	public static void init() {
	}
}
