package net.herobrinemyth.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.herobrinemyth.HerobrineMyth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * Old analog TV "snow": black and white noise, regenerated every frame, with scanlines
 * and a brighter band slowly rolling down the screen.
 */
public final class TvStatic {
    private static final int W = 240, H = 135;
    private static final ResourceLocation ID = new ResourceLocation(HerobrineMyth.MODID, "dynamic/tv_static");
    private static final RandomSource RNG = RandomSource.create();
    private static DynamicTexture texture;
    private static long lastFrame = -1;
    private static float roll;

    private static void regenerate() {
        if (texture == null) {
            texture = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, W, H, false));
            Minecraft.getInstance().getTextureManager().register(ID, texture);
        }
        long frame = System.nanoTime() / 33_000_000L; // ~30 fps of noise, like a real TV
        if (frame == lastFrame) return;
        lastFrame = frame;
        roll = (roll + 1.3F) % (H + 40);
        NativeImage img = texture.getPixels();
        if (img == null) return;
        for (int y = 0; y < H; y++) {
            float band = Math.max(0F, 1F - Math.abs(y - roll + 20) / 14F) * 70F;
            boolean scan = (y & 1) == 1;
            int lineShift = RNG.nextInt(40) == 0 ? 60 : 0; // the odd brighter line
            for (int x = 0; x < W; x++) {
                int v = RNG.nextInt(256);
                v = (int) (v * (scan ? 0.72F : 1F) + band + lineShift);
                if (v > 255) v = 255;
                img.setPixelRGBA(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
            }
        }
        texture.upload();
    }

    /** Draws the noise over the whole screen at the given opacity (0..1). */
    public static void draw(GuiGraphics g, int w, int h, float alpha) {
        if (alpha <= 0.01F) return;
        regenerate();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1F, 1F, 1F, Math.min(1F, alpha));
        g.blit(ID, 0, 0, w, h, 0, 0, W, H, W, H);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
    }

    private TvStatic() {}
}
