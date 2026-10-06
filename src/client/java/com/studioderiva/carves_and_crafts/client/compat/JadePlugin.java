package com.studioderiva.carves_and_crafts.client.compat;

import com.studioderiva.carves_and_crafts.CarvesAndCrafts;
import com.studioderiva.carves_and_crafts.block.CustomPumpkinBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/** Jade entry point (only loaded when Jade is installed): design, authors and light source of a placed pumpkin. */
@WailaPlugin
public class JadePlugin implements IWailaPlugin {
	@Override
	public void registerClient(IWailaClientRegistration registration) {
		registration.registerBlockComponent(Provider.INSTANCE, CustomPumpkinBlock.class);
	}

	private enum Provider implements IBlockComponentProvider {
		INSTANCE;

		private static final Identifier UID = CarvesAndCrafts.id("pumpkin");

		@Override
		public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
			for (Component line : PumpkinInfo.lines(accessor.getBlockEntity())) {
				tooltip.add(line);
			}
		}

		@Override
		public Identifier getUid() {
			return UID;
		}
	}
}
