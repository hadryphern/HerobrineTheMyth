package net.herobrinemyth.client;

import com.mojang.blaze3d.shaders.FogShape;
import net.herobrinemyth.HerobrineMyth;
import net.herobrinemyth.Config;
import net.herobrinemyth.entity.HerobrineEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/** Everything the player sees and hears, on the client. */
@Mod.EventBusSubscriber(modid = HerobrineMyth.MODID, value = Dist.CLIENT)
public final class ClientHorror {
    static final RandomSource RNG = RandomSource.create();

    // timers (ticks remaining) and their starting values
    static int silence;
    static int fog, fogMax;
    static float fogTarget, fogCurrent;
    static int jumpscare, jumpscareMax;
    static float jumpscareIntensity;
    static int shake, shakeMax;
    static float shakeIntensity;
    static int blackout, blackoutMax;
    static int blink;
    static int glitch, glitchMax;
    static float glitchIntensity;
    static int redFlash, redFlashMax;
    static float redIntensity;
    static int heartbeat, heartbeatTimer;

    // possession
    static int possess, possessMax;
    static boolean possessWalk;
    static Vec3 possessTarget = Vec3.ZERO;
    static float forcedYaw, forcedPitch, forcedYawO, forcedPitchO;
    static int spin;

    // text
    static Component text = Component.empty();
    static int textTicks, textMax, textStyle;

    // proximity dread 0..1
    static float dread, dreadTarget;

    static boolean crashPending, takePending;

    // the day of the haunting, as told by the server
    static int moodDay;
    static int blood, bloodMax;
    private static long clientTicks, lastHeard, lastScream;
    /** Places where he is doing something: sounds there are never muted or quieted. {x, y, z, radius, untilTick} */
    private static final List<double[]> UNMUTED = new ArrayList<>();

    static void unmute(double x, double y, double z, float radius, int ticks) {
        UNMUTED.add(new double[]{x, y, z, radius, clientTicks + ticks});
    }

    private static boolean causedByHim(SoundInstance s) {
        UNMUTED.removeIf(z -> z[4] < clientTicks);
        if (s.isRelative()) return !UNMUTED.isEmpty();
        for (double[] z : UNMUTED) {
            double dx = s.getX() - z[0], dy = s.getY() - z[1], dz = s.getZ() - z[2];
            if (dx * dx + dy * dy + dz * dz <= z[3] * z[3]) return true;
        }
        return false;
    }

    private static final Set<Integer> POSSESSED = new HashSet<>();
    private static final Set<SoundInstance> ALLOWED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<SoundInstance> REVERB = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final List<Object[]> STOPPING = new ArrayList<>();

    public static boolean isPossessed(int id) {
        return POSSESSED.contains(id);
    }

    static void setPossessed(int id, boolean on) {
        if (on) POSSESSED.add(id);
        else POSSESSED.remove(id);
    }

    public static void clear() {
        silence = fog = jumpscare = shake = blackout = blink = glitch = redFlash = heartbeat = possess = textTicks = spin = blood = 0;
        fogTarget = 0;
    }

    // ------------------------------------------------------------------ sound

    /** Plays a sound that is allowed through the silence. */
    public static SoundInstance play(ResourceLocation id, SoundSource source, float volume, float pitch, double x, double y, double z) {
        return play(id, source, volume, pitch, x, y, z, false);
    }

    public static SoundInstance play(ResourceLocation id, SoundSource source, float volume, float pitch, double x, double y, double z, boolean reverb) {
        SimpleSoundInstance s = new SimpleSoundInstance(id, source, volume, pitch, RNG, false, 0,
                SoundInstance.Attenuation.LINEAR, x, y, z, false);
        ALLOWED.add(s);
        if (reverb && Config.REVERB.get()) REVERB.add(s);
        Minecraft.getInstance().getSoundManager().play(s);
        return s;
    }

    /** Runs on the sound thread: gives the marked sounds their echo. */
    @SubscribeEvent
    public static void onSoundSource(net.minecraftforge.client.event.sound.PlaySoundSourceEvent event) {
        if (REVERB.contains(event.getSound())) Reverb.apply(event.getChannel());
    }

    static void stopLater(SoundInstance s, int ticks) {
        STOPPING.add(new Object[]{s, ticks});
    }

