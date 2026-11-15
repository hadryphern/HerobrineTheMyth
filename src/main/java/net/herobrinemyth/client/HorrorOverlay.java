package net.herobrinemyth.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import static net.herobrinemyth.client.ClientHorror.*;

/** Everything drawn on top of the screen. */
public final class HorrorOverlay {

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        g.pose().pushPose();
        g.pose().translate(0F, 0F, 600F); // above chat and everything else
        draw(g, mc.font, w, h);
        g.pose().popPose();
    }

    private static void draw(GuiGraphics g, Font font, int w, int h) {
        String label = net.herobrinemyth.Config.VERSION_LABEL.get();
        if (!label.isEmpty() && !Minecraft.getInstance().options.renderDebug) {
            g.drawString(font, label, 2, 2, 0xFFFFFFFF, true);
        }

        if (redFlash > 0 && redFlashMax > 0) {
            float k = redIntensity * (redFlash / (float) redFlashMax) * 0.55F;
            g.fill(0, 0, w, h, ((int) (k * 255) << 24) | 0x8A0000);
        }

        if (glitch > 0) drawGlitch(g, font, w, h);

        if (textTicks > 0) drawText(g, font, w, h);

        if (jumpscare > 0) drawFace(g, w, h);

        if (blackout > 0 && blackoutMax > 0) {
            int elapsed = blackoutMax - blackout;
            float a = Math.min(1F, Math.min(elapsed / 5F, blackout / 10F));
            g.fill(0, 0, w, h, ((int) (Mth.clamp(a, 0F, 1F) * 255) << 24));
        }
        if (blink > 0) g.fill(0, 0, w, h, 0xFF000000);
    }

    /**
     * His face. It does not move, it does not shake. Normal, human eyes. Black around it.
     * It just looks at you. The subtle version is a ghost of it for a frame or two.
     */
    private static void drawFace(GuiGraphics g, int w, int h) {
        float progress = 1F - jumpscare / (float) Math.max(1, jumpscareMax);
        boolean subtle = jumpscareIntensity < 0.5F;
        // draw at real screen pixels so the eyes can have detail
        double gs = Minecraft.getInstance().getWindow().getGuiScale();
        int W = (int) Math.ceil(w * gs), H = (int) Math.ceil(h * gs);
        g.pose().pushPose();
        g.pose().scale((float) (1 / gs), (float) (1 / gs), 1F);
        float alpha = subtle ? 0.35F : Math.min(1F, progress * 6F);
        int size = (int) (Math.min(W, H) * (subtle ? 0.55F : 0.64F + 0.06F * progress));
        int x = (W - size) / 2, y = (H - size) / 2;
        if (!subtle) g.fill(0, 0, W, H, 0xFF000000);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(0.5F, 0.5F, 0.5F, alpha);
        g.blit(EyeQuads.STEVE, x, y, size, size, 8, 8, 8, 8, 64, 64);
        g.blit(EyeQuads.STEVE, x, y, size, size, 40, 8, 8, 8, 64, 64);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
        {
            float px = size / 8F;
            // the whole eye cells of the skin, black: nothing of the old eye shows through
            int black = subtle ? 0xE0000000 : 0xFF000000;
            int top = y + (int) Math.floor(4 * px), bottom = y + (int) Math.ceil(5 * px);
            RenderSystem.enableBlend();
            g.fill(x + (int) Math.floor(1 * px), top, x + (int) Math.ceil(3 * px), bottom, black);
            g.fill(x + (int) Math.floor(5 * px), top, x + (int) Math.ceil(7 * px), bottom, black);
            RenderSystem.disableBlend();
        }
        g.pose().popPose();
    }

    private static void drawText(GuiGraphics g, Font font, int w, int h) {
        float life = textTicks / (float) Math.max(1, textMax);
        float fade = Math.min(1F, Math.min((1F - life) * 6F, life * 4F));
        int a = Mth.clamp((int) (fade * 255), 8, 255);
        switch (textStyle) {
            case 0 -> { // whisper, faint, low
                int col = (Mth.clamp((int) (a * 0.6F), 8, 255) << 24) | 0xB0B0B0;
                MutableComponent c = text.copy().withStyle(ChatFormatting.ITALIC);
                g.drawString(font, c, (w - font.width(c)) / 2, (int) (h * 0.66F), col, false);
            }
            case 1 -> { // big red
                g.pose().pushPose();
                g.pose().translate(w / 2F, h / 2F - 20, 0);
                g.pose().scale(3F, 3F, 1F);
                int col = (a << 24) | 0xAA0000;
                g.drawString(font, text, -font.width(text) / 2, 0, col, false);
                g.pose().popPose();
            }
            case 2 -> { // subliminal
                g.pose().pushPose();
                g.pose().translate(w / 2F, h / 2F - 30, 0);
                g.pose().scale(5F, 5F, 1F);
                g.drawString(font, text, -font.width(text) / 2, 0, 0xFFFFFFFF, false);
                g.pose().popPose();
            }
            default -> { // corrupted
                String s = text.getString();
                MutableComponent c = Component.empty();
                for (int i = 0; i < s.length(); i++) {
                    String ch = String.valueOf(s.charAt(i));
                    c.append(RNG.nextInt(6) == 0
                            ? Component.literal(ch).withStyle(ChatFormatting.OBFUSCATED, ChatFormatting.DARK_RED)
                            : Component.literal(ch).withStyle(ChatFormatting.RED));
                }
                g.pose().pushPose();
                g.pose().translate(w / 2F + RNG.nextInt(5) - 2, h / 2F + RNG.nextInt(5) - 2, 0);
                g.pose().scale(2F, 2F, 1F);
                g.drawString(font, c, -font.width(c) / 2, 0, (a << 24) | 0xFFFFFF, false);
                g.pose().popPose();
            }
        }
    }

    /** Old TV snow: black and white noise, strongest at the start, flickering. */
    private static void drawGlitch(GuiGraphics g, Font font, int w, int h) {
        float life = glitch / (float) Math.max(1, glitchMax);
        float k = glitchIntensity * (0.55F + 0.45F * life);
        // the signal comes and goes
        if (RNG.nextInt(7) == 0) k *= 0.35F;
        TvStatic.draw(g, w, h, Math.min(0.95F, k));
        if (glitchIntensity > 0.7F && RNG.nextInt(10) == 0) {
            Component c = Component.literal("HEROBRINE").withStyle(ChatFormatting.OBFUSCATED);
            g.drawString(font, c, RNG.nextInt(Math.max(1, w - 60)), RNG.nextInt(Math.max(1, h - 10)), 0xC0FFFFFF, false);
        }
    }

    private HorrorOverlay() {}
}
