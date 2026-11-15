package net.herobrinemyth.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Where can he stand? Can you see him? */
public final class Spots {

    // ------------------------------------------------------------ perception

    /** Unobstructed line from the player's eyes to the point. */
    public static boolean canSee(ServerPlayer p, Vec3 target) {
        Vec3 eye = p.getEyePosition();
        if (eye.distanceToSqr(target) > 160 * 160) return false;
        return p.level().clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p))
                .getType() == HitResult.Type.MISS;
    }

    /** Is the point inside the player's view cone? cos 0.5 = 60 degrees off-center. */
    public static boolean inView(ServerPlayer p, Vec3 target, double cos) {
        Vec3 to = target.subtract(p.getEyePosition());
        if (to.lengthSqr() < 1.0E-4) return true;
        return p.getViewVector(1F).dot(to.normalize()) > cos;
    }

    public static boolean isLookingAt(ServerPlayer p, Vec3 target, double cos) {
        return inView(p, target, cos) && canSee(p, target);
    }

    /** Looking at the entity: either its eyes or its body are visible inside the cone. */
    public static boolean isLookingAt(ServerPlayer p, Entity e, double cos) {
        Vec3 eyes = e.getEyePosition();
        Vec3 mid = e.position().add(0, e.getBbHeight() * 0.5, 0);
        return (inView(p, eyes, cos) && canSee(p, eyes)) || (inView(p, mid, cos) && canSee(p, mid));
    }

    // ------------------------------------------------------------ environment

    public static boolean isUnderground(ServerPlayer p) {
        BlockPos pos = p.blockPosition();
        return !p.level().canSeeSky(pos) && p.getY() < p.level().getSeaLevel() - 4;
    }

    public static int light(ServerLevel level, BlockPos pos) {
        return level.getMaxLocalRawBrightness(pos);
    }

    public static boolean isDark(ServerLevel level, BlockPos pos, int max) {
        return light(level, pos) <= max;
    }

    public static boolean solidFloor(ServerLevel level, BlockPos below) {
        BlockState s = level.getBlockState(below);
        return s.isFaceSturdy(level, below, Direction.UP) || s.is(BlockTags.LEAVES);
    }

    public static boolean passable(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getCollisionShape(level, pos).isEmpty() && s.getFluidState().isEmpty();
    }

    /** Feet position at x/z where a 2-tall humanoid can stand, searched around yHint. */
    @Nullable
    public static BlockPos standable(ServerLevel level, int x, int yHint, int z, int range) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        if (!level.hasChunkAt(m.set(x, yHint, z))) return null;
        for (int i = 0; i <= range; i++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                int y = yHint + i * sign;
                if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 2) continue;
                m.set(x, y, z);
                if (passable(level, m) && passable(level, m.above()) && solidFloor(level, m.below())) {
                    return m.immutable();
                }
                if (i == 0) break;
            }
        }
        return null;
    }

    @Nullable
    public static BlockPos surface(ServerLevel level, int x, int z) {
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos p = new BlockPos(x, y, z);
        if (!passable(level, p) || !passable(level, p.above())) return null;
        if (!level.getBlockState(p.below()).getFluidState().isEmpty()) return null;
        return p;
    }

    /**
     * A spot between min and max blocks away that is outside the player's view (cos below maxCos).
     * underground players get spots near their own height, others on the surface.
     */
    @Nullable
    public static Vec3 findHidden(ServerPlayer p, double min, double max, double maxCos, int darkMax, int tries) {
        ServerLevel level = p.serverLevel();
        boolean under = isUnderground(p);
        Vec3 look = p.getViewVector(1F);
        for (int i = 0; i < tries; i++) {
            double ang = Fear.RNG.nextDouble() * Math.PI * 2;
            double dist = min + Fear.RNG.nextDouble() * (max - min);
            double dx = Math.cos(ang), dz = Math.sin(ang);
            if (look.x * dx + look.z * dz > maxCos) continue;
            int x = Mth.floor(p.getX() + dx * dist);
            int z = Mth.floor(p.getZ() + dz * dist);
            BlockPos pos = under ? standable(level, x, p.getBlockY(), z, 8) : surface(level, x, z);
            if (pos == null && !under) pos = standable(level, x, p.getBlockY(), z, 10);
            if (pos == null) continue;
            if (darkMax < 15 && !isDark(level, pos, darkMax)) continue;
            Vec3 v = Vec3.atBottomCenterOf(pos);
            if (inView(p, v.add(0, 1.6, 0), maxCos + 0.05) && canSee(p, v.add(0, 1.6, 0))) continue;
            return v;
        }
        return null;
    }

    /** A spot the player could see if they turned around: has line of sight from the eyes. */
    @Nullable
    public static Vec3 findWatchingSpot(ServerPlayer p, double min, double max, int darkMax, int tries) {
        for (int i = 0; i < tries; i++) {
            Vec3 v = findHidden(p, min, max, 0.2, darkMax, 4);
            if (v != null && canSee(p, v.add(0, 1.62, 0))) return v;
        }
        return null;
    }

    /**
     * Where he likes to stand: in the dark, half hidden. Out of your view right now, so you find him when
     * you turn around, and when you do you only see part of him: the feet under a branch, a face over a ridge.
     */
    @Nullable
    public static Vec3 findLurking(ServerPlayer p, double min, double max) {
        ServerLevel level = p.serverLevel();
        Vec3 best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < 28; i++) {
            Vec3 v = findHidden(p, min, max, 0.25, 15, 3);
            if (v == null) continue;
            boolean head = canSee(p, v.add(0, 1.62, 0)), mid = canSee(p, v.add(0, 1.0, 0)), feet = canSee(p, v.add(0, 0.2, 0));
            int seen = (head ? 1 : 0) + (mid ? 1 : 0) + (feet ? 1 : 0);
            if (seen == 0) continue;
            int light = light(level, BlockPos.containing(v).above());
            int score = (seen < 3 ? 4 : 0) + (light <= 4 ? 4 : light <= 8 ? 2 : 0) + Fear.RNG.nextInt(3);
            if (score > bestScore) {
                bestScore = score;
                best = v;
            }
        }
        return best;
    }

    /** Directly behind the player, snapped to the ground. */
    @Nullable
    public static Vec3 behind(ServerPlayer p, double dist) {
        Vec3 look = p.getViewVector(1F);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        for (double d = dist; d >= 1.5; d -= 0.5) {
            Vec3 t = p.position().subtract(flat.scale(d));
            BlockPos pos = standable(p.serverLevel(), Mth.floor(t.x), p.getBlockY(), Mth.floor(t.z), 3);
            if (pos != null) return new Vec3(t.x, pos.getY(), t.z);
        }
        return null;
    }

    public record TreeSpot(Vec3 stand, BlockPos trunk) {}

    /** Like behindTree, but also says which trunk he is hiding behind. */
    @Nullable
    public static TreeSpot findTreeSpot(ServerPlayer p, int min, int max) {
        ServerLevel level = p.serverLevel();
        // every trunk in the ring around you, in random order
        java.util.List<BlockPos> trunks = new java.util.ArrayList<>();
        int px = p.getBlockX(), pz = p.getBlockZ();
        for (int dx = -max; dx <= max; dx++) {
            for (int dz = -max; dz <= max; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 < min * min || d2 > max * max) continue;
                int x = px + dx, z = pz + dz;
                if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
                // the bottom of a trunk near your height: a log on the ground with a log above it
                for (int y = p.getBlockY() - 4; y <= p.getBlockY() + 4; y++) {
                    BlockPos b = new BlockPos(x, y, z);
                    if (level.getBlockState(b).is(BlockTags.LOGS) && level.getBlockState(b.above()).is(BlockTags.LOGS)
                            && !level.getBlockState(b.below()).is(BlockTags.LOGS) && solidFloor(level, b.below())) {
                        trunks.add(b);
                        break;
                    }
                }
            }
        }
        java.util.Collections.shuffle(trunks, new java.util.Random(Fear.RNG.nextLong()));
        for (BlockPos log : trunks) {
            BlockPos base = log;
            while (level.getBlockState(base.below()).is(BlockTags.LOGS) && base.getY() > level.getMinBuildHeight()) base = base.below();
            if (!level.getBlockState(base.above()).is(BlockTags.LOGS)) continue;
            Vec3 dir = Vec3.atCenterOf(base).subtract(p.position());
            dir = new Vec3(dir.x, 0, dir.z).normalize();
            Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(Fear.RNG.nextBoolean() ? 0.62 : -0.62);
            Vec3 stand = Vec3.atBottomCenterOf(base).add(dir.scale(0.95)).add(side);
            BlockPos sp = standable(level, Mth.floor(stand.x), base.getY(), Mth.floor(stand.z), 2);
            if (sp == null) continue;
            Vec3 at = new Vec3(stand.x, sp.getY(), stand.z);
            // only half of him should be visible: the trunk covers the rest
            if (!canSee(p, at.add(0, 1.62, 0)) && !canSee(p, at.add(0, 1.0, 0))) continue;
            return new TreeSpot(at, base);
        }
        return null;
    }

    /** A tree trunk with free space behind it (relative to the player). Returns feet position to stand. */
    @Nullable
    public static Vec3 behindTree(ServerPlayer p, int min, int max) {
        ServerLevel level = p.serverLevel();
        for (int i = 0; i < 60; i++) {
            double ang = Fear.RNG.nextDouble() * Math.PI * 2;
            double dist = min + Fear.RNG.nextDouble() * (max - min);
            int x = Mth.floor(p.getX() + Math.cos(ang) * dist);
            int z = Mth.floor(p.getZ() + Math.sin(ang) * dist);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos log = new BlockPos(x, top - 1, z);
            // walk down the trunk to the ground
            if (!level.getBlockState(log).is(BlockTags.LOGS)) continue;
            BlockPos base = log;
            while (level.getBlockState(base.below()).is(BlockTags.LOGS) && base.getY() > level.getMinBuildHeight()) base = base.below();
            if (!level.getBlockState(base.above()).is(BlockTags.LOGS)) continue;
            Vec3 dir = Vec3.atCenterOf(base).subtract(p.position());
            dir = new Vec3(dir.x, 0, dir.z).normalize();
            Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(Fear.RNG.nextBoolean() ? 0.55 : -0.55);
            Vec3 stand = Vec3.atBottomCenterOf(base).add(dir.scale(1.05)).add(side);
            BlockPos sp = standable(level, Mth.floor(stand.x), base.getY(), Mth.floor(stand.z), 2);
            if (sp == null) continue;
            return new Vec3(stand.x, sp.getY(), stand.z);
        }
        return null;
    }

    public static float yawTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dz = to.z - from.z;
        return (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
    }

    public static float pitchTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        return (float) (-(Mth.atan2(dy, h) * (180F / Math.PI)));
    }

    private Spots() {}
}
