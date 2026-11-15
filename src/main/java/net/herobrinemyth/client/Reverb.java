package net.herobrinemyth.client;

import com.mojang.blaze3d.audio.Channel;
import net.herobrinemyth.HerobrineMyth;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;

import java.lang.reflect.Field;

/**
 * A long, dark echo for cave sounds and his sounds, using OpenAL's EFX reverb.
 * Every call here runs on Minecraft's sound thread (inside PlaySoundSourceEvent).
 * If the sound card / OpenAL does not support it, it silently turns itself off.
 */
final class Reverb {
    private static boolean failed;
    private static int slot = -1;
    private static Field sourceField;

    private static boolean init() {
        if (failed) return false;
        if (slot >= 0) return true;
        try {
            sourceField = ObfuscationReflectionHelper.findField(Channel.class, "f_83642_");
            int effect = EXTEfx.alGenEffects();
            EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_REVERB);
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DENSITY, 1.0F);
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DIFFUSION, 1.0F);
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_GAIN, 0.5F);
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_GAINHF, 0.35F);
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DECAY_TIME, 7.5F);
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DECAY_HFRATIO, 0.4F);
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_REFLECTIONS_GAIN, 0.6F);
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_REFLECTIONS_DELAY, 0.12F);
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_LATE_REVERB_GAIN, 1.6F);
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_LATE_REVERB_DELAY, 0.09F);
            slot = EXTEfx.alGenAuxiliaryEffectSlots();
            EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, effect);
            if (AL10.alGetError() != AL10.AL_NO_ERROR) throw new IllegalStateException("OpenAL error while creating the reverb");
            HerobrineMyth.LOGGER.info("Reverb ready.");
            return true;
        } catch (Throwable t) {
            failed = true;
            HerobrineMyth.LOGGER.warn("No reverb available ({}). Sounds will play without echo.", t.toString());
            return false;
        }
    }

    static void apply(Channel channel) {
        if (!init()) return;
        try {
            int source = sourceField.getInt(channel);
            AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, slot, 0, EXTEfx.AL_FILTER_NULL);
            AL10.alGetError();
        } catch (Throwable t) {
            failed = true;
            HerobrineMyth.LOGGER.warn("Reverb disabled: {}", t.toString());
        }
    }

    private Reverb() {}
}
