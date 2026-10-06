package com.studioderiva.carves_and_crafts.client.render;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.block.entity.CustomPumpkinBlockEntity;
import com.studioderiva.carves_and_crafts.design.DesignCodec;
import com.studioderiva.carves_and_crafts.design.PumpkinDesign;
import com.studioderiva.carves_and_crafts.geometry.AtlasLayout;
import com.studioderiva.carves_and_crafts.geometry.PumpkinMesh;
import com.studioderiva.carves_and_crafts.model.PumpkinModel;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Client cache of render data (texture + mesh) keyed by design hash, so identical pumpkins share one GPU texture.
 *
 * <p>Texture ids come from a reusable pool: vanilla memoizes one RenderType per texture id forever,
 * so ids must not grow without bound. Render thread only.
 */
public final class PumpkinRenderCache {
	public static final PumpkinRenderCache INSTANCE = new PumpkinRenderCache();
	private static final int MAX_ENTRIES = 512;

	public static final class Entry {
		final String key;
		final int slot;
		final Identifier texture;
		final PumpkinMesh mesh;
		/** Decorations, drawn with the model's own texture. */
		final Identifier decorTexture;
		final PumpkinMesh decorMesh;
		boolean released;

		private Entry(String key, int slot, Identifier texture, PumpkinMesh mesh, Identifier decorTexture, PumpkinMesh decorMesh) {
			this.key = key;
			this.slot = slot;
			this.texture = texture;
			this.mesh = mesh;
			this.decorTexture = decorTexture;
			this.decorMesh = decorMesh;
		}
	}

	private record Ref(int revision, boolean lit, Entry entry) {
	}

	private final Map<CustomPumpkinBlockEntity, Ref> refs = new WeakHashMap<>();
	private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(64, 0.75F, true);
	private final ArrayDeque<Integer> freeSlots = new ArrayDeque<>();
	private int nextSlot;

	private PumpkinRenderCache() {
	}

	/** @param lit lit pumpkins use a texture with a pale, glowing inside */
	public Entry get(CustomPumpkinBlockEntity pumpkin, PumpkinModel model, boolean lit) {
		Ref ref = refs.get(pumpkin);
		if (ref != null && ref.revision == pumpkin.getRevision() && ref.lit == lit && !ref.entry.released) {
			entries.get(ref.entry.key); // keep it recently used
			return ref.entry;
		}
		PumpkinDesign design = pumpkin.getDesign() != null ? pumpkin.getDesign() : virginDesign(model);
		// same design on another model has another shape and textures
		String key = model.id() + "/" + DesignCodec.hash(design) + (lit ? "/lit" : "");
		Entry entry = entries.get(key);
		if (entry == null) {
			entry = create(key, design, model, lit);
			entries.put(key, entry);
			evictOverflow();
		}
		refs.put(pumpkin, new Ref(pumpkin.getRevision(), lit, entry));
		return entry;
	}

	/** The model without any design, for pumpkins beyond the design render distance. */
	public Entry plain(PumpkinModel model, boolean lit) {
		String key = model.id() + "/plain" + (lit ? "/lit" : "");
		Entry entry = entries.get(key);
		if (entry == null) {
			entry = create(key, virginDesign(model), model, lit);
			entries.put(key, entry);
			evictOverflow();
		}
		return entry;
	}

	/** Releases every texture, e.g. on disconnect or resource reload. */
	public void clear() {
		for (Entry entry : entries.values()) {
			release(entry);
		}
		entries.clear();
		refs.clear();
		PumpkinAtlas.invalidate();
	}

	private static PumpkinDesign virginDesign(PumpkinModel model) {
		return new PumpkinDesign(model.layout(model.densities().get(0)));
	}

	private Entry create(String key, PumpkinDesign design, PumpkinModel model, boolean lit) {
		int slot = freeSlots.isEmpty() ? nextSlot++ : freeSlots.pop();
		Identifier id = CarvesAndCrafts.id("dynamic/pumpkin_" + slot);
		AtlasLayout atlas = AtlasLayout.of(design, model.shape());
		DynamicTexture texture = new DynamicTexture(() -> "Pumpkin design " + key, PumpkinAtlas.build(design, model, atlas, lit));
		Minecraft.getInstance().getTextureManager().register(id, texture);
		Identifier decorTexture = model.texture().withPrefix("textures/").withSuffix(".png");
		return new Entry(key, slot, id, PumpkinMesh.build(design, model.shape(), atlas),
			decorTexture, PumpkinMesh.decor(model.geometry(), model.shape(), design));
	}

	private void evictOverflow() {
		Iterator<Entry> it = entries.values().iterator();
		while (entries.size() > MAX_ENTRIES && it.hasNext()) {
			Entry eldest = it.next();
			it.remove();
			release(eldest);
		}
	}

	private void release(Entry entry) {
		if (entry.released) {
			return;
		}
		entry.released = true;
		Minecraft.getInstance().getTextureManager().release(entry.texture);
		freeSlots.push(entry.slot);
	}
}
