package net.herobrinemyth.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.herobrinemyth.HerobrineMyth;
import net.herobrinemyth.Config;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The overworld sky: always overcast. Grey by day, dark by night, no sun, no moon, no stars.
 * During blood rain, the rain is red.
 */
@Mod.EventBusSubscriber(modid = HerobrineMyth.MODID, value = Dist.CLIENT)
public final class Sky {

    /** Vanilla's overworld effects, with the sky itself left out. The background is the fog colour: grey. */
    public static final class Overcast extends DimensionSpecialEffects.OverworldEffects {
        @Override
        public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack pose, Camera camera,
                                 Matrix4f projection, boolean foggy, Runnable setupFog) {
            return Config.OVERCAST.get();
        }

        /** Daylight is dimmer, as if the clouds were thick. Torches and lamps stay as bright as ever. */
        @Override
        public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker,
                                         float skyLight, int pixelX, int pixelY, org.joml.Vector3f colors) {
            if (Config.OVERCAST.get()) {
                float sky = pixelY / 15F, block = pixelX / 15F;
                float k = 1F - 0.45F * sky * (1F - block) * (1F - skyDarken);
                colors.mul(k);
            }
            if (Config.OLD_LIGHTING.get()) {
                // the old light: grey, no warm torches, no blue nights
                float grey = colors.x() * 0.3F + colors.y() * 0.59F + colors.z() * 0.11F;
                colors.set(grey, grey, grey);
            }
        }

        @Override
        public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture light,
                                         double camX, double camY, double camZ) {
            if (bloody()) RenderSystem.setShaderColor(0.75F, 0.02F, 0.02F, 1.0F);
            return false;
        }

        /** In blood rain the drops that hit the ground are red too, not the blue water splashes. */
        @Override
        public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
            if (!bloody()) return false;
            float rain = level.getRainLevel(1F);
            if (rain <= 0F) return true;
            net.minecraft.util.RandomSource r = level.random;
            net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(camera.getPosition());
            net.minecraft.core.particles.DustParticleOptions drop =
                    new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.5F, 0F, 0F), 0.45F);
            net.minecraft.core.BlockPos sound = null;
            int count = (int) (25F * rain * rain);
            for (int i = 0; i < count; i++) {
                net.minecraft.core.BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        at.offset(r.nextInt(21) - 10, 0, r.nextInt(21) - 10));
                if (top.getY() > at.getY() + 10 || top.distSqr(at) > 400) continue;
                level.addParticle(drop, top.getX() + r.nextDouble(), top.getY() + 0.1, top.getZ() + r.nextDouble(), 0, 0, 0);
                sound = top;
            }
            // keep the sound of the rain
            if (sound != null && ticks % 4 == 0) {
                boolean above = sound.getY() > camera.getPosition().y + 1 && level.getHeightmapPos(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, at).getY() > camera.getPosition().y;
                level.playLocalSound(sound, above ? net.minecraft.sounds.SoundEvents.WEATHER_RAIN_ABOVE : net.minecraft.sounds.SoundEvents.WEATHER_RAIN,
                        net.minecraft.sounds.SoundSource.WEATHER, above ? 0.1F : 0.2F, above ? 0.5F : 1F, false);
            }
            return true;
        }
    }

    /** Every rain is blood (by default), not only his blood rain. */
    static boolean bloody() {
        return ClientHorror.blood > 0 || Config.ALWAYS_BLOOD_RAIN.get();
    }

    @SubscribeEvent
    public static void afterWeather(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    /** The fog (and so the sky behind it) turns grey, like heavy clouds, without changing how dark it is. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (!Config.OVERCAST.get()) return;
        if (event.getCamera().getEntity().level().dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        float r = event.getRed(), g = event.getGreen(), b = event.getBlue();
        float lum = r * 0.3F + g * 0.59F + b * 0.11F;
        float grey = lum * 0.45F;
        float k = 0.88F;
        event.setRed(r + (grey - r) * k);
        event.setGreen(g + (grey - g) * k);
        event.setBlue(b + (grey * 1.04F - b) * k);
    }

    private Sky() {}
}
