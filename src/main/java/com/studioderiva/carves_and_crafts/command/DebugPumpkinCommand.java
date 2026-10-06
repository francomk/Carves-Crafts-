package com.studioderiva.carves_and_crafts.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.studioderiva.carves_and_crafts.block.CustomPumpkinBlock;
import com.studioderiva.carves_and_crafts.block.entity.CustomPumpkinBlockEntity;
import com.studioderiva.carves_and_crafts.model.PumpkinModel;
import com.studioderiva.carves_and_crafts.design.CanvasFace;
import com.studioderiva.carves_and_crafts.design.CanvasLayout;
import com.studioderiva.carves_and_crafts.design.DesignCodec;
import com.studioderiva.carves_and_crafts.design.PumpkinDesign;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Operator command to inspect and edit the design of a placed pumpkin directly, for debugging.
 * <pre>
 * /dpumpkin &lt;pos&gt; init &lt;density&gt;
 * /dpumpkin &lt;pos&gt; cut|erase &lt;face&gt; &lt;x&gt; &lt;y&gt;
 * /dpumpkin &lt;pos&gt; paint &lt;face&gt; &lt;x&gt; &lt;y&gt; &lt;#RRGGBB&gt;
 * /dpumpkin &lt;pos&gt; fill &lt;face&gt; &lt;x0&gt; &lt;y0&gt; &lt;x1&gt; &lt;y1&gt; cut|erase|#RRGGBB
 * /dpumpkin &lt;pos&gt; demo
 * /dpumpkin &lt;pos&gt; info
 * </pre>
 */
public final class DebugPumpkinCommand {
	private static final SimpleCommandExceptionType NOT_A_PUMPKIN = new SimpleCommandExceptionType(Component.literal("No custom pumpkin at that position"));
	private static final SimpleCommandExceptionType NO_DESIGN = new SimpleCommandExceptionType(Component.literal("Pumpkin has no design yet, use 'init' first"));
	private static final SimpleCommandExceptionType BAD_FACE = new SimpleCommandExceptionType(Component.literal("Unknown or disabled face"));
	private static final SimpleCommandExceptionType BAD_COLOR = new SimpleCommandExceptionType(Component.literal("Color must be #RRGGBB"));
	private static final SimpleCommandExceptionType BAD_PIXEL = new SimpleCommandExceptionType(Component.literal("Pixel outside the canvas"));
	private static final SimpleCommandExceptionType BAD_DENSITY = new SimpleCommandExceptionType(Component.literal("Density not allowed for this pumpkin model"));

