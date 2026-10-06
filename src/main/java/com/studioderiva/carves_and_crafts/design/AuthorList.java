package com.studioderiva.carves_and_crafts.design;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Players who carved a pumpkin, in order of first contribution. Copies keep the list unchanged.
 * Capped so a pumpkin passed around many players can't grow without bound.
 */
public record AuthorList(List<Author> authors) {
	public static final int MAX_AUTHORS = 8;
	public static final int MAX_NAME_LENGTH = 16;
	public static final AuthorList EMPTY = new AuthorList(List.of());

	public record Author(UUID id, String name) {
		public static final Codec<Author> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(Author::id),
			Codec.string(1, MAX_NAME_LENGTH).fieldOf("name").forGetter(Author::name)
		).apply(i, Author::new));
		public static final StreamCodec<ByteBuf, Author> STREAM_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, Author::id,
			ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH), Author::name,
			Author::new
		);
	}

	public static final Codec<AuthorList> CODEC = Author.CODEC.sizeLimitedListOf(MAX_AUTHORS).xmap(AuthorList::new, AuthorList::authors);
	public static final StreamCodec<ByteBuf, AuthorList> STREAM_CODEC =
		Author.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_AUTHORS)).map(AuthorList::new, AuthorList::authors);

	public AuthorList {
		authors = List.copyOf(authors);
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
		return new AuthorList(next);
	}

	public boolean isEmpty() {
		return authors.isEmpty();
	}

	public List<String> names() {
		return authors.stream().map(Author::name).toList();
	}
}
