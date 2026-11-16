package net.herobrinemyth.server;

import net.herobrinemyth.Config;
import net.herobrinemyth.entity.HerobrineEntity;
import net.herobrinemyth.entity.HerobrineEntity.Mode;
import net.herobrinemyth.network.Fx;
import net.herobrinemyth.network.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Version 1.4: more of him, everywhere. Small things that say "he is real, he is here",
 * appearances close enough to be seen in the fog, attacks out of nowhere.
 */
public final class Terror {

    private static String name(ServerPlayer p) {
        return p.getGameProfile().getName();
    }

    // ================================================================== sounds and the mind

    /** Breathing, right next to your ear. */
    public static boolean breath(ServerPlayer p) {
        for (int i = 0; i < 3; i++) {
            Scheduler.later(p, i * 26, sp -> Fear.ear(sp, "entity.wolf.pant", 0.55F, 0.42F));
        }
        return true;
    }

    /** Your name, whispered. */
    public static boolean nameWhisper(ServerPlayer p) {
        Fear.whisper(p, Component.translatable("herobrine.namewhisper", name(p)), 70);
        Fear.ear(p, "ambient.cave", 0.35F, 1.6F);
        return true;
    }

    /** Someone picking up an item right behind you. */
    public static boolean pickup(ServerPlayer p) {
        Fear.soundRel(p, "entity.item.pickup", 0, 0, -2.2, 0.7F, 0.8F);
        return true;
    }

    /** Someone eating behind you. */
    public static boolean eating(ServerPlayer p) {
        for (int i = 0; i < 4; i++) {
            int n = i;
            Scheduler.later(p, i * 5, sp -> Fear.soundRel(sp, n == 3 ? "entity.player.burp" : "entity.generic.eat", 0.3, 0, -3, 0.8F, 0.85F));
        }
        return true;
    }

    /** A player getting hurt, far away. You are alone in this world. */
    public static boolean farHurt(ServerPlayer p) {
        Vec3 at = p.position().add(Vec3.directionFromRotation(0, Fear.RNG.nextFloat() * 360F).scale(14 + Fear.RNG.nextInt(8)));
        Fear.soundReverb(p, "entity.player.hurt", at, 1.2F, 1F);
        Scheduler.later(p, 18, sp -> Fear.soundReverb(sp, "entity.player.hurt", at, 1.2F, 1F));
        if (Fear.RNG.nextInt(3) == 0) Scheduler.later(p, 50, sp -> Fear.soundReverb(sp, "entity.player.death", at, 1.2F, 0.9F));
        return true;
    }

    private static final String[] DEATHS = {"death.attack.outOfWorld", "death.fell.accident.generic", "death.attack.inWall",
            "death.attack.generic", "death.attack.lava", "death.attack.drown"};

    /** Someone died. Maybe Steve. Maybe you. */
    public static boolean fakeDeath(ServerPlayer p) {
        if (!Config.FAKE_CHAT.get()) return false;
        String who = switch (Fear.RNG.nextInt(4)) {
            case 0 -> "Steve";
            case 1 -> "Alex";
            default -> name(p);
        };
        Component msg = Component.translatable(Fear.pick(DEATHS), who);
        if (Fear.shared(0.5)) for (ServerPlayer o : Fear.everyone(p)) o.sendSystemMessage(msg);
        else p.sendSystemMessage(msg);
        return true;
    }

    /** "<you> help". You did not write that. */
    public static boolean selfChat(ServerPlayer p) {
        if (!Config.FAKE_CHAT.get()) return false;
        Component line = Component.literal("<" + name(p) + "> ").append(Component.translatable("herobrine.self." + Fear.RNG.nextInt(Msg.SELF)));
        if (Fear.shared(0.6)) for (ServerPlayer o : Fear.everyone(p)) o.sendSystemMessage(line);
        else p.sendSystemMessage(line);
        return true;
    }

