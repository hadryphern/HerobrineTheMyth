package net.herobrinemyth.server;

import net.herobrinemyth.Config;
import net.herobrinemyth.network.Fx;
import net.herobrinemyth.network.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Small helpers to make one player's life worse. Everything here is per-player:
 * nobody else hears or sees it.
 *
 * Sound rule: only old, classic sounds. Most events make no sound at all.
 */
public final class Fear {
    public static final RandomSource RNG = RandomSource.create();

    /** Cave sounds and old moans. Played slowed and with an echo. */
    public static final String[] AMBIENT = {
            "ambient.cave", "ambient.cave", "ambient.cave", "ambient.cave", "ambient.cave", "ambient.cave",
            "entity.ghast.ambient", "entity.zombie.ambient", "block.portal.ambient"
    };
    /** Classic screams, used rarely. */
    public static final String[] SCREAMS = {
            "entity.ghast.scream", "entity.ghast.scream", "entity.horse.death", "entity.ghast.hurt", "entity.horse.angry",
            "entity.zombie_villager.ambient"
    };
    /** After the totem only. */
    public static final String[] LOUD = {
            "entity.lightning_bolt.thunder", "entity.ghast.scream", "entity.ghast.death", "entity.generic.explode",
            "entity.horse.death", "entity.wither.spawn", "entity.enderman.scream", "entity.zombie.break_wooden_door"
    };

    public static ResourceLocation rl(String id) {
        return id.contains(":") ? new ResourceLocation(id) : new ResourceLocation("minecraft", id);
    }

    public static String pick(String[] arr) {
        return arr[RNG.nextInt(arr.length)];
    }

    public static float range(float min, float max) {
        return min + RNG.nextFloat() * (max - min);
    }

    // ---------------------------------------------------------------- effects

    public static void fx(ServerPlayer p, int type, int duration, float intensity) {
        Net.send(p, new Net.Effect(type, duration, intensity));
    }

    public static void fxAt(ServerPlayer p, int type, int duration, float intensity, Vec3 pos, int extra) {
        Net.send(p, new Net.Effect(type, duration, intensity, pos.x, pos.y, pos.z, extra));
    }

    /** His face, black eyes, on black. One classic scream. Sometimes everyone gets it, even far away. */
    public static void jumpscare(ServerPlayer p) {
        jumpscareOne(p);
        if (shared(0.12)) for (ServerPlayer o : everyone(p)) if (o != p) jumpscareOne(o);
    }

    private static void jumpscareOne(ServerPlayer p) {
        fx(p, Fx.JUMPSCARE, 30, 0.9F);
        sound(p, pick(new String[]{"entity.ghast.scream", "entity.horse.death", "entity.ghast.hurt"}), p.getEyePosition(), 1F, range(0.5F, 0.7F));
    }

    /** A very loud one: his face and a scream right in your ears. */
    public static void loudJumpscare(ServerPlayer p) {
        fx(p, Fx.JUMPSCARE, 34, 0.9F);
        for (int i = 0; i < 3; i++)
            Net.send(p, new Net.Sound(rl(i == 1 ? "entity.ghast.scream" : "entity.ghast.hurt"), 1F, range(0.5F, 0.6F), 0, 0, 0, true, 0, false));
        Net.send(p, new Net.Sound(rl("entity.horse.death"), 1F, 0.5F, 0, 0, 0, true, 0, true));
    }

    // ---------------------------------------------------------------- shared (multiplayer)

    /** In multiplayer, does this one reach the others too? */
    public static boolean shared(double chance) {
        return Config.SHARED_EVENTS.get() && RNG.nextDouble() < chance;
    }

    static java.util.List<ServerPlayer> everyone(ServerPlayer p) {
        return p.getServer() == null ? java.util.List.of(p) : p.getServer().getPlayerList().getPlayers();
    }

