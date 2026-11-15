package net.herobrinemyth.server;

import net.herobrinemyth.Config;
import net.herobrinemyth.entity.HerobrineEntity;
import net.herobrinemyth.entity.HerobrineEntity.Mode;
import net.herobrinemyth.network.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Built-up scares. None of them always ends the same way: sometimes the thing you expect happens,
 * sometimes nothing happens at all, and the scare comes later, when you stopped expecting it.
 */
public final class Stalking {

    /** Calls check every few ticks until it returns true or time runs out (then onTimeout). */
    static void poll(ServerPlayer p, int every, int maxTicks, Predicate<ServerPlayer> check, @Nullable Consumer<ServerPlayer> onTimeout) {
        pollStep(p, every, maxTicks, check, onTimeout);
    }

    private static void pollStep(ServerPlayer p, int every, int left, Predicate<ServerPlayer> check, @Nullable Consumer<ServerPlayer> onTimeout) {
        Scheduler.later(p, every, sp -> {
            if (check.test(sp)) return;
            if (left - every <= 0) {
                if (onTimeout != null) onTimeout.accept(sp);
                return;
            }
            pollStep(sp, every, left - every, check, onTimeout);
        });
    }

    // ================================================================== the stranger

    /**
     * A plain Steve, next to you. His body does not move. His head follows you.
     * You cannot hurt him. Two minutes later he is gone. Only you can see him.
     */
    public static boolean steve(ServerPlayer p) {
        Vec3 v = Spots.findHidden(p, 2.5, 5, 0.3, 15, 30);
        if (v == null) return false;
        return HerobrineEntity.spawn(p, v, Mode.STEVE, 20 * 120) != null;
    }

    // ================================================================== where you are looking

    /**
     * Right where you are looking, far away: in the dark of that cave, between those trees.
     * He looks back at you. Two, three seconds. Gone.
     */
    public static boolean glimpse(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1F);
        if (Math.abs(look.y) > 0.55) return false;
        net.minecraft.world.phys.BlockHitResult hit = l.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(look.scale(96)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        double dist = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? 96 : hit.getLocation().distanceTo(eye);
        if (dist < 18) return false;
        float yaw = p.getYRot();
        for (int i = 0; i < 24; i++) {
            float y = yaw + (Fear.RNG.nextFloat() - 0.5F) * 14F;
            double d = 18 + Fear.RNG.nextDouble() * (dist - 20);
            Vec3 dir = Vec3.directionFromRotation(0, y);
            Vec3 c = eye.add(dir.scale(d));
            BlockPos s = Spots.standable(l, Mth.floor(c.x), Mth.floor(c.y) - 1, Mth.floor(c.z), 8);
            if (s == null) continue;
            Vec3 v = Vec3.atBottomCenterOf(s);
            if (!Spots.canSee(p, v.add(0, 1.62, 0))) continue;
            return HerobrineEntity.spawn(p, v, Mode.ECHO, 40 + Fear.RNG.nextInt(25)) != null;
        }
        return false;
    }

    /** Behind a tree, looking at you. When you look back he steps behind the trunk, and he is gone. */
    public static boolean tree(ServerPlayer p) {
        Spots.TreeSpot t = Spots.findTreeSpot(p, 10, 34);
        if (t == null) return false;
        HerobrineEntity h = HerobrineEntity.spawn(p, t.stand(), Mode.TREE, 20 * 90);
        if (h == null) return false;
        h.setAnchor(t.trunk());
        return true;
    }

    // ================================================================== windows and cracks

