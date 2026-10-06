package com.studioderiva.carves_and_crafts.client.compat;

import com.studioderiva.carves_and_crafts.block.CustomPumpkinBlock;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaClientPlugin;
import net.minecraft.network.chat.Component;

/**
 * WTHIT entry point (declared in waila_plugins.json, only loaded when WTHIT is installed): design, authors and
 * light source of a placed pumpkin.
 */
public class WthitPlugin implements IWailaClientPlugin {
	@Override
	public void register(IClientRegistrar registrar) {
		registrar.body(new IBlockComponentProvider() {
			@Override
			public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
				for (Component line : PumpkinInfo.lines(accessor.getBlockEntity())) {
					tooltip.addLine(line);
				}
			}
		}, CustomPumpkinBlock.class);
	}
}
