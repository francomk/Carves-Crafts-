package com.studioderiva.carves_and_crafts.client.compat;

import com.studioderiva.carves_and_crafts.block.CustomPumpkinBlock;
import com.studioderiva.carves_and_crafts.block.entity.CustomPumpkinBlockEntity;
import com.studioderiva.carves_and_crafts.client.CarvesAndCraftsClient;
import com.studioderiva.carves_and_crafts.design.PumpkinDesign;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Lines shown by Jade and WTHIT for a placed pumpkin, from the data the client already has. */
public final class PumpkinInfo {
	private PumpkinInfo() {
	}

	public static List<Component> lines(BlockEntity blockEntity) {
		List<Component> lines = new ArrayList<>();
		if (!(blockEntity instanceof CustomPumpkinBlockEntity pumpkin)
			|| !(pumpkin.getBlockState().getBlock() instanceof CustomPumpkinBlock block)) {
			return lines;
		}
		PumpkinDesign design = pumpkin.getDesign();
		Integer density = design == null ? null : block.model().density(design);
		lines.add(density == null
			? Component.translatable("tooltip.carves_and_crafts.info.blank").withStyle(ChatFormatting.GRAY)
			: Component.translatable("tooltip.carves_and_crafts.info.design", density).withStyle(ChatFormatting.GRAY));
		if (!pumpkin.getAuthors().isEmpty()) {
			lines.add(CarvesAndCraftsClient.authorsLine(pumpkin.getAuthors().names()));
		}
		if (!pumpkin.getLightItem().isEmpty()) {
			lines.add(Component.translatable("tooltip.carves_and_crafts.info.lit", pumpkin.getLightItem().getHoverName())
				.withStyle(ChatFormatting.GRAY));
		}
		return lines;
	}
}
