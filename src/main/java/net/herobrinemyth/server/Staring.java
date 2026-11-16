package net.herobrinemyth.server;

import net.herobrinemyth.entity.HerobrineEntity;
import net.herobrinemyth.network.Net;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Normal mobs, no white eyes, doing something they should not: they stop and stare.
 * Or they only stare while your back is turned, and when you look they are just animals again.
 * Or one of them says "pss..." as you pass, and when you look it is eating grass.
 */
public final class Staring {
    private enum Kind { ALWAYS, BACK_TURNED, PSS }

    private static final class Info {
        final ResourceKey<Level> dim;
        final UUID player;
        final Kind kind;
        final long until;
        boolean frozen, done;

        Info(ResourceKey<Level> dim, UUID player, Kind kind, long until) {
            this.dim = dim;
            this.player = player;
            this.kind = kind;
            this.until = until;
        }
    }

    private static final Map<UUID, Info> MOBS = new HashMap<>();

    private static boolean usable(Mob m) {
        return !(m instanceof HerobrineEntity) && !Possession.isPossessed(m) && !MOBS.containsKey(m.getUUID()) && !m.isNoAi();
    }

    private static List<Mob> around(ServerPlayer p, double r) {
        return p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(r, 6, r), Staring::usable);
    }

    /** One to three animals stop what they are doing and stare at you. Walk left, walk right: their heads follow. */
    public static boolean animals(ServerPlayer p) {
        List<Mob> list = around(p, 24);
        list.removeIf(m -> !(m instanceof Animal) && !(m instanceof net.minecraft.world.entity.npc.Villager));
        if (list.isEmpty()) return false;
        java.util.Collections.shuffle(list, new java.util.Random(Fear.RNG.nextLong()));
        int n = Math.min(list.size(), 1 + Fear.RNG.nextInt(3));
        long until = Scheduler.now() + 20 * (25 + Fear.RNG.nextInt(35));
        for (int i = 0; i < n; i++) add(list.get(i), p, Kind.ALWAYS, until);
        return true;
    }

    /** Every mob around stares at you, but only while you are not looking at it. */
    public static boolean backTurned(ServerPlayer p) {
        List<Mob> list = around(p, 28);
        if (list.isEmpty()) return false;
        long until = Scheduler.now() + 20 * (60 + Fear.RNG.nextInt(60));
        for (Mob m : list) add(m, p, Kind.BACK_TURNED, until);
        return true;
    }

    /** Pass close to a mob and it whispers. Look at it and it is just a mob. */
    public static boolean pss(ServerPlayer p) {
        List<Mob> list = around(p, 20);
        if (list.isEmpty()) return false;
        Mob m = list.get(Fear.RNG.nextInt(list.size()));
        add(m, p, Kind.PSS, Scheduler.now() + 20 * 120);
        return true;
    }

    private static void add(Mob m, ServerPlayer p, Kind kind, long until) {
        MOBS.put(m.getUUID(), new Info(m.level().dimension(), p.getUUID(), kind, until));
        m.setPersistenceRequired();
    }

    private static void face(Mob mob, ServerPlayer p) {
        float yaw = Spots.yawTo(mob.position(), p.position());
        mob.setYHeadRot(yaw);
        mob.setXRot(Spots.pitchTo(mob.getEyePosition(), p.getEyePosition()));
        if (mob.getNavigation().isInProgress()) mob.getNavigation().stop();
    }

    public static void tick(MinecraftServer server) {
        if (MOBS.isEmpty()) return;
        long now = Scheduler.now();
        Iterator<Map.Entry<UUID, Info>> it = MOBS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Info> en = it.next();
            Info info = en.getValue();
            ServerLevel level = server.getLevel(info.dim);
            Entity e = level == null ? null : level.getEntity(en.getKey());
            ServerPlayer p = server.getPlayerList().getPlayer(info.player);
            boolean over = info.done || now >= info.until || p == null || !(e instanceof Mob) || !e.isAlive() || p.level() != e.level();
            if (over) {
                if (e instanceof Mob m && info.frozen) m.setNoAi(false);
                it.remove();
                continue;
            }
            Mob mob = (Mob) e;
            boolean seen = Spots.isLookingAt(p, mob, 0.82) && Spots.canSee(p, mob.getEyePosition());
            switch (info.kind) {
                case ALWAYS -> {
                    // they stop, and stare, wherever you go
                    if (!info.frozen) {
                        info.frozen = true;
                        mob.setNoAi(true);
                    }
                    float yaw = Spots.yawTo(mob.position(), p.position());
                    mob.setYRot(yaw);
                    mob.yBodyRot = yaw;
                    face(mob, p);
                }
                case BACK_TURNED -> {
                    if (seen) {
                        // you looked: just a cow
                        if (info.frozen) {
                            info.frozen = false;
                            mob.setNoAi(false);
                        }
                    } else {
                        if (!info.frozen) {
                            info.frozen = true;
                            mob.setNoAi(true);
                        }
                        float yaw = Spots.yawTo(mob.position(), p.position());
                        mob.setYRot(yaw);
                        mob.yBodyRot = yaw;
                        face(mob, p);
                    }
                }
                case PSS -> {
                    // when you pass close with your back to it: "pss..."
                    if (!seen && mob.distanceTo(p) < 5.5) {
                        Net.Sound s = Fear.RNG.nextBoolean()
                                ? new Net.Sound(Fear.rl("entity.creeper.primed"), 0.5F, 1.5F, mob.getX(), mob.getEyeY(), mob.getZ(), false, 7, false)
                                : new Net.Sound(Fear.rl("entity.villager.ambient"), 0.45F, 0.6F, mob.getX(), mob.getEyeY(), mob.getZ(), false, 0, true);
                        Net.send(p, s);
                        Fear.unmute(p, mob.position(), 4, 40);
                        face(mob, p);
                        info.done = true;
                    }
                }
            }
        }
    }

    public static void clear() {
        MOBS.clear();
    }

    private Staring() {}
}
