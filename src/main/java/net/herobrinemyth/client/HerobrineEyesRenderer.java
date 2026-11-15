package net.herobrinemyth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.herobrinemyth.entity.HerobrineEyesEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Just the eyes. They blink, sometimes. */
public class HerobrineEyesRenderer extends EntityRenderer<HerobrineEyesEntity> {

    public HerobrineEyesRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(HerobrineEyesEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        int t = e.tickCount + e.getId() * 37;
        if (t % 97 < 3 || (t % 211 > 205)) return; // blink
        pose.pushPose();
        pose.translate(0, 0.2, 0);
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(EyeQuads.WHITE));
        float p = 1F / 16F;
        EyeQuads.rect(pose, vc, -3 * p, 0, -1 * p, p, 0, LightTexture.FULL_BRIGHT);
        EyeQuads.rect(pose, vc, 1 * p, 0, 3 * p, p, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(HerobrineEyesEntity e) {
        return EyeQuads.WHITE;
    }
}
