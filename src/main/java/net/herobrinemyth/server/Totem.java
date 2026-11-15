package net.herobrinemyth.server;

import net.herobrinemyth.Config;
import net.herobrinemyth.entity.HerobrineEntity;
import net.herobrinemyth.network.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The classic shrine:
 * <pre>
 *   fire
 *   netherrack
 *   mossy cobblestone, with a redstone torch on each of its four sides
 *   3x3 gold blocks
 * </pre>
 */
public final class Totem {

    /** Called a couple of ticks after someone uses flint and steel / a fire charge near a block. */
    public static void check(ServerPlayer p, BlockPos clicked) {
        if (!Config.TOTEM_ENABLED.get()) return;
        ServerLevel level = p.serverLevel();
        for (BlockPos fire : BlockPos.betweenClosed(clicked.offset(-1, -1, -1), clicked.offset(1, 2, 1))) {
            if (level.getBlockState(fire).getBlock() instanceof BaseFireBlock && isShrine(level, fire.below())) {
                activate(p, fire.immutable());
                return;
            }
        }
    }

    /** The fire position of a built shrine near pos, if any. */
    public static java.util.Optional<BlockPos> findNear(ServerLevel level, BlockPos pos, int r) {
        for (BlockPos b : BlockPos.betweenClosed(pos.offset(-r, -4, -r), pos.offset(r, 4, r))) {
            if (level.getBlockState(b).is(Blocks.NETHERRACK) && isShrine(level, b)) return java.util.Optional.of(b.above().immutable());
        }
        return java.util.Optional.empty();
    }

    /**
     * The three classic altars, all lit with fire on the netherrack:
     * iLoveYou: 3x3 gold, mossy cobblestone, 4 redstone torches around the mossy cobblestone, netherrack.
     * HerobrineAI: 3x3 gold, netherrack on it with 4 redstone torches around the netherrack.
     * BasicHerobrine: mossy cobblestone with netherrack on top.
     */
    public static boolean isShrine(ServerLevel level, BlockPos netherrack) {
        if (!level.getBlockState(netherrack).is(Blocks.NETHERRACK)) return false;
        BlockPos below = netherrack.below();
        if (goldAndTorches(level, below.below(), below) && level.getBlockState(below).is(Blocks.MOSSY_COBBLESTONE)) return true;
        if (goldAndTorches(level, below, netherrack)) return true;
        return level.getBlockState(below).is(Blocks.MOSSY_COBBLESTONE);
    }

