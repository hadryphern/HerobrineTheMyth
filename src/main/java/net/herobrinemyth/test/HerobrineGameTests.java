package net.herobrinemyth.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.herobrinemyth.HerobrineMyth;
import net.herobrinemyth.entity.HerobrineEntity;
import net.herobrinemyth.entity.HerobrineEyesEntity;
import net.herobrinemyth.server.Builder;
import net.herobrinemyth.server.Haunts;
import net.herobrinemyth.server.HauntData;
import net.herobrinemyth.server.Possession;
import net.herobrinemyth.server.Scheduler;
import net.herobrinemyth.server.Totem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Automated checks, run with: gradlew runGameTestServer */
@GameTestHolder(HerobrineMyth.MODID)
@PrefixGameTestTemplate(false)
public final class HerobrineGameTests {

    private static ServerPlayer player(GameTestHelper h, int x, int y, int z) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "herobrine-test")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, p);
        Vec3 v = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x, y, z)));
        p.moveTo(v.x, v.y, v.z, 0F, 0F); // facing south (+z)
        return p;
    }

    private static void buildShrine(GameTestHelper h, BlockPos goldCenter) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                h.setBlock(goldCenter.offset(dx, 0, dz), Blocks.GOLD_BLOCK);
        BlockPos mossy = goldCenter.above();
        h.setBlock(mossy, Blocks.MOSSY_COBBLESTONE);
        for (Direction d : Direction.Plane.HORIZONTAL) h.setBlock(mossy.relative(d), Blocks.REDSTONE_TORCH);
        h.setBlock(mossy.above(), Blocks.NETHERRACK);
    }

    @GameTest(template = "empty")
    public static void totemShapeIsRecognised(GameTestHelper h) {
        BlockPos gold = new BlockPos(8, 1, 8);
        buildShrine(h, gold);
        ServerLevel level = h.getLevel();
        BlockPos rack = h.absolutePos(gold.above(2));
        h.assertTrue(Totem.isShrine(level, rack), "complete shrine should be recognised");
        h.setBlock(gold.above(), Blocks.COBBLESTONE);
        h.assertTrue(!Totem.isShrine(level, rack), "plain cobblestone instead of mossy must not count");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void herobrineStalksThenVanishes(GameTestHelper h) {
        ServerPlayer p = player(h, 8, 1, 2);
        Vec3 at = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8, 1, 14)));
        HerobrineEntity hb = HerobrineEntity.spawn(p, at, HerobrineEntity.Mode.STALK, 40);
        h.assertTrue(hb != null && hb.isAlive(), "Herobrine should spawn");
        h.assertTrue(p.getUUID().equals(hb.targetId()), "Herobrine should target the player");
        h.runAfterDelay(60, () -> {
            h.assertTrue(hb.isRemoved(), "Herobrine should vanish when his time is up");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void herobrineIgnoresDamageOutsideFights(GameTestHelper h) {
        ServerPlayer p = player(h, 8, 1, 2);
        HerobrineEntity hb = HerobrineEntity.spawn(p, Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8, 1, 6))), HerobrineEntity.Mode.WATCH, 200);
        h.assertTrue(hb != null, "spawn");
        boolean hurt = hb.hurt(h.getLevel().damageSources().playerAttack(p), 50F);
        h.assertTrue(!hurt, "a stalking Herobrine takes no damage");
        h.assertTrue(hb.isRemoved(), "hitting him makes him vanish");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void possessedMobsStareThenWake(GameTestHelper h) {
        ServerPlayer p = player(h, 8, 1, 2);
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(8, 1, 12));
        Possession.possess(z, p, 20, 80, true);
        h.assertTrue(z.isNoAi() && Possession.isPossessed(z), "possessed mob freezes and stares");
        h.runAfterDelay(40, () -> {
            h.assertTrue(!z.isNoAi(), "after staring the mob wakes up");
            h.assertTrue(z.getTarget() == p, "and goes for the player");
            Possession.release(z);
            h.assertTrue(!Possession.isPossessed(z), "released");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void heIsBehindTheBlockYouMine(GameTestHelper h) {
        ServerPlayer p = player(h, 8, 1, 2);
        // a stone wall in front of the player
        for (int x = 6; x <= 10; x++)
            for (int y = 1; y <= 3; y++)
                for (int z = 4; z <= 6; z++)
                    h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        BlockPos hole = h.absolutePos(new BlockPos(8, 2, 4));
        h.getLevel().setBlock(hole, Blocks.AIR.defaultBlockState(), 3);
        h.assertTrue(Haunts.blockScare(p, hole), "block scare should find room behind the block");
        h.runAfterDelay(4, () -> {
            List<HerobrineEntity> found = h.getLevel().getEntitiesOfClass(HerobrineEntity.class, new AABB(hole).inflate(2));
            h.assertTrue(!found.isEmpty(), "Herobrine should be standing behind the mined block");
            h.assertTrue(found.get(0).getMode() == HerobrineEntity.Mode.SCARE, "in scare mode");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void eyesInTheDark(GameTestHelper h) {
        ServerPlayer p = player(h, 8, 1, 2);
        HerobrineEyesEntity e = HerobrineEyesEntity.spawn(p, Vec3.atCenterOf(h.absolutePos(new BlockPos(8, 2, 15))), 30);
        h.assertTrue(e != null, "eyes spawn");
        h.runAfterDelay(40, () -> {
            h.assertTrue(e.isRemoved(), "eyes go away");
            h.succeed();
        });
    }

    /** Every structure builder runs without throwing. */
    @GameTest(template = "empty", timeoutTicks = 40, batch = "zy_builders")
    public static void allStructuresRun(GameTestHelper h) {
        ServerPlayer p = player(h, 8, 1, 8);
        List<String> errors = new ArrayList<>();
        Builder.STRUCTURES.forEach((id, b) -> {
            try {
                b.test(p);
            } catch (Exception ex) {
                errors.add(id + ": " + ex);
            }
        });
        h.assertTrue(errors.isEmpty(), "structure errors: " + errors);
        h.succeed();
    }

    /** Every haunt (except the endings) runs, and every delayed step they schedule runs too, without errors. */
    @GameTest(template = "empty", timeoutTicks = 1200, batch = "zx_haunts")
    public static void allHauntsRun(GameTestHelper h) {
        ServerPlayer p = player(h, 8, 1, 8);
        int before = Scheduler.failures;
        List<String> errors = new ArrayList<>();
        int i = 0;
        for (Haunts.Haunt haunt : Haunts.ALL.values()) {
            if (java.util.Set.of("crash", "take_world", "kick", "wither", "warden", "dragon").contains(haunt.id())) continue;
            h.runAfterDelay(1 + i * 4L, () -> {
                try {
                    haunt.run().test(p);
                } catch (Exception ex) {
                    errors.add(haunt.id() + ": " + ex);
                    HerobrineMyth.LOGGER.error("haunt {} threw", haunt.id(), ex);
                }
            });
            i++;
        }
        h.runAfterDelay(1100, () -> {
            h.assertTrue(errors.isEmpty(), "haunt errors: " + errors);
            h.assertTrue(Scheduler.failures == before, "scheduled steps failed: " + (Scheduler.failures - before));
            h.succeed();
        });
    }

    /** Blocks of his structures are remembered; breaking one is noticed once. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void structureBlocksAreMarked(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos at = h.absolutePos(new BlockPos(4, 2, 4));
        Builder.set(level, at, Blocks.COBBLESTONE.defaultBlockState());
        HauntData data = HauntData.get(level.getServer());
        h.assertTrue(data.unmark(at), "a block he placed must be remembered");
        h.assertTrue(!data.unmark(at), "and forgotten once it is broken");
        h.assertTrue(!data.unmark(at.above(3)), "other blocks are not his");
        h.succeed();
    }

    /** Animals that stop and stare, without his eyes. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void animalsStopAndStare(GameTestHelper h) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
        ServerPlayer p = player(h, 8, 1, 2);
        net.minecraft.world.entity.animal.Cow cow = h.spawn(EntityType.COW, new BlockPos(8, 1, 10));
        h.assertTrue(net.herobrinemyth.server.Staring.animals(p), "there is a cow to stare");
        h.runAfterDelay(5, () -> {
            h.assertTrue(cow.isNoAi(), "the cow must stop");
            h.assertTrue(!Possession.isPossessed(cow), "without his eyes");
            h.succeed();
        });
    }

    /** After the totem: the bosses he can bring. */
    @GameTest(template = "empty", timeoutTicks = 40, batch = "zz_bosses")
    public static void totemBossesSpawn(GameTestHelper h) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        ServerPlayer p = player(h, 8, 1, 8);
        boolean any = net.herobrinemyth.server.Terror.warden(p) | net.herobrinemyth.server.Terror.wither(p);
        h.assertTrue(any, "a boss should spawn");
        h.runAfterDelay(2, () -> {
            AABB box = new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40);
            for (var e : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box,
                    m -> m instanceof net.minecraft.world.entity.monster.warden.Warden || m instanceof net.minecraft.world.entity.boss.wither.WitherBoss))
                e.discard();
            h.succeed();
        });
    }

    /** The full totem sequence, then reset. */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "zz_totem")
    public static void totemActivationSequence(GameTestHelper h) {
        ServerPlayer p = player(h, 8, 1, 2);
        BlockPos gold = new BlockPos(8, 1, 8);
        buildShrine(h, gold);
        h.setBlock(gold.above(3), Blocks.FIRE);
        int before = Scheduler.failures;
        Totem.check(p, h.absolutePos(gold.above(2)));
        HauntData data = HauntData.get(h.getLevel().getServer());
        h.assertTrue(data.totem, "lighting the shrine activates the totem");
        h.runAfterDelay(200, () -> {
            h.assertTrue(Scheduler.failures == before, "totem sequence steps failed");
            data.totem = false;
            data.setDirty();
            h.succeed();
        });
    }

    /** The two other classic altars: HerobrineAI's and BasicHerobrine's. */
    @GameTest(template = "empty")
    public static void classicAltarShapes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos gold = new BlockPos(4, 1, 4);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                h.setBlock(gold.offset(dx, 0, dz), Blocks.GOLD_BLOCK);
        h.setBlock(gold.above(), Blocks.NETHERRACK);
        for (Direction d : Direction.Plane.HORIZONTAL) h.setBlock(gold.above().relative(d), Blocks.REDSTONE_TORCH);
        h.assertTrue(Totem.isShrine(level, h.absolutePos(gold.above())), "HerobrineAI altar");
        h.setBlock(new BlockPos(11, 1, 11), Blocks.MOSSY_COBBLESTONE);
        h.setBlock(new BlockPos(11, 2, 11), Blocks.NETHERRACK);
        h.assertTrue(Totem.isShrine(level, h.absolutePos(new BlockPos(11, 2, 11))), "BasicHerobrine altar");
        h.setBlock(new BlockPos(13, 1, 13), Blocks.STONE);
        h.setBlock(new BlockPos(13, 2, 13), Blocks.NETHERRACK);
        h.assertTrue(!Totem.isShrine(level, h.absolutePos(new BlockPos(13, 2, 13))), "plain netherrack is not an altar");
        h.succeed();
    }

    /** iLoveYou's thief: he robs a chest you used while you are away, standing on it. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void thiefRobsYourChest(GameTestHelper h) {
        ServerPlayer p = player(h, 8, 1, 8);
        ServerLevel level = h.getLevel();
        BlockPos chest = h.absolutePos(new BlockPos(8, 1, 8)).offset(26, 0, 0);
        level.setBlock(chest.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        level.setBlock(chest.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(chest.above(2), Blocks.AIR.defaultBlockState(), 3);
        var be = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(chest);
        for (int i = 0; i < 27; i++) be.setItem(i, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
        HauntData.get(level.getServer()).rememberChest(level.dimension().location().toString(), chest);
        h.assertTrue(net.herobrinemyth.server.Classic.thief(p), "thief should find the remembered chest");
        int left = 0;
        for (int i = 0; i < 27; i++) if (!be.getItem(i).isEmpty()) left++;
        h.assertTrue(left < 27, "he took something (" + left + " stacks left)");
        h.assertTrue(!level.getEntitiesOfClass(HerobrineEntity.class, new AABB(chest).inflate(2)).isEmpty(), "he is standing on the chest");
        level.setBlock(chest, Blocks.AIR.defaultBlockState(), 3);
        h.succeed();
    }

    /** HerobrineAI: buried three blocks down under cracked stone bricks. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void buriedAlive(GameTestHelper h) {
        for (int x = 6; x <= 9; x++)
            for (int z = 5; z <= 9; z++)
                for (int y = 1; y <= 5; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        ServerPlayer p = player(h, 8, 6, 8);
        double y0 = p.getY();
        h.assertTrue(net.herobrinemyth.server.Classic.bury(p), "bury should work on solid stone");
        h.assertTrue(p.getY() < y0 - 2.5, "player is three blocks down");
        h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(8, 5, 8))).is(Blocks.CRACKED_STONE_BRICKS), "covered with cracked stone bricks");
        h.succeed();
    }

    /** HerobrineAI's graveyard: there and back again. */
    @GameTest(template = "empty", timeoutTicks = 700, batch = "graveyard")
    public static void graveyardRoundTrip(GameTestHelper h) {
        ServerPlayer p = player(h, 8, 1, 8);
        h.assertTrue(net.herobrinemyth.server.Graveyard.send(p), "graveyard should open");
        h.assertTrue(net.herobrinemyth.server.Graveyard.isGraveyard(p.level()), "player is in the graveyard");
        h.runAfterDelay(620, () -> {
            h.assertTrue(!net.herobrinemyth.server.Graveyard.isGraveyard(p.level()), "player came back");
            h.succeed();
        });
    }

    /** The Ancient Sword keeps his attacks away. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void ancientSwordProtects(GameTestHelper h) {
        ServerPlayer p = player(h, 8, 1, 8);
        h.assertTrue(!net.herobrinemyth.server.Artifacts.protectedFromHim(p), "unprotected at first");
        p.getInventory().add(net.herobrinemyth.server.Artifacts.make("ancient"));
        h.assertTrue(net.herobrinemyth.server.Artifacts.protectedFromHim(p), "protected with the Ancient Sword");
        h.assertTrue(!net.herobrinemyth.server.Classic.curse(p), "curse refused");
        h.succeed();
    }

    /** He is only for the player he haunts: nobody else receives him at all. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void onlyTheTargetSeesHim(GameTestHelper h) {
        ServerPlayer target = player(h, 8, 1, 2);
        ServerPlayer friend = player(h, 6, 1, 2);
        HerobrineEntity hb = HerobrineEntity.spawn(target, Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8, 1, 12))), HerobrineEntity.Mode.WATCH, 200);
        h.assertTrue(hb != null, "spawn");
        // a normal sighting (rarely he is shared on purpose: that case is checked below)
        hb.shared = false;
        h.assertTrue(hb.broadcastToPlayer(target), "the target sees him");
        h.assertTrue(!hb.broadcastToPlayer(friend), "the friend standing next to you sees nothing");
        hb.shared = true;
        h.assertTrue(hb.broadcastToPlayer(friend), "a shared sighting is seen by everyone");
        hb.discard();
        h.succeed();
    }

    /** The plain Steve cannot be hurt or chased away. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void steveCannotBeHurt(GameTestHelper h) {
        ServerPlayer p = player(h, 8, 1, 2);
        HerobrineEntity s = HerobrineEntity.spawn(p, Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8, 1, 5))), HerobrineEntity.Mode.STEVE, 200);
        h.assertTrue(s != null, "spawn");
        h.assertTrue(!s.hurt(h.getLevel().damageSources().playerAttack(p), 100F), "no damage");
        h.assertTrue(!s.isRemoved(), "and he is still there");
        h.assertTrue(!s.eyesGlow(), "plain eyes");
        s.discard();
        h.succeed();
    }

    /** Animals and villagers come out with his eyes. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void possessedHerd(GameTestHelper h) {
        ServerPlayer p = player(h, 8, 1, 8);
        h.assertTrue(Possession.herd(p), "a herd appears");
        List<net.minecraft.world.entity.Mob> mobs = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                p.getBoundingBox().inflate(30), Possession::isPossessed);
        h.assertTrue(!mobs.isEmpty(), "and they are possessed");
        for (var m : mobs) m.discard();
        h.succeed();
    }

    /** Blood rain: night at once, rain. */
    @GameTest(template = "empty", timeoutTicks = 40, batch = "weather")
    public static void bloodRainBringsNight(GameTestHelper h) {
        ServerPlayer p = player(h, 8, 1, 8);
        ServerLevel l = h.getLevel();
        h.assertTrue(net.herobrinemyth.server.Stalking.bloodRain(p), "blood rain starts");
        h.assertTrue(l.getLevelData().isRaining(), "it rains");
        long t = l.getDayTime() % 24000;
        h.assertTrue(t >= 13000 && t <= 23000, "and it is night (" + t + ")");
        l.setWeatherParameters(6000, 0, false, false);
        h.succeed();
    }

    /** Days count up, and things unlock by day. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void progressionByDays(GameTestHelper h) {
        ServerPlayer p = player(h, 8, 1, 8);
        HauntData data = HauntData.get(h.getLevel().getServer());
        HauntData.PlayerState st = data.player(p.getUUID());
        st.days = 1;
        h.assertTrue(data.level(p.getUUID()) == 1, "day one");
        int unlockedDay1 = 0, unlockedDay30 = 0;
        for (Haunts.Haunt hh : Haunts.ALL.values()) {
            if (hh.totemOnly()) continue;
            if (Haunts.unlockDay(hh) <= 1) unlockedDay1++;
            if (Haunts.unlockDay(hh) <= 30) unlockedDay30++;
        }
        h.assertTrue(unlockedDay1 <= 3, "almost nothing on day one (" + unlockedDay1 + ")");
        h.assertTrue(unlockedDay30 > 50, "almost everything by day thirty (" + unlockedDay30 + ")");
        st.days = 12;
        h.assertTrue(data.level(p.getUUID()) == 12, "day twelve");
        h.succeed();
    }

    /** "Every 5 days he gets stronger": day 11 is step 3. "Every 10 minutes": 25 minutes is step 3. */
    @GameTest(template = "empty", timeoutTicks = 40, batch = "worldconfig")
    public static void perWorldProgression(GameTestHelper h) {
        ServerPlayer p = player(h, 8, 1, 8);
        HauntData data = HauntData.get(h.getLevel().getServer());
        HauntData.PlayerState st = data.player(p.getUUID());
        try {
            net.herobrinemyth.ServerConfig.MODE.set(net.herobrinemyth.ServerConfig.Mode.DAYS);
            net.herobrinemyth.ServerConfig.DAYS_PER_STEP.set(5);
            st.days = 11;
            h.assertTrue(data.level(p.getUUID()) == 3, "every 5 days: day 11 is step 3, got " + data.level(p.getUUID()));
            net.herobrinemyth.ServerConfig.MODE.set(net.herobrinemyth.ServerConfig.Mode.MINUTES);
            net.herobrinemyth.ServerConfig.MINUTES_PER_STEP.set(10);
            st.playTicks = 20L * 60 * 25;
            h.assertTrue(data.level(p.getUUID()) == 3, "every 10 minutes: 25 minutes is step 3, got " + data.level(p.getUUID()));
        } finally {
            net.herobrinemyth.ServerConfig.MODE.set(net.herobrinemyth.ServerConfig.Mode.DAYS);
            net.herobrinemyth.ServerConfig.DAYS_PER_STEP.set(1);
            st.days = 1;
            st.playTicks = 0;
        }
        h.succeed();
    }

    /** He hides behind a tree trunk, half visible. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void hidesBehindTrees(GameTestHelper h) {
        // a clean patch of ground with one tree
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
                for (int y = 1; y < 9; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
        ServerPlayer p = player(h, 8, 1, 1);
        for (int y = 1; y <= 5; y++) h.setBlock(new BlockPos(8, y, 13), Blocks.OAK_LOG);
        h.assertTrue(net.herobrinemyth.server.Stalking.tree(p), "he finds the tree");
        List<HerobrineEntity> found = h.getLevel().getEntitiesOfClass(HerobrineEntity.class, new AABB(h.absolutePos(new BlockPos(8, 1, 13))).inflate(3));
        h.assertTrue(!found.isEmpty() && found.get(0).getMode() == HerobrineEntity.Mode.TREE, "and stands behind it");
        for (HerobrineEntity e : found) e.discard();
        h.succeed();
    }

    private HerobrineGameTests() {}
}
