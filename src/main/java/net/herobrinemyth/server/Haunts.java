package net.herobrinemyth.server;

import net.herobrinemyth.Config;
import net.herobrinemyth.entity.HerobrineEntity;
import net.herobrinemyth.entity.HerobrineEntity.Mode;
import net.herobrinemyth.entity.HerobrineEyesEntity;
import net.herobrinemyth.network.Fx;
import net.herobrinemyth.network.Net;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Everything he can do to you. */
public final class Haunts {

    /**
     * minDay: the first day of the haunting it can happen (stretched by the dayScale config).
     * cooldown: ticks before the same thing may happen again to the same player.
     */
    public record Haunt(String id, boolean major, int minDay, int weight, boolean totemOnly, int cooldown, Predicate<ServerPlayer> run) {}

    public static final Map<String, Haunt> ALL = new LinkedHashMap<>();
    private static final int DAY = 24000;

    private static void minor(String id, int minDay, int weight, Predicate<ServerPlayer> run) {
        ALL.put(id, new Haunt(id, false, minDay, weight, false, 20 * 60 * 4, run));
    }

    private static void major(String id, int minDay, int weight, Predicate<ServerPlayer> run) {
        ALL.put(id, new Haunt(id, true, minDay, weight, false, DAY / 2, run));
    }

    private static void totem(String id, int weight, Predicate<ServerPlayer> run) {
        ALL.put(id, new Haunt(id, true, 100, weight, true, 20 * 60 * 2, run));
    }

