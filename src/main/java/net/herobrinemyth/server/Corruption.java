package net.herobrinemyth.server;

import net.herobrinemyth.Config;
import net.herobrinemyth.network.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** What happens to the world after the totem. */
public final class Corruption {

    // ------------------------------------------------------------------ chunk eating

    private static final List<Eater> EATERS = new ArrayList<>();

    private static final class Eater {
        final ServerLevel level;
        final ChunkPos chunk;
        int y;

        Eater(ServerLevel level, ChunkPos chunk) {
            this.level = level;
            this.chunk = chunk;
            this.y = level.getMaxBuildHeight() - 1;
        }

        /** @return true when finished */
        boolean step() {
            if (!level.hasChunk(chunk.x, chunk.z)) return true;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            BlockState air = Blocks.AIR.defaultBlockState();
            for (int layer = 0; layer < 3 && y >= level.getMinBuildHeight(); layer++, y--) {
                for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
                    for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                        m.set(x, y, z);
                        if (!level.getBlockState(m).isAir()) {
                            level.setBlock(m, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        }
                    }
                }
            }
            return y < level.getMinBuildHeight();
        }
    }

    public static void tickEaters() {
        Iterator<Eater> it = EATERS.iterator();
        while (it.hasNext()) {
            if (it.next().step()) it.remove();
        }
    }

    /** A whole chunk, from the sky to the void, gone. */
    public static boolean eatChunk(ServerPlayer p) {
        if (!Config.CHUNK_DELETION.get()) return false;
        ServerLevel level = p.serverLevel();
        ChunkPos me = p.chunkPosition();
        for (int i = 0; i < 10; i++) {
            int dx = (Fear.RNG.nextInt(3) + 2) * (Fear.RNG.nextBoolean() ? 1 : -1);
            int dz = (Fear.RNG.nextInt(5) - 2);
            if (Fear.RNG.nextBoolean()) {
                int t = dx;
                dx = dz;
                dz = t;
            }
            ChunkPos target = new ChunkPos(me.x + dx, me.z + dz);
            if (!level.hasChunk(target.x, target.z)) continue;
            EATERS.add(new Eater(level, target));
            Vec3 c = new Vec3(target.getMiddleBlockX(), p.getY(), target.getMiddleBlockZ());
            Fear.sound(p, "entity.wither.break_block", c, 2F, 0.5F);
            Fear.sound(p, "entity.warden.sonic_boom", c, 2F, 0.4F);
            Fear.fx(p, Fx.GLITCH, 30, 0.6F);
            Fear.chat(p, Component.translatable("herobrine.chunk." + Fear.RNG.nextInt(3)));
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ surface corruption

    public static boolean corrupt(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        int changed = 0;
        for (int i = 0; i < 90; i++) {
            int x = Mth.floor(p.getX()) + Fear.RNG.nextInt(49) - 24;
            int z = Mth.floor(p.getZ()) + Fear.RNG.nextInt(49) - 24;
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            for (int y = top; y >= top - 3; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState s = level.getBlockState(pos);
                BlockState to = corrupted(s);
                if (to != null && Builder.replaceable(level, pos)) {
                    level.setBlock(pos, to, Block.UPDATE_ALL);
                    changed++;
                    if (to.is(Blocks.NETHERRACK) && Fear.RNG.nextInt(10) == 0 && level.getBlockState(pos.above()).isAir())
                        level.setBlock(pos.above(), Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
                    break;
                }
            }
        }
        if (changed > 0) Fear.sound(p, "block.sculk.spread", p.position(), 1F, 0.5F);
        return changed > 0;
    }

    private static BlockState corrupted(BlockState s) {
        int r = Fear.RNG.nextInt(10);
        if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM)) {
            return r < 5 ? Blocks.NETHERRACK.defaultBlockState() : r < 8 ? Blocks.SOUL_SOIL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
        }
        if (s.is(BlockTags.LEAVES)) return Blocks.AIR.defaultBlockState();
        if (s.is(BlockTags.FLOWERS) || s.is(Blocks.GRASS) || s.is(Blocks.FERN)) return r < 4 ? Blocks.WITHER_ROSE.defaultBlockState() : Blocks.DEAD_BUSH.defaultBlockState();
        if (s.is(BlockTags.SAND)) return Blocks.SOUL_SAND.defaultBlockState();
        if (s.is(Blocks.STONE) || s.is(Blocks.COBBLESTONE)) return r < 5 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.GILDED_BLACKSTONE.defaultBlockState();
        if (s.is(BlockTags.LOGS_THAT_BURN)) return r < 3 ? Blocks.BASALT.defaultBlockState() : null;
        if (s.is(Blocks.WATER) && s.getFluidState().isSource()) return r < 2 ? Blocks.OBSIDIAN.defaultBlockState() : null;
        if (s.is(Blocks.GLASS) || s.is(Blocks.GLASS_PANE)) return Blocks.AIR.defaultBlockState();
        return null;
    }

    // ------------------------------------------------------------------ noise and weather

    /** Deafening. */
    public static boolean noise(ServerPlayer p) {
        int n = 4 + Fear.RNG.nextInt(5);
        for (int i = 0; i < n; i++) {
            int delay = i * (2 + Fear.RNG.nextInt(5));
            Scheduler.later(p, delay, sp -> {
                Fear.sound(sp, Fear.pick(Fear.LOUD), sp.position().add(Fear.RNG.nextGaussian() * 2, 0, Fear.RNG.nextGaussian() * 2), 1F, Fear.range(0.4F, 1.1F));
            });
        }
        return true;
    }

    /** Real lightning, around you. */
    public static boolean storm(ServerPlayer p) {
        int n = 4 + Fear.RNG.nextInt(5);
        for (int i = 0; i < n; i++) {
            Scheduler.later(p, i * 8 + Fear.RNG.nextInt(6), sp -> {
                ServerLevel l = sp.serverLevel();
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(l);
                if (bolt == null) return;
                double x = sp.getX() + Fear.RNG.nextGaussian() * 9, z = sp.getZ() + Fear.RNG.nextGaussian() * 9;
                int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
                bolt.moveTo(x, y, z);
                l.addFreshEntity(bolt);
            });
        }
        return true;
    }

    public static void worldTick(MinecraftServer server, long now) {
        HauntData data = HauntData.get(server);
        if (!data.totem || !Config.ETERNAL_NIGHT.get() || now % 100 != 0) return;
        ServerLevel ow = server.overworld();
        long day = ow.getDayTime() % 24000L;
        if (day < 14000 || day > 22000) ow.setDayTime(ow.getDayTime() - day + 18000);
        if (!ow.isThundering()) ow.setWeatherParameters(0, 20 * 60 * 20, true, true);
    }

    // ------------------------------------------------------------------ the end

    /** Crash the game. Safely: the world is saved first, always. */
    public static boolean crash(ServerPlayer p) {
        if (Config.CRASH_MODE.get() == Config.CrashMode.OFF) return false;
        MinecraftServer server = p.getServer();
        if (server == null) return false;
        HauntData data = HauntData.get(server);
        data.player(p.getUUID()).crashes++;
        data.setDirty();
        Fear.fx(p, Fx.GLITCH, 60, 1F);
        Fear.corruptText(p, Component.translatable("herobrine.crash.pre"), 60);
        boolean host = !server.isDedicatedServer() && server.isSingleplayerOwner(p.getGameProfile());
        Scheduler.later(p, 50, sp -> {
            if (host) {
                Fear.fx(sp, Fx.CRASH, 1, 1F);
            } else {
                sp.connection.disconnect(Component.literal("Internal Exception: java.lang.IllegalStateException: Herobrine"));
            }
        });
        return true;
    }

    /** Takes the world. Only for the singleplayer owner; configurable. */
    public static boolean takeWorld(ServerPlayer p) {
        if (Config.DELETION_MODE.get() == Config.DeletionMode.OFF) return false;
        MinecraftServer server = p.getServer();
        if (server == null || server.isDedicatedServer() || !server.isSingleplayerOwner(p.getGameProfile())) return false;
        HauntData.PlayerState st = HauntData.get(server).player(p.getUUID());
        if (st.deletionStarted) return false;
        st.deletionStarted = true;
        Fear.fx(p, Fx.FOG, 2000, 1F);
        Fear.chat(p, Component.translatable("herobrine.end.0", p.getGameProfile().getName()));
        Scheduler.later(p, 60, sp -> Fear.chat(sp, Component.translatable("herobrine.end.1")));
        Scheduler.later(p, 120, sp -> {
            Fear.chat(sp, Component.translatable("herobrine.end.2"));
            Fear.fx(sp, Fx.GLITCH, 80, 1F);
            noise(sp);
        });
        Scheduler.later(p, 190, sp -> Fear.fx(sp, Fx.DELETE_WORLD, 1, 1F));
        return true;
    }

    public static void clear() {
        EATERS.clear();
    }

    private Corruption() {}
}