    /** Players other than p who are close to pos. */
    static java.util.List<ServerPlayer> near(ServerPlayer p, Vec3 pos, double radius) {
        java.util.List<ServerPlayer> out = new java.util.ArrayList<>();
        for (ServerPlayer o : everyone(p)) {
            if (o != p && o.level() == p.level() && o.position().distanceToSqr(pos) < radius * radius) out.add(o);
        }
        return out;
    }

    private static void soundShared(ServerPlayer p, Net.Sound s) {
        Net.send(p, s);
        if (shared(0.8)) for (ServerPlayer o : near(p, new Vec3(s.x(), s.y(), s.z()), 40)) Net.send(o, s);
    }

    /** The face for 1-2 frames. No sound. Did you see that? */
    public static void subliminal(ServerPlayer p) {
        fx(p, Fx.JUMPSCARE, 2, 0.35F);
    }

    /** Sounds around pos (radius blocks) are his doing: the client must not mute them. */
    public static void unmute(ServerPlayer p, Vec3 pos, float radius, int ticks) {
        fxAt(p, Fx.UNMUTE, ticks, radius, pos, 0);
    }

    // ---------------------------------------------------------------- sounds

    public static void sound(ServerPlayer p, String id, Vec3 pos, float volume, float pitch) {
        soundShared(p, new Net.Sound(rl(id), volume, pitch, pos.x, pos.y, pos.z, false, 0, false));
    }

    /** Slowed down, with a long echo. */
    public static void soundReverb(ServerPlayer p, String id, Vec3 pos, float volume, float pitch) {
        soundShared(p, new Net.Sound(rl(id), volume, pitch, pos.x, pos.y, pos.z, false, 0, true));
    }

    public static void soundStopping(ServerPlayer p, String id, Vec3 pos, float volume, float pitch, int stopAfter) {
        soundShared(p, new Net.Sound(rl(id), volume, pitch, pos.x, pos.y, pos.z, false, stopAfter, true));
    }

    /** right/up/forward relative to where the player is looking when it arrives. */
    public static void soundRel(ServerPlayer p, String id, double right, double up, double forward, float volume, float pitch) {
        Net.send(p, new Net.Sound(rl(id), volume, pitch, right, up, forward, true, 0, false));
    }

    /** Right next to your ear. */
    public static void ear(ServerPlayer p, String id, float volume, float pitch) {
        double side = RNG.nextBoolean() ? 0.7 : -0.7;
        Net.send(p, new Net.Sound(rl(id), volume, pitch, side, 0.1, -0.25, true, 0, true));
    }

    /** A cave sound from somewhere around you: slow, with an echo, from nowhere. */
    public static void caveSound(ServerPlayer p) {
        Vec3 pos = p.position().add(RNG.nextGaussian() * 12, RNG.nextGaussian() * 4, RNG.nextGaussian() * 12);
        soundReverb(p, pick(AMBIENT), pos, 1F, range(0.5F, 0.8F));
    }

    // ---------------------------------------------------------------- "someone" sounds

    public static String stepSound(BlockState s) {
        return s.getSoundType().getStepSound().getLocation().toString();
    }

    /** Footsteps that walk from pos towards dir, and stop the moment the player looks there. */
    public static void phantomSteps(ServerPlayer p, Vec3 from, Vec3 dir, int duration) {
        BlockState under = p.level().getBlockState(BlockPos.containing(from).below());
        if (under.isAir()) under = p.level().getBlockState(p.blockPosition().below());
        phantomShared(p, new Net.Phantom(0, from.x, from.y, from.z, dir.x, dir.z, duration, rl(stepSound(under)), rl(stepSound(under))));
    }

    private static void phantomShared(ServerPlayer p, Net.Phantom ph) {
        Net.send(p, ph);
        if (shared(0.8)) for (ServerPlayer o : near(p, new Vec3(ph.x(), ph.y(), ph.z()), 40)) Net.send(o, ph);
    }

