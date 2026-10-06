package com.studioderiva.carves_and_crafts.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.studioderiva.carves_and_crafts.block.CustomPumpkinBlock;
import com.studioderiva.carves_and_crafts.block.LightSource;
import com.studioderiva.carves_and_crafts.block.entity.CustomPumpkinBlockEntity;
import com.studioderiva.carves_and_crafts.client.ClientConfig;
import com.studioderiva.carves_and_crafts.geometry.PumpkinMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Draws the hollow, carved pumpkin. The block itself has no baked model. */
public class CustomPumpkinRenderer implements BlockEntityRenderer<CustomPumpkinBlockEntity, CustomPumpkinRenderState> {
	public CustomPumpkinRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public CustomPumpkinRenderState createRenderState() {
		return new CustomPumpkinRenderState();
	}

	@Override
	public void extractRenderState(
		CustomPumpkinBlockEntity pumpkin,
		CustomPumpkinRenderState state,
		float partialTick,
		Vec3 cameraPos,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling
	) {
		BlockEntityRenderState.extractBase(pumpkin, state, crumbling);
		BlockState blockState = pumpkin.getBlockState();
		if (!(blockState.getBlock() instanceof CustomPumpkinBlock block)) {
			state.entry = null; // block replaced, BE not removed yet
			return;
		}
		state.rotation = blockState.getValue(CustomPumpkinBlock.ROTATION);
		state.light = blockState.getValue(CustomPumpkinBlock.LIGHT);
		boolean lit = state.light != LightSource.NONE;
		// far away: the plain model, shared by every pumpkin of that model (no per-design texture or mesh)
		double designDistance = ClientConfig.get().designRenderDistance;
		state.entry = Vec3.atCenterOf(pumpkin.getBlockPos()).closerThan(cameraPos, designDistance)
			? PumpkinRenderCache.INSTANCE.get(pumpkin, block.model(), lit)
			: PumpkinRenderCache.INSTANCE.plain(block.model(), lit);
	}

	/** Like a normal block, visible up to the game's render distance (vanilla stops block entities at 64). */
	@Override
	public int getViewDistance() {
		return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
	}

	@Override
	public void submit(CustomPumpkinRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		PumpkinRenderCache.Entry entry = state.entry;
		if (entry == null) {
			return;
		}
		int light = state.lightCoords;

		poseStack.pushPose();
		// model front is north; rotation 0 faces south, each step is 45° clockwise seen from above
		poseStack.translate(0.5F, 0.0F, 0.5F);
		poseStack.mulPose(Axis.YP.rotationDegrees(-(state.rotation * 45.0F + 180.0F)));
		poseStack.translate(-0.5F, 0.0F, -0.5F);
		PumpkinMesh body = entry.mesh;
		submit(collector, poseStack, entry.texture, body, 0, body.outsideQuads(), light, -1);
		// lit: the inside glows in the color of the source, whatever the world light
		boolean lit = state.light != LightSource.NONE;
		submit(collector, poseStack, entry.texture, body, body.outsideQuads(), body.quadCount(),
			lit ? LightTexture.FULL_BRIGHT : light, lit ? state.light.tint() : -1);
		submit(collector, poseStack, entry.decorTexture, entry.decorMesh, 0, entry.decorMesh.quadCount(), light, -1);
		poseStack.popPose();
	}

	/** Draws quads [fromQuad, toQuad) of a mesh. */
	private static void submit(SubmitNodeCollector collector, PoseStack poseStack, Identifier texture, PumpkinMesh mesh,
		int fromQuad, int toQuad, int light, int color) {
		if (fromQuad >= toQuad) {
			return;
		}
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, consumer) -> {
			float[] v = mesh.vertices();
			int end = toQuad * PumpkinMesh.VERTICES_PER_QUAD * PumpkinMesh.STRIDE;
			for (int i = fromQuad * PumpkinMesh.VERTICES_PER_QUAD * PumpkinMesh.STRIDE; i < end; i += PumpkinMesh.STRIDE) {
				consumer.addVertex(pose, v[i], v[i + 1], v[i + 2])
					.setColor(color)
					.setUv(v[i + 3], v[i + 4])
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light)
					.setNormal(pose, v[i + 5], v[i + 6], v[i + 7]);
			}
		});
	}
}
