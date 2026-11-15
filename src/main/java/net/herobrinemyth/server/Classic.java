package net.herobrinemyth.server;

import com.mojang.authlib.GameProfile;
import net.herobrinemyth.Config;
import net.herobrinemyth.entity.HerobrineEntity;
import net.herobrinemyth.entity.HerobrineEntity.Mode;
import net.herobrinemyth.network.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the old ones did. Ported by behaviour (not by code) from:
 * iLoveYou (tkelly / Talmor / darkdiplomat, 2011-2013), HerobrineAI (Jakub1221),
 * BasicHerobrine (steaks4uce) and Herobrine Unleashed.
 */
public final class Classic {

    private static void smoke(ServerLevel l, Vec3 at) {
        l.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 20, 0.3, 0.6, 0.3, 0.01);
    }

    private static boolean griefing() {
        return Config.ALLOW_GRIEFING.get();
    }

    // ================================================================== appearances

    /** iLoveYou: 17 blocks away, holding a redstone torch. Walk towards him and he is gone. */
    public static boolean ghost(ServerPlayer p) {
        Vec3 v = Spots.findLurking(p, 15, 20);
        if (v == null) v = Spots.findWatchingSpot(p, 10, 25, 15, 20);
        return v != null && HerobrineEntity.spawn(p, v, Mode.GHOST, 20 * 120) != null;
    }

    /** iLoveYou: "Watch your back". He appears exactly where you stand. */
    public static boolean watchYourBack(ServerPlayer p) {
        HerobrineEntity h = HerobrineEntity.spawn(p, p.position(), Mode.ECHO, 60);
        if (h == null) return false;
        h.setYRot(p.getYRot());
        h.setYHeadRot(p.getYRot());
        Fear.chat(p, Component.translatable("herobrine.classic.watch"));
        return true;
    }

    /** HerobrineAI: walking around the world. */
    public static boolean wander(ServerPlayer p) {
        Vec3 v = Spots.findWatchingSpot(p, 30, 55, 15, 30);
        if (v == null) v = Spots.findHidden(p, 30, 55, 0.3, 15, 30);
        return v != null && HerobrineEntity.spawn(p, v, Mode.WANDER, 20 * 180) != null;
    }

    /** HerobrineAI's haunt, with the cave he digs at the start. */
    public static boolean haunt(ServerPlayer p) {
        Vec3 v = Spots.findWatchingSpot(p, 10, 20, 15, 30);
        if (v == null) return false;
        HerobrineEntity.spawn(p, v, Mode.HAUNT, 20 * (50 + Fear.RNG.nextInt(40)));
        if (Spots.isUnderground(p) && Config.ALLOW_STRUCTURES.get() && Fear.RNG.nextInt(3) == 0) cave(p);
        return true;
    }

    /** HerobrineAI's attack: your torches go out, your head spins, and he keeps appearing next to you. */
    public static void attack(ServerPlayer p) {
        if (Artifacts.protectedFromHim(p)) return;
        Haunts.breakTorches(p, 6, false);
        p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 12, 1));
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 4, 0));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 30, 1));
        Fear.chat(p, Component.translatable("herobrine.classic.attack" + Fear.RNG.nextInt(5)));
        Vec3 v = Spots.behind(p, 2);
        if (v == null) v = p.position();
        HerobrineEntity.spawn(p, v, Mode.STALKER, 20 * 40);
    }

    public static boolean attackEvent(ServerPlayer p) {
        if (Artifacts.protectedFromHim(p)) return false;
        attack(p);
        return true;
    }

    /** HerobrineAI's curse: sick, blind, slow, poisoned, weak, and three lightning strikes. */
    public static boolean curse(ServerPlayer p) {
        if (Artifacts.protectedFromHim(p)) return false;
        p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 30, 1));
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 8, 1));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 40, 1));
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 40, 1));
        for (int i = 0; i < 3; i++) {
            Scheduler.later(p, 1 + i * 150, sp -> {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sp.level());
                if (bolt != null) {
                    bolt.moveTo(sp.getX(), sp.getY(), sp.getZ());
                    sp.level().addFreshEntity(bolt);
                }
                sp.addEffect(new MobEffectInstance(MobEffects.POISON, 50, 1));
            });
        }
        Fear.chat(p, Component.translatable("herobrine.classic.curse"));
        return true;
    }

    // ================================================================== iLoveYou: diamonds

    /** "I have something for you." He digs down to diamonds, you follow, and the way back closes. */
    public static boolean diamonds(ServerPlayer p) {
        if (!griefing()) return false;
        ServerLevel l = p.serverLevel();
        BlockPos found = null;
        BlockPos o = p.blockPosition();
        search:
        for (int r = 6; r <= 22; r += 2) {
            for (int i = 0; i < 60; i++) {
                double a = Fear.RNG.nextDouble() * Math.PI * 2;
                int x = o.getX() + (int) (Math.cos(a) * r), z = o.getZ() + (int) (Math.sin(a) * r);
                for (int y = o.getY() - 4; y > Math.max(l.getMinBuildHeight() + 1, o.getY() - 70); y--) {
                    BlockPos b = new BlockPos(x, y, z);
                    if (!l.hasChunkAt(b)) break;
                    BlockState s = l.getBlockState(b);
                    if (s.is(Blocks.DIAMOND_ORE) || s.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
                        found = b;
                        break search;
                    }
                }
            }
        }
        if (found == null) return false;
        int x = found.getX() + 2, z = found.getZ();
        BlockPos start = Spots.isUnderground(p) ? Spots.standable(l, x, p.getBlockY(), z, 8) : Spots.surface(l, x, z);
        if (start == null || start.getY() <= found.getY()) return false;
        HerobrineEntity h = HerobrineEntity.spawn(p, Vec3.atBottomCenterOf(start), Mode.GUIDE, 20 * 240);
        if (h == null) return false;
        h.setDiamond(found);
        Fear.chat(p, Component.translatable("herobrine.diamond.hi" + Fear.RNG.nextInt(4)));
        return true;
    }

    // ================================================================== iLoveYou: thief

    /** He stands on one of your chests while you are away, and takes things. */
    public static boolean thief(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        List<BlockPos> chests = new ArrayList<>(HauntData.get(p.getServer()).chests(l.dimension().location().toString()));
        Collections.shuffle(chests);
        for (BlockPos c : chests) {
            double d = Math.sqrt(c.distSqr(p.blockPosition()));
            if (d < 20 || d > 120 || !l.hasChunkAt(c)) continue;
            BlockEntity be = l.getBlockEntity(c);
            if (!(be instanceof ChestBlockEntity chest)) continue;
            BlockPos top = c.above();
            if (!Spots.passable(l, top) || !Spots.passable(l, top.above())) continue;
            rob(l, c, chest);
            HerobrineEntity.spawn(p, new Vec3(c.getX() + 0.5, c.getY() + 0.875, c.getZ() + 0.5), Mode.THIEF, 20 * 300);
            return true;
        }
        return false;
    }

    /** iLoveYou's rule: one in five things ends up on the floor, three in ten are just gone. */
    private static void rob(ServerLevel l, BlockPos pos, Container c) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) continue;
            double r = Fear.RNG.nextDouble();
            if (r < 0.2) {
                Block.popResource(l, pos.above(), s.copy());
                c.setItem(i, ItemStack.EMPTY);
            } else if (r < 0.5) {
                c.setItem(i, ItemStack.EMPTY);
            }
        }
        c.setChanged();
    }

    // ================================================================== HerobrineAI: bury

    /** The ground opens. You fall three blocks. It closes over you. Your name is on the sign above. */
    public static boolean bury(ServerPlayer p) {
        if (!griefing() || Artifacts.protectedFromHim(p)) return false;
        ServerLevel l = p.serverLevel();
        BlockPos b = p.blockPosition();
        for (int dy = 1; dy <= 4; dy++) {
            for (BlockPos col : new BlockPos[]{b, b.north()}) {
                BlockPos at = col.below(dy);
                BlockState s = l.getBlockState(at);
                if (!s.isSolidRender(l, at) || !Builder.replaceable(l, at)) return false;
            }
        }
        for (int dy = 1; dy <= 3; dy++) {
            l.destroyBlock(b.below(dy), false);
            l.destroyBlock(b.north().below(dy), false);
        }
        p.teleportTo(l, b.getX() + 0.5, b.getY() - 3, b.getZ() + 0.5, p.getYRot(), p.getXRot());
        l.setBlock(b.below(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
        l.setBlock(b.north().below(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos sign = b.north(2);
        if (Spots.passable(l, sign)) {
            Builder.placeSign(l, sign, Vec3.atCenterOf(b), Component.empty(), Component.literal(p.getGameProfile().getName()),
                    Component.empty(), Component.empty());
        }
        Fear.chat(p, Component.translatable("herobrine.classic.grave"));
        return true;
    }

    // ================================================================== small things

    /** HerobrineAI's random sounds: six of them, one every second and a half. */
    public static boolean classicSounds(ServerPlayer p) {
        String[] s = {"entity.ghast.scream", "entity.wither.death", "entity.wither.hurt", "entity.bat.hurt",
                "entity.player.breath", "entity.player.hurt", "block.iron_door.open", "block.iron_door.close"};
        for (int i = 1; i <= 6; i++) {
            Scheduler.later(p, i * 30, sp -> Fear.sound(sp, Fear.pick(s), sp.position(), 0.75F, 0.75F));
        }
        return true;
    }

    public static boolean burn(ServerPlayer p) {
        if (Artifacts.protectedFromHim(p)) return false;
        p.setSecondsOnFire(6);
        return true;
    }

    public static boolean explosion(ServerPlayer p) {
        Vec3 v = p.position().add(Fear.RNG.nextInt(16) - 8, 0, Fear.RNG.nextInt(16) - 8);
        p.level().explode(null, v.x, v.y, v.z, 1.0F, griefing() ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
        return true;
    }

    /** BasicHerobrine: a redstone torch appears near you, in a puff of smoke. */
    public static boolean redstoneTorch(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 v = Spots.findHidden(p, 4, 7, 0.4, 15, 20);
        if (v == null) return false;
        BlockPos at = BlockPos.containing(v);
        if (!l.getBlockState(at).isAir()) return false;
        l.setBlock(at, Blocks.REDSTONE_TORCH.defaultBlockState(), Block.UPDATE_ALL);
        smoke(l, Vec3.atBottomCenterOf(at));
        return true;
    }

    /** HerobrineAI: heads everywhere around you, all looking at you. Five seconds later, nothing. */
    public static boolean heads(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        List<BlockPos> placed = new ArrayList<>();
        BlockPos o = p.blockPosition();
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                if (Math.abs(x) < 2 && Math.abs(z) < 2) continue;
                if (Fear.RNG.nextInt(7) != 0) continue;
                BlockPos at = Spots.standable(l, o.getX() + x, o.getY(), o.getZ() + z, 3);
                if (at == null || !l.getBlockState(at).isAir()) continue;
                float yaw = Spots.yawTo(Vec3.atCenterOf(at), p.position());
                l.setBlock(at, Blocks.PLAYER_HEAD.defaultBlockState().setValue(SkullBlock.ROTATION, RotationSegment.convertToSegment(yaw)), Block.UPDATE_ALL);
                placed.add(at);
            }
        }
        if (placed.isEmpty()) return false;
        Scheduler.later(100, () -> {
            for (BlockPos b : placed) if (l.getBlockState(b).is(Blocks.PLAYER_HEAD)) l.removeBlock(b, false);
        });
        return true;
    }

    /** Herobrine Unleashed: the flowers around you turn into mushrooms. */
    public static boolean flowers(ServerPlayer p) {
        if (!griefing()) return false;
        ServerLevel l = p.serverLevel();
        int n = 0;
        for (BlockPos b : BlockPos.betweenClosed(p.blockPosition().offset(-12, -3, -12), p.blockPosition().offset(12, 3, 12))) {
            BlockState s = l.getBlockState(b);
            if (s.is(BlockTags.SMALL_FLOWERS)) {
                l.setBlock(b, (Fear.RNG.nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState(), Block.UPDATE_CLIENTS);
                n++;
            }
        }
        return n > 0;
    }

    /** Herobrine Unleashed: a ring of fire around you. */
    public static boolean fireRing(ServerPlayer p) {
        if (!griefing()) return false;
        ServerLevel l = p.serverLevel();
        int n = 0;
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI * 2 / 32;
            BlockPos at = Spots.standable(l, Mth.floor(p.getX() + Math.cos(a) * 4), p.getBlockY(), Mth.floor(p.getZ() + Math.sin(a) * 4), 2);
            if (at != null && l.getBlockState(at).isAir()) {
                l.setBlock(at, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
                n++;
            }
        }
        if (n > 0) Fear.sound(p, "item.flintandsteel.use", p.position(), 1F, 0.5F);
        return n > 0;
    }

    /** Herobrine Unleashed: thrown into the air. */
    public static boolean launch(ServerPlayer p) {
        p.setDeltaMovement(p.getDeltaMovement().add(0, 1.6, 0));
        p.hurtMarked = true;
        Fear.sound(p, "entity.ender_dragon.flap", p.position(), 1F, 0.5F);
        return true;
    }

    /** Herobrine Unleashed: everything in your inventory is somewhere else now. */
    public static boolean shuffle(ServerPlayer p) {
        Inventory inv = p.getInventory();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 36; i++) items.add(inv.items.get(i));
        Collections.shuffle(items);
        for (int i = 0; i < 36; i++) inv.items.set(i, items.get(i));
        inv.setChanged();
        Fear.whisper(p, Component.translatable("herobrine.classic.moved"), 50);
        return true;
    }

    /** Herobrine Unleashed: something is missing. */
    public static boolean steal(ServerPlayer p) {
        Inventory inv = p.getInventory();
        List<Integer> full = new ArrayList<>();
        for (int i = 0; i < 36; i++) if (!inv.items.get(i).isEmpty()) full.add(i);
        if (full.isEmpty()) return false;
        inv.items.set(full.get(Fear.RNG.nextInt(full.size())), ItemStack.EMPTY);
        inv.setChanged();
        Fear.chat(p, Component.translatable("herobrine.classic.thanks"));
        return true;
    }

    /** BasicHerobrine: lit TNT, right there. */
    public static boolean tnt(ServerPlayer p) {
        if (!griefing()) return false;
        Vec3 v = Spots.findHidden(p, 4, 6, 0.3, 15, 20);
        if (v == null) return false;
        PrimedTnt t = new PrimedTnt(p.level(), v.x, v.y, v.z, null);
        t.setFuse(50);
        p.level().addFreshEntity(t);
        return true;
    }

    public static boolean bats(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        int n = 3 + Fear.RNG.nextInt(4);
        for (int i = 0; i < n; i++) {
            Bat bat = EntityType.BAT.create(l);
            if (bat == null) continue;
            bat.moveTo(p.getX() + Fear.RNG.nextGaussian(), p.getEyeY(), p.getZ() + Fear.RNG.nextGaussian());
            l.addFreshEntity(bat);
        }
        smoke(l, p.position().add(p.getViewVector(1F).scale(2)));
        return true;
    }

    /** HerobrineAI: one angry wolf. */
    public static boolean angryWolf(ServerPlayer p) {
        Vec3 v = Spots.findHidden(p, 6, 12, 0.3, 15, 20);
        if (v == null) return false;
        Wolf w = EntityType.WOLF.create(p.level());
        if (w == null) return false;
        w.moveTo(v.x, v.y, v.z);
        w.setPersistentAngerTarget(p.getUUID());
        w.startPersistentAngerTimer();
        w.setTarget(p);
        p.level().addFreshEntity(w);
        return true;
    }

    /** Herobrine Unleashed: spun around. */
    public static boolean spin(ServerPlayer p) {
        Fear.fx(p, Fx.SPIN, 40, 1F);
        Fear.ear(p, "entity.witch.celebrate", 0.7F, 0.5F);
        return true;
    }

    /** Herobrine Unleashed: your head on a pike. */
    public static boolean pike(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 v = Spots.findHidden(p, 6, 14, 0.2, 15, 30);
        if (v == null) return false;
        BlockPos at = BlockPos.containing(v);
        if (!Spots.passable(l, at.above(2))) return false;
        l.setBlock(at, Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
        l.setBlock(at.above(), Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
        placeHead(l, at.above(2), p.getGameProfile(), Spots.yawTo(Vec3.atCenterOf(at), p.position()));
        return true;
    }

    private static void placeHead(ServerLevel l, BlockPos at, GameProfile owner, float yaw) {
        l.setBlock(at, Blocks.PLAYER_HEAD.defaultBlockState().setValue(SkullBlock.ROTATION, RotationSegment.convertToSegment(yaw)), Block.UPDATE_ALL);
        if (l.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.SkullBlockEntity skull) {
            skull.setOwner(owner);
            skull.setChanged();
        }
    }

    public static ItemStack headOf(ServerPlayer p) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.getOrCreateTag().put("SkullOwner", NbtUtils.writeGameProfile(new CompoundTag(), p.getGameProfile()));
        return head;
    }

    /** BasicHerobrine: a chest appears out of smoke. */
    public static boolean giftChest(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 v = Spots.findHidden(p, 4, 7, 0.3, 15, 20);
        if (v == null) return false;
        BlockPos at = BlockPos.containing(v);
        l.setBlock(at, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.getNearest(p.getX() - at.getX(), 0, p.getZ() - at.getZ())), Block.UPDATE_ALL);
        if (l.getBlockEntity(at) instanceof ChestBlockEntity c) {
            c.setItem(11, headOf(p));
            c.setItem(13, Msg.book(p));
            c.setItem(15, new ItemStack(Items.FLINT_AND_STEEL));
        }
        smoke(l, Vec3.atBottomCenterOf(at));
        return true;
    }

    /** HerobrineAI's "Demon": a skeleton that is not just a skeleton. */
    public static boolean demon(ServerPlayer p) {
        Vec3 v = Spots.findHidden(p, 4, 8, 0.3, 15, 20);
        if (v == null) return false;
        ServerLevel l = p.serverLevel();
        Skeleton s = EntityType.SKELETON.create(l);
        if (s == null) return false;
        s.moveTo(v.x, v.y, v.z);
        s.finalizeSpawn(l, l.getCurrentDifficultyAt(BlockPos.containing(v)), MobSpawnType.EVENT, null, null);
        s.setCustomName(Component.translatable("entity.herobrinemyth.demon"));
        s.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
        s.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 120, 0));
        l.addFreshEntity(s);
        Possession.possess(s, p, 30, 20 * 120, true);
        return true;
    }

    /** HerobrineAI's temple guardians. */
    public static void guardians(ServerLevel l, BlockPos at, int n) {
        for (int i = 0; i < n; i++) {
            Zombie z = EntityType.ZOMBIE.create(l);
            if (z == null) continue;
            z.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            z.setCustomName(Component.translatable("entity.herobrinemyth.guardian"));
            z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
            z.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
            z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
            z.setPersistenceRequired();
            l.addFreshEntity(z);
        }
    }

    /** HerobrineAI's cave: two-high tunnels going off from where you stand, some with redstone torches. */
    public static boolean cave(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        if (p.getY() > 60 && !Spots.isUnderground(p)) return false;
        BlockPos base = p.blockPosition();
        int x = base.getX(), y = base.getY(), z = base.getZ();
        int branches = 4 + Fear.RNG.nextInt(3), len = 4 + Fear.RNG.nextInt(10);
        for (int b = 0; b < branches; b++) {
            boolean byX = Fear.RNG.nextBoolean();
            int dir = Fear.RNG.nextBoolean() ? 1 : -1;
            for (int i = 0; i <= len; i++) {
                if (byX) x += dir;
                else z += dir;
                for (int h = 0; h < 2; h++) {
                    BlockPos at = new BlockPos(x, y + h, z);
                    if (at.closerThan(base, 2)) continue;
                    BlockState s = l.getBlockState(at);
                    if ((s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.DIRT) || s.is(Blocks.GRAVEL)) && Builder.replaceable(l, at))
                        l.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                if (Fear.RNG.nextInt(10) < 2) {
                    BlockPos t = new BlockPos(x, y, z);
                    if (l.getBlockState(t).isAir() && Spots.solidFloor(l, t.below()))
                        l.setBlock(t, Blocks.REDSTONE_TORCH.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        return true;
    }

    // ================================================================== reactive

    /** HerobrineAI: a chest you close may now hold a book... or your own head. */
    public static void onChestClosed(ServerPlayer p, Container chest) {
        HauntData data = HauntData.get(p.getServer());
        if (data.level(p.getUUID()) < 2 || !Config.ENABLED.get()) return;
        int empty = -1;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).isEmpty()) {
                empty = i;
                break;
            }
        }
        if (empty < 0) return;
        int r = Fear.RNG.nextInt(100);
        if (r < 3) chest.setItem(empty, headOf(p));
        else if (r < 8) chest.setItem(empty, Msg.classicBook(p));
        chest.setChanged();
    }

    /** HerobrineAI: playing record 11 calls him. */
    public static void discPlayed(ServerPlayer p) {
        if (!Config.ENABLED.get()) return;
        Scheduler.later(p, 100 + Fear.RNG.nextInt(100), sp -> {
            Fear.chat(sp, Component.translatable("herobrine.classic.disc"));
            haunt(sp);
        });
    }

    /** iLoveYou: after the altar, your commands answer in garbage. */
    public static String garbage(int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append((char) (0xA1 + Fear.RNG.nextInt(0x5E)));
        return sb.toString();
    }

    @Nullable
    public static BlockPos surfaceAt(ServerLevel l, int x, int z) {
        if (!l.hasChunkAt(new BlockPos(x, 0, z))) return null;
        return new BlockPos(x, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private Classic() {}
}