    /** "has made the advancement [He Sees You]". */
    public static boolean fakeAdvancement(ServerPlayer p) {
        if (!Config.FAKE_CHAT.get()) return false;
        Component adv = ComponentUtils.wrapInSquareBrackets(Component.translatable("herobrine.adv." + Fear.RNG.nextInt(Msg.ADV)))
                .withStyle(ChatFormatting.GREEN);
        Component msg = Component.translatable("chat.type.advancement.task", p.getDisplayName(), adv);
        if (Fear.shared(0.5)) for (ServerPlayer o : Fear.everyone(p)) o.sendSystemMessage(msg);
        else p.sendSystemMessage(msg);
        return true;
    }

    /** A little damage. From nothing. */
    public static boolean sting(ServerPlayer p) {
        if (!Config.RANDOM_DAMAGE.get() || p.isCreative()) return false;
        Fear.hurt(p, p.damageSources().generic(), 1F + Fear.RNG.nextInt(3));
        return true;
    }

    /** The light leaves for a few seconds. Your heart is loud. */
    public static boolean darkness(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 7, 0, false, false));
        Fear.fx(p, Fx.HEARTBEAT, 20 * 7, 1F);
        return true;
    }

    /** The torches around you go out... and come back. Twice. */
    public static boolean flicker(ServerPlayer p) {
        if (!Config.ALLOW_GRIEFING.get()) return false;
        ServerLevel l = p.serverLevel();
        List<BlockPos> torches = new ArrayList<>();
        BlockPos c = p.blockPosition();
        for (BlockPos b : BlockPos.betweenClosed(c.offset(-12, -4, -12), c.offset(12, 4, 12))) {
            BlockState s = l.getBlockState(b);
            if (s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH)) torches.add(b.immutable());
            if (torches.size() > 8) break;
        }
        if (torches.isEmpty()) return false;
        for (int round = 0; round < 2; round++) {
            int at = round * 40;
            for (BlockPos b : torches) {
                BlockState was = l.getBlockState(b);
                Scheduler.later(at, () -> {
                    if (l.getBlockState(b) == was) l.setBlock(b, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                });
                Scheduler.later(at + 12 + Fear.RNG.nextInt(10), () -> {
                    if (l.getBlockState(b).isAir()) l.setBlock(b, was, Block.UPDATE_ALL);
                });
            }
        }
        Fear.sound(p, "block.fire.extinguish", p.position(), 0.5F, 0.5F);
        return true;
    }

    /** Something in your inventory has a new name. */
    public static boolean renameItem(ServerPlayer p) {
        List<ItemStack> full = new ArrayList<>();
        for (ItemStack s : p.getInventory().items) if (!s.isEmpty() && !s.hasCustomHoverName()) full.add(s);
        if (full.isEmpty()) return false;
        ItemStack s = full.get(Fear.RNG.nextInt(full.size()));
        s.setHoverName(Component.translatable("herobrine.item." + Fear.RNG.nextInt(Msg.ITEM)).withStyle(ChatFormatting.DARK_RED));
        return true;
    }

    /** The sign you wrote says something else now. */
    public static boolean signEdit(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        BlockPos c = p.blockPosition();
        for (BlockPos b : BlockPos.betweenClosed(c.offset(-16, -5, -16), c.offset(16, 5, 16))) {
            BlockEntity be = l.getBlockEntity(b);
            if (!(be instanceof SignBlockEntity sign) || Spots.inView(p, Vec3.atCenterOf(b), 0.6)) continue;
            SignText text = new SignText();
            int sign2 = Fear.RNG.nextInt(Msg.SIGNS);
            String[] parts = {"a", "b", "c", "d"};
            for (int i = 0; i < 4; i++) text = text.setMessage(i, Component.translatable("herobrine.sign." + sign2 + "." + parts[i]));
            sign.setText(text, true);
            sign.setChanged();
            l.sendBlockUpdated(b, l.getBlockState(b), l.getBlockState(b), Block.UPDATE_ALL);
            return true;
        }
        return false;
    }

    /** One of your windows breaks. Nobody is there. */
    public static boolean glassBreak(ServerPlayer p) {
        if (!Config.ALLOW_HOUSE_DAMAGE.get()) return false;
        ServerLevel l = p.serverLevel();
        BlockPos c = p.blockPosition();
        for (int i = 0; i < 200; i++) {
            BlockPos b = c.offset(Fear.RNG.nextInt(25) - 12, Fear.RNG.nextInt(7) - 2, Fear.RNG.nextInt(25) - 12);
            BlockState s = l.getBlockState(b);
            if (!(s.is(Blocks.GLASS) || s.is(Blocks.GLASS_PANE)) || b.distSqr(c) < 9) continue;
            l.destroyBlock(b, false);
            return true;
        }
        return false;
    }

    /** Something thrown at you from where you cannot see. */
    public static boolean thrown(ServerPlayer p) {
        Vec3 from = Spots.findHidden(p, 6, 12, 0.2, 15, 30);
        if (from == null) return false;
        ServerLevel l = p.serverLevel();
        ItemStack stack = switch (Fear.RNG.nextInt(9)) {
            case 0 -> new ItemStack(Items.ROTTEN_FLESH);
            case 1 -> new ItemStack(Items.BONE);
            case 2 -> new ItemStack(Items.REDSTONE_TORCH);
            case 3, 4 -> Msg.note(p);
            case 5 -> new ItemStack(Items.MUTTON);
            case 6 -> new ItemStack(Items.FLINT_AND_STEEL);
            case 7 -> new ItemStack(Items.STICK);
            default -> Fear.RNG.nextInt(4) == 0 ? Classic.headOf(p) : new ItemStack(Items.COBBLESTONE);
        };
        Vec3 start = from.add(0, 1.4, 0);
        Vec3 to = p.position().add(0, 0.5, 0).subtract(start);
        double d = to.length();
        Vec3 v = to.normalize().scale(Math.min(1.2, 0.25 + d * 0.07)).add(0, 0.25 + d * 0.012, 0);
        ItemEntity item = new ItemEntity(l, start.x, start.y, start.z, stack);
        item.setDeltaMovement(v);
        item.setPickUpDelay(10);
        l.addFreshEntity(item);
        Fear.sound(p, "entity.snowball.throw", start, 0.6F, 0.6F);
        return true;
    }

    /** An arrow, out of the dark. It misses. This time. */
    public static boolean arrow(ServerPlayer p) {
        Vec3 from = Spots.findHidden(p, 10, 18, 0.25, 15, 30);
        if (from == null) return false;
        ServerLevel l = p.serverLevel();
        Vec3 start = from.add(0, 1.5, 0);
        Vec3 side = p.getViewVector(1F).cross(new Vec3(0, 1, 0)).normalize().scale(Fear.RNG.nextBoolean() ? 1.6 : -1.6);
        Vec3 aim = p.getEyePosition().add(side).subtract(start);
        Arrow a = new Arrow(l, start.x, start.y, start.z);
        a.pickup = AbstractArrow.Pickup.DISALLOWED;
        a.shoot(aim.x, aim.y + aim.length() * 0.05, aim.z, 2.2F, 0.5F);
        l.addFreshEntity(a);
        Fear.sound(p, "entity.arrow.shoot", start, 0.8F, 0.8F);
        return true;
    }

    /** A line of redstone torches into the dark. Follow it. */
    public static boolean torchTrail(ServerPlayer p) {
        if (!Config.ALLOW_STRUCTURES.get()) return false;
        ServerLevel l = p.serverLevel();
        Vec3 end = Spots.findHidden(p, 16, 28, 0.6, 15, 40);
        if (end == null) return false;
        Vec3 start = p.position();
        Vec3 dir = end.subtract(start);
        int steps = (int) (dir.length() / 3);
        int placed = 0;
        for (int i = 1; i <= steps; i++) {
            Vec3 at = start.add(dir.scale(i / (double) steps));
            BlockPos s = Spots.standable(l, Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z), 4);
            if (s == null || !l.getBlockState(s).isAir() || s.distSqr(p.blockPosition()) < 16) continue;
            Builder.put(l, s, Blocks.REDSTONE_TORCH.defaultBlockState(), Block.UPDATE_ALL);
            placed++;
        }
        return placed >= 2;
    }

    /** Someone playing notes on a note block. Five notes. Always the same five. */
    public static boolean notes(ServerPlayer p) {
        Vec3 at = Spots.findHidden(p, 8, 16, 0.3, 15, 20);
        if (at == null) at = p.position().add(8, 0, 0);
        float[] tune = {0.6F, 0.75F, 0.6F, 0.53F, 0.5F};
        for (int i = 0; i < tune.length; i++) {
            float pitch = tune[i];
            Vec3 pos = at;
            Scheduler.later(p, i * 9, sp -> Fear.soundReverb(sp, "block.note_block.harp", pos, 1F, pitch));
        }
        return true;
    }

    /** A bell, far away, in the middle of the night. */
    public static boolean bell(ServerPlayer p) {
        Vec3 at = p.position().add(Vec3.directionFromRotation(0, Fear.RNG.nextFloat() * 360F).scale(14));
        for (int i = 0; i < 3; i++) Scheduler.later(p, i * 30, sp -> Fear.soundReverb(sp, "block.bell.use", at, 1F, 0.5F));
        return true;
    }

    /** Pistons moving inside the stone. */
    public static boolean pistons(ServerPlayer p) {
        Vec3 at = p.position().add(Fear.RNG.nextGaussian() * 6, -2 - Fear.RNG.nextInt(4), Fear.RNG.nextGaussian() * 6);
        for (int i = 0; i < 6; i++) {
            boolean out = i % 2 == 0;
            Scheduler.later(p, i * 12, sp -> Fear.sound(sp, out ? "block.piston.extend" : "block.piston.contract", at, 0.8F, 0.6F));
        }
        return true;
    }

    /** A furnace burning somewhere close. You have no furnace here. */
    public static boolean furnace(ServerPlayer p) {
        Vec3 at = Spots.findHidden(p, 5, 10, 0.3, 15, 20);
        if (at == null) return false;
        for (int i = 0; i < 6; i++) Scheduler.later(p, i * 15, sp -> Fear.sound(sp, "block.furnace.fire_crackle", at.add(0, 0.5, 0), 1F, 0.8F));
        return true;
    }

    /** The things in one of your chests are not where you left them. */
    public static boolean chestShuffle(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        List<BlockPos> chests = new ArrayList<>(HauntData.get(p.getServer()).chests(l.dimension().location().toString()));
        Collections.shuffle(chests, new java.util.Random(Fear.RNG.nextLong()));
        for (BlockPos b : chests) {
            if (!l.hasChunkAt(b) || b.distSqr(p.blockPosition()) > 96 * 96) continue;
            if (!(l.getBlockEntity(b) instanceof Container c)) continue;
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < c.getContainerSize(); i++) items.add(c.getItem(i));
            if (items.stream().allMatch(ItemStack::isEmpty)) continue;
            Collections.shuffle(items, new java.util.Random(Fear.RNG.nextLong()));
            for (int i = 0; i < items.size(); i++) c.setItem(i, items.get(i));
            c.setChanged();
            return true;
        }
        return false;
    }

    /** Lightning, again and again, each one closer to you. */
    public static boolean lightningWalk(ServerPlayer p) {
        if (Spots.isUnderground(p)) return false;
        ServerLevel l = p.serverLevel();
        float yaw = Fear.RNG.nextFloat() * 360F;
        for (int i = 0; i < 6; i++) {
            double d = 34 - i * 6;
            Scheduler.later(p, i * 22, sp -> {
                Vec3 at = sp.position().add(Vec3.directionFromRotation(0, yaw).scale(d));
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(l);
                if (bolt == null) return;
                bolt.moveTo(at.x, l.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(at.x), Mth.floor(at.z)), at.z);
                bolt.setVisualOnly(true);
                l.addFreshEntity(bolt);
            });
        }
        Scheduler.later(p, 6 * 22 + 10, Haunts::behind);
        return true;
    }

    // ================================================================== the stranger, more

    /** In a cave, in the dark: a plain Steve, standing there. Only for you. */
    public static boolean steveDark(ServerPlayer p) {
        Vec3 v = Spots.findWatchingSpot(p, 5, 11, 5, 40);
        if (v == null) return false;
        return HerobrineEntity.spawn(p, v, Mode.STEVE, 20 * 150) != null;
    }

    /** At night, behind you. Turn around. He is closer than he was. */
    public static boolean steveBehind(ServerPlayer p) {
        Vec3 v = Spots.behind(p, 5.5);
        if (v == null) return false;
        Fear.soundRel(p, "entity.villager.ambient", 0, 0, -5, 0.4F, 0.5F);
        return HerobrineEntity.spawn(p, v, Mode.STEVE, 20 * 180) != null;
    }

    /** A row of them, standing still, far into the fog. */
    public static boolean crowd(ServerPlayer p) {
        Vec3 v = Spots.findWatchingSpot(p, 9, 16, 15, 30);
        if (v == null) return false;
        Vec3 side = v.subtract(p.position()).cross(new Vec3(0, 1, 0)).normalize();
        int n = 3 + Fear.RNG.nextInt(3), placed = 0;
        for (int i = 0; i < n; i++) {
            Vec3 at = v.add(side.scale((i - n / 2.0) * 2.2));
            BlockPos s = Spots.standable(p.serverLevel(), Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z), 3);
            if (s == null) continue;
            if (HerobrineEntity.spawn(p, Vec3.atBottomCenterOf(s), i == n / 2 ? Mode.STALK : Mode.STEVE, 20 * 60) != null) placed++;
        }
        return placed > 0;
    }

    /** In your doorway. Looking in. */
    public static boolean doorway(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        BlockPos c = p.blockPosition();
        for (BlockPos b : BlockPos.betweenClosed(c.offset(-18, -3, -18), c.offset(18, 3, 18))) {
            BlockState s = l.getBlockState(b);
            if (!(s.getBlock() instanceof DoorBlock) || b.distSqr(c) < 16) continue;
            Vec3 to = Vec3.atCenterOf(b).subtract(p.position());
            Direction away = Direction.getNearest(to.x, 0, to.z);
            BlockPos out = b.relative(away);
            BlockPos feet = Spots.standable(l, out.getX(), out.getY(), out.getZ(), 2);
            if (feet == null) continue;
            return HerobrineEntity.spawn(p, Vec3.atBottomCenterOf(feet), Mode.WATCH, 20 * 30) != null;
        }
        return false;
    }

    /** On top of something tall, looking down at you. */
    public static boolean roof(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        BlockPos best = null;
        for (int i = 0; i < 60; i++) {
            int x = p.getBlockX() + Fear.RNG.nextInt(31) - 15, z = p.getBlockZ() + Fear.RNG.nextInt(31) - 15;
            if ((x - p.getBlockX()) * (x - p.getBlockX()) + (z - p.getBlockZ()) * (z - p.getBlockZ()) < 49) continue;
            BlockPos top = Spots.surface(l, x, z);
            if (top == null || top.getY() < p.getBlockY() + 4) continue;
            if (best == null || top.getY() > best.getY()) best = top;
        }
        if (best == null || !Spots.canSee(p, Vec3.atBottomCenterOf(best).add(0, 1.6, 0))) return false;
        return HerobrineEntity.spawn(p, Vec3.atBottomCenterOf(best), Mode.STALK, 20 * 40) != null;
    }

    /** Far behind you. Then closer. Then closer. Then right there. */
    public static boolean closer(ServerPlayer p) {
        double[] dists = {16, 11, 7};
        for (int i = 0; i < dists.length; i++) {
            double d = dists[i];
            Scheduler.later(p, i * 20 * 7, sp -> {
                Vec3 b = Spots.behind(sp, d);
                if (b != null) HerobrineEntity.spawn(sp, b, Mode.STALK, 20 * 6);
                Fear.soundRel(sp, "entity.villager.ambient", 0, 0, -d, 0.3F, 0.5F);
            });
        }
        Scheduler.later(p, 3 * 20 * 7, Haunts::behind);
        return true;
    }

    // ================================================================== attacks

    private static final EntityType<?>[][] GROUPS = {
            {EntityType.VILLAGER}, {EntityType.COW}, {EntityType.PIG}, {EntityType.CHICKEN}, {EntityType.SHEEP},
            {EntityType.VILLAGER, EntityType.ZOMBIE_VILLAGER}
    };
    private static final EntityType<?>[] DEAD = {
            EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.HUSK, EntityType.VILLAGER, EntityType.ZOMBIE_VILLAGER
    };

    private static int spawnGroup(ServerPlayer p, EntityType<?>[] pool, int count, int stare, double min, double max) {
        ServerLevel level = p.serverLevel();
        int spawned = 0;
        for (int i = 0; i < count * 4 && spawned < count; i++) {
            Vec3 v = Spots.findHidden(p, min, max, 0.4, 15, 6);
            if (v == null) continue;
            Entity e = pool[Fear.RNG.nextInt(pool.length)].create(level);
            if (!(e instanceof Mob mob)) continue;
            mob.moveTo(v.x, v.y, v.z, Spots.yawTo(v, p.position()), 0);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(v)), MobSpawnType.EVENT, null, null);
            level.addFreshEntity(mob);
            Possession.possess(mob, p, stare, 20 * 120, true);
            spawned++;
        }
        return spawned;
    }

    /** Two to five villagers, or cows, or pigs, or chickens. Out of nowhere. All for you. */
    public static boolean groupAttack(ServerPlayer p) {
        EntityType<?>[] pool = GROUPS[Fear.RNG.nextInt(GROUPS.length)];
        int n = spawnGroup(p, pool, 2 + Fear.RNG.nextInt(4), 15, 6, 12);
        if (n > 0) Fear.soundReverb(p, "entity.ghast.scream", p.position().add(0, 3, 0), 1F, 0.6F);
        return n > 0;
    }

    /** The dead walk towards you. Slowly. With his eyes. */
    public static boolean hunters(ServerPlayer p) {
        int n = spawnGroup(p, DEAD, 3 + Fear.RNG.nextInt(3), 20 * 6, 10, 18);
        return n > 0;
    }

    /** Animals all around, in a half circle, staring. Then they fall over. */
    public static boolean audience(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        EntityType<?>[] pool = {EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN, EntityType.VILLAGER};
        float yaw = p.getYRot();
        int n = 4 + Fear.RNG.nextInt(4), spawned = 0;
        for (int i = 0; i < n; i++) {
            float a = yaw + (i - (n - 1) / 2F) * (120F / n);
            Vec3 at = p.position().add(Vec3.directionFromRotation(0, a).scale(9 + Fear.RNG.nextInt(3)));
            BlockPos s = Spots.standable(level, Mth.floor(at.x), p.getBlockY(), Mth.floor(at.z), 4);
            if (s == null) continue;
            Entity e = pool[Fear.RNG.nextInt(pool.length)].create(level);
            if (!(e instanceof Mob mob)) continue;
            mob.moveTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, Spots.yawTo(Vec3.atBottomCenterOf(s), p.position()), 0);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(s), MobSpawnType.EVENT, null, null);
            level.addFreshEntity(mob);
            Possession.possess(mob, p, 20 * 25, 20 * 28, false);
            spawned++;
        }
        return spawned > 0;
    }

    // ================================================================== the music

    /**
     * Out of nowhere: horrible, frantic music, as if something were running after you. Nothing is there.
     * It stops. Silence. Sometimes, when you finally turn around, he is right there, screaming.
     */
    public static boolean chaseMusic(ServerPlayer p) {
        int len = 20 * (10 + Fear.RNG.nextInt(9));
        Net.send(p, new Net.Sound(Fear.rl("music_disc.pigstep"), 1F, 1.7F, 0, 0, 0, true, len, false));
        Net.send(p, new Net.Sound(Fear.rl("music_disc.11"), 1F, 2F, 0, 0, 0, true, len, false));
        Net.send(p, new Net.Sound(Fear.rl("music_disc.13"), 1F, 0.5F, 0, 0, 0, true, len, true));
        for (int t = 0; t < len; t += 9) {
            int tt = t;
            Scheduler.later(p, t, sp -> {
                Fear.soundRel(sp, "entity.warden.heartbeat", 0, 0, 0, 1F, 1.2F + tt / (float) len);
                if (tt % 18 == 0) Fear.soundRel(sp, Fear.stepSound(sp.level().getBlockState(sp.blockPosition().below())), 0, 0, -3, 1F, 1.3F);
            });
        }
        Fear.fx(p, Fx.HEARTBEAT, len, 1F);
        boolean him = Fear.RNG.nextBoolean();
        Scheduler.later(p, len + 2, sp -> {
            Fear.fx(sp, Fx.SILENCE, 20 * 8, 1F);
            if (!him) return;
            float yaw0 = sp.getYRot();
            Stalking.poll(sp, 2, 20 * 40, s2 -> {
                if (Math.abs(Mth.wrapDegrees(s2.getYRot() - yaw0)) < 110) return false;
                Fear.fx(s2, Fx.CLEAR, 1, 0F);
                Fear.loudJumpscare(s2);
                Vec3 front = s2.position().add(Vec3.directionFromRotation(0, s2.getYRot()).scale(1.6));
                HerobrineEntity.spawn(s2, front, Mode.SCARE, 22);
                return true;
            }, null);
        });
        return true;
    }

    // ================================================================== thrown out

    /** "Banned by Herobrine." Back to the title screen. The world is saved. */
    public static boolean kick(ServerPlayer p) {
        if (!Config.KICKS.get() || Config.CRASH_MODE.get() == Config.CrashMode.OFF) return false;
        var server = p.getServer();
        if (server == null) return false;
        int which = Fear.RNG.nextInt(Msg.KICK);
        Fear.fx(p, Fx.GLITCH, 30, 1F);
        Fear.ear(p, "entity.ghast.scream", 1F, 0.5F);
        boolean host = !server.isDedicatedServer() && server.isSingleplayerOwner(p.getGameProfile());
        Scheduler.later(p, 25, sp -> {
            if (host) Fear.fx(sp, Fx.KICK, 1, which);
            else sp.connection.disconnect(Component.translatable("herobrine.kick." + which));
        });
        return true;
    }

    // ================================================================== after the totem: the bosses

    public static boolean wither(ServerPlayer p) {
        if (!Config.TOTEM_BOSSES.get()) return false;
        Vec3 v = Spots.findHidden(p, 14, 24, 0.5, 15, 30);
        if (v == null) return false;
        var w = EntityType.WITHER.create(p.level());
        if (w == null) return false;
        w.moveTo(v.x, v.y + 2, v.z);
        w.makeInvulnerable();
        p.level().addFreshEntity(w);
        Fear.chat(p, Component.translatable("herobrine.totem.chat." + Fear.RNG.nextInt(Msg.TOTEM_CHAT), name(p)));
        return true;
    }

    public static boolean warden(ServerPlayer p) {
        if (!Config.TOTEM_BOSSES.get()) return false;
        Vec3 v = Spots.findHidden(p, 10, 18, 0.5, 15, 30);
        if (v == null) return false;
        var w = EntityType.WARDEN.create(p.level());
        if (w == null) return false;
        w.moveTo(v.x, v.y, v.z, Spots.yawTo(v, p.position()), 0);
        p.level().addFreshEntity(w);
        w.increaseAngerAt(p, 150, false);
        return true;
    }

    public static boolean dragon(ServerPlayer p) {
        if (!Config.TOTEM_BOSSES.get() || Spots.isUnderground(p)) return false;
        var d = EntityType.ENDER_DRAGON.create(p.level());
        if (d == null) return false;
        d.moveTo(p.getX(), p.getY() + 40, p.getZ());
        d.setFightOrigin(p.blockPosition());
        p.level().addFreshEntity(d);
        Fear.sound(p, "entity.ender_dragon.growl", p.position().add(0, 20, 0), 3F, 0.6F);
        return true;
    }

    private Terror() {}
}