    static {
        // ---------------------------------------------------- small things. most of them make no sound at all.
        minor("cave_sound", 1, 10, p -> {
            Fear.caveSound(p);
            return true;
        });
        minor("footsteps", 1, 7, Haunts::footsteps);
        minor("mining", 2, 6, Haunts::mining);
        minor("glimpse", 2, 5, Stalking::glimpse);
        minor("faint", 2, 6, Haunts::faint);
        minor("tree", 2, 1, Stalking::tree);
        minor("redstone_torch", 2, 3, Classic::redstoneTorch);
        minor("building", 3, 4, Haunts::building);
        minor("sign", 3, 3, p -> Config.ALLOW_STRUCTURES.get() && Builder.signs(p, 1));
        minor("knock", 4, 3, Haunts::knock);
        minor("nameless", 4, 1, p -> {
            Fear.nameless(p, Msg.whisper());
            return Config.FAKE_CHAT.get();
        });
        minor("whisper", 5, 3, Haunts::whisper);
        minor("house", 5, 3, Haunts::house);
        minor("lightning", 5, 1, Haunts::farLightning);
        minor("chat", 6, 2, p -> {
            Fear.chat(p, Msg.chat(p));
            return Config.FAKE_CHAT.get();
        });
        minor("torch_out", 6, 2, p -> breakTorches(p, 1, false));
        minor("animals", 6, 3, Possession::animals);
        minor("joined", 7, 1, Haunts::fakeJoin);
        minor("flowers", 7, 1, Classic::flowers);
        minor("fog", 8, 2, p -> {
            Fear.fx(p, Fx.FOG, 20 * (25 + Fear.RNG.nextInt(30)), Fear.range(0.6F, 1F));
            return true;
        });
        minor("bats", 8, 1, Classic::bats);
        minor("book", 8, 1, p -> p.getInventory().add(Msg.book(p)));
        minor("heads", 9, 1, Classic::heads);
        minor("hiss", 9, 1, p -> {
            Fear.soundRel(p, "entity.creeper.primed", 0, 0, -1.2, 1F, 0.5F);
            return true;
        });
        minor("subliminal", 10, 1, p -> {
            Fear.subliminal(p);
            return true;
        });
        minor("gift", 10, 1, Classic::giftChest);
        minor("classic_sounds", 10, 1, Classic::classicSounds);
        minor("watch", 11, 1, Classic::watchYourBack);
        minor("blink", 12, 1, p -> {
            Fear.fx(p, Fx.BLINK, 4, 1F);
            return true;
        });
        minor("pike", 12, 1, Classic::pike);
        minor("shuffle", 12, 1, Classic::shuffle);
        minor("wolf", 12, 1, Classic::angryWolf);
        minor("steal", 14, 1, Classic::steal);
        minor("spin", 14, 1, Classic::spin);
        minor("burn", 16, 1, Classic::burn);
        minor("explosion", 18, 1, Classic::explosion);
        minor("fire_ring", 20, 1, Classic::fireRing);
        minor("launch", 20, 1, Classic::launch);
        minor("tnt", 22, 1, Classic::tnt);

        // ---------------------------------------------------- 1.4: he is real, and he is here
        minor("stare", 2, 5, Staring::animals);
        minor("pickup", 2, 3, Terror::pickup);
        minor("pss", 3, 4, Staring::pss);
        minor("breath", 3, 4, Terror::breath);
        minor("name_whisper", 3, 3, Terror::nameWhisper);
        minor("far_hurt", 3, 3, Terror::farHurt);
        minor("notes", 3, 2, Terror::notes);
        minor("human_pillar", 3, 3, Human::pillar);
        minor("human_tree", 3, 3, Human::halfTree);
        minor("back_turned", 4, 4, Staring::backTurned);
        minor("eating", 4, 2, Terror::eating);
        minor("thrown", 4, 3, Terror::thrown);
        minor("bell", 4, 2, Terror::bell);
        minor("furnace", 4, 2, Terror::furnace);
        minor("human_holes", 4, 2, Human::holes);
        minor("human_camp", 4, 3, Human::camp);
        minor("sting", 5, 3, Terror::sting);
        minor("torch_trail", 5, 2, Terror::torchTrail);
        minor("human_stairs", 5, 2, Human::stairsDown);
        minor("human_mined", 5, 2, Human::mined);
        minor("fake_death", 6, 2, Terror::fakeDeath);
        minor("flicker", 6, 3, Terror::flicker);
        minor("pistons", 6, 2, Terror::pistons);
        minor("human_hut", 6, 2, Human::hut);
        minor("self_chat", 7, 2, Terror::selfChat);
        minor("advancement", 8, 1, Terror::fakeAdvancement);
        minor("sign_edit", 8, 2, Terror::signEdit);
        minor("kill_animals", 8, 2, Human::killAnimals);
        minor("darkness", 9, 2, Terror::darkness);
        minor("arrow", 9, 2, Terror::arrow);
        minor("rename", 10, 1, Terror::renameItem);
        minor("chest_shuffle", 10, 1, Terror::chestShuffle);
        minor("burn_grass", 10, 2, Human::burnGrass);
        minor("glass", 12, 1, Terror::glassBreak);

        // ---------------------------------------------------- him
        major("stalk", 2, 8, Haunts::stalk);
        major("peek", 2, 6, Haunts::peek);
        major("steve", 3, 4, Stalking::steve);
        major("ghost", 3, 5, Classic::ghost);
        major("build", 3, 6, p -> Config.ALLOW_STRUCTURES.get() && Builder.random(p, level(p)));
        major("dark", 4, 6, Haunts::dark);
        major("eyes", 5, 4, Haunts::eyes);
        major("window", 5, 4, Stalking::window);
        major("crack", 6, 4, Stalking::crack);
        major("left", 6, 4, Stalking::left);
        major("disc", 6, 3, Stalking::disc);
        major("behind", 7, 5, Haunts::behind);
        major("wander", 8, 3, Classic::wander);
        major("torches", 8, 4, p -> breakTorches(p, 14, true));
        major("thief", 9, 2, Classic::thief);
        major("approach", 10, 3, Haunts::approach);
        major("possess_mobs", 10, 3, p -> Possession.hostiles(p, 3 + Fear.RNG.nextInt(4)));
        major("jumpscare", 10, 1, Haunts::jumpscare);
        major("haunt", 11, 3, Classic::haunt);
        major("separate", 11, 2, Stalking::separate);
        major("herd", 12, 3, Possession::herd);
        major("blood_rain", 12, 2, Stalking::bloodRain);
        major("diamonds", 12, 2, Classic::diamonds);
        major("cave", 13, 2, p -> Caves.teleport(p, !HauntData.get(p.getServer()).totem));
        major("possess", 13, 2, Haunts::possessPlayer);
        major("chase", 14, 3, Haunts::chase);
        major("demon", 15, 2, Classic::demon);
        major("clones", 16, 2, Haunts::clones);
        major("attack", 17, 3, Classic::attackEvent);
        major("graveyard", 18, 2, Graveyard::send);
        major("curse", 19, 2, Classic::curse);
        major("bury", 21, 2, Classic::bury);
        major("guards", 22, 3, Stalking::guards);
        major("fight", 24, 1, Haunts::fight);
        major("swarm", 25, 2, p -> Possession.swarm(p, 8 + Fear.RNG.nextInt(8)));

        major("steve_dark", 3, 4, Terror::steveDark);
        major("steve_behind", 4, 3, Terror::steveBehind);
        major("roof", 4, 3, Terror::roof);
        major("doorway", 5, 3, Terror::doorway);
        major("closer", 6, 3, Terror::closer);
        major("group_attack", 6, 3, Terror::groupAttack);
        major("chase_music", 6, 3, Terror::chaseMusic);
        major("audience", 7, 2, Terror::audience);
        major("crowd", 8, 2, Terror::crowd);
        major("hunters", 9, 3, Terror::hunters);
        major("lightning_walk", 9, 2, Terror::lightningWalk);
        ALL.put("kick", new Haunt("kick", true, 8, 1, false, DAY * 2, Terror::kick));
        ALL.put("burn_forest", new Haunt("burn_forest", true, 10, 1, false, DAY, Human::burnForest));
        ALL.put("burn_village", new Haunt("burn_village", true, 12, 1, false, DAY * 2, Human::burnVillage));

        // ---------------------------------------------------- after the totem
        totem("chunk", 3, Corruption::eatChunk);
        totem("corrupt", 6, Corruption::corrupt);
        totem("noise", 3, Corruption::noise);
        totem("storm", 4, Corruption::storm);
        totem("crash", 0, Corruption::crash);
        totem("take_world", 0, Corruption::takeWorld);
        ALL.put("wither", new Haunt("wither", true, 100, 1, true, 20 * 60 * 24, Terror::wither));
        ALL.put("warden", new Haunt("warden", true, 100, 1, true, 20 * 60 * 16, Terror::warden));
        ALL.put("dragon", new Haunt("dragon", true, 100, 1, true, 20 * 60 * 32, Terror::dragon));
    }

