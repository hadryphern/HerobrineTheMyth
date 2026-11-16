package net.herobrinemyth.server;

import net.herobrinemyth.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Traces of someone else. Someone like you: a pillar of cobblestone, a tree chopped halfway,
 * a crafting table and a furnace left in a field, a hole going down. And fire.
 */
public final class Human {

    private static boolean structures() {
        return Config.ALLOW_STRUCTURES.get();
    }

    /** A spot on the surface out of sight, 12-28 blocks away. */
    @Nullable
    private static BlockPos hiddenGround(ServerPlayer p, double min, double max) {
        Vec3 v = Spots.findHidden(p, min, max, 0.2, 15, 40);
        if (v == null) return null;
        BlockPos at = BlockPos.containing(v);
        return Spots.solidFloor(p.serverLevel(), at.below()) ? at : null;
    }

    /** A 1x1 pillar of cobblestone, like someone who climbed up to look around. */
    public static boolean pillar(ServerPlayer p) {
        if (!structures()) return false;
        BlockPos at = hiddenGround(p, 12, 28);
        if (at == null) return false;
        ServerLevel l = p.serverLevel();
        int h = 6 + Fear.RNG.nextInt(10);
        for (int i = 0; i < h; i++) {
            if (!Builder.replaceable(l, at.above(i)) || !l.getBlockState(at.above(i)).isAir()) return i > 2;
            Builder.set(l, at.above(i), (Fear.RNG.nextInt(6) == 0 ? Blocks.DIRT : Blocks.COBBLESTONE).defaultBlockState());
        }
        if (Fear.RNG.nextBoolean()) Builder.set(l, at.above(h), Blocks.TORCH.defaultBlockState());
        return true;
    }