    static void startSilence(int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (silence <= 0) {
            mc.getSoundManager().stop();
            mc.getMusicManager().stopPlaying();
        }
        silence = Math.max(silence, ticks);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance s = event.getSound();
        if (s == null) return;
        if (ALLOWED.contains(s) || ALLOWED.contains(event.getOriginalSound())) return;
        String path = s.getLocation().getPath();
        // thunder always breaks the silence: a lightning strike without its sound is no lightning strike
        if (path.startsWith("entity.lightning_bolt")) {
            if (SelfTest.active()) HerobrineMyth.LOGGER.info("[selftest] thunder played (silence={})", silence);
            lastHeard = clientTicks;
            return;
        }
        if (silence > 0 || (dread > 0.35F && s.getSource() == SoundSource.MUSIC)) {
            event.setSound(null);
            return;
        }
        // what he does is never muted: only the world's own natural sounds are
        if (causedByHim(s)) {
            lastHeard = clientTicks;
            return;
        }
        // back then the game was much quieter
        if (Config.CLASSIC_SOUNDS.get() && ClassicSounds.isModern(path)) {
            event.setSound(null);
            return;
        }
        // cave sounds: slow, with a long echo
        if (Config.SLOW_CAVE_SOUNDS.get() && path.equals("ambient.cave")) {
            SimpleSoundInstance slow = new SimpleSoundInstance(s.getLocation(), s.getSource(), 1F, 0.5F + RNG.nextFloat() * 0.12F, RNG,
                    false, 0, s.getAttenuation(), s.getX(), s.getY(), s.getZ(), s.isRelative());
            ALLOWED.add(slow);
            if (Config.REVERB.get()) REVERB.add(slow);
            event.setSound(slow);
        }
        if (s.getSource() != SoundSource.MASTER && s.getSource() != SoundSource.MUSIC) lastHeard = clientTicks;
        // and everything else, quieter
        SoundInstance now = event.getSound();
        float vol = Config.GAME_VOLUME.get().floatValue();
        if (now != null && vol < 0.99F && !(now instanceof net.minecraft.client.resources.sounds.TickableSoundInstance)
                && !ALLOWED.contains(now) && now.getSource() != SoundSource.MASTER) {
            event.setSound(new QuietSound(now, vol));
        }
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (crashPending) {
            crashPending = false;
            Endings.crash();
            return;
        }
        if (takePending) {
            takePending = false;
            Endings.takeWorld();
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (mc.isPaused()) return;

        clientTicks++;
        if (silence > 0) silence--;
        if (blood > 0) blood--;
        quietScream(player);
        if (fog > 0 && --fog == 0) fogTarget = 0;
        fogCurrent = Mth.approach(fogCurrent, fog > 0 ? fogTarget : 0F, fog > 0 ? 0.02F : 0.01F);
        if (jumpscare > 0) jumpscare--;
        if (shake > 0) shake--;
        if (blackout > 0) blackout--;
        if (blink > 0) blink--;
        if (glitch > 0) glitch--;
        if (redFlash > 0) redFlash--;
        if (textTicks > 0) textTicks--;

        // stop sounds that were meant to be cut short
        Iterator<Object[]> it = STOPPING.iterator();
        while (it.hasNext()) {
            Object[] o = it.next();
            int left = (Integer) o[1] - 1;
            if (left <= 0) {
                mc.getSoundManager().stop((SoundInstance) o[0]);
                it.remove();
            } else {
                o[1] = left;
            }
        }

        // how close is he?
        if (player.tickCount % 5 == 0) {
            dreadTarget = 0F;
            for (HerobrineEntity h : mc.level.getEntitiesOfClass(HerobrineEntity.class, player.getBoundingBox().inflate(40))) {
                float d = 1F - (float) (h.distanceTo(player) / 40.0);
                dreadTarget = Math.max(dreadTarget, d);
            }
        }
        dread = Mth.approach(dread, dreadTarget, 0.03F);
        if (dread > 0.35F && mc.getMusicManager() != null) mc.getMusicManager().stopPlaying();

        // heartbeat
        if (heartbeat > 0) heartbeat--;
        if (heartbeat > 0) {
            int interval = dread > 0.75F ? 13 : 20;
            if (--heartbeatTimer <= 0) {
                heartbeatTimer = interval;
                play(new ResourceLocation("entity.warden.heartbeat"), SoundSource.HOSTILE, 1F, 1F,
                        player.getX(), player.getEyeY(), player.getZ());
            }
        }

        // possession: the body turns by itself
        forcedYawO = forcedYaw;
        forcedPitchO = forcedPitch;
        if (possess > 0) {
            possess--;
            Vec3 eye = player.getEyePosition();
            double dx = possessTarget.x - eye.x, dy = possessTarget.y - eye.y, dz = possessTarget.z - eye.z;
            float wantYaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
            float wantPitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180F / Math.PI));
            forcedYaw = Mth.approachDegrees(forcedYaw, wantYaw, possessWalk ? 7F : 4F);
            forcedPitch = Mth.approach(forcedPitch, wantPitch, 3F);
            player.setYRot(forcedYaw);
            player.setXRot(forcedPitch);
            player.setYHeadRot(forcedYaw);
            player.setSprinting(false);
        } else if (spin > 0) {
            spin--;
            forcedYaw += 22F + (spin % 7) * 3F;
            forcedPitch = Mth.approach(forcedPitch, 0F, 6F);
            player.setYRot(forcedYaw);
            player.setXRot(forcedPitch);
            player.setYHeadRot(forcedYaw);
        }
    }

    static void startSpin(int ticks) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        if (spin <= 0 && possess <= 0) {
            forcedYaw = forcedYawO = p.getYRot();
            forcedPitch = forcedPitchO = p.getXRot();
        }
        spin = Math.max(spin, ticks);
    }

    /**
     * When nothing at all has made a sound for a while... a scream, far away, slowed down, echoing.
     * Not always. Not predictable.
     */
    private static void quietScream(LocalPlayer player) {
        if (!Config.QUIET_SCREAMS.get() || moodDay < 4) return;
        long quiet = clientTicks - lastHeard;
        if (quiet < 20 * 25 || clientTicks - lastScream < 20 * 60 * 6) return;
        int chance = Math.max(300, 2400 - moodDay * 40);
        if (RNG.nextInt(chance) != 0) return;
        lastScream = clientTicks;
        double a = RNG.nextDouble() * Math.PI * 2, d = 18 + RNG.nextInt(20);
        String[] screams = {"entity.ghast.scream", "entity.horse.death", "entity.ghast.hurt", "entity.zombie_villager.ambient", "entity.horse.angry"};
        play(new ResourceLocation(screams[RNG.nextInt(screams.length)]), SoundSource.HOSTILE, 4F, 0.5F + RNG.nextFloat() * 0.15F,
                player.getX() + Math.cos(a) * d, player.getEyeY() + RNG.nextInt(6) - 3, player.getZ() + Math.sin(a) * d, true);
        lastHeard = clientTicks;
    }

    static void startPossession(Vec3 target, int ticks, boolean walk) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        if (possess <= 0) {
            forcedYaw = forcedYawO = p.getYRot();
            forcedPitch = forcedPitchO = p.getXRot();
        }
        possess = possessMax = ticks;
        possessWalk = walk;
        possessTarget = target;
    }

    // ------------------------------------------------------------------ control

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (possess <= 0 && spin <= 0) return;
        var in = event.getInput();
        in.forwardImpulse = possessWalk ? 0.8F : 0F;
        in.leftImpulse = 0F;
        in.up = possessWalk;
        in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (possess > 0 || spin > 0) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    // ------------------------------------------------------------------ camera, fog

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        float pt = (float) event.getPartialTick();
        if (possess > 0 || spin > 0) {
            event.setYaw(Mth.rotLerp(pt, forcedYawO, forcedYaw));
            event.setPitch(Mth.lerp(pt, forcedPitchO, forcedPitch));
        }
        if (shakeMax < 0 && shake > 0) { // screen shaking is gone for good
            float m = shakeIntensity * 4F * (shake / (float) shakeMax);
            event.setYaw(event.getYaw() + (RNG.nextFloat() - 0.5F) * m);
            event.setPitch(event.getPitch() + (RNG.nextFloat() - 0.5F) * m);
            event.setRoll(event.getRoll() + (RNG.nextFloat() - 0.5F) * m * 1.5F);
        }
    }

    static float fogAmount() {
        return fogCurrent;
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        float f = fogAmount();
        if (f <= 0.01F) return;
        float far = Mth.lerp(f, event.getFarPlaneDistance(), 6F);
        float near = Mth.lerp(f, event.getNearPlaneDistance(), 0F);
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(Math.min(near, far * 0.8F));
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float f = fogAmount();
        if (f <= 0.01F) return;
        float k = 1F - 0.9F * f;
        event.setRed(event.getRed() * k);
        event.setGreen(event.getGreen() * k);
        event.setBlue(event.getBlue() * k);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        POSSESSED.clear();
        STOPPING.clear();
        Phantoms.clear();
        moodDay = 0;
        fogCurrent = 0;
        dread = dreadTarget = 0;
    }

    private ClientHorror() {}
}
