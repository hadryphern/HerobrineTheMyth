package net.herobrinemyth.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/** The things he builds while you are not looking. */
public final class Builder {
    public static final int FLAGS = Block.UPDATE_CLIENTS;

    public static final Map<String, Predicate<ServerPlayer>> STRUCTURES = new LinkedHashMap<>();

    static {
        STRUCTURES.put("pyramid", Builder::pyramid);
        STRUCTURES.put("leafless", Builder::leafless);
        STRUCTURES.put("tunnel", Builder::tunnel);
        STRUCTURES.put("cross", Builder::cross);
        STRUCTURES.put("grave", Builder::grave);
        STRUCTURES.put("signs", p -> signs(p, 1 + Fear.RNG.nextInt(3)));
        STRUCTURES.put("redstone", Builder::redstoneTorches);
        STRUCTURES.put("pillar", Builder::pillar);
        STRUCTURES.put("shrine", Builder::shrine);
        STRUCTURES.put("dungeon", Builder::dungeon);
        STRUCTURES.put("face", Builder::face);
        STRUCTURES.put("temple", Builder::temple);
        STRUCTURES.put("cave", Classic::cave);
        STRUCTURES.put("heads", Classic::heads);
        STRUCTURES.put("pike", Classic::pike);
        STRUCTURES.put("chest", Classic::giftChest);
        STRUCTURES.put("ground_cross", Builder::groundCross);
        STRUCTURES.put("smile", Builder::smile);
    }

