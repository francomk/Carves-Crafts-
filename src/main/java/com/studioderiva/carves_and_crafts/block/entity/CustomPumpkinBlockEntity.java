package com.studioderiva.carves_and_crafts.block.entity;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.block.CustomPumpkinBlock;
import com.studioderiva.carves_and_crafts.design.AuthorList;
import com.studioderiva.carves_and_crafts.design.EncodedDesign;
import com.studioderiva.carves_and_crafts.design.PumpkinDesign;
import com.studioderiva.carves_and_crafts.registry.ModBlockEntities;
import com.studioderiva.carves_and_crafts.registry.ModComponents;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Holds the design and authors of a placed pumpkin. A null design means a virgin pumpkin (no canvas density chosen yet).
 * The design is stored inline (not as a reference into a shared library), so pumpkins never lose their design.
 */
public class CustomPumpkinBlockEntity extends BlockEntity {
	private static final String DESIGN_KEY = "design";
	private static final String AUTHORS_KEY = "authors";
	private static final String LIGHT_KEY = "light";
	private static final String DESIGN_HASH_KEY = "design_hash";
	private static final String DENSITY_KEY = "density";

	private @Nullable PumpkinDesign design;
	/** Encoding of {@link #design}, kept because chunk data, saves and design requests all need it. */
	private @Nullable EncodedDesign encoded;
	/** Client side: the update tag carries only these, the design itself comes on request. */
	private @Nullable String syncedHash;
	private int syncedDensity;
	private AuthorList authors = AuthorList.EMPTY;
	/** Torch or candle put inside (see CustomPumpkinBlock#useItemOn); dropped when the pumpkin is broken. */
	private ItemStack lightItem = ItemStack.EMPTY;
	/** Bumped on every change; client render caches compare it to know when to rebuild. */
	private int revision;

	public CustomPumpkinBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CUSTOM_PUMPKIN, pos, state);
	}

	public @Nullable PumpkinDesign getDesign() {
		return design;
	}

	/** Encoded design, or null for a virgin pumpkin. Server side (the client only has {@link #designHash()}). */
	public @Nullable EncodedDesign encodedDesign() {
		if (encoded == null && design != null) {
			encoded = EncodedDesign.of(design);
		}
		return encoded;
	}

	/** Hash of the design, or null for a virgin pumpkin; known on both sides. */
	public @Nullable String designHash() {
		EncodedDesign current = encodedDesign();
		return current != null ? current.hash() : syncedHash;
	}

	/** Canvas density of the design, or null for a virgin pumpkin; known on both sides. */
	public @Nullable Integer density() {
		if (design != null) {
			return getBlockState().getBlock() instanceof CustomPumpkinBlock block ? block.model().density(design) : null;
		}
		return syncedHash != null && syncedDensity > 0 ? syncedDensity : null;
	}

	public AuthorList getAuthors() {
		return authors;
	}

	public ItemStack getLightItem() {
		return lightItem;
	}

	public void setLightItem(ItemStack stack) {
		lightItem = stack;
		setChanged();
	}

	/** The pumpkin itself drops through its loot table; the light source inside drops here. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (level != null && !lightItem.isEmpty()) {
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), lightItem);
			lightItem = ItemStack.EMPTY;
		}
	}

	public int getRevision() {
		return revision;
	}

	public void setDesign(@Nullable PumpkinDesign design) {
		this.design = design;
		markDesignChanged();
	}

	/**
	 * Applies an edit to the current design and syncs it if anything changed.
	 *
	 * @return false if there is no design or the edit changed nothing
	 */
	public boolean editDesign(Predicate<PumpkinDesign> edit) {
		if (design == null || !edit.test(design)) {
			return false;
		}
		markDesignChanged();
		return true;
	}

	private void markDesignChanged() {
		encoded = null;
		revision++;
		setChanged();
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (design != null) {
			output.store(DESIGN_KEY, EncodedDesign.CODEC, encodedDesign());
		}
		if (!authors.isEmpty()) {
			output.store(AUTHORS_KEY, AuthorList.CODEC, authors);
		}
		if (!lightItem.isEmpty()) {
			output.store(LIGHT_KEY, ItemStack.CODEC, lightItem);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		design = input.read(DESIGN_KEY, EncodedDesign.CODEC).map(EncodedDesign::decode).orElse(null);
		encoded = null;
		syncedHash = input.getString(DESIGN_HASH_KEY).orElse(null);
		syncedDensity = input.getIntOr(DENSITY_KEY, 0);
		authors = input.read(AUTHORS_KEY, AuthorList.CODEC).orElse(AuthorList.EMPTY);
		lightItem = input.read(LIGHT_KEY, ItemStack.CODEC).orElse(ItemStack.EMPTY);
		revision++;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	/**
	 * Sends the design's hash instead of the design: a chunk full of large designs would otherwise make a chunk
	 * packet too big for the client to accept, disconnecting everyone who comes near.
	 */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = saveWithoutMetadata(registries);
		tag.remove(DESIGN_KEY);
		String hash = designHash();
		if (hash != null) {
			tag.putString(DESIGN_HASH_KEY, hash);
			Integer density = density();
			tag.putInt(DENSITY_KEY, density != null ? density : 0);
		}
		return tag;
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		EncodedDesign encoded = components.get(ModComponents.DESIGN);
		try {
			design = encoded == null ? null : encoded.decode();
			this.encoded = encoded;
		} catch (IllegalArgumentException e) {
			CarvesAndCrafts.LOGGER.warn("Dropping invalid pumpkin design at {}: {}", worldPosition, e.getMessage());
			design = null;
			this.encoded = null;
		}
		authors = components.getOrDefault(ModComponents.AUTHORS, AuthorList.EMPTY);
		revision++;
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		if (design != null) {
			builder.set(ModComponents.DESIGN, encodedDesign());
		}
		if (!authors.isEmpty()) {
			builder.set(ModComponents.AUTHORS, authors);
		}
	}

	@Override
	public void removeComponentsFromTag(ValueOutput output) {
		output.discard(DESIGN_KEY);
		output.discard(AUTHORS_KEY);
	}
}