    private static boolean goldAndTorches(ServerLevel level, BlockPos goldCenter, BlockPos torchRingCenter) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if (!level.getBlockState(goldCenter.offset(dx, 0, dz)).is(Blocks.GOLD_BLOCK)) return false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState t = level.getBlockState(torchRingCenter.relative(d));
            if (!t.is(Blocks.REDSTONE_TORCH) && !t.is(Blocks.REDSTONE_WALL_TORCH)) return false;
        }
        return true;
    }

    /** iLoveYou: the altar is gone afterwards, as if it never existed. */
    private static void deleteAltar(ServerLevel level, BlockPos fire) {
        for (BlockPos b : BlockPos.betweenClosed(fire.offset(-1, -3, -1), fire.offset(1, 0, 1))) {
            BlockState s = level.getBlockState(b);
            if (s.is(Blocks.GOLD_BLOCK) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.NETHERRACK) || s.is(Blocks.REDSTONE_TORCH)
                    || s.is(Blocks.REDSTONE_WALL_TORCH) || s.getBlock() instanceof BaseFireBlock) {
                level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, fire.getX() + 0.5, fire.getY() - 1, fire.getZ() + 0.5, 60, 0.8, 1, 0.8, 0.02);
    }

    private static void destroyTorches(ServerLevel level, BlockPos center, int radius) {
        for (BlockPos b : BlockPos.betweenClosed(center.offset(-radius, -radius / 2, -radius), center.offset(radius, radius / 2, radius))) {
            BlockState s = level.getBlockState(b);
            if (s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH)
                    || s.is(Blocks.LANTERN) || s.is(Blocks.SOUL_LANTERN)) level.destroyBlock(b, true);
        }
    }

    public static void activate(ServerPlayer summoner, BlockPos fire) {
        ServerLevel level = summoner.serverLevel();
        HauntData data = HauntData.get(level.getServer());
        boolean already = data.totem;
        data.totem = true;
        data.totemPos = fire;
        data.setDirty();

        List<ServerPlayer> all = level.getServer().getPlayerList().getPlayers();
        for (ServerPlayer p : all) {
            Fear.fx(p, Fx.FOG, 20 * 20, 0.9F);
            HauntData.PlayerState st = data.player(p.getUUID());
            st.nextMajor = Scheduler.now() + 20 * 40;
            st.nextMinor = Scheduler.now() + 20 * 25;
        }
        level.setWeatherParameters(0, 20 * 60 * 20, true, true);
        level.setDayTime(level.getDayTime() - level.getDayTime() % 24000 + 14000);
        // BasicHerobrine: no creative mode in front of him
        if (summoner.isCreative()) summoner.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        // iLoveYou: the torches go out in growing rings: 5, 10, 20 blocks
        Scheduler.later(25, () -> destroyTorches(level, fire, 5));
        Scheduler.later(45, () -> destroyTorches(level, fire, 10));
        Scheduler.later(65, () -> destroyTorches(level, fire, 20));
        Garble.start(20 * 60);

        Vec3 c = Vec3.atBottomCenterOf(fire);
        // lightning onto the shrine, then all around it
        for (int i = 0; i < 9; i++) {
            final int n = i;
            Scheduler.later(i == 0 ? 10 : 10 + i * 5, () -> {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt == null) return;
                if (n == 0) bolt.moveTo(c.x, c.y, c.z);
                else bolt.moveTo(c.x + Fear.RNG.nextGaussian() * 7, c.y - 3, c.z + Fear.RNG.nextGaussian() * 7);
                bolt.setVisualOnly(n == 0 || Fear.RNG.nextBoolean());
                level.addFreshEntity(bolt);
            });
        }

        Scheduler.later(summoner, 35, sp -> {
            Vec3 dir = sp.position().subtract(c);
            dir = new Vec3(dir.x, 0, dir.z);
            if (dir.lengthSqr() < 1.0E-3) dir = new Vec3(1, 0, 0);
            dir = dir.normalize();
            Vec3 at = c.add(dir.scale(3)).add(0, -3, 0);
            BlockPos s = Spots.standable(sp.serverLevel(), BlockPos.containing(at).getX(), BlockPos.containing(at).getY(), BlockPos.containing(at).getZ(), 3);
            if (s != null) at = Vec3.atBottomCenterOf(s);
            HerobrineEntity.spawn(sp, at, HerobrineEntity.Mode.SHRINE, 110);
            for (ServerPlayer p : sp.getServer().getPlayerList().getPlayers()) if (!already) Fear.joined(p);
        });
        Scheduler.later(summoner, 70, sp -> Fear.chat(sp, Component.translatable("herobrine.totem.0")));
        Scheduler.later(summoner, 110, sp -> {
            for (ServerPlayer p : sp.getServer().getPlayerList().getPlayers()) {
                Fear.jumpscare(p);
                Corruption.noise(p);
                Fear.bigText(p, Component.translatable("herobrine.totem.title"), 70);
            }
        });
        // iLoveYou: the curse, then the altar is gone
        Scheduler.later(summoner, 130, sp -> sp.setSecondsOnFire(10));
        Scheduler.later(240, () -> deleteAltar(level, fire));
        Scheduler.later(summoner, 160, sp -> {
            Fear.chat(sp, Component.translatable("herobrine.totem.1", sp.getGameProfile().getName()));
            for (ServerPlayer p : sp.getServer().getPlayerList().getPlayers()) Fear.fx(p, Fx.HEARTBEAT, 20 * 20, 1F);
        });
    }

    private Totem() {}
}