    public static int level(ServerPlayer p) {
        return HauntData.get(p.getServer()).level(p.getUUID());
    }

    /** The day an event unlocks, after the dayScale config. */
    public static int unlockDay(Haunt h) {
        return (int) Math.ceil(h.minDay * Config.DAY_SCALE.get());
    }

    /**
     * Run a random event of the given kind. Never the same one as the last few,
     * never one that is still cooling down, weighted by where you are.
     */
    public static boolean random(ServerPlayer p, boolean major) {
        HauntData data = HauntData.get(p.getServer());
        HauntData.PlayerState st = data.player(p.getUUID());
        int day = data.level(p.getUUID());
        boolean totem = data.totem;
        boolean under = Spots.isUnderground(p);
        boolean night = p.level().isNight();
        boolean dark = Spots.isDark(p.serverLevel(), p.blockPosition(), 7);
        long now = Scheduler.now();
        List<Haunt> pool = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (Haunt h : ALL.values()) {
            if (h.major != major || h.weight <= 0) continue;
            if (!totem && unlockDay(h) > day) continue;
            if (h.totemOnly && !totem) continue;
            if (st.recent.contains(h.id)) continue;
            Long last = st.lastRun.get(h.id);
            if (last != null && now - last < (totem ? h.cooldown / 4 : h.cooldown)) continue;
            int w = weight(h, p, day, totem, under, night, dark);
            if (w <= 0) continue;
            pool.add(h);
            weights.add(w);
            total += w;
        }
        for (int attempt = 0; attempt < 4 && total > 0; attempt++) {
            int r = Fear.RNG.nextInt(total);
            for (int i = 0; i < pool.size(); i++) {
                r -= weights.get(i);
                if (r < 0) {
                    Haunt h = pool.get(i);
                    if (run(h, p)) {
                        Fear.unmute(p, p.position(), 56, 20 * 25);
                        st.lastRun.put(h.id, now);
                        st.recent.addLast(h.id);
                        while (st.recent.size() > 5) st.recent.removeFirst();
                        return true;
                    }
                    break;
                }
            }
        }
        return false;
    }

