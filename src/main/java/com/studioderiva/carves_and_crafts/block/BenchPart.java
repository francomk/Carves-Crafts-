package com.studioderiva.carves_and_crafts.block;

import net.minecraft.util.StringRepresentable;

/**
 * The carving bench is two blocks wide: the main part (where it was placed: block entity and model) and the side part,
 * one block clockwise from its facing, which only gives the rest of the table its hitbox.
 */
public enum BenchPart implements StringRepresentable {
	MAIN("main"),
	SIDE("side");

	private final String name;

	BenchPart(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