    /** Outside one of your windows, looking in. */
    public static boolean window(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        BlockPos c = p.blockPosition();
        for (BlockPos b : BlockPos.betweenClosed(c.offset(-14, -3, -14), c.offset(14, 4, 14))) {
            BlockState s = l.getBlockState(b);
            if (!(s.is(Blocks.GLASS) || s.is(Blocks.GLASS_PANE) || s.is(BlockTags.IMPERMEABLE))) continue;
            if (b.distSqr(c) < 9) continue;
            // the side of the glass away from you
            Vec3 toGlass = Vec3.atCenterOf(b).subtract(p.getEyePosition());
            Direction away = Direction.getNearest(toGlass.x, 0, toGlass.z);
            BlockPos outside = b.relative(away);
            BlockPos feet = Spots.standable(l, outside.getX(), outside.getY() - 1, outside.getZ(), 2);
            if (feet == null || l.canSeeSky(feet) && !p.level().isNight()) continue;
            Vec3 at = Vec3.atBottomCenterOf(feet);
            if (!Spots.canSee(p, Vec3.atCenterOf(b))) continue;
            HerobrineEntity h = HerobrineEntity.spawn(p, at, Mode.WATCH, 20 * 40);
            return h != null;
        }
        return false;
    }

    /** A block in a wall near you breaks. Behind the hole: his face. */
    public static boolean crack(ServerPlayer p) {
        if (!Config.ALLOW_GRIEFING.get()) return false;
        ServerLevel l = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        for (int i = 0; i < 80; i++) {
            BlockPos b = p.blockPosition().offset(Fear.RNG.nextInt(17) - 8, Fear.RNG.nextInt(3), Fear.RNG.nextInt(17) - 8);
            if (b.distSqr(p.blockPosition()) < 16) continue;
            BlockState s = l.getBlockState(b);
            if (!(s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.DIRT)) || !Builder.replaceable(l, b)) continue;
            Vec3 to = Vec3.atCenterOf(b).subtract(eye);
            Direction away = Direction.getNearest(to.x, 0, to.z);
            BlockPos front = b.relative(away.getOpposite());
            if (!Spots.passable(l, front)) continue; // the wall must face you
            BlockPos head = b.relative(away), feet = head.below();
            if (!Builder.replaceable(l, head) || !Builder.replaceable(l, feet)) continue;
            if (!Spots.canSee(p, Vec3.atCenterOf(front))) continue;
            l.destroyBlock(b, false);
            l.setBlock(head, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            l.setBlock(feet, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            HerobrineEntity h = HerobrineEntity.spawn(p, Vec3.atBottomCenterOf(feet), Mode.WATCH, 20 * 30);
            if (h != null) h.setNoGravity(true);
            BlockState was = s;
            // when he leaves, the wall closes again
            poll(p, 10, 20 * 40, sp -> {
                if (h != null && !h.isRemoved()) return false;
                l.setBlock(b, was, Block.UPDATE_ALL);
                return true;
            }, sp -> l.setBlock(b, was, Block.UPDATE_ALL));
            return true;
        }
        return false;
    }

    // ================================================================== "left..."

    /**
     * "Left..." You look left. Nothing. You look back. He is there, where you were looking before.
     * Or he isn't. Or he is behind you instead.
     */
    public static boolean left(ServerPlayer p) {
        float yaw0 = p.getYRot();
        Vec3 front = p.position().add(Vec3.directionFromRotation(0, yaw0).scale(7));
        boolean right = Fear.RNG.nextInt(4) == 0;
        Fear.chat(p, Component.translatable(right ? "herobrine.left.right" : "herobrine.left.left"));
        poll(p, 2, 20 * 15, sp -> {
            float d = Mth.wrapDegrees(sp.getYRot() - yaw0);
            boolean turned = right ? d > 55 : d < -55;
            if (!turned) return false;
            int outcome = Fear.RNG.nextInt(10);
            if (outcome < 6) {
                BlockPos at = Spots.standable(sp.serverLevel(), Mth.floor(front.x), sp.getBlockY(), Mth.floor(front.z), 4);
                if (at != null) HerobrineEntity.spawn(sp, Vec3.atBottomCenterOf(at), Mode.WATCH, 20 * 20);
            } else if (outcome < 8) {
                Vec3 b = Spots.behind(sp, 2.5);
                if (b != null) HerobrineEntity.spawn(sp, b, Mode.BEHIND, 20 * 20);
            }
            // otherwise: nothing. this time.
            return true;
        }, null);
        return true;
    }

    // ================================================================== record 11

    /**
     * Record 11 starts playing somewhere close. Then anything can happen: nothing, footsteps,
     * the music stops and he is standing where it came from, a scream, a chase, possessed mobs.
     */
    public static boolean disc(ServerPlayer p) {
        Vec3 v = Spots.findHidden(p, 10, 18, 0.3, 15, 20);
        if (v == null) v = p.position().add(12, 0, 0);
        final Vec3 src = v;
        int length = 20 * (20 + Fear.RNG.nextInt(40));
        String disc = Fear.RNG.nextInt(3) == 0 ? "music_disc.13" : "music_disc.11";
        int outcome = Fear.RNG.nextInt(100);
        int cut = outcome < 35 ? 20 * 140 : length; // sometimes it just plays to the end
        Fear.soundStopping(p, disc, src, 1.5F, 1F, cut);
        Scheduler.later(p, Math.min(cut, length) + 10, sp -> {
            if (outcome < 35) return; // nothing at all
            if (outcome < 55) {
                BlockPos at = Spots.standable(sp.serverLevel(), Mth.floor(src.x), Mth.floor(src.y), Mth.floor(src.z), 4);
                if (at != null) HerobrineEntity.spawn(sp, Vec3.atBottomCenterOf(at), Mode.DARK, 20 * 40);
            } else if (outcome < 70) {
                Haunts.footsteps(sp);
            } else if (outcome < 80) {
                Fear.jumpscare(sp);
            } else if (outcome < 90) {
                Haunts.chase(sp);
            } else {
                Possession.herd(sp);
            }
        });
        return true;
    }

    // ================================================================== multiplayer

    /**
     * He wants you alone. If another player is close, one of you wakes up somewhere dark,
     * not too far (60 to 150 blocks), away from the others.
     */
    public static boolean separate(ServerPlayer p) {
        if (!Config.ALLOW_CAVE_TELEPORT.get() || p.getServer() == null) return false;
        boolean together = false;
        for (ServerPlayer o : p.getServer().getPlayerList().getPlayers()) {
            if (o != p && o.level() == p.level() && o.distanceTo(p) < 40) together = true;
        }
        if (!together) return false;
        BlockPos spot = Caves.findDarkSpot(p.serverLevel(), p.blockPosition(), 60, 150);
        if (spot == null) return false;
        Fear.fx(p, Fx.BLACKOUT, 40, 1F);
        Scheduler.later(p, 12, sp -> {
            sp.teleportTo(sp.serverLevel(), spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, Fear.RNG.nextFloat() * 360F, 0F);
            Scheduler.later(sp, 200 + Fear.RNG.nextInt(400), Haunts::footsteps);
        });
        return true;
    }

    // ================================================================== blood rain

    /** Night falls at once. Red rain. The fog closes in. */
    public static boolean bloodRain(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        if (l.dimension() != net.minecraft.world.level.Level.OVERWORLD) return false;
        int dur = 20 * (90 + Fear.RNG.nextInt(90));
        long day = l.getDayTime() - l.getDayTime() % 24000;
        l.setDayTime(day + 14000);
        l.setWeatherParameters(0, dur, true, false);
        for (ServerPlayer o : l.players()) Fear.fx(o, Fx.BLOOD, dur, 1F);
        if (Fear.RNG.nextInt(3) == 0) Scheduler.later(p, dur / 2, Haunts::stalk);
        return true;
    }

    // ================================================================== the old mods: he fights, with his guards

    /** Like in the old mods: he appears, hits you, and mobs come out to protect him. */
    public static boolean guards(ServerPlayer p) {
        if (Artifacts.protectedFromHim(p)) return false;
        Vec3 v = Spots.findWatchingSpot(p, 6, 12, 15, 30);
        if (v == null) v = Spots.behind(p, 5);
        if (v == null) return false;
        HerobrineEntity h = HerobrineEntity.spawn(p, v, Mode.FIGHT, 20 * 50);
        if (h == null) return false;
        Fear.chat(p, Component.translatable("herobrine.chat.fight." + Fear.RNG.nextInt(Msg.FIGHT)));
        Possession.spawnAround(p, v, 4 + Fear.RNG.nextInt(5));
        return true;
    }

    private Stalking() {}
}
