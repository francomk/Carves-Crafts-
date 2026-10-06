package com.studioderiva.carves_and_crafts.design;

/** Thrown when encoded design bytes are malformed or exceed limits. */
public class DesignFormatException extends IllegalArgumentException {
	public DesignFormatException(String message) {
		super(message);
	}
}
