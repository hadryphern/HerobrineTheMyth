package net.herobrinemyth.server;

import net.herobrinemyth.network.Fx;
import net.herobrinemyth.network.Net;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Mobs with white eyes. They stare. Then they come. */
public final class Possession {
    private static final Map<UUID, Info> MOBS = new HashMap<>();

    private static final class Info {
        final ResourceKey<Level> dim;
        final UUID player;
        final long stareUntil, endAt;
        final boolean attack;
        boolean awake;
        int hitCooldown;

        Info(ResourceKey<Level> dim, UUID player, long stareUntil, long endAt, boolean attack) {
            this.dim = dim;
            this.player = player;
            this.stareUntil = stareUntil;
            this.endAt = endAt;
            this.attack = attack;
        }
    }

    public static boolean isPossessed(Entity e) {
        return MOBS.containsKey(e.getUUID());
    }

    public static void possess(Mob mob, ServerPlayer target, int stareTicks, int totalTicks, boolean attack) {
        long now = Scheduler.now();
        MOBS.put(mob.getUUID(), new Info(mob.level().dimension(), target.getUUID(), now + stareTicks, now + totalTicks, attack));
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        Net.sendTracking(mob, new Net.Possessed(mob.getId(), true));
        Fear.unmute(target, mob.position(), 40, totalTicks);
    }

    public static void release(Mob mob) {
        if (MOBS.remove(mob.getUUID()) != null) {
            mob.setNoAi(false);
            Net.sendTracking(mob, new Net.Possessed(mob.getId(), false));
        }
    }

    public static void onStartTracking(PlayerEvent.StartTracking event) {
        Entity e = event.getTarget();
        if (event.getEntity() instanceof ServerPlayer sp && MOBS.containsKey(e.getUUID())) {
            Net.send(sp, new Net.Possessed(e.getId(), true));
        }
    }

