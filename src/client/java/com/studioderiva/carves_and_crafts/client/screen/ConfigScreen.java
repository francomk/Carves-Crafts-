package com.studioderiva.carves_and_crafts.client.screen;

import com.studioderiva.carves_and_crafts.client.ClientConfig;
import com.studioderiva.carves_and_crafts.config.ServerConfig;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Options screen (opened from Mod Menu). Client options always; server options only when they apply to this game,
 * i.e. from the title screen or in single player (on a dedicated server they live in the server's own config file).
 */
public class ConfigScreen extends Screen {
	private static final int MAX_DESIGN_DISTANCE = 256;
	private static final int DISTANCE_STEP = 8;
	private static final int WIDTH = 200;

	private final Screen parent;
	private int designDistance;
	private int maxSchematics;
	private int actionsPerMinute;
	private boolean allowImport;

	public ConfigScreen(Screen parent) {
		super(Component.translatable("config.carves_and_crafts.title"));
		this.parent = parent;
		designDistance = ClientConfig.get().designRenderDistance;
		ServerConfig server = ServerConfig.get();
		maxSchematics = server.maxSchematicsPerPlayer;
		actionsPerMinute = server.schematicActionsPerMinute;
		allowImport = server.allowSchematicImport;
	}

	/** Server options are editable only where this game owns them: no world open, or single player. */
	private static boolean serverOptionsEditable() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null || mc.hasSingleplayerServer();
	}

	@Override
	protected void init() {
		int x = (width - WIDTH) / 2;
		int y = 48;
		addRenderableWidget(new AbstractSliderButton(x, y, WIDTH, 20, Component.empty(), designDistance / (double) MAX_DESIGN_DISTANCE) {
			{
				updateMessage();
				setTooltip(Tooltip.create(Component.translatable("config.carves_and_crafts.design_distance.tip")));
			}

			@Override
			protected void updateMessage() {
				setMessage(Component.translatable("config.carves_and_crafts.design_distance", designDistance));
			}

			@Override
			protected void applyValue() {
				designDistance = (int) Math.round(value * MAX_DESIGN_DISTANCE / DISTANCE_STEP) * DISTANCE_STEP;
			}
		});

		y += 40;
		if (serverOptionsEditable()) {
			numberBox(x, y, "config.carves_and_crafts.max_schematics", maxSchematics, v -> maxSchematics = v);
			y += 36;
			numberBox(x, y, "config.carves_and_crafts.actions_per_minute", actionsPerMinute, v -> actionsPerMinute = v);
			y += 28;
			addRenderableWidget(CycleButton.onOffBuilder(allowImport)
				.create(x, y, WIDTH, 20, Component.translatable("config.carves_and_crafts.allow_import"), (button, value) -> allowImport = value))
				.setTooltip(Tooltip.create(Component.translatable("config.carves_and_crafts.allow_import.tip")));
		}

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
			.bounds((width - WIDTH) / 2, height - 28, WIDTH, 20).build());
	}

	/** A number field (0 = unlimited) with its label drawn above it in {@link #render}. */
	private void numberBox(int x, int y, String key, int value, IntConsumer setter) {
		EditBox box = new EditBox(font, x, y + 10, WIDTH, 18, Component.translatable(key));
		box.setValue(Integer.toString(value));
		box.setFilter(text -> text.isEmpty() || text.matches("\\d{1,6}"));
		box.setResponder(text -> setter.accept(text.isEmpty() ? 0 : Integer.parseInt(text)));
		box.setTooltip(Tooltip.create(Component.translatable(key + ".tip")));
		addRenderableWidget(box);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int x = (width - WIDTH) / 2;
		graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFFFF);
		graphics.drawString(font, Component.translatable("config.carves_and_crafts.client"), x, 36, 0xFFA0A0A0);
		if (serverOptionsEditable()) {
			graphics.drawString(font, Component.translatable("config.carves_and_crafts.server"), x, 76, 0xFFA0A0A0);
			graphics.drawString(font, Component.translatable("config.carves_and_crafts.max_schematics"), x, 88, 0xFFFFFFFF);
			graphics.drawString(font, Component.translatable("config.carves_and_crafts.actions_per_minute"), x, 124, 0xFFFFFFFF);
		} else {
			graphics.drawWordWrap(font, Component.translatable("config.carves_and_crafts.server_remote"), x, 88, WIDTH, 0xFFA0A0A0, false);
		}
	}

	@Override
	public void onClose() {
		ClientConfig.get().designRenderDistance = designDistance;
		ClientConfig.save();
		if (serverOptionsEditable()) {
			ServerConfig server = ServerConfig.get();
			server.maxSchematicsPerPlayer = maxSchematics;
			server.schematicActionsPerMinute = actionsPerMinute;
			server.allowSchematicImport = allowImport;
			ServerConfig.save();
		}
		minecraft.setScreen(parent);
	}
}
