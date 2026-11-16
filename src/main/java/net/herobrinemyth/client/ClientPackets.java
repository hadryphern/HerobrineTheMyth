package net.herobrinemyth.client;

import net.herobrinemyth.network.Fx;
import net.herobrinemyth.network.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Client handlers. Only ever loaded on the physical client. */
public final class ClientPackets {

    public static void effect(Net.Effect m) {
        int d = m.duration();
        float k = m.intensity();
        switch (m.type()) {
            case Fx.SILENCE -> ClientHorror.startSilence(d);
            case Fx.FOG -> {
                ClientHorror.fog = Math.max(ClientHorror.fog, d);
                ClientHorror.fogMax = d;
                ClientHorror.fogTarget = Math.max(ClientHorror.fogTarget, k);
            }
            case Fx.JUMPSCARE -> {
                ClientHorror.jumpscare = ClientHorror.jumpscareMax = d;
                ClientHorror.jumpscareIntensity = k;
            }
            case Fx.SHAKE -> {
                // no more screen shaking
            }
            case Fx.BLACKOUT -> ClientHorror.blackout = ClientHorror.blackoutMax = d;
            case Fx.BLINK -> ClientHorror.blink = d;
            case Fx.GLITCH -> {
                ClientHorror.glitch = ClientHorror.glitchMax = d;
                ClientHorror.glitchIntensity = k;
            }
            case Fx.RED_FLASH -> {
                ClientHorror.redFlash = ClientHorror.redFlashMax = d;
                ClientHorror.redIntensity = k;
            }
            case Fx.HEARTBEAT -> ClientHorror.heartbeat = Math.max(ClientHorror.heartbeat, d);
            case Fx.POSSESS -> ClientHorror.startPossession(new Vec3(m.x(), m.y(), m.z()), d, m.extra() == 1);
            case Fx.LOOK_AT -> ClientHorror.startPossession(new Vec3(m.x(), m.y(), m.z()), d, false);
            case Fx.SPIN -> ClientHorror.startSpin(d);
            case Fx.BLOOD -> ClientHorror.blood = ClientHorror.bloodMax = d;
            case Fx.MOOD -> ClientHorror.moodDay = (int) k;
            case Fx.UNMUTE -> ClientHorror.unmute(m.x(), m.y(), m.z(), k, d);
            case Fx.CLEAR -> ClientHorror.clear();
            case Fx.CRASH -> ClientHorror.crashPending = true;
            case Fx.KICK -> ClientHorror.kickPending = (int) k;
            case Fx.DELETE_WORLD -> ClientHorror.takePending = true;
            default -> {
            }
        }
    }

    public static void sound(Net.Sound m) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        double x = m.x(), y = m.y(), z = m.z();
        if (m.relative()) {
            float yaw = p.getYRot() * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
            double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
            Vec3 eye = p.getEyePosition();
            x = eye.x + rx * m.x() + fx * m.z();
            y = eye.y + m.y();
            z = eye.z + rz * m.x() + fz * m.z();
        }
        SoundSource src = m.sound().getPath().startsWith("music_disc") ? SoundSource.RECORDS : SoundSource.HOSTILE;
        SoundInstance s = ClientHorror.play(m.sound(), src, m.volume(), m.pitch(), x, y, z, m.reverb());
        if (m.stopAfter() > 0) ClientHorror.stopLater(s, m.stopAfter());
    }

    public static void possessed(Net.Possessed m) {
        ClientHorror.setPossessed(m.entityId(), m.on());
    }

    public static void text(Net.Text m) {
        ClientHorror.text = m.text();
        ClientHorror.textTicks = ClientHorror.textMax = Math.max(1, m.duration());
        ClientHorror.textStyle = m.style();
    }

    private ClientPackets() {}
}