    /** Someone mining at pos. Stops when looked at. */
    public static void phantomMining(ServerPlayer p, BlockPos pos, int duration) {
        BlockState s = p.level().getBlockState(pos);
        Vec3 c = Vec3.atCenterOf(pos);
        phantomShared(p, new Net.Phantom(1, c.x, c.y, c.z, 0, 0, duration,
                s.getSoundType().getHitSound().getLocation(), s.getSoundType().getBreakSound().getLocation()));
    }

    /** Someone building at pos. Stops when looked at. */
    public static void phantomBuilding(ServerPlayer p, BlockPos pos, int duration) {
        BlockState s = p.level().getBlockState(pos);
        if (s.isAir()) s = net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState();
        Vec3 c = Vec3.atCenterOf(pos);
        phantomShared(p, new Net.Phantom(2, c.x, c.y, c.z, 0, 0, duration,
                s.getSoundType().getPlaceSound().getLocation(), s.getSoundType().getBreakSound().getLocation()));
    }

    /** Someone knocking on a door. */
    public static void phantomKnock(ServerPlayer p, Vec3 at, int duration) {
        phantomShared(p, new Net.Phantom(3, at.x, at.y, at.z, 0, 0, duration, rl("entity.zombie.attack_wooden_door"), rl("block.wooden_door.open")));
    }

    // ---------------------------------------------------------------- damage

    /** Hurts the player, but never kills unless the config allows it. */
    public static void hurt(ServerPlayer p, DamageSource src, float amount) {
        if (!Config.ALLOW_KILL.get()) amount = Math.min(amount, Math.max(0F, p.getHealth() - 1F));
        if (amount > 0) p.hurt(src, amount);
    }

    // ---------------------------------------------------------------- text

    public static MutableComponent t(String key, Object... args) {
        return Component.translatable(key, args);
    }

    public static void whisper(ServerPlayer p, Component c, int duration) {
        Net.send(p, new Net.Text(c, duration, 0));
    }

    public static void bigText(ServerPlayer p, Component c, int duration) {
        Net.send(p, new Net.Text(c, duration, 1));
    }

    public static void flashText(ServerPlayer p, Component c) {
        Net.send(p, new Net.Text(c, 3, 2));
    }

    public static void corruptText(ServerPlayer p, Component c, int duration) {
        Net.send(p, new Net.Text(c, duration, 3));
    }

    /** "<Herobrine> ..." in chat, formatted exactly like a real player. Half the time everyone sees it. */
    public static void chat(ServerPlayer p, Component msg) {
        if (!Config.FAKE_CHAT.get()) return;
        Component line = Component.literal("<Herobrine> ").append(msg);
        if (shared(0.5)) for (ServerPlayer o : everyone(p)) o.sendSystemMessage(line);
        else p.sendSystemMessage(line);
    }

    /** "<Herobrine> ..." for everyone, always. */
    public static void chatAll(ServerPlayer p, Component msg) {
        if (!Config.FAKE_CHAT.get()) return;
        Component line = Component.literal("<Herobrine> ").append(msg);
        for (ServerPlayer o : everyone(p)) o.sendSystemMessage(line);
    }

    /** A message with no name at all. */
    public static void nameless(ServerPlayer p, Component msg) {
        if (!Config.FAKE_CHAT.get()) return;
        Component line = Component.literal("<> ").append(msg);
        if (shared(0.5)) for (ServerPlayer o : everyone(p)) o.sendSystemMessage(line);
        else p.sendSystemMessage(line);
    }

    public static void joined(ServerPlayer p) {
        if (!Config.FAKE_CHAT.get()) return;
        p.sendSystemMessage(Component.translatable("multiplayer.player.joined", "Herobrine").withStyle(ChatFormatting.YELLOW));
    }

    public static void left(ServerPlayer p) {
        if (!Config.FAKE_CHAT.get()) return;
        p.sendSystemMessage(Component.translatable("multiplayer.player.left", "Herobrine").withStyle(ChatFormatting.YELLOW));
    }

    private Fear() {}
}
