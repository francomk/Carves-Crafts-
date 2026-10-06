package com.studioderiva.carves_and_crafts.schematic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.studioderiva.carves_and_crafts.design.AuthorList;
import com.studioderiva.carves_and_crafts.design.EncodedDesign;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A saved design in a player's library.
 *
 * @param model id of the pumpkin model it was made for; it can only be copied onto that model
 */
public record Schematic(int id, String name, long createdAt, String model, EncodedDesign design, AuthorList authors) {
	/** Model assumed for schematics saved before models existed. */
	public static final String LEGACY_MODEL = "custom_pumpkin";
	public static final int MAX_NAME_LENGTH = 32;

	public static final Codec<Schematic> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("id").forGetter(Schematic::id),
		Codec.string(0, MAX_NAME_LENGTH).fieldOf("name").forGetter(Schematic::name),
		Codec.LONG.fieldOf("created").forGetter(Schematic::createdAt),
		Codec.string(1, 64).optionalFieldOf("model", LEGACY_MODEL).forGetter(Schematic::model),
		EncodedDesign.CODEC.fieldOf("design").forGetter(Schematic::design),
		AuthorList.CODEC.optionalFieldOf("authors", AuthorList.EMPTY).forGetter(Schematic::authors)
	).apply(i, Schematic::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, Schematic> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, Schematic::id,
		ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH), Schematic::name,
		ByteBufCodecs.VAR_LONG, Schematic::createdAt,
		ByteBufCodecs.stringUtf8(64), Schematic::model,
		EncodedDesign.STREAM_CODEC, Schematic::design,
		AuthorList.STREAM_CODEC, Schematic::authors,
		Schematic::new
	);

	public Schematic withName(String newName) {
		return new Schematic(id, newName, createdAt, model, design, authors);
	}

	/**
	 * Makes a user-typed name safe to store: no formatting codes or control characters, trimmed, length-capped.
	 * Returns an empty string if nothing usable is left.
	 */
	public static String sanitizeName(String raw) {
		StringBuilder clean = new StringBuilder();
		for (int i = 0; i < raw.length() && clean.length() < MAX_NAME_LENGTH; i++) {
			char c = raw.charAt(i);
			if (c == '§') {
				i++; // drop the formatting code and the character it applies
				continue;
			}
			if (Character.isISOControl(c)) {
				continue;
			}
			clean.append(c);
		}
		return clean.toString().trim();
	}
}