    /**
     * Picks something that fits where the player is and how far the haunting has gone.
     * Early days: small things you might not even notice. Later: things nobody could miss.
     */
    public static boolean random(ServerPlayer p, int day) {
        boolean under = Spots.isUnderground(p);
        java.util.List<String> pool = new java.util.ArrayList<>();
        if (under) {
            java.util.Collections.addAll(pool, "redstone", "redstone", "signs", "tunnel", "cave");
            if (day >= 6) java.util.Collections.addAll(pool, "tunnel", "smile", "ground_cross");
            if (day >= 10) java.util.Collections.addAll(pool, "dungeon", "smile");
        } else {
            java.util.Collections.addAll(pool, "signs", "signs", "ground_cross", "redstone", "leafless");
            if (day >= 6) java.util.Collections.addAll(pool, "pyramid", "pyramid", "cross", "grave", "pillar", "smile", "leafless");
            if (day >= 12) java.util.Collections.addAll(pool, "shrine", "pike", "smile", "ground_cross");
            if (day >= 16) java.util.Collections.addAll(pool, "face", "temple", "cross");
        }
        for (int i = 0; i < 4; i++) {
            String id = pool.get(Fear.RNG.nextInt(pool.size()));
            if (STRUCTURES.get(id).test(p)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ helpers

    public static boolean replaceable(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return !s.hasBlockEntity() && s.getDestroySpeed(level, pos) >= 0 && !s.is(Blocks.BEDROCK);
    }

    public static void set(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.hasChunkAt(pos) && replaceable(level, pos)) {
            put(level, pos, state, FLAGS);
            if (!state.isAir()) mark(level, pos);
        }
    }

    /** Places a block and remembers it as his. */
    public static void put(ServerLevel level, BlockPos pos, BlockState state, int flags) {
        level.setBlock(pos, state, flags);
        mark(level, pos);
    }

    /** One of his blocks: breaking it has consequences. */
    public static void mark(ServerLevel level, BlockPos pos) {
        if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) HauntData.get(level.getServer()).mark(pos);
    }

    @Nullable
    private static BlockPos hiddenSurface(ServerPlayer p, double min, double max) {
        Vec3 v = Spots.findHidden(p, min, max, 0.1, 15, 40);
        if (v == null) return null;
        BlockPos s = Spots.surface(p.serverLevel(), Mth.floor(v.x), Mth.floor(v.z));
        return s;
    }

    public static boolean placeSign(ServerLevel level, BlockPos pos, Vec3 faceTowards, Component... lines) {
        if (!level.hasChunkAt(pos) || !replaceable(level, pos)) return false;
        float yaw = Spots.yawTo(faceTowards, Vec3.atCenterOf(pos));
        BlockState sign = Blocks.DARK_OAK_SIGN.defaultBlockState()
                .setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(yaw + 180F));
        put(level, pos, sign, Block.UPDATE_ALL);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SignBlockEntity s) {
            SignText text = new SignText().setColor(DyeColor.RED).setHasGlowingText(true);
            for (int i = 0; i < Math.min(4, lines.length); i++) text = text.setMessage(i, lines[i]);
            s.setText(text, true);
            s.setWaxed(true);
            s.setChanged();
            level.sendBlockUpdated(pos, sign, sign, Block.UPDATE_ALL);
        }
        return true;
    }

    // ------------------------------------------------------------------ structures

    /** The classic sand pyramid. */
    public static boolean pyramid(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos c = hiddenSurface(p, 22, 48);
        if (c == null) return false;
        int baseY = c.getY();
        boolean classic = Fear.RNG.nextBoolean(); // HerobrineAI's small sandstone one, or the creepypasta's sand one
        int[] sizes = classic ? new int[]{5, 3, 1} : new int[]{7, 5, 3, 1};
        for (int layer = 0; layer < sizes.length; layer++) {
            int r = sizes[layer] / 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos at = new BlockPos(c.getX() + dx, baseY + layer, c.getZ() + dz);
                    if (layer == 0) {
                        // support down to the ground so nothing falls
                        int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
                        for (int y = Math.min(g, baseY) ; y < baseY; y++) set(level, new BlockPos(at.getX(), y, at.getZ()), Blocks.SANDSTONE.defaultBlockState());
                    }
                    set(level, at, (classic ? Blocks.SANDSTONE : Blocks.SAND).defaultBlockState());
                }
            }
        }
        set(level, new BlockPos(c.getX(), baseY + sizes.length, c.getZ()), Blocks.REDSTONE_TORCH.defaultBlockState());
        return true;
    }

    /** Every tree around you, stripped of leaves. */
    public static boolean leafless(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos c = p.blockPosition();
        int r = 26, count = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (!level.hasChunkAt(m.set(c.getX() + x, 0, c.getZ() + z))) continue;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, c.getX() + x, c.getZ() + z);
                for (int y = top; y >= top - 14; y--) {
                    m.set(c.getX() + x, y, c.getZ() + z);
                    if (level.getBlockState(m).is(BlockTags.LEAVES)) {
                        level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        count++;
                    }
                }
            }
        }
        return count > 20;
    }

    /** Perfect 2x2 tunnels through the stone. Nobody digs like that. */
    public static boolean tunnel(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 start = Spots.findHidden(p, 6, 14, 0.2, 15, 30);
        if (start == null) return false;
        Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(Fear.RNG);
        Direction side = dir.getClockWise();
        BlockPos s = BlockPos.containing(start);
        int len = 24 + Fear.RNG.nextInt(30);
        int carved = 0;
        for (int i = -len / 2; i < len / 2; i++) {
            for (int w = 0; w < 2; w++) {
                for (int h = 0; h < 2; h++) {
                    BlockPos at = s.relative(dir, i).relative(side, w).above(h);
                    BlockState st = level.getBlockState(at);
                    if ((st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(BlockTags.DIRT) || st.is(Blocks.GRAVEL)) && replaceable(level, at)) {
                        level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS);
                        carved++;
                    }
                }
            }
            BlockPos floor = s.relative(dir, i);
            if (Fear.RNG.nextInt(10) < 2 && level.getBlockState(floor).isAir() && Spots.solidFloor(level, floor.below()))
                put(level, floor, Blocks.REDSTONE_TORCH.defaultBlockState(), Block.UPDATE_ALL);
        }
        return carved > 10;
    }

    /** A burning netherrack cross. */
    public static boolean cross(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos c = hiddenSurface(p, 14, 34);
        if (c == null) return false;
        Vec3 d = p.position().subtract(Vec3.atCenterOf(c));
        Direction across = Math.abs(d.x) > Math.abs(d.z) ? Direction.SOUTH : Direction.EAST;
        BlockState rack = Blocks.NETHERRACK.defaultBlockState();
        for (int y = 0; y < 5; y++) set(level, c.above(y), rack);
        set(level, c.above(3).relative(across), rack);
        set(level, c.above(3).relative(across.getOpposite()), rack);
        set(level, c.above(5), Blocks.FIRE.defaultBlockState());
        set(level, c.above(4).relative(across), Blocks.FIRE.defaultBlockState());
        set(level, c.above(4).relative(across.getOpposite()), Blocks.FIRE.defaultBlockState());
        return true;
    }

    /** Your grave. */
    public static boolean grave(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos c = hiddenSurface(p, 6, 16);
        if (c == null) return false;
        Vec3 d = p.position().subtract(Vec3.atCenterOf(c));
        Direction toward = Direction.getNearest(d.x, 0, d.z);
        set(level, c.below(), Blocks.COARSE_DIRT.defaultBlockState());
        set(level, c.below().relative(toward), Blocks.COARSE_DIRT.defaultBlockState());
        set(level, c.below().relative(toward, 2), Blocks.PODZOL.defaultBlockState());
        set(level, c, Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState());
        placeSign(level, c.relative(toward), p.position(),
                Component.literal("R.I.P."),
                Component.literal(p.getGameProfile().getName()),
                Component.translatable("herobrine.sign.grave"),
                Component.literal("-H"));
        put(level, c.above(), Blocks.SKELETON_SKULL.defaultBlockState()
                .setValue(net.minecraft.world.level.block.SkullBlock.ROTATION, RotationSegment.convertToSegment(Spots.yawTo(p.position(), Vec3.atCenterOf(c)) + 180F)), FLAGS);
        return true;
    }

    /** Signs with messages, out of sight. */
    public static boolean signs(ServerPlayer p, int count) {
        ServerLevel level = p.serverLevel();
        int placed = 0;
        for (int i = 0; i < count; i++) {
            Vec3 v = Spots.findHidden(p, 4, 13, 0.2, 15, 30);
            if (v == null) continue;
            BlockPos pos = BlockPos.containing(v);
            int n = Fear.RNG.nextInt(Msg.SIGNS);
            if (placeSign(level, pos, p.position(),
                    Component.translatable("herobrine.sign." + n + ".a").withStyle(ChatFormatting.DARK_RED),
                    Component.translatable("herobrine.sign." + n + ".b"),
                    Component.translatable("herobrine.sign." + n + ".c"),
                    Component.translatable("herobrine.sign." + n + ".d"))) placed++;
        }
        return placed > 0;
    }

    /** Redstone torches in the dark caves. Someone was here. */
    public static boolean redstoneTorches(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        int placed = 0;
        for (int i = 0; i < 9; i++) {
            Vec3 v = Spots.findHidden(p, 5, 18, 0.3, 6, 20);
            if (v == null) continue;
            BlockPos pos = BlockPos.containing(v);
            if (level.getBlockState(pos).isAir()) {
                put(level, pos, Blocks.REDSTONE_TORCH.defaultBlockState(), Block.UPDATE_ALL);
                placed++;
            }
        }
        return placed > 0;
    }

    /** A tall thin pillar with fire on top. */
    public static boolean pillar(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos c = hiddenSurface(p, 18, 40);
        if (c == null) return false;
        int h = 10 + Fear.RNG.nextInt(14);
        BlockState mat = Fear.RNG.nextBoolean() ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
        for (int y = 0; y < h; y++) set(level, c.above(y), mat);
        set(level, c.above(h), Blocks.NETHERRACK.defaultBlockState());
        set(level, c.above(h + 1), Blocks.FIRE.defaultBlockState());
        return true;
    }

    /** His shrine. Unlit. Waiting for you. */
    public static boolean shrine(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos c = hiddenSurface(p, 16, 32);
        if (c == null) return false;
        BlockPos base = c;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                set(level, base.offset(dx, 0, dz), Blocks.GOLD_BLOCK.defaultBlockState());
        set(level, base.above(), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        for (Direction d : Direction.Plane.HORIZONTAL) set(level, base.above().relative(d), Blocks.REDSTONE_TORCH.defaultBlockState());
        set(level, base.above(2), Blocks.NETHERRACK.defaultBlockState());
        placeSign(level, base.relative(Direction.getNearest(p.getX() - base.getX(), 0, p.getZ() - base.getZ()), 2), p.position(),
                Component.translatable("herobrine.sign.shrine.a"), Component.translatable("herobrine.sign.shrine.b"),
                Component.translatable("herobrine.sign.shrine.c"), Component.empty());
        return true;
    }

    /** A mossy room with a spawner, hidden in the stone near you. */
    public static boolean dungeon(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 v = Spots.findHidden(p, 10, 22, 0.3, 15, 40);
        if (v == null) return false;
        BlockPos c = BlockPos.containing(v);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos at = c.offset(dx, dy, dz);
                    boolean shell = Math.abs(dx) == 3 || Math.abs(dz) == 3 || dy == -1 || dy == 3;
                    if (shell) {
                        if (!level.getBlockState(at).isAir() || dy == -1)
                            set(level, at, (Fear.RNG.nextBoolean() ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState());
                    } else {
                        set(level, at, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        put(level, c, Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(c) instanceof SpawnerBlockEntity sp) {
            EntityType<?>[] types = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CAVE_SPIDER};
            sp.setEntityId(types[Fear.RNG.nextInt(types.length)], Fear.RNG);
            sp.setChanged();
        }
        BlockPos chest = c.offset(2, 0, 2);
        put(level, chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(chest) instanceof ChestBlockEntity ch) {
            ch.setItem(13, Msg.book(p));
            ch.setItem(4, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE_TORCH, 4));
            ch.setItem(22, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE, 3));
        }
        return true;
    }

    /** A ruined stone brick temple with one of his artifacts inside, and guardians. */
    public static boolean temple(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos c = hiddenSurface(p, 22, 44);
        if (c == null) return false;
        java.util.Random r = new java.util.Random(Fear.RNG.nextLong());
        BlockState[] bricks = {Blocks.STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(),
                Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState()};
        int x0 = c.getX() - 5, z0 = c.getZ() - 6, y0 = c.getY();
        int W = 11, D = 13, H = 6;
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < D; z++) {
                // foundation down to the ground
                for (int y = y0 - 1; y > y0 - 8 && Spots.passable(level, new BlockPos(x0 + x, y, z0 + z)); y--)
                    set(level, new BlockPos(x0 + x, y, z0 + z), bricks[r.nextInt(bricks.length)]);
                set(level, new BlockPos(x0 + x, y0, z0 + z), bricks[r.nextInt(bricks.length)]);
                boolean edge = x == 0 || z == 0 || x == W - 1 || z == D - 1;
                for (int y = 1; y < H; y++) {
                    BlockPos at = new BlockPos(x0 + x, y0 + y, z0 + z);
                    if (!edge) set(level, at, Blocks.AIR.defaultBlockState());
                    else if (z == D - 1 && x >= 4 && x <= 6 && y <= 3) set(level, at, Blocks.AIR.defaultBlockState()); // door
                    else if ((x == 0 || x == W - 1) && (y == 2 || y == 3) && z % 3 == 1) set(level, at, Blocks.IRON_BARS.defaultBlockState());
                    else if (r.nextInt(12) != 0) set(level, at, bricks[r.nextInt(bricks.length)]); // some holes, it is old
                }
                set(level, new BlockPos(x0 + x, y0 + H, z0 + z), bricks[r.nextInt(bricks.length)]);
                if (x >= 2 && x <= W - 3 && z >= 2 && z <= D - 3 && (x == 2 || z == 2 || x == W - 3 || z == D - 3))
                    set(level, new BlockPos(x0 + x, y0 + H + 1, z0 + z), bricks[r.nextInt(bricks.length)]);
            }
        }
        // vines on the outside
        for (int i = 0; i < 40; i++) {
            int side = r.nextInt(4);
            int x = side == 0 ? -1 : side == 1 ? W : 1 + r.nextInt(W - 2);
            int z = side == 2 ? -1 : side == 3 ? D : 1 + r.nextInt(D - 2);
            if (side < 2) z = 1 + r.nextInt(D - 2);
            BlockPos at = new BlockPos(x0 + x, y0 + 1 + r.nextInt(H), z0 + z);
            Direction toWall = side == 0 ? Direction.EAST : side == 1 ? Direction.WEST : side == 2 ? Direction.SOUTH : Direction.NORTH;
            if (level.getBlockState(at).isAir())
                put(level, at, Blocks.VINE.defaultBlockState().setValue(net.minecraft.world.level.block.VineBlock.getPropertyForFace(toWall), true), FLAGS);
        }
        // redstone torches on the roof corners and inside
        set(level, new BlockPos(x0 + 2, y0 + H + 2, z0 + 2), Blocks.REDSTONE_TORCH.defaultBlockState());
        set(level, new BlockPos(x0 + W - 3, y0 + H + 2, z0 + 2), Blocks.REDSTONE_TORCH.defaultBlockState());
        set(level, new BlockPos(x0 + 2, y0 + H + 2, z0 + D - 3), Blocks.REDSTONE_TORCH.defaultBlockState());
        set(level, new BlockPos(x0 + W - 3, y0 + H + 2, z0 + D - 3), Blocks.REDSTONE_TORCH.defaultBlockState());
        set(level, new BlockPos(x0 + 1, y0 + 1, z0 + 1), Blocks.REDSTONE_TORCH.defaultBlockState());
        set(level, new BlockPos(x0 + W - 2, y0 + 1, z0 + 1), Blocks.REDSTONE_TORCH.defaultBlockState());
        // the altar and the chest
        BlockPos chest = new BlockPos(x0 + 5, y0 + 1, z0 + 2);
        set(level, chest.below(), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        put(level, chest, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
        if (level.getBlockEntity(chest) instanceof ChestBlockEntity ch) {
            ch.setItem(13, Artifacts.random());
            if (r.nextInt(3) == 0) ch.setItem(4, Msg.classicBook(p));
            ch.setItem(22, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE, 2 + r.nextInt(5)));
        }
        Classic.guardians(level, new BlockPos(x0 + 5, y0 + 1, z0 + 5), 2 + r.nextInt(2));
        return true;
    }

    /** A cross lying on the ground, with a fire where the arms meet. */
    public static boolean groundCross(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos c = hiddenSurface(p, 10, 30);
        if (c == null) return false;
        BlockState mat = (Fear.RNG.nextBoolean() ? Blocks.NETHERRACK : Fear.RNG.nextBoolean() ? Blocks.COBBLESTONE : Blocks.GRAVEL).defaultBlockState();
        Direction along = Direction.Plane.HORIZONTAL.getRandomDirection(Fear.RNG);
        Direction across = along.getClockWise();
        BlockPos ground = c.below();
        for (int i = -2; i <= 3; i++) set(level, ground.relative(along, i), mat);
        set(level, ground.relative(across), mat);
        set(level, ground.relative(across.getOpposite()), mat);
        if (mat.is(Blocks.NETHERRACK)) set(level, c, Blocks.FIRE.defaultBlockState());
        else set(level, c, Blocks.REDSTONE_TORCH.defaultBlockState());
        return true;
    }

    /**
     * A smile. Two redstone torches for eyes, a mouth of netherrack, on a wall or a hillside,
     * facing where you are standing right now.
     */
    public static boolean smile(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (int i = 0; i < 40; i++) {
            Vec3 v = Spots.findHidden(p, 8, 24, 0.3, 15, 4);
            if (v == null) continue;
            BlockPos c = BlockPos.containing(v);
            Vec3 d = p.position().subtract(Vec3.atCenterOf(c));
            Direction toPlayer = Direction.getNearest(d.x, 0, d.z);
            BlockPos wall = c.relative(toPlayer.getOpposite());
            // a wall of solid blocks, 5 wide and 4 tall, facing the player
            Direction right = toPlayer.getCounterClockWise();
            boolean ok = true;
            for (int w = -2; w <= 2 && ok; w++)
                for (int h = 0; h < 4 && ok; h++)
                    if (!level.getBlockState(wall.relative(right, w).above(h)).isSolidRender(level, wall.relative(right, w).above(h))) ok = false;
            if (!ok) continue;
            // eyes
            set(level, wall.relative(right, -1).above(3), Blocks.REDSTONE_BLOCK.defaultBlockState());
            set(level, wall.relative(right, 1).above(3), Blocks.REDSTONE_BLOCK.defaultBlockState());
            // mouth
            set(level, wall.relative(right, -2).above(1), Blocks.NETHERRACK.defaultBlockState());
            set(level, wall.relative(right, 2).above(1), Blocks.NETHERRACK.defaultBlockState());
            for (int w = -1; w <= 1; w++) set(level, wall.relative(right, w), Blocks.NETHERRACK.defaultBlockState());
            return true;
        }
        return false;
    }

    private static final String[] FACE = {
            "HHHHHHHH",
            "HHHHHHHH",
            "HSSSSSSH",
            "SSSSSSSS",
            "SWWSSWWS",
            "SSSNNSSS",
            "SSMMMMSS",
            "SSMSSMSS",
    };

    /** His face, eight blocks tall, staring at where you were standing. */
    public static boolean face(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos c = hiddenSurface(p, 26, 46);
        if (c == null) return false;
        Vec3 d = p.position().subtract(Vec3.atCenterOf(c));
        Direction facing = Direction.getNearest(d.x, 0, d.z);
        Direction right = facing.getCounterClockWise();
        int baseY = c.getY();
        for (int i = -4; i < 4; i++) {
            BlockPos col = c.relative(right, i);
            int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
            baseY = Math.max(baseY, g);
        }
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                char ch = FACE[row].charAt(col);
                BlockState st = switch (ch) {
                    case 'H' -> Blocks.DARK_OAK_PLANKS.defaultBlockState();
                    case 'W' -> Blocks.SEA_LANTERN.defaultBlockState();
                    case 'N' -> Blocks.ORANGE_TERRACOTTA.defaultBlockState();
                    case 'M' -> Blocks.BROWN_TERRACOTTA.defaultBlockState();
                    default -> Blocks.TERRACOTTA.defaultBlockState();
                };
                // col 0 is the face's right side == viewer's left
                BlockPos at = new BlockPos(c.getX(), baseY + 7 - row, c.getZ()).relative(right, col - 4);
                set(level, at, st);
            }
        }
        // legs of dirt down to the ground so it stands
        for (int i = -4; i < 4; i++) {
            BlockPos col = new BlockPos(c.getX(), baseY - 1, c.getZ()).relative(right, i);
            for (int y = col.getY(); y > col.getY() - 12 && Spots.passable(level, new BlockPos(col.getX(), y, col.getZ())); y--)
                set(level, new BlockPos(col.getX(), y, col.getZ()), Blocks.DARK_OAK_PLANKS.defaultBlockState());
        }
        return true;
    }

    private Builder() {}
}