    /** By day he shows himself and leaves things. By night: sounds, whispers, the mind, the chase. */
    private static int weight(Haunt h, ServerPlayer p, int day, boolean totem, boolean under, boolean night, boolean dark) {
        int w = h.weight;
        boolean daylight = !night && !under;
        switch (h.id) {
            case "whisper", "mining", "dark", "eyes", "crack", "knock" -> w *= under ? 3 : (night || dark ? 2 : 1);
            case "peek", "lightning", "build", "wander", "pike", "heads", "window", "blood_rain", "roof", "crowd", "lightning_walk",
                 "burn_forest", "burn_village", "burn_grass", "human_pillar", "human_tree", "human_camp", "human_hut", "human_holes",
                 "torch_trail", "thrown", "doorway", "audience", "kill_animals" -> w *= under ? 0 : (daylight ? 2 : 1);
            case "diamonds", "human_mined", "human_stairs" -> w *= under ? 2 : 1;
            case "pistons" -> w *= under ? 3 : 0;
            case "steve_dark" -> w *= under || dark ? 3 : 0;
            case "steve_behind" -> w *= night || dark ? 2 : 0;
            case "bell" -> w *= night && !under ? 2 : 0;
            case "stalk", "ghost" -> w *= night || dark ? 2 : 1;
            case "separate" -> w *= p.getServer().getPlayerCount() > 1 ? 3 : 0;
            case "tree" -> w = under ? 0 : Math.min(9, 1 + day / 3); // rare at first, common later
            case "chat", "nameless", "self_chat" -> w *= night ? 3 : 1;
            case "glimpse", "steve" -> w *= night || under ? 2 : (daylight ? 2 : 1);
            case "footsteps", "breath", "name_whisper", "notes", "darkness", "flicker", "back_turned", "chase_music", "closer",
                 "hunters", "far_hurt" -> w *= night || dark ? 2 : 1;
            case "fight", "chase", "clones", "possess", "guards", "swarm" -> w *= totem ? 3 : (night ? 2 : 1);
            default -> {
            }
        }
        return w;
    }

    public static boolean trigger(String id, ServerPlayer p) {
        Haunt h = ALL.get(id);
        if (h != null) Fear.unmute(p, p.position(), 56, 20 * 25);
        return h != null && run(h, p);
    }

    private static boolean run(Haunt h, ServerPlayer p) {
        try {
            return h.run.test(p);
        } catch (Exception e) {
            net.herobrinemyth.HerobrineMyth.LOGGER.error("Haunt {} failed", h.id, e);
            return false;
        }
    }

    // ================================================================== minor

    /**
     * A sound so quiet you are not sure you heard it: a "pss...", a "hm...", glass breaking far away,
     * sand falling, an enderman, a ghast, a horse dying.
     */
    public static boolean faint(ServerPlayer p) {
        int r = Fear.RNG.nextInt(8);
        double a = Fear.RNG.nextDouble() * Math.PI * 2, d = 4 + Fear.RNG.nextInt(10);
        Vec3 at = p.getEyePosition().add(Math.cos(a) * d, Fear.RNG.nextGaussian(), Math.sin(a) * d);
        switch (r) {
            case 0 -> Net.send(p, new Net.Sound(Fear.rl("entity.creeper.primed"), 0.18F, 1.4F, 0.6, 0.1, -0.3, true, 6, false)); // "pss..."
            case 1 -> Net.send(p, new Net.Sound(Fear.rl("entity.villager.ambient"), 0.15F, 0.75F, -0.6, 0.1, -0.3, true, 0, true)); // "hm..."
            case 2 -> Fear.soundReverb(p, "block.glass.break", at, 0.2F, 0.8F);
            case 3 -> Fear.soundReverb(p, "block.sand.break", at, 0.25F, 0.8F);
            case 4 -> Fear.soundReverb(p, "entity.enderman.scream", at.add(at.subtract(p.position()).scale(2)), 0.25F, 0.6F);
            case 5 -> Fear.soundReverb(p, "entity.ghast.scream", at.add(at.subtract(p.position()).scale(3)), 0.3F, 0.6F);
            case 6 -> Fear.soundReverb(p, "entity.horse.death", at.add(at.subtract(p.position()).scale(3)), 0.3F, 0.55F);
            default -> Fear.soundReverb(p, "block.wooden_door.close", at, 0.2F, 0.7F);
        }
        return true;
    }

