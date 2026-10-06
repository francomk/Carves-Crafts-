package com.studioderiva.carves_and_crafts.design;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Players who carved a pumpkin, in order of first contribution. Copies keep the list unchanged.
 * Capped so a pumpkin passed around many players can't grow without bound.
 *
 * @param fromFile the names came from an imported {@code .pumpkin} file, which anyone can edit, so the server
 *                 couldn't check them; tooltips say so
 */
public record AuthorList(List<Author> authors, boolean fromFile) {
	public static final int MAX_AUTHORS = 8;
	public static final int MAX_NAME_LENGTH = 16;
	public static final AuthorList EMPTY = new AuthorList(List.of());

	public record Author(UUID id, String name) {
		public static final Codec<Author> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(Author::id),
			Codec.string(1, MAX_NAME_LENGTH).fieldOf("name").forGetter(Author::name)
		).apply(i, Author::new));
		// Rejects what CODEC can't save: an empty name would make the whole player data attachment unsavable.
		public static final StreamCodec<ByteBuf, Author> STREAM_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, Author::id,
			ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).map(Author::requireSavableName, Function.identity()), Author::name,
			Author::new
		);

		/** Stricter than CODEC: also refuses control and formatting characters, which would style the tooltip. */
		public static boolean isValidName(String name) {
			if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
				return false;
			}
			for (int i = 0; i < name.length(); i++) {
				char c = name.charAt(i);
				if (Character.isISOControl(c) || c == '§') {
					return false;
				}
			}
			return true;
		}

		private static String requireSavableName(String name) {
			if (name.isEmpty()) {
				throw new DecoderException("Empty author name");
			}
			return name;
		}
	}

	private static final Codec<List<Author>> LIST_CODEC = Author.CODEC.sizeLimitedListOf(MAX_AUTHORS);
	private static final Codec<AuthorList> FLAGGED_CODEC = RecordCodecBuilder.create(i -> i.group(
		LIST_CODEC.fieldOf("authors").forGetter(AuthorList::authors),
		Codec.BOOL.fieldOf("from_file").forGetter(AuthorList::fromFile)
	).apply(i, AuthorList::new));
	/** A plain list unless the names came from a file, so data saved before the flag existed still loads. */
	public static final Codec<AuthorList> CODEC = Codec.either(LIST_CODEC, FLAGGED_CODEC).xmap(
		either -> either.map(AuthorList::new, Function.identity()),
		list -> list.fromFile ? Either.right(list) : Either.left(list.authors)
	);
	public static final StreamCodec<ByteBuf, AuthorList> STREAM_CODEC = StreamCodec.composite(
		Author.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_AUTHORS)), AuthorList::authors,
		ByteBufCodecs.BOOL, AuthorList::fromFile,
		AuthorList::new
	);

	public AuthorList {
		authors = List.copyOf(authors);
	}

	public AuthorList(List<Author> authors) {
		this(authors, false);
	}

	/** True if every name can be stored and shown as is; for lists sent by clients. */
	public boolean isValid() {
		return authors.size() <= MAX_AUTHORS && authors.stream().allMatch(a -> Author.isValidName(a.name()));
	}

	/** Same names, marked as unverified. */
	public AuthorList asFromFile() {
		return isEmpty() || fromFile ? this : new AuthorList(authors, true);
	}

	public boolean contains(UUID id) {
		return authors.stream().anyMatch(a -> a.id().equals(id));
	}

	/** Adds a contributor at the end unless already listed or the list is full. */
	public AuthorList with(UUID id, String name) {
		if (contains(id) || authors.size() >= MAX_AUTHORS) {
			return this;
		}
		List<Author> next = new ArrayList<>(authors);
		next.add(new Author(id, name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name));
		return new AuthorList(next, fromFile);
	}

	public boolean isEmpty() {
		return authors.isEmpty();
	}

	public List<String> names() {
		return authors.stream().map(Author::name).toList();
	}
}
