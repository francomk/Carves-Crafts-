package com.studioderiva.carves_and_crafts.client.render;

import com.studioderiva.carves_and_crafts.block.LightSource;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jspecify.annotations.Nullable;

public class CustomPumpkinRenderState extends BlockEntityRenderState {
	public int rotation;
	public LightSource light = LightSource.NONE;
	public PumpkinRenderCache.@Nullable Entry entry;
}
