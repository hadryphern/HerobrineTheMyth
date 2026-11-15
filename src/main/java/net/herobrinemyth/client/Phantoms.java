package net.herobrinemyth.client;

import net.herobrinemyth.HerobrineMyth;
import net.herobrinemyth.network.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Sounds with someone behind them. Footsteps, mining, building, knocking.
 * The moment you turn and look where they come from, they stop. He knows you noticed.
 */
@Mod.EventBusSubscriber(modid = HerobrineMyth.MODID, value = Dist.CLIENT)
public final class Phantoms {
    private static final List<Phantom> ACTIVE = new ArrayList<>();

    private static final class Phantom {
        final int kind;
        Vec3 pos;
        final Vec3 dir;
        final ResourceLocation sound, sound2;
        int life, timer, hits;
        SoundInstance last;

        Phantom(Net.Phantom m) {
            this.kind = m.kind();
            this.pos = new Vec3(m.x(), m.y(), m.z());
            Vec3 d = new Vec3(m.dx(), 0, m.dz());
            this.dir = d.lengthSqr() > 1.0E-4 ? d.normalize() : Vec3.ZERO;
            this.sound = m.sound();
            this.sound2 = m.sound2();
            this.life = m.duration();
            this.timer = 10 + ClientHorror.RNG.nextInt(20);
        }
    }

    public static void start(Net.Phantom m) {
        ACTIVE.add(new Phantom(m));
    }

    /** Is the player looking towards that point? (no line of sight needed: looking at the wall counts) */
    static boolean lookingAt(LocalPlayer p, Vec3 at, double cos) {
        Vec3 to = at.subtract(p.getEyePosition());
        if (to.lengthSqr() < 1.0E-3) return true;
        return p.getViewVector(1F).dot(to.normalize()) > cos;
    }

    private static void play(Phantom ph, ResourceLocation id, float volume, float pitch) {
        ph.last = ClientHorror.play(id, SoundSource.HOSTILE, volume, pitch, ph.pos.x, ph.pos.y, ph.pos.z);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.isPaused()) {
            if (p == null) ACTIVE.clear();
            return;
        }
        Iterator<Phantom> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Phantom ph = it.next();
            double dist = ph.pos.distanceTo(p.getEyePosition());
            // you looked. it stops. right now, mid-sound.
            if (lookingAt(p, ph.pos, 0.9) || dist < 2.2 || --ph.life <= 0) {
                if (ph.last != null && ph.life > 0) mc.getSoundManager().stop(ph.last);
                it.remove();
                continue;
            }
            if (--ph.timer > 0) continue;
            switch (ph.kind) {
                case 0 -> { // footsteps: a slow walk, getting closer
                    play(ph, ph.sound, 0.35F, 0.85F + ClientHorror.RNG.nextFloat() * 0.15F);
                    if (dist > 3.5) ph.pos = ph.pos.add(ph.dir.scale(0.65));
                    ph.timer = 8 + ClientHorror.RNG.nextInt(3);
                    if (ClientHorror.RNG.nextInt(14) == 0) ph.timer += 30; // a pause. listening.
                }
                case 1 -> { // mining: hits, then the block breaks, then a pause
                    if (++ph.hits <= 5 + ClientHorror.RNG.nextInt(3)) {
                        play(ph, ph.sound, 0.5F, 0.6F);
                        ph.timer = 4;
                    } else {
                        play(ph, ph.sound2, 0.8F, 0.8F);
                        ph.hits = 0;
                        ph.timer = 25 + ClientHorror.RNG.nextInt(40);
                    }
                }
                case 2 -> { // building
                    play(ph, ph.sound, 0.6F, 0.8F);
                    ph.timer = 10 + ClientHorror.RNG.nextInt(25);
                }
                default -> { // knocking, three by three
                    play(ph, ph.sound, 0.7F, 0.7F + ClientHorror.RNG.nextFloat() * 0.1F);
                    ph.timer = ++ph.hits % 3 == 0 ? 50 + ClientHorror.RNG.nextInt(60) : 9;
                }
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private Phantoms() {}
}
