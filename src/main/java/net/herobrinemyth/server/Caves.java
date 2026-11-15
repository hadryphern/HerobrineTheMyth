package net.herobrinemyth.server;

import net.herobrinemyth.Config;
import net.herobrinemyth.entity.HerobrineEntity;
import net.herobrinemyth.network.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** You blink, and you are somewhere deep, dark and empty. */
public final class Caves {

    public static boolean teleport(ServerPlayer p, boolean returnAfter) {
        if (!Config.ALLOW_CAVE_TELEPORT.get()) return false;
        ServerLevel level = p.serverLevel();
        BlockPos cave = findCave(level, p.blockPosition());
        if (cave == null) cave = carveRoom(level, p.blockPosition());
        if (cave == null) return false;
        final BlockPos target = cave;
        final Vec3 back = p.position();
        final float yaw = p.getYRot(), pitch = p.getXRot();

        Fear.fx(p, Fx.BLACKOUT, 50, 1F);
        Fear.ear(p, "entity.enderman.teleport", 1F, 0.4F);
        Scheduler.later(p, 15, sp -> {
            sp.teleportTo(level, target.getX() + 0.5, target.getY(), target.getZ() + 0.5, Fear.RNG.nextFloat() * 360F, 0F);
            Fear.fx(sp, Fx.FOG, 20 * 45, 0.85F);
        });
        Scheduler.later(p, 80, Haunts::footsteps);
        Scheduler.later(p, 180, sp -> {
            Fear.ear(sp, "ambient.cave", 1F, 0.7F);
            Fear.whisper(sp, Msg.whisper(), 60);
        });
        Scheduler.later(p, 260, Haunts::eyes);
        Scheduler.later(p, 420, sp -> {
            if (!Haunts.dark(sp)) Haunts.behind(sp);
        });
        if (returnAfter) {
            Scheduler.later(p, 760, sp -> Fear.fx(sp, Fx.BLACKOUT, 60, 1F));
            Scheduler.later(p, 785, sp -> {
                sp.teleportTo(level, back.x, back.y, back.z, yaw, pitch);
                Fear.fx(sp, Fx.CLEAR, 1, 0F);
                Scheduler.later(sp, 30, s2 -> {
                    Vec3 v = Spots.findHidden(s2, 2, 4, 0.0, 15, 30);
                    if (v != null) {
                        Builder.placeSign(s2.serverLevel(), BlockPos.containing(v), s2.position(),
                                Component.translatable("herobrine.sign.dream.a"), Component.translatable("herobrine.sign.dream.b"),
                                Component.translatable("herobrine.sign.dream.c"), Component.empty());
                    }
                });
            });
        }
        return true;
    }

    /**
     * A dark place min..max blocks away: a cave if there is one, otherwise any dark corner,
     * otherwise a dark spot on the surface at night. Never the 1000-block kind of far.
     */
    @Nullable
    public static BlockPos findDarkSpot(ServerLevel level, BlockPos origin, int min, int max) {
        for (int i = 0; i < 500; i++) {
            double a = Fear.RNG.nextDouble() * Math.PI * 2;
            int d = min + Fear.RNG.nextInt(Math.max(1, max - min));
            int x = origin.getX() + (int) (Math.cos(a) * d), z = origin.getZ() + (int) (Math.sin(a) * d);
            int y = i < 300 ? Math.max(level.getMinBuildHeight() + 6, origin.getY() - 10 - Fear.RNG.nextInt(40)) : origin.getY();
            BlockPos s = Spots.standable(level, x, y, z, 8);
            if (s == null || Spots.light(level, s) > 3 || nearLava(level, s)) continue;
            return s;
        }
        return null;
    }

    /** A real cave: dark, roofed, far from here, no lava. */
    @Nullable
    private static BlockPos findCave(ServerLevel level, BlockPos origin) {
        int minY = level.getMinBuildHeight() + 6;
        int maxY = Math.min(level.getSeaLevel() - 20, 30);
        if (maxY <= minY) return null;
        for (int i = 0; i < 400; i++) {
            int x = origin.getX() + Fear.RNG.nextInt(161) - 80;
            int z = origin.getZ() + Fear.RNG.nextInt(161) - 80;
            int y = minY + Fear.RNG.nextInt(maxY - minY);
            BlockPos s = Spots.standable(level, x, y, z, 6);
            if (s == null || s.distSqr(origin) < 30 * 30) continue;
            if (level.canSeeSky(s) || Spots.light(level, s) > 0) continue;
            if (!roomy(level, s) || nearLava(level, s)) continue;
            return s;
        }
        return null;
    }

    private static boolean roomy(ServerLevel level, BlockPos s) {
        int air = 0;
        for (BlockPos b : BlockPos.betweenClosed(s.offset(-3, 0, -3), s.offset(3, 2, 3))) {
            if (level.getBlockState(b).isAir()) air++;
        }
        return air > 50;
    }

    private static boolean nearLava(ServerLevel level, BlockPos s) {
        for (BlockPos b : BlockPos.betweenClosed(s.offset(-5, -2, -5), s.offset(5, 3, 5))) {
            if (level.getBlockState(b).is(Blocks.LAVA)) return true;
        }
        return false;
    }

    /** No cave? He makes one. A sealed deepslate room far below you. */
    @Nullable
    private static BlockPos carveRoom(ServerLevel level, BlockPos origin) {
        int y = Math.max(level.getMinBuildHeight() + 8, Math.min(origin.getY() - 30, level.getMinBuildHeight() + 40));
        BlockPos c = new BlockPos(origin.getX() + Fear.RNG.nextInt(41) - 20, y, origin.getZ() + Fear.RNG.nextInt(41) - 20);
        if (!level.hasChunkAt(c)) return null;
        BlockState wall = Blocks.DEEPSLATE.defaultBlockState();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -1; dy <= 5; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos at = c.offset(dx, dy, dz);
                    boolean shell = Math.abs(dx) == 5 || Math.abs(dz) == 5 || dy == -1 || dy == 5;
                    if (!Builder.replaceable(level, at)) continue;
                    if (shell) {
                        BlockState s = level.getBlockState(at);
                        if (s.isAir() || !s.getFluidState().isEmpty() || dy == -1) level.setBlock(at, wall, Block.UPDATE_CLIENTS);
                    } else {
                        level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        return c;
    }

    private Caves() {}
}
