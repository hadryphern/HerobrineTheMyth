package net.herobrinemyth.server;

import net.herobrinemyth.HerobrineMyth;
import net.herobrinemyth.entity.HerobrineEntity;
import net.herobrinemyth.network.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * HerobrineAI's graveyard: you are somewhere else, at night, among graves.
 * He is at the far end of the path. Every few seconds he is closer.
 */
public final class Graveyard {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(HerobrineMyth.MODID, "graveyard"));
    private static final int GROUND = 5;
    private static final Vec3 ARRIVE = new Vec3(-2.5, GROUND, 12.5);
    private static final double[] STEPS = {-12.5, -8.5, -4.5, -0.5, 3.5, 7.5, 10.5};

    private static final class Session {
        final ResourceKey<Level> backDim;
        final Vec3 back;
        final float yaw, pitch;
        int t;
        HerobrineEntity him;

        Session(ResourceKey<Level> backDim, Vec3 back, float yaw, float pitch) {
            this.backDim = backDim;
            this.back = back;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static boolean isGraveyard(Level level) {
        return level.dimension() == KEY;
    }

    public static boolean send(ServerPlayer p) {
        if (Artifacts.protectedFromHim(p) || SESSIONS.containsKey(p.getUUID()) || isGraveyard(p.level())) return false;
        MinecraftServer server = p.getServer();
        ServerLevel g = server == null ? null : server.getLevel(KEY);
        if (g == null) return false;
        build(g);
        clearMobs(g);
        Session s = new Session(p.level().dimension(), p.position(), p.getYRot(), p.getXRot());
        SESSIONS.put(p.getUUID(), s);
        Fear.fx(p, Fx.BLACKOUT, 40, 1F);
        Fear.ear(p, "entity.enderman.teleport", 1F, 0.4F);
        p.teleportTo(g, ARRIVE.x, ARRIVE.y, ARRIVE.z, 180F, 0F);
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> e = it.next();
            Session s = e.getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            if (!isGraveyard(p.level())) {
                it.remove();
                continue;
            }
            s.t++;
            ServerLevel g = p.serverLevel();
            if (s.t % 20 == 0) clearMobs(g);
            if (s.t == 20) {
                s.him = HerobrineEntity.spawn(p, new Vec3(ARRIVE.x, GROUND, STEPS[0]), HerobrineEntity.Mode.LURE, 20 * 40);
                Fear.fx(p, Fx.HEARTBEAT, 20 * 30, 1F);
            }
            int step = (s.t - 20) / 50;
            if (s.him != null && !s.him.isRemoved() && s.t > 20 && (s.t - 20) % 50 == 0 && step < STEPS.length) {
                s.him.teleportTo(ARRIVE.x, GROUND, STEPS[step]);
                Fear.sound(p, "block.gravel.step", s.him.position(), 0.6F, 0.8F);
                if (step == 3) Fear.whisper(p, Component.translatable("herobrine.whisper.behind"), 40);
            }
            boolean gone = s.t > 40 && (s.him == null || s.him.isRemoved());
            if (gone || s.t > 20 * 25) {
                it.remove();
                if (s.him != null && !s.him.isRemoved()) {
                    Fear.jumpscare(p);
                    s.him.vanish(false);
                }
                Fear.fx(p, Fx.BLACKOUT, 40, 1F);
                ServerLevel back = server.getLevel(s.backDim);
                if (back == null) back = server.overworld();
                final ServerLevel to = back;
                Scheduler.later(15, () -> {
                    ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
                    if (sp != null && isGraveyard(sp.level())) {
                        sp.teleportTo(to, s.back.x, s.back.y, s.back.z, s.yaw, s.pitch);
                        Fear.fx(sp, Fx.CLEAR, 1, 0F);
                    }
                });
            }
        }
    }

    /** Someone who logs in inside the graveyard is put back at the world spawn. */
    public static void rescue(ServerPlayer p) {
        if (!isGraveyard(p.level()) || SESSIONS.containsKey(p.getUUID())) return;
        ServerLevel ow = p.getServer().overworld();
        BlockPos spawn = ow.getSharedSpawnPos();
        p.teleportTo(ow, spawn.getX() + 0.5, ow.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ()), spawn.getZ() + 0.5, 0F, 0F);
    }

    private static void clearMobs(ServerLevel g) {
        for (Entity en : g.getEntities((Entity) null, new AABB(-64, 0, -64, 64, 128, 64),
                en -> !(en instanceof Player) && !(en instanceof HerobrineEntity))) {
            en.discard();
        }
    }

    private static void set(ServerLevel g, int x, int y, int z, BlockState s) {
        g.setBlock(new BlockPos(x, y, z), s, Block.UPDATE_CLIENTS);
    }

    /** The same graveyard every time. */
    static void build(ServerLevel g) {
        Random r = new Random(666);
        for (int x = -16; x <= 16; x++)
            for (int z = -18; z <= 18; z++) {
                for (int y = GROUND; y <= GROUND + 10; y++) set(g, x, y, z, Blocks.AIR.defaultBlockState());
                set(g, x, GROUND - 1, z, (r.nextInt(5) == 0 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK).defaultBlockState());
            }
        // the path he walks
        for (int z = -18; z <= 18; z++) {
            set(g, -3, GROUND - 1, z, Blocks.GRAVEL.defaultBlockState());
            set(g, -2, GROUND - 1, z, Blocks.GRAVEL.defaultBlockState());
        }
        // fence all around
        for (int x = -15; x <= 15; x++) {
            set(g, x, GROUND, -17, Blocks.DARK_OAK_FENCE.defaultBlockState());
            if (x < -4 || x > -1) set(g, x, GROUND, 17, Blocks.DARK_OAK_FENCE.defaultBlockState());
        }
        for (int z = -17; z <= 17; z++) {
            set(g, -15, GROUND, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
            set(g, 15, GROUND, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
        }
        String[] names = {"Steve", "???", "Alex", "you", "R.I.P.", "...", "he saw him", "do not dig", "Herobrine?", "not alone"};
        BlockState[] stones = {Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
                Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState()};
        int[] xs = {-12, -8, 3, 7, 11};
        int[] zs = {-14, -9, -4, 1, 6};
        for (int gx : xs) {
            for (int gz : zs) {
                if (r.nextInt(6) == 0) continue;
                set(g, gx, GROUND, gz, stones[r.nextInt(stones.length)]);
                set(g, gx, GROUND - 1, gz + 1, Blocks.PODZOL.defaultBlockState());
                set(g, gx, GROUND - 1, gz + 2, Blocks.COARSE_DIRT.defaultBlockState());
                Builder.placeSign(g, new BlockPos(gx, GROUND, gz + 1), new Vec3(gx + 0.5, GROUND, gz + 20),
                        Component.literal("R.I.P."), Component.literal(names[r.nextInt(names.length)]), Component.empty(), Component.empty());
                if (r.nextInt(3) == 0) set(g, gx + 1, GROUND, gz + 2, Blocks.DEAD_BUSH.defaultBlockState());
                if (r.nextInt(4) == 0) set(g, gx - 1, GROUND, gz, Blocks.COBWEB.defaultBlockState());
            }
        }
        // a dead tree
        for (int y = 0; y < 7; y++) set(g, 9, GROUND + y, -15, Blocks.DARK_OAK_LOG.defaultBlockState());
        set(g, 10, GROUND + 5, -15, Blocks.DARK_OAK_LOG.defaultBlockState());
        set(g, 8, GROUND + 4, -15, Blocks.DARK_OAK_LOG.defaultBlockState());
        set(g, 9, GROUND + 6, -14, Blocks.DARK_OAK_LOG.defaultBlockState());
        // two weak lights at the gate
        set(g, -5, GROUND + 1, 17, Blocks.SOUL_LANTERN.defaultBlockState());
        set(g, -5, GROUND, 17, Blocks.DARK_OAK_FENCE.defaultBlockState());
        set(g, 0, GROUND + 1, 17, Blocks.SOUL_LANTERN.defaultBlockState());
        set(g, 0, GROUND, 17, Blocks.DARK_OAK_FENCE.defaultBlockState());
        // his own grave at the end of the path
        set(g, -3, GROUND, -16, Blocks.NETHERRACK.defaultBlockState());
        set(g, -2, GROUND, -16, Blocks.NETHERRACK.defaultBlockState());
        set(g, -3, GROUND + 1, -16, Blocks.FIRE.defaultBlockState());
        set(g, -2, GROUND + 1, -16, Blocks.FIRE.defaultBlockState());
    }

    public static void clear() {
        SESSIONS.clear();
    }

    private Graveyard() {}
}
