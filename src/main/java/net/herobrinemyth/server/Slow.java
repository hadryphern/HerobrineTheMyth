package net.herobrinemyth.server;

import net.herobrinemyth.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The slow things. Nothing here happens in front of you. You find it later, or never.
 * "He is here, getting closer, little by little."
 */
public final class Slow {

    /** Your home: where you respawn, or the world spawn. */
    static BlockPos home(ServerPlayer p) {
        BlockPos bed = p.getRespawnPosition();
        if (bed != null && p.getRespawnDimension() == Level.OVERWORLD) return bed;
        return p.getServer().overworld().getSharedSpawnPos();
    }

    /**
     * Every day a small marker, a little closer to your home than the day before, always on the same line.
     * Cobblestone with a redstone torch. Then a sign. Then he is at your door.
     */
    public static void marker(ServerPlayer p, HauntData.PlayerState st) {
        if (p.level().dimension() != Level.OVERWORLD) return;
        ServerLevel l = p.serverLevel();
        BlockPos home = home(p);
        double angle = (p.getUUID().getLeastSignificantBits() & 0xFFFF) / 65535.0 * Math.PI * 2;
        int dist = Math.max(5, 70 - st.markerStep * 5);
        int x = home.getX() + (int) Math.round(Math.cos(angle) * dist);
        int z = home.getZ() + (int) Math.round(Math.sin(angle) * dist);
        BlockPos at = Spots.surface(l, x, z);
        if (at == null) return; // that chunk is not loaded: tomorrow, then
        st.markerStep++;
        if (dist <= 5) {
            Builder.placeSign(l, at, Vec3.atCenterOf(home), Component.empty(), Component.translatable("herobrine.marker.here"),
                    Component.empty(), Component.empty());
            return;
        }
        if (dist <= 15) {
            Builder.placeSign(l, at, Vec3.atCenterOf(home), Component.empty(), Component.literal(String.valueOf(dist)),
                    Component.empty(), Component.empty());
            return;
        }
        Builder.set(l, at, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        Builder.set(l, at.above(), Blocks.REDSTONE_TORCH.defaultBlockState());
    }

    /** A small patch of the world, somewhere out of sight, starts to rot. */
    public static void rot(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 v = Spots.findHidden(p, 18, 40, 0.2, 15, 20);
        if (v == null) return;
        BlockPos c = BlockPos.containing(v);
        int r = 2 + Fear.RNG.nextInt(3);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r || Fear.RNG.nextInt(3) == 0) continue;
                BlockPos top = Spots.surface(l, c.getX() + dx, c.getZ() + dz);
                if (top == null) continue;
                BlockState above = l.getBlockState(top);
                if (above.is(BlockTags.FLOWERS) || above.is(Blocks.GRASS) || above.is(Blocks.FERN))
                    l.setBlock(top, (Fear.RNG.nextBoolean() ? Blocks.DEAD_BUSH : Blocks.WITHER_ROSE).defaultBlockState(), Block.UPDATE_CLIENTS);
                BlockPos ground = top.below();
                BlockState g = l.getBlockState(ground);
                if (g.is(Blocks.GRASS_BLOCK) || g.is(Blocks.DIRT) || g.is(Blocks.PODZOL)) {
                    int roll = Fear.RNG.nextInt(10);
                    BlockState to = roll < 5 ? Blocks.COARSE_DIRT.defaultBlockState() : roll < 8 ? Blocks.SOUL_SOIL.defaultBlockState() : Blocks.NETHERRACK.defaultBlockState();
                    if (Builder.replaceable(l, ground)) l.setBlock(ground, to, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Somewhere out of sight, something is burning. */
    public static void burn(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        BlockPos o = p.blockPosition();
        for (int i = 0; i < 60; i++) {
            BlockPos b = o.offset(Fear.RNG.nextInt(61) - 30, Fear.RNG.nextInt(13) - 4, Fear.RNG.nextInt(61) - 30);
            if (b.distSqr(o) < 15 * 15 || !l.hasChunkAt(b)) continue;
            if (!l.getBlockState(b).is(BlockTags.LOGS_THAT_BURN) && !l.getBlockState(b).is(BlockTags.LEAVES)) continue;
            if (Spots.isLookingAt(p, Vec3.atCenterOf(b), 0.5)) continue;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos f = b.relative(d);
                if (l.getBlockState(f).isAir()) {
                    l.setBlock(f, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
                    return;
                }
            }
        }
    }

    /** While you are away, someone was in your house. A window is broken. A door is open. */
    public static void ransack(ServerPlayer p) {
        if (!Config.ALLOW_HOUSE_DAMAGE.get() || p.level().dimension() != Level.OVERWORLD) return;
        ServerLevel l = p.serverLevel();
        BlockPos home = home(p);
        if (p.blockPosition().closerThan(home, 24) || !l.hasChunkAt(home)) return;
        List<BlockPos> glass = new ArrayList<>(), doors = new ArrayList<>();
        for (BlockPos b : BlockPos.betweenClosed(home.offset(-10, -3, -10), home.offset(10, 6, 10))) {
            BlockState s = l.getBlockState(b);
            if (s.is(Blocks.GLASS) || s.is(Blocks.GLASS_PANE) || s.is(BlockTags.IMPERMEABLE)) glass.add(b.immutable());
            else if (s.getBlock() instanceof DoorBlock && s.is(BlockTags.WOODEN_DOORS)) doors.add(b.immutable());
        }
        Collections.shuffle(glass);
        for (int i = 0; i < Math.min(glass.size(), 1 + Fear.RNG.nextInt(4)); i++) l.destroyBlock(glass.get(i), false);
        if (!doors.isEmpty()) {
            BlockPos d = doors.get(Fear.RNG.nextInt(doors.size()));
            BlockState s = l.getBlockState(d);
            if (s.getBlock() instanceof DoorBlock door) door.setOpen(null, l, s, d, true);
        }
    }

    /** Distance helper for tests. */
    static int markerDistance(int step) {
        return Math.max(5, 70 - step * 5);
    }

    static int clampDay(int d) {
        return Mth.clamp(d, 1, 100000);
    }

    private Slow() {}
}