	private DebugPumpkinCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("dpumpkin")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.argument("pos", BlockPosArgument.blockPos())
				.then(Commands.literal("init")
					.then(Commands.argument("density", IntegerArgumentType.integer())
						.executes(DebugPumpkinCommand::init)))
				.then(pixelCommand("cut", Op.CUT))
				.then(pixelCommand("erase", Op.ERASE))
				.then(Commands.literal("paint")
					.then(Commands.argument("face", StringArgumentType.word()).suggests(DebugPumpkinCommand::suggestFaces)
						.then(Commands.argument("x", IntegerArgumentType.integer(0))
							.then(Commands.argument("y", IntegerArgumentType.integer(0))
								.then(Commands.argument("color", StringArgumentType.greedyString())
									.executes(ctx -> pixel(ctx, Op.PAINT)))))))
				.then(Commands.literal("fill")
					.then(Commands.argument("face", StringArgumentType.word()).suggests(DebugPumpkinCommand::suggestFaces)
						.then(Commands.argument("x0", IntegerArgumentType.integer(0))
							.then(Commands.argument("y0", IntegerArgumentType.integer(0))
								.then(Commands.argument("x1", IntegerArgumentType.integer(0))
									.then(Commands.argument("y1", IntegerArgumentType.integer(0))
										.then(Commands.argument("what", StringArgumentType.greedyString())
											.executes(DebugPumpkinCommand::fill))))))))
				.then(Commands.literal("demo").executes(DebugPumpkinCommand::demo))
				.then(Commands.literal("info").executes(DebugPumpkinCommand::info))));
	}

	private enum Op {
		CUT,
		ERASE,
		PAINT
	}

	private static ArgumentBuilder<CommandSourceStack, ?> pixelCommand(String name, Op op) {
		return Commands.literal(name)
			.then(Commands.argument("face", StringArgumentType.word()).suggests(DebugPumpkinCommand::suggestFaces)
				.then(Commands.argument("x", IntegerArgumentType.integer(0))
					.then(Commands.argument("y", IntegerArgumentType.integer(0))
						.executes(ctx -> pixel(ctx, op)))));
	}

	private static CompletableFuture<Suggestions> suggestFaces(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
		return SharedSuggestionProvider.suggest(Arrays.stream(CanvasFace.values()).map(f -> f.name().toLowerCase(Locale.ROOT)), builder);
	}

	private static int init(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		int density = IntegerArgumentType.getInteger(ctx, "density");
		CustomPumpkinBlockEntity pumpkin = pumpkin(ctx);
		PumpkinModel model = ((CustomPumpkinBlock) pumpkin.getBlockState().getBlock()).model();
		if (!model.densities().contains(density)) {
			throw BAD_DENSITY.create();
		}
		CanvasLayout layout = model.layout(density);
		pumpkin.setDesign(new PumpkinDesign(layout));
		ctx.getSource().sendSuccess(() -> Component.literal("New design " + layout), false);
		return 1;
	}

	private static int pixel(CommandContext<CommandSourceStack> ctx, Op op) throws CommandSyntaxException {
		CustomPumpkinBlockEntity pumpkin = pumpkin(ctx);
		PumpkinDesign design = design(pumpkin);
		CanvasFace face = face(ctx, design);
		int x = IntegerArgumentType.getInteger(ctx, "x");
		int y = IntegerArgumentType.getInteger(ctx, "y");
		if (x >= design.width(face) || y >= design.height(face)) {
			throw BAD_PIXEL.create();
		}
		int color = op == Op.PAINT ? color(StringArgumentType.getString(ctx, "color")) : 0;
		boolean changed = pumpkin.editDesign(d -> switch (op) {
			case CUT -> d.cut(face, x, y);
			case ERASE -> d.erase(face, x, y);
			case PAINT -> d.paint(face, x, y, color);
		});
		ctx.getSource().sendSuccess(() -> Component.literal(changed ? "Changed" : "No change"), false);
		return changed ? 1 : 0;
	}

	private static int fill(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CustomPumpkinBlockEntity pumpkin = pumpkin(ctx);
		PumpkinDesign design = design(pumpkin);
		CanvasFace face = face(ctx, design);
		int x0 = IntegerArgumentType.getInteger(ctx, "x0");
		int y0 = IntegerArgumentType.getInteger(ctx, "y0");
		int x1 = IntegerArgumentType.getInteger(ctx, "x1");
		int y1 = IntegerArgumentType.getInteger(ctx, "y1");
		if (Math.max(x0, x1) >= design.width(face) || Math.max(y0, y1) >= design.height(face)) {
			throw BAD_PIXEL.create();
		}
		String what = StringArgumentType.getString(ctx, "what").trim();
		Op op = switch (what) {
			case "cut" -> Op.CUT;
			case "erase" -> Op.ERASE;
			default -> Op.PAINT;
		};
		int color = op == Op.PAINT ? color(what) : 0;
		boolean changed = pumpkin.editDesign(d -> {
			boolean any = false;
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					any |= switch (op) {
						case CUT -> d.cut(face, x, y);
						case ERASE -> d.erase(face, x, y);
						case PAINT -> d.paint(face, x, y, color);
					};
				}
			}
			return any;
		});
		ctx.getSource().sendSuccess(() -> Component.literal(changed ? "Changed" : "No change"), false);
		return changed ? 1 : 0;
	}

	/** Classic jack-o'-lantern face on the front canvas, scaled to its size, plus a painted band on the back. */
	private static int demo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CustomPumpkinBlockEntity pumpkin = pumpkin(ctx);
		design(pumpkin);
		pumpkin.editDesign(d -> {
			boolean changed = false;
			int w = d.width(CanvasFace.NORTH);
			int h = d.height(CanvasFace.NORTH);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					float u = (x + 0.5F) / w;
					float v = (y + 0.5F) / h;
					if (isEye(u, v, 0.30F) || isEye(u, v, 0.70F) || isMouth(u, v)) {
						changed |= d.cut(CanvasFace.NORTH, x, y);
					}
				}
			}
			if (d.hasFace(CanvasFace.SOUTH)) {
				int sh = d.height(CanvasFace.SOUTH);
				for (int y = sh / 3; y < sh - sh / 3; y++) {
					for (int x = 0; x < d.width(CanvasFace.SOUTH); x++) {
						changed |= d.paint(CanvasFace.SOUTH, x, y, 0x2E7D32);
					}
				}
			}
			return changed;
		});
		ctx.getSource().sendSuccess(() -> Component.literal("Demo face carved"), false);
		return 1;
	}

	private static boolean isEye(float u, float v, float centerU) {
		// upward triangle
		float top = 0.25F;
		float bottom = 0.42F;
		if (v < top || v > bottom) {
			return false;
		}
		float halfWidth = 0.10F * (v - top) / (bottom - top);
		return Math.abs(u - centerU) <= halfWidth;
	}

	private static boolean isMouth(float u, float v) {
		if (u < 0.2F || u > 0.8F) {
			return false;
		}
		float du = (u - 0.5F) / 0.3F;
		float upper = 0.58F + 0.10F * du * du;
		float lower = upper + 0.10F;
		boolean tooth = v < upper + 0.05F && Math.abs(du) < 0.12F;
		return v >= upper && v <= lower && !tooth;
	}

	private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		PumpkinDesign design = design(pumpkin(ctx));
		byte[] bytes = DesignCodec.encode(design);
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
			"%s, palette %d, %d bytes, hash %s",
			design.layout(), design.paletteSize(), bytes.length, DesignCodec.hash(bytes)
		)), false);
		return 1;
	}

	private static CustomPumpkinBlockEntity pumpkin(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
		if (ctx.getSource().getLevel().getBlockEntity(pos) instanceof CustomPumpkinBlockEntity pumpkin) {
			return pumpkin;
		}
		throw NOT_A_PUMPKIN.create();
	}

	private static PumpkinDesign design(CustomPumpkinBlockEntity pumpkin) throws CommandSyntaxException {
		PumpkinDesign design = pumpkin.getDesign();
		if (design == null) {
			throw NO_DESIGN.create();
		}
		return design;
	}

	private static CanvasFace face(CommandContext<CommandSourceStack> ctx, PumpkinDesign design) throws CommandSyntaxException {
		try {
			CanvasFace face = CanvasFace.valueOf(StringArgumentType.getString(ctx, "face").toUpperCase(Locale.ROOT));
			if (design.hasFace(face)) {
				return face;
			}
		} catch (IllegalArgumentException ignored) {
			// fall through
		}
		throw BAD_FACE.create();
	}

	private static int color(String text) throws CommandSyntaxException {
		String hex = text.trim().startsWith("#") ? text.trim().substring(1) : text.trim();
		if (hex.length() != 6) {
			throw BAD_COLOR.create();
		}
		try {
			return Integer.parseInt(hex, 16);
		} catch (NumberFormatException e) {
			throw BAD_COLOR.create();
		}
	}
}