    /** A tree, chopped from the bottom, the top left floating. Someone gave up halfway. */
    public static boolean halfTree(ServerPlayer p) {
        if (!Config.ALLOW_GRIEFING.get()) return false;
        Spots.TreeSpot t = Spots.findTreeSpot(p, 8, 30);
        if (t == null) return false;
        ServerLevel l = p.serverLevel();
        BlockPos trunk = t.trunk();
        int cut = 1 + Fear.RNG.nextInt(2);
        for (int i = 0; i < cut; i++) {
            BlockPos b = trunk.above(i);
            if (l.getBlockState(b).is(BlockTags.LOGS)) l.setBlock(b, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
    }

    /** A 1x1 staircase digging straight down. With a torch at the top. */
    public static boolean stairsDown(ServerPlayer p) {
        if (!Config.ALLOW_GRIEFING.get()) return false;
        BlockPos at = hiddenGround(p, 10, 22);
        if (at == null) return false;
        ServerLevel l = p.serverLevel();
        Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(Fear.RNG);
        BlockPos c = at.below();
        int dug = 0;
        for (int i = 0; i < 10; i++) {
            BlockPos b = c.relative(d, i).below(i);
            for (BlockPos x : new BlockPos[]{b, b.above(), b.above(2)}) {
                BlockState s = l.getBlockState(x);
                if (s.isAir()) continue;
                if (!Builder.replaceable(l, x) || !s.getFluidState().isEmpty()) return dug > 2;
                if (x.getY() > at.getY() + 1) continue;
                l.setBlock(x, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            dug++;
        }
        Builder.set(l, at.relative(d.getOpposite()), Blocks.TORCH.defaultBlockState());
        return true;
    }

    /** Holes in the ground: one block, two blocks deep. Like someone collecting dirt. */
    public static boolean holes(ServerPlayer p) {
        if (!Config.ALLOW_GRIEFING.get()) return false;
        BlockPos at = hiddenGround(p, 8, 20);
        if (at == null) return false;
        ServerLevel l = p.serverLevel();
        int n = 0;
        for (int i = 0; i < 9; i++) {
            BlockPos b = Spots.surface(l, at.getX() + Fear.RNG.nextInt(7) - 3, at.getZ() + Fear.RNG.nextInt(7) - 3);
            if (b == null) continue;
            BlockPos ground = b.below();
            if (!l.getBlockState(ground).is(BlockTags.DIRT)) continue;
            l.setBlock(ground, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            if (Fear.RNG.nextBoolean() && l.getBlockState(ground.below()).is(BlockTags.DIRT))
                l.setBlock(ground.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            n++;
        }
        return n > 0;
    }

    /** A crafting table, a furnace still warm, a chest with a few things. Nobody. */
    public static boolean camp(ServerPlayer p) {
        if (!structures()) return false;
        BlockPos at = hiddenGround(p, 12, 26);
        if (at == null) return false;
        ServerLevel l = p.serverLevel();
        Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(Fear.RNG);
        BlockPos table = at, furnace = at.relative(d, 1 + Fear.RNG.nextInt(2)), chest = at.relative(d.getClockWise(), 2);
        if (!Spots.solidFloor(l, furnace.below()) || !l.getBlockState(furnace).isAir()) furnace = null;
        Builder.set(l, table, Blocks.CRAFTING_TABLE.defaultBlockState());
        if (furnace != null)
            Builder.set(l, furnace, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, d.getOpposite())
                    .setValue(FurnaceBlock.LIT, Fear.RNG.nextBoolean()));
        if (Fear.RNG.nextBoolean() && l.getBlockState(chest).isAir() && Spots.solidFloor(l, chest.below())) {
            Builder.put(l, chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, d), Block.UPDATE_ALL);
            if (l.getBlockEntity(chest) instanceof ChestBlockEntity c) {
                c.setItem(0, new ItemStack(Items.COBBLESTONE, 12 + Fear.RNG.nextInt(40)));
                c.setItem(1, new ItemStack(Items.DIRT, 3 + Fear.RNG.nextInt(20)));
                c.setItem(2, new ItemStack(Items.STICK, 1 + Fear.RNG.nextInt(6)));
                if (Fear.RNG.nextInt(3) == 0) c.setItem(13, Msg.note(p));
            }
        }
        BlockPos torch = at.relative(d.getCounterClockWise());
        if (l.getBlockState(torch).isAir() && Spots.solidFloor(l, torch.below())) Builder.set(l, torch, Blocks.TORCH.defaultBlockState());
        return true;
    }

    /** A dirt hut, the kind you build on your first night. Empty. */
    public static boolean hut(ServerPlayer p) {
        if (!structures()) return false;
        BlockPos at = hiddenGround(p, 14, 28);
        if (at == null) return false;
        ServerLevel l = p.serverLevel();
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++)
                for (int y = 0; y <= 2; y++) {
                    boolean wall = x != 0 || z != 0 || y == 2;
                    BlockPos b = at.offset(x, y, z);
                    if (!l.getBlockState(b).isAir() && !l.getBlockState(b).canBeReplaced()) continue;
                    if (wall && !(z == -1 && x == 0 && y < 2)) Builder.set(l, b, Blocks.DIRT.defaultBlockState());
                }
        Builder.set(l, at, Blocks.TORCH.defaultBlockState());
        return true;
    }

    /** Blocks broken in a wall of stone, in a line, as if someone mined there and stopped. */
    public static boolean mined(ServerPlayer p) {
        if (!Config.ALLOW_GRIEFING.get()) return false;
        ServerLevel l = p.serverLevel();
        for (int i = 0; i < 80; i++) {
            BlockPos b = p.blockPosition().offset(Fear.RNG.nextInt(25) - 12, Fear.RNG.nextInt(5) - 2, Fear.RNG.nextInt(25) - 12);
            if (b.distSqr(p.blockPosition()) < 25 || Spots.inView(p, Vec3.atCenterOf(b), 0.5)) continue;
            if (!l.getBlockState(b).is(BlockTags.BASE_STONE_OVERWORLD)) continue;
            Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(Fear.RNG);
            int n = 0;
            for (int k = 0; k < 4 + Fear.RNG.nextInt(4); k++) {
                for (BlockPos x : new BlockPos[]{b.relative(d, k), b.relative(d, k).above()}) {
                    if (l.getBlockState(x).is(BlockTags.BASE_STONE_OVERWORLD) && Builder.replaceable(l, x)) {
                        l.setBlock(x, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        n++;
                    }
                }
            }
            if (n > 0) return true;
        }
        return false;
    }

    // ================================================================== fire

    /** Trees on fire, somewhere you can see the smoke. */
    public static boolean burnForest(ServerPlayer p) {
        if (!Config.ALLOW_GRIEFING.get() || !Config.BURN_VILLAGES.get()) return false;
        ServerLevel l = p.serverLevel();
        int lit = 0;
        for (int i = 0; i < 300 && lit < 10; i++) {
            BlockPos b = p.blockPosition().offset(Fear.RNG.nextInt(61) - 30, Fear.RNG.nextInt(20) - 4, Fear.RNG.nextInt(61) - 30);
            if (b.distSqr(p.blockPosition()) < 15 * 15) continue;
            if (!l.getBlockState(b).is(BlockTags.LEAVES) && !l.getBlockState(b).is(BlockTags.LOGS)) continue;
            BlockPos up = b.above();
            if (l.getBlockState(up).isAir()) {
                l.setBlock(up, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
                lit++;
            }
        }
        return lit > 0;
    }

    /** A field of grass, burning. */
    public static boolean burnGrass(ServerPlayer p) {
        if (!Config.ALLOW_GRIEFING.get()) return false;
        ServerLevel l = p.serverLevel();
        BlockPos at = hiddenGround(p, 10, 24);
        if (at == null) return false;
        int lit = 0;
        for (int i = 0; i < 40; i++) {
            BlockPos b = Spots.surface(l, at.getX() + Fear.RNG.nextInt(9) - 4, at.getZ() + Fear.RNG.nextInt(9) - 4);
            if (b == null) continue;
            BlockState s = l.getBlockState(b);
            if (s.is(Blocks.GRASS) || s.is(Blocks.TALL_GRASS) || s.isAir()) {
                if (l.getBlockState(b.below()).is(Blocks.GRASS_BLOCK)) {
                    l.setBlock(b, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
                    lit++;
                }
            }
        }
        return lit > 0;
    }

    /** The nearest village burns. */
    public static boolean burnVillage(ServerPlayer p) {
        if (!Config.ALLOW_GRIEFING.get() || !Config.BURN_VILLAGES.get()) return false;
        ServerLevel l = p.serverLevel();
        PoiManager poi = l.getPoiManager();
        Optional<BlockPos> bell = poi.findClosest(h -> h.is(PoiTypes.MEETING), p.blockPosition(), 160, PoiManager.Occupancy.ANY);
        if (bell.isEmpty()) return false;
        BlockPos c = bell.get();
        int lit = 0;
        for (int i = 0; i < 600 && lit < 24; i++) {
            BlockPos b = c.offset(Fear.RNG.nextInt(49) - 24, Fear.RNG.nextInt(12) - 3, Fear.RNG.nextInt(49) - 24);
            if (!l.hasChunkAt(b)) continue;
            BlockState s = l.getBlockState(b);
            if (!(s.is(BlockTags.PLANKS) || s.is(BlockTags.LOGS) || s.is(BlockTags.WOOL) || s.is(Blocks.HAY_BLOCK))) continue;
            for (Direction d : Direction.values()) {
                BlockPos f = b.relative(d);
                if (l.getBlockState(f).isAir() && d != Direction.DOWN) {
                    l.setBlock(f, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
                    lit++;
                    break;
                }
            }
        }
        if (lit > 0) Fear.soundReverb(p, "block.bell.use", Vec3.atCenterOf(c).add(Vec3.atCenterOf(p.blockPosition()).subtract(Vec3.atCenterOf(c)).scale(0.6)), 1.5F, 0.5F);
        return lit > 0;
    }

    /** The animals around you, dead. Their drops still on the ground. */
    public static boolean killAnimals(ServerPlayer p) {
        List<Animal> list = new ArrayList<>(p.level().getEntitiesOfClass(Animal.class, p.getBoundingBox().inflate(32, 10, 32),
                a -> !Spots.isLookingAt(p, a, 0.7)));
        if (list.isEmpty()) return false;
        int n = Math.min(list.size(), 2 + Fear.RNG.nextInt(4));
        for (int i = 0; i < n; i++) list.get(i).hurt(p.damageSources().magic(), 1000F);
        return true;
    }

    /** Lava, falling from above. Gone a few seconds later. */
    public static void lavaRain(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        List<BlockPos> placed = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            BlockPos b = p.blockPosition().offset(Fear.RNG.nextInt(7) - 3, 4 + Fear.RNG.nextInt(3), Fear.RNG.nextInt(7) - 3);
            if (l.getBlockState(b).isAir()) {
                l.setBlock(b, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
                placed.add(b);
            }
        }
        Scheduler.later(50, () -> {
            for (BlockPos b : placed) if (l.getBlockState(b).is(Blocks.LAVA)) l.setBlock(b, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            // the lava that flowed down cools down too
            BlockPos c = p.blockPosition();
            for (BlockPos b : BlockPos.betweenClosed(c.offset(-6, -3, -6), c.offset(6, 8, 6))) {
                BlockState s = l.getBlockState(b);
                if (s.is(Blocks.LAVA) && !s.getFluidState().isSource()) l.setBlock(b, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        });
    }

    static int clamp(int v, int min, int max) {
        return Mth.clamp(v, min, max);
    }

    private Human() {}
}