    public static void tick(MinecraftServer server) {
        if (MOBS.isEmpty()) return;
        long now = Scheduler.now();
        boolean screamed = false;
        Iterator<Map.Entry<UUID, Info>> it = MOBS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Info> en = it.next();
            Info info = en.getValue();
            ServerLevel level = server.getLevel(info.dim);
            Entity e = level == null ? null : level.getEntity(en.getKey());
            if (!(e instanceof Mob mob) || !mob.isAlive()) {
                if (e == null && now < info.endAt) continue; // chunk unloaded, keep for now
                it.remove();
                continue;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(info.player);
            if (p == null || now >= info.endAt || p.level() != mob.level()) {
                it.remove();
                mob.setNoAi(false);
                Net.sendTracking(mob, new Net.Possessed(mob.getId(), false));
                continue;
            }
            if (now < info.stareUntil) {
                // stare. do not blink. do not move.
                float yaw = Spots.yawTo(mob.position(), p.position());
                mob.setYRot(yaw);
                mob.yBodyRot = yaw;
                mob.setYHeadRot(yaw);
                mob.setXRot(Spots.pitchTo(mob.getEyePosition(), p.getEyePosition()));
            } else if (!info.awake) {
                info.awake = true;
                mob.setNoAi(false);
                if (info.attack && mob instanceof Enemy) {
                    mob.setTarget(p);
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60, 1));
                    mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 60, 0));
                    if (!screamed) {
                        screamed = true;
                        Fear.soundReverb(p, "entity.zombie.ambient", mob.position(), 1.4F, 0.5F);
                    }
                } else if (info.attack) {
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60, 1));
                } else if (mob instanceof Animal && Fear.RNG.nextInt(3) == 0) {
                    // they all fall over at once
                    mob.kill();
                }
            } else if (info.attack && mob instanceof Enemy && mob.getTarget() == null) {
                mob.setTarget(p);
            } else if (info.attack && !(mob instanceof Enemy)) {
                // pigs, cows, sheep, villagers: they cannot attack. these ones do.
                mob.getNavigation().moveTo(p, 1.35);
                mob.getLookControl().setLookAt(p, 30F, 30F);
                if (info.hitCooldown > 0) info.hitCooldown--;
                if (info.hitCooldown == 0 && mob.distanceTo(p) < 1.7) {
                    mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    Fear.hurt(p, mob.damageSources().mobAttack(mob), 2F);
                    info.hitCooldown = 20;
                }
            }
        }
    }

    // ------------------------------------------------------------------ events

    /** Every animal nearby stops and stares at you. */
    public static boolean animals(ServerPlayer p) {
        List<Animal> list = p.level().getEntitiesOfClass(Animal.class, p.getBoundingBox().inflate(28, 10, 28));
        if (list.isEmpty()) return false;
        int stare = 20 * (10 + Fear.RNG.nextInt(15));
        for (Animal a : list) possess(a, p, stare, stare + 40, false);
        return true;
    }

    /** Hostiles around you get white eyes, stare in silence, then all come at once. */
    public static boolean hostiles(ServerPlayer p, int spawnIfFewer) {
        List<Monster> list = p.level().getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(28, 12, 28),
                m -> !(m instanceof net.herobrinemyth.entity.HerobrineEntity));
        int stare = 20 * (4 + Fear.RNG.nextInt(6));
        for (Monster m : list) possess(m, p, stare, 20 * 120, true);
        int missing = spawnIfFewer - list.size();
        if (missing > 0) spawnWave(p, missing, stare);
        animals(p);
        return true;
    }

    private static final EntityType<?>[] WAVE = {
            EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SKELETON, EntityType.SPIDER,
            EntityType.CREEPER, EntityType.HUSK, EntityType.STRAY, EntityType.ZOMBIE_VILLAGER, EntityType.DROWNED
    };
    private static final EntityType<?>[] SWARM = {
            EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SKELETON, EntityType.SPIDER,
            EntityType.CREEPER, EntityType.HUSK, EntityType.STRAY, EntityType.ENDERMAN, EntityType.WITHER_SKELETON,
            EntityType.CAVE_SPIDER, EntityType.ZOMBIE_VILLAGER, EntityType.VILLAGER, EntityType.PIG, EntityType.COW,
            EntityType.SHEEP, EntityType.CHICKEN, EntityType.VILLAGER
    };

    /** The animals and the villagers, possessed. */
    private static final EntityType<?>[] HERD = {
            EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN, EntityType.VILLAGER, EntityType.VILLAGER,
            EntityType.PIG, EntityType.SHEEP, EntityType.COW, EntityType.ZOMBIE_VILLAGER
    };
    /** His guards: what comes out to protect him. */
    private static final EntityType<?>[] GUARDS = {
            EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SKELETON, EntityType.VILLAGER,
            EntityType.PIG, EntityType.SPIDER, EntityType.ZOMBIE_VILLAGER, EntityType.COW
    };

    /** Animals and villagers come out of nowhere, with his eyes. They stare. Then they come for you. */
    public static boolean herd(ServerPlayer p) {
        int n = spawn(p, 5 + Fear.RNG.nextInt(6), 20 * (3 + Fear.RNG.nextInt(5)), HERD);
        return n > 0;
    }

    /** Mobs that appear around a point (his guards). */
    public static int spawnAround(ServerPlayer p, Vec3 center, int count) {
        ServerLevel level = p.serverLevel();
        int spawned = 0;
        for (int i = 0; i < count * 4 && spawned < count; i++) {
            BlockPos s = Spots.standable(level, Mth.floor(center.x) + Fear.RNG.nextInt(7) - 3, Mth.floor(center.y),
                    Mth.floor(center.z) + Fear.RNG.nextInt(7) - 3, 3);
            if (s == null) continue;
            EntityType<?> type = GUARDS[Fear.RNG.nextInt(GUARDS.length)];
            Entity e = type.create(level);
            if (!(e instanceof Mob mob)) continue;
            mob.moveTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, Spots.yawTo(Vec3.atBottomCenterOf(s), p.position()), 0);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(s), MobSpawnType.EVENT, null, null);
            level.addFreshEntity(mob);
            possess(mob, p, 10, 20 * 120, true);
            spawned++;
        }
        return spawned;
    }

    public static int spawnWave(ServerPlayer p, int count, int stare) {
        return spawn(p, count, stare, WAVE);
    }

    /** After the totem: a lot of them. */
    public static boolean swarm(ServerPlayer p, int count) {
        int n = spawn(p, count, 20 * 3, SWARM);
        return n > 0;
    }

    private static int spawn(ServerPlayer p, int count, int stare, EntityType<?>[] pool) {
        ServerLevel level = p.serverLevel();
        int spawned = 0;
        for (int i = 0; i < count * 3 && spawned < count; i++) {
            Vec3 v = Spots.findHidden(p, 8, 22, 0.35, 15, 6);
            if (v == null) continue;
            EntityType<?> type = pool[Fear.RNG.nextInt(pool.length)];
            Entity e = type.create(level);
            if (!(e instanceof Mob mob)) continue;
            mob.moveTo(v.x, v.y, v.z, Spots.yawTo(v, p.position()), 0);
            if (type == EntityType.PHANTOM) mob.moveTo(v.x, v.y + 12, v.z);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(v)), MobSpawnType.EVENT, null, null);
            level.addFreshEntity(mob);
            possess(mob, p, stare, 20 * 180, true);
            spawned++;
        }
        return spawned;
    }

    public static void clear() {
        MOBS.clear();
    }

    /** Used by the calm command. */
    public static void releaseAround(ServerPlayer p) {
        AABB box = p.getBoundingBox().inflate(64);
        for (Mob m : p.level().getEntitiesOfClass(Mob.class, box, Possession::isPossessed)) release(m);
    }

    private Possession() {}
}
