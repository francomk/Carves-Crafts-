package com.studioderiva.carves_and_crafts.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.studioderiva.carves_and_crafts.config.ServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Server options for admins: /carves maxcolors [off|1-254]. Changes are saved to the config file and apply at once.
 */
public final class CarvesCommand {
	private CarvesCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("carves")
			.requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
			.then(Commands.literal("maxcolors")
				.executes(context -> showMaxColors(context.getSource()))
				.then(Commands.literal("off").executes(context -> setMaxColors(context.getSource(), 0)))
				.then(Commands.argument("colors", IntegerArgumentType.integer(1, ServerConfig.MAX_COLORS_LIMIT))
					.executes(context -> setMaxColors(context.getSource(), IntegerArgumentType.getInteger(context, "colors"))))));
	}

	private static int showMaxColors(CommandSourceStack source) {
		int limit = ServerConfig.get().colorLimit();
		source.sendSuccess(() -> limit == 0
			? Component.translatable("commands.carves_and_crafts.maxcolors.off")
			: Component.translatable("commands.carves_and_crafts.maxcolors.value", limit), false);
		return limit;
	}

	private static int setMaxColors(CommandSourceStack source, int colors) {
		ServerConfig.get().maxColorsPerDesign = colors;
		ServerConfig.save();
		source.sendSuccess(() -> colors == 0
			? Component.translatable("commands.carves_and_crafts.maxcolors.set_off")
			: Component.translatable("commands.carves_and_crafts.maxcolors.set", colors), true);
		return colors;
	}
}
