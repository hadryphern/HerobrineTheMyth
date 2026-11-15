package net.herobrinemyth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.herobrinemyth.HerobrineMyth;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** The white eyes. Drawn as two tiny quads, the way the legend describes them. */
public final class EyeQuads {
    public static final ResourceLocation WHITE = new ResourceLocation(HerobrineMyth.MODID, "textures/entity/white.png");
    public static final ResourceLocation STEVE = new ResourceLocation("textures/entity/player/wide/steve.png");

    /**
     * Draws eyes on the front of a humanoid head. The pose must already be in head space
     * (after {@code head.translateAndRotate}). Units are model pixels: the face is at z = -4,
     * the eyes span x -3..-1 and 1..3, one pixel tall starting at {@code eyeTop}.
     */
    public static void head(PoseStack pose, MultiBufferSource buffers, boolean glow, int light, float eyeTop) {
        VertexConsumer vc = buffers.getBuffer(glow ? RenderType.eyes(WHITE) : RenderType.entityCutoutNoCull(WHITE));
        int l = glow ? LightTexture.FULL_BRIGHT : light;
        float p = 1F / 16F;
        float z = -4.04F * p;
        rect(pose, vc, -3 * p, eyeTop * p, -1 * p, (eyeTop + 1) * p, z, l);
        rect(pose, vc, 1 * p, eyeTop * p, 3 * p, (eyeTop + 1) * p, z, l);
    }

    /**
     * Two eyes on any head. All values are model pixels in head space: the face plane z,
     * the top of the eyes, and the left/right eye spans on x.
     */
    public static void face(PoseStack pose, MultiBufferSource buffers, boolean glow, int light, float z, float eyeTop,
                            float l0, float l1, float r0, float r1) {
        VertexConsumer vc = buffers.getBuffer(glow ? RenderType.eyes(WHITE) : RenderType.entityCutoutNoCull(WHITE));
        int lt = glow ? LightTexture.FULL_BRIGHT : light;
        float p = 1F / 16F;
        float zz = (z - 0.04F) * p;
        rect(pose, vc, l0 * p, eyeTop * p, l1 * p, (eyeTop + 1) * p, zz, lt);
        rect(pose, vc, r0 * p, eyeTop * p, r1 * p, (eyeTop + 1) * p, zz, lt);
    }

    public static void rect(PoseStack pose, VertexConsumer vc, float x0, float y0, float x1, float y1, float z, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        v(vc, m, n, x0, y0, z, 0, 0, light, -1);
        v(vc, m, n, x0, y1, z, 0, 1, light, -1);
        v(vc, m, n, x1, y1, z, 1, 1, light, -1);
        v(vc, m, n, x1, y0, z, 1, 0, light, -1);
        // and the other winding so it shows from any side
        v(vc, m, n, x1, y0, z, 1, 0, light, 1);
        v(vc, m, n, x1, y1, z, 1, 1, light, 1);
        v(vc, m, n, x0, y1, z, 0, 1, light, 1);
        v(vc, m, n, x0, y0, z, 0, 0, light, 1);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float vv, int light, float nz) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(n, 0, 0, nz).endVertex();
    }

    private EyeQuads() {}
}