    /** Someone walking up behind you. Turn around and the steps stop. */
    public static boolean footsteps(ServerPlayer p) {
        Vec3 look = p.getViewVector(1F);
        Vec3 back = new Vec3(-look.x, 0, -look.z);
        if (back.lengthSqr() < 1.0E-3) back = new Vec3(1, 0, 0);
        back = back.normalize();
        double side = Fear.RNG.nextGaussian() * 0.4;
        Vec3 dir = back.add(back.z * side, 0, -back.x * side).normalize();
        Vec3 from = p.position().add(dir.scale(7 + Fear.RNG.nextInt(4)));
        BlockPos ground = Spots.standable(p.serverLevel(), Mth.floor(from.x), p.getBlockY(), Mth.floor(from.z), 4);
        if (ground != null) from = new Vec3(from.x, ground.getY() + 0.1, from.z);
        Fear.phantomSteps(p, from, dir.scale(-1), 20 * 40);
        return true;
    }

    /** Someone mining inside the walls. Look at that wall and it stops. */
    public static boolean mining(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (int i = 0; i < 60; i++) {
            BlockPos b = p.blockPosition().offset(Fear.RNG.nextInt(21) - 10, Fear.RNG.nextInt(9) - 4, Fear.RNG.nextInt(21) - 10);
            if (b.distSqr(p.blockPosition()) < 36) continue;
            BlockState s = level.getBlockState(b);
            if (!(s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.DIRT))) continue;
            if (Spots.inView(p, Vec3.atCenterOf(b), 0.6)) continue;
            Fear.phantomMining(p, b, 20 * (30 + Fear.RNG.nextInt(40)));
            return true;
        }
        return false;
    }

    /** Someone placing blocks, somewhere close. */
    public static boolean building(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (int i = 0; i < 60; i++) {
            BlockPos b = p.blockPosition().offset(Fear.RNG.nextInt(25) - 12, Fear.RNG.nextInt(7) - 3, Fear.RNG.nextInt(25) - 12);
            if (b.distSqr(p.blockPosition()) < 64) continue;
            if (!level.getBlockState(b).isSolidRender(level, b)) continue;
            if (Spots.inView(p, Vec3.atCenterOf(b), 0.6)) continue;
            Fear.phantomBuilding(p, b, 20 * (20 + Fear.RNG.nextInt(30)));
            return true;
        }
        return false;
    }

    /** Knocking on one of your doors. */
    public static boolean knock(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos c = p.blockPosition();
        for (BlockPos b : BlockPos.betweenClosed(c.offset(-16, -4, -16), c.offset(16, 4, 16))) {
            BlockState s = level.getBlockState(b);
            if (s.getBlock() instanceof DoorBlock && b.distSqr(c) > 9) {
                Fear.phantomKnock(p, Vec3.atCenterOf(b), 20 * 30);
                return true;
            }
        }
        return false;
    }

    private static boolean whisper(ServerPlayer p) {
        Fear.ear(p, "ambient.cave", 0.7F, Fear.range(0.5F, 0.7F));
        if (Fear.RNG.nextInt(3) == 0) Fear.whisper(p, Msg.whisper(), 60);
        return true;
    }

    /** Doors open. Chests open. Nobody is there. */
    private static boolean house(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        List<BlockPos> doors = new ArrayList<>(), chests = new ArrayList<>();
        BlockPos c = p.blockPosition();
        for (BlockPos b : BlockPos.betweenClosed(c.offset(-14, -4, -14), c.offset(14, 4, 14))) {
            BlockState s = level.getBlockState(b);
            if (s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                    && s.is(BlockTags.WOODEN_DOORS)) doors.add(b.immutable());
            else if (s.getBlock() instanceof ChestBlock) chests.add(b.immutable());
            if (doors.size() + chests.size() > 12) break;
        }
        if (Config.ALLOW_GRIEFING.get() && !doors.isEmpty()) {
            BlockPos d = doors.get(Fear.RNG.nextInt(doors.size()));
            BlockState s = level.getBlockState(d);
            DoorBlock door = (DoorBlock) s.getBlock();
            boolean open = door.isOpen(s);
            door.setOpen(null, level, s, d, !open);
            Scheduler.later(40 + Fear.RNG.nextInt(60), () -> {
                BlockState s2 = level.getBlockState(d);
                if (s2.getBlock() instanceof DoorBlock db) db.setOpen(null, level, s2, d, open);
            });
            return true;
        }
        if (!chests.isEmpty()) {
            BlockPos ch = chests.get(Fear.RNG.nextInt(chests.size()));
            Block block = level.getBlockState(ch).getBlock();
            level.blockEvent(ch, block, 1, 1);
            Fear.sound(p, "block.chest.open", Vec3.atCenterOf(ch), 0.6F, 0.8F);
            Scheduler.later(p, 50 + Fear.RNG.nextInt(40), sp -> {
                level.blockEvent(ch, block, 1, 0);
                Fear.sound(sp, "block.chest.close", Vec3.atCenterOf(ch), 0.6F, 0.8F);
            });
            return true;
        }
        return false;
    }

    private static boolean fakeJoin(ServerPlayer p) {
        if (!Config.FAKE_CHAT.get()) return false;
        Fear.joined(p);
        Scheduler.later(p, 60 + Fear.RNG.nextInt(140), sp -> Fear.chat(sp, Msg.chat(sp)));
        if (!HauntData.get(p.getServer()).totem)
            Scheduler.later(p, 300 + Fear.RNG.nextInt(300), Fear::left);
        return true;
    }

    private static boolean farLightning(ServerPlayer p) {
        if (Spots.isUnderground(p)) return false;
        ServerLevel l = p.serverLevel();
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(l);
        if (bolt == null) return false;
        double a = Fear.RNG.nextDouble() * Math.PI * 2, d = 40 + Fear.RNG.nextInt(40);
        double x = p.getX() + Math.cos(a) * d, z = p.getZ() + Math.sin(a) * d;
        if (!l.hasChunkAt(BlockPos.containing(x, 0, z))) return false;
        bolt.moveTo(x, l.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)), z);
        bolt.setVisualOnly(true);
        l.addFreshEntity(bolt);
        return true;
    }

    /** Your lights go out one by one, farthest first. The dark comes closer. */
    public static boolean breakTorches(ServerPlayer p, int max, boolean thenHim) {
        if (!Config.ALLOW_GRIEFING.get()) return false;
        ServerLevel level = p.serverLevel();
        BlockPos c = p.blockPosition();
        List<BlockPos> lights = new ArrayList<>();
        for (BlockPos b : BlockPos.betweenClosed(c.offset(-15, -6, -15), c.offset(15, 6, 15))) {
            BlockState s = level.getBlockState(b);
            if (s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH)
                    || s.is(Blocks.LANTERN) || s.is(Blocks.SOUL_LANTERN)) lights.add(b.immutable());
        }
        if (lights.isEmpty() || (thenHim && lights.size() < 2)) return false;
        lights.sort(Comparator.comparingDouble((BlockPos b) -> b.distSqr(c)).reversed());
        int n = Math.min(max, lights.size());
        int t = 0;
        for (int i = 0; i < n; i++) {
            BlockPos b = lights.get(i);
            Scheduler.later(p, t, sp -> {
                BlockState s = level.getBlockState(b);
                if (!s.isAir()) {
                    level.destroyBlock(b, false);
                    Fear.sound(sp, "block.fire.extinguish", Vec3.atCenterOf(b), 0.6F, 0.6F);
                }
            });
            t += 6 + Fear.RNG.nextInt(12);
        }
        if (thenHim) {
            Scheduler.later(p, t + 30, sp -> {
                if (Fear.RNG.nextBoolean()) behind(sp);
                else if (!dark(sp)) eyes(sp);
            });
        }
        return true;
    }

    // ================================================================== major

    private static int life(int seconds) {
        return 20 * seconds;
    }

    public static boolean stalk(ServerPlayer p) {
        Vec3 v = Spots.findLurking(p, 10, 22);
        if (v == null) v = Spots.findWatchingSpot(p, 9, 20, 15, 20);
        if (v == null) return false;
        return HerobrineEntity.spawn(p, v, Mode.STALK, life(90)) != null;
    }

    public static boolean peek(ServerPlayer p) {
        Vec3 v = Spots.behindTree(p, 7, 18);
        if (v == null) return stalk(p);
        return HerobrineEntity.spawn(p, v, Mode.PEEK, life(60)) != null;
    }

    /** No eyes. Just a shape in the dark. */
    public static boolean dark(ServerPlayer p) {
        Vec3 v = Spots.findWatchingSpot(p, 6, 14, 3, 30);
        if (v == null) v = Spots.findLurking(p, 7, 16);
        if (v == null) return false;
        return HerobrineEntity.spawn(p, v, Mode.DARK, life(80)) != null;
    }

    public static boolean eyes(ServerPlayer p) {
        int n = 1 + Fear.RNG.nextInt(HauntData.get(p.getServer()).totem ? 8 : 3);
        int placed = 0;
        for (int i = 0; i < n * 3 && placed < n; i++) {
            Vec3 v = Spots.findWatchingSpot(p, 6, 16, 4, 6);
            if (v == null) continue;
            if (HerobrineEyesEntity.spawn(p, v.add(0, 1.62, 0), life(40 + Fear.RNG.nextInt(30))) != null) placed++;
        }
        if (placed > 0) {
            Fear.sound(p, "ambient.cave", p.position().add(0, 2, 0), 0.8F, 0.5F);
        }
        return placed > 0;
    }

    /** Turn around. */
    public static boolean behind(ServerPlayer p) {
        Scheduler.later(p, 40, sp -> {
            Vec3 b = Spots.behind(sp, 2.2);
            if (b == null) return;
            HerobrineEntity.spawn(sp, b, Mode.BEHIND, life(25));
            Fear.soundRel(sp, sp.level().getBlockState(sp.blockPosition().below()).getSoundType().getStepSound().getLocation().toString(),
                    0, -1.5, -2, 0.5F, 0.9F);
            if (Fear.RNG.nextInt(3) == 0) Fear.whisper(sp, Component.translatable("herobrine.whisper.behind"), 60);
            else Fear.ear(sp, "ambient.cave", 0.6F, 1.3F);
        });
        return true;
    }

    /** Only moves when you are not looking. */
    public static boolean approach(ServerPlayer p) {
        Vec3 v = Spots.findWatchingSpot(p, 11, 17, 15, 30);
        if (v == null) return false;
        Fear.fx(p, Fx.FOG, life(60), 0.55F);
        Fear.fx(p, Fx.HEARTBEAT, life(60), 1F);
        return HerobrineEntity.spawn(p, v, Mode.APPROACH, life(60)) != null;
    }

    public static boolean jumpscare(ServerPlayer p) {
        int wait = 60 + Fear.RNG.nextInt(100);
        Scheduler.later(p, wait, Fear::jumpscare);
        return true;
    }

    public static boolean clones(ServerPlayer p) {
        boolean totem = HauntData.get(p.getServer()).totem;
        int n = totem ? 10 + Fear.RNG.nextInt(10) : 4 + Fear.RNG.nextInt(5);
        int placed = 0;
        for (int i = 0; i < n * 2 && placed < n; i++) {
            Vec3 v = Spots.findWatchingSpot(p, 7, 20, 15, 6);
            if (v == null) continue;
            if (HerobrineEntity.spawn(p, v, Mode.CLONE, life(70)) != null) placed++;
        }
        if (placed > 0) {
            Fear.fx(p, Fx.FOG, life(40), 0.45F);
        }
        return placed > 0;
    }

    public static boolean chase(ServerPlayer p) {
        if (Artifacts.protectedFromHim(p)) return false;
        Vec3 v = Spots.findWatchingSpot(p, 10, 16, 15, 30);
        if (v == null) v = Spots.behind(p, 12);
        if (v == null) return false;
        boolean totem = HauntData.get(p.getServer()).totem;
        return HerobrineEntity.spawn(p, v, Mode.CHASE, life(totem ? 20 : 12)) != null;
    }

    /** Your body is not yours for a while. */
    public static boolean possessPlayer(ServerPlayer p) {
        if (!Config.ALLOW_PLAYER_POSSESSION.get()) return false;
        boolean walk = Fear.RNG.nextBoolean();
        Vec3 v = walk ? Spots.findWatchingSpot(p, 8, 14, 15, 30) : Spots.behind(p, 3);
        if (v == null) return false;
        HerobrineEntity h = HerobrineEntity.spawn(p, v, Mode.LURE, life(walk ? 10 : 7));
        if (h == null) return false;
        Fear.fx(p, Fx.HEARTBEAT, life(10), 1F);
        Fear.fxAt(p, walk ? Fx.POSSESS : Fx.LOOK_AT, life(walk ? 9 : 6), 1F, v.add(0, 1.62, 0), walk ? 1 : 0);
        if (!walk) Scheduler.later(p, life(6), sp -> {
            if (!h.isRemoved()) {
                Fear.jumpscare(sp);
                h.vanish(false);
            }
        });
        return true;
    }

    public static boolean fight(ServerPlayer p) {
        Vec3 v = Spots.findWatchingSpot(p, 6, 12, 15, 30);
        if (v == null) v = Spots.behind(p, 5);
        if (v == null) return false;
        boolean totem = HauntData.get(p.getServer()).totem;
        Fear.chat(p, Component.translatable("herobrine.chat.fight." + Fear.RNG.nextInt(Msg.FIGHT)));
        Fear.sound(p, "entity.lightning_bolt.thunder", v, 1F, 0.6F);
        return HerobrineEntity.spawn(p, v, Mode.FIGHT, life(totem ? 90 : 45)) != null;
    }

    // ================================================================== reactive (not random)

    /** He was behind the block you just mined. */
    public static boolean blockScare(ServerPlayer p, BlockPos hole) {
        ServerLevel level = p.serverLevel();
        Vec3 look = p.getViewVector(1F);
        Direction d = Direction.getNearest(look.x, 0, look.z);
        BlockPos head = hole.relative(d);
        BlockPos feet = head.below();
        for (BlockPos b : new BlockPos[]{head, feet}) {
            if (!Builder.replaceable(level, b)) return false;
        }
        Scheduler.later(p, 1, sp -> {
            level.setBlock(head, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(feet, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            HerobrineEntity.spawn(sp, Vec3.atBottomCenterOf(feet), Mode.SCARE, 24);
        });
        return true;
    }

    /** You wake up. He is standing next to your bed. */
    public static void bedScare(ServerPlayer p) {
        Scheduler.later(p, 50 + Fear.RNG.nextInt(30), sp -> {
            if (!sp.isSleeping()) return;
            BlockPos bed = sp.getSleepingPos().orElse(sp.blockPosition());
            sp.stopSleepInBed(true, true);
            Vec3 at = null;
            for (int i = 0; i < 12 && at == null; i++) {
                BlockPos s = Spots.standable(sp.serverLevel(), bed.getX() + Fear.RNG.nextInt(5) - 2, bed.getY(), bed.getZ() + Fear.RNG.nextInt(5) - 2, 2);
                if (s != null && s.distSqr(bed) >= 2) at = Vec3.atBottomCenterOf(s);
            }
            if (at == null) return;
            HerobrineEntity.spawn(sp, at, Mode.WATCH, life(12));
            Fear.sound(sp, "entity.enderman.stare", at, 0.8F, 0.5F);
            Fear.fxAt(sp, Fx.LOOK_AT, 30, 1F, at.add(0, 1.62, 0), 0);
        });
    }

    private Haunts() {}
}
