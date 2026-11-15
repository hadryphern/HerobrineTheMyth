package net.herobrinemyth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.herobrinemyth.entity.HerobrineEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** Steve's skin. Not Steve. */
public class HerobrineRenderer extends HumanoidMobRenderer<HerobrineEntity, HerobrineModel> {

    public HerobrineRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HerobrineModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.4F);
        this.addLayer(new Eyes(this));
    }

    @Override
    public ResourceLocation getTextureLocation(HerobrineEntity entity) {
        return EyeQuads.STEVE;
    }

    @Override
    protected boolean shouldShowName(HerobrineEntity entity) {
        return false;
    }

    static final class Eyes extends RenderLayer<HerobrineEntity, HerobrineModel> {
        Eyes(RenderLayerParent<HerobrineEntity, HerobrineModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, HerobrineEntity e, float limbSwing,
                           float limbAmount, float partialTick, float age, float headYaw, float headPitch) {
            if (e.isInvisible() || e.getMode() == HerobrineEntity.Mode.STEVE) return;
            pose.pushPose();
            this.getParentModel().head.translateAndRotate(pose);
            EyeQuads.head(pose, buffers, e.eyesGlow(), light, -4F);
            pose.popPose();
        }
    }
}
