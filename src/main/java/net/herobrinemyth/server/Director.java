package net.herobrinemyth.server;

import net.herobrinemyth.Config;
import net.herobrinemyth.network.Fx;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Decides when. Checked every second, for every player.
 *
 * The haunting is counted in days. On day 1 almost nothing happens: a sound you are not sure you heard.
 * Every day there is a little more, and new things become possible (see Haunts for what unlocks when).
 * Events are spread over the day with a budget, never back to back, never the same one twice in a row.
 */
public final class Director {

    public static void tick(MinecraftServer server) {
        long now = Scheduler.now();
        HauntData data = HauntData.get(server);
        long dayIndex = server.overworld().getDayTime() / 24000L;
        bloodMoon(server, data, dayIndex);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            tickPlayer(p, data, now, dayIndex);
        }
        data.setDirty();
    }

    /** Is tonight a blood moon? */
    public static boolean bloodMoonNow(MinecraftServer server) {
        HauntData data = HauntData.get(server);
        return data.bloodMoonNight >= 0 && data.bloodMoonNight == server.overworld().getDayTime() / 24000L
                && server.overworld().getDayTime() % 24000L >= 12500;
    }

    /**
     * Some nights the moon is red: one night of total chaos. Mobs with his eyes, him, everything, until sunrise.
     * It never deletes the world and never corrupts it completely: only the totem does that.
     */
    private static void bloodMoon(MinecraftServer server, HauntData data, long dayIndex) {
        if (data.totem || !Config.ENABLED.get() || !Config.BLOOD_MOON.get()) return;
        net.minecraft.server.level.ServerLevel ow = server.overworld();
        long time = ow.getDayTime() % 24000L;
        if (data.bloodMoonNight >= 0 && data.bloodMoonNight != dayIndex) data.bloodMoonNight = -1;
        if (time < 12500 || time > 12520 || data.bloodMoonNight == dayIndex) return;
        int best = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) best = Math.max(best, data.level(p.getUUID()));
        if (best < 10 * Config.DAY_SCALE.get() || Fear.RNG.nextInt(best >= 25 ? 4 : 7) != 0) return;
        startBloodMoon(server);
    }

    /** Starts tonight's blood moon (also used by the command). */
    public static void startBloodMoon(MinecraftServer server) {
        HauntData data = HauntData.get(server);
        net.minecraft.server.level.ServerLevel ow = server.overworld();
        long time = ow.getDayTime() % 24000L;
        if (time < 12500) {
            ow.setDayTime(ow.getDayTime() - time + 12500);
            time = 12500;
        }
        data.bloodMoonNight = ow.getDayTime() / 24000L;
        int left = (int) (23500 - time);
        ow.setWeatherParameters(0, left, true, false);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() != ow) continue;
            Fear.fx(p, Fx.BLOOD, left, 1F);
            Fear.bigText(p, net.minecraft.network.chat.Component.translatable("herobrine.bloodmoon")
                    .withStyle(net.minecraft.ChatFormatting.DARK_RED), 120);
            Fear.soundReverb(p, "entity.wither.spawn", p.position().add(0, 30, 0), 1F, 0.5F);
            HauntData.PlayerState st = data.player(p.getUUID());
            st.nextMajor = Math.min(st.nextMajor, Scheduler.now() + 20 * 30);
        }
        data.setDirty();
    }

    private static boolean hauntable(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator() || Graveyard.isGraveyard(p.level())) return false;
        return !p.isCreative() || Config.HAUNT_CREATIVE.get();
    }

    /** Events of one kind per in-game day, for the day of the haunting. */
    static double perDay(boolean major, int day, boolean totem) {
        double scale = Config.DAY_SCALE.get();
        double d = day / scale;
        // from day 3 on: he almost never shows up at first, then a little more every day
        double n = major ? 1.2 + d * 0.3 : 5.0 + d * 1.0;
        n = Math.min(n, major ? Config.MAX_MAJOR_PER_DAY.get() : Config.MAX_MINOR_PER_DAY.get());
        n *= Config.FREQUENCY.get();
        if (totem) n *= Config.TOTEM_MULTIPLIER.get();
        return Math.max(0.05, n);
    }

    /** Ticks until the next event of a kind, spread randomly around the average. He is stronger at night. */
    private static long gap(boolean major, int day, boolean totem, boolean night) {
        double avg = 24000.0 / perDay(major, day, totem);
        if (night) avg /= Config.NIGHT_ACTIVITY.get();
        return Math.max(20 * 15, (long) (avg * (0.5 + Fear.RNG.nextDouble())));
    }

    /** The world is never quite silent: cave sounds, steps, someone mining, a whisper. Not counted as events. */
    private static final String[] AMBIENT_DAY = {"cave_sound", "cave_sound", "footsteps", "mining", "faint", "building", "faint"};
    private static final String[] AMBIENT_NIGHT = {"cave_sound", "cave_sound", "footsteps", "footsteps", "mining", "faint", "knock",
            "whisper", "breath", "building"};

    private static void ambient(ServerPlayer p, HauntData.PlayerState st, int day, long now, boolean night) {
        double secs = Math.max(30, 120 - day * 3.0);
        if (night) secs *= 0.65;
        if (st.nextAmbient == 0) st.nextAmbient = now + (long) (20 * secs * Fear.RNG.nextDouble());
        if (now < st.nextAmbient) return;
        st.nextAmbient = now + (long) (20 * secs * (0.6 + Fear.RNG.nextDouble() * 0.8));
        String[] pool = night ? AMBIENT_NIGHT : AMBIENT_DAY;
        for (int i = 0; i < 4; i++) {
            Haunts.Haunt h = Haunts.ALL.get(pool[Fear.RNG.nextInt(pool.length)]);
            if (h != null && Haunts.unlockDay(h) <= Math.max(day, 2) && Haunts.trigger(h.id(), p)) return;
        }
    }

    /**
     * The first two days: a few unmistakable things, far apart. Enough to know the world is cursed.
     * Then, from day 3, it goes quiet again and grows slowly.
     */
    private static final String[] INTRO_DAY_1 = {"footsteps", "glimpse", "cave_sound", "tree", "knock", "glimpse"};
    private static final String[] INTRO_DAY_2 = {"sign", "glimpse", "mining", "stalk", "nameless", "tree", "redstone_torch", "footsteps"};

    private static void intro(ServerPlayer p, HauntData.PlayerState st, long now) {
        if (st.introNext == 0) st.introNext = now + 20 * 60 * (2 + Fear.RNG.nextInt(3));
        if (now < st.introNext) return;
        String[] list = st.days <= 1 ? INTRO_DAY_1 : INTRO_DAY_2;
        for (int i = 0; i < 6; i++) {
            String id = list[Fear.RNG.nextInt(list.length)];
            if (st.recent.contains(id)) continue;
            if (Haunts.trigger(id, p)) {
                st.recent.addLast(id);
                while (st.recent.size() > 3) st.recent.removeFirst();
                break;
            }
        }
        boolean night = p.level().isNight();
        st.introNext = now + 20 * 60 * (night ? 3 + Fear.RNG.nextInt(3) : 5 + Fear.RNG.nextInt(4));
    }

    private static void tickPlayer(ServerPlayer p, HauntData data, long now, long dayIndex) {
        HauntData.PlayerState st = data.player(p.getUUID());
        st.playTicks += 20;
        if (data.totem) st.postTotemTicks += 20;

        // a new day of the haunting
        if (st.lastDayIndex < 0) st.lastDayIndex = dayIndex;
        if (dayIndex != st.lastDayIndex) {
            st.lastDayIndex = dayIndex;
            st.days++;
            onNewDay(p, data, st);
        }
        if (now % 600 == 20) Fear.fx(p, Fx.MOOD, 1, data.level(p.getUUID()));

        if (!Config.ENABLED.get() || !hauntable(p)) return;
        if (!data.totem && st.playTicks < Config.GRACE_SECONDS.get() * 20L) return;

        int day = data.level(p.getUUID());
        boolean night = p.level().isNight() || Spots.isUnderground(p);
        ambient(p, st, day, now, night);

        if (!data.totem && st.days <= 2 && st.forcedLevel <= 0) {
            intro(p, st, now);
            return;
        }
        if (bloodMoonNow(p.getServer())) bloodMoonTick(p, now);
        if (st.nextMinor == 0) st.nextMinor = now + gap(false, day, data.totem, night) / 2;
        if (st.nextMajor == 0) st.nextMajor = now + gap(true, day, data.totem, night) / 2;

        if (now >= st.nextMinor) {
            Haunts.random(p, false);
            st.nextMinor = now + gap(false, day, data.totem, night);
        }
        if (now >= st.nextMajor && now >= st.busyUntil) {
            if (Haunts.random(p, true)) st.busyUntil = now + 20 * 45;
            st.nextMajor = now + gap(true, day, data.totem, night);
        }

        if (data.totem) totemTick(p, st, now);
    }

    /** During a blood moon: waves of his mobs, and him. */
    private static void bloodMoonTick(ServerPlayer p, long now) {
        if (now % (20 * 50) == 0) Possession.swarm(p, 3 + Fear.RNG.nextInt(4));
        if (now % (20 * 35) == 0) Fear.caveSound(p);
        if (now % (20 * 75) == 0) {
            String[] big = {"chase", "hunters", "group_attack", "closer", "clones", "stalk", "chase_music", "lightning_walk"};
            Haunts.trigger(big[Fear.RNG.nextInt(big.length)], p);
        }
    }

    /** Once per day: what he leaves behind while the days pass. */
    private static void onNewDay(ServerPlayer p, HauntData data, HauntData.PlayerState st) {
        int day = data.level(p.getUUID());
        Fear.fx(p, Fx.MOOD, 1, day);
        if (!Config.ENABLED.get() || !hauntable(p)) return;
        // he gets a little closer to your home every day
        if (day >= 2 && Config.ALLOW_STRUCTURES.get()) Slow.marker(p, st);
        // and the world slowly rots
        int corruptDay = Config.CORRUPTION_START_DAY.get();
        if (corruptDay > 0 && day >= corruptDay) Scheduler.later(p, 20 * (60 + Fear.RNG.nextInt(600)), Slow::rot);
        int fireDay = Config.FIRE_START_DAY.get();
        if (fireDay > 0 && day >= fireDay && Config.ALLOW_GRIEFING.get() && Fear.RNG.nextInt(3) == 0)
            Scheduler.later(p, 20 * (60 + Fear.RNG.nextInt(900)), Slow::burn);
        int chunkDay = Config.CHUNK_DELETION_START_DAY.get();
        if (chunkDay > 0 && day >= chunkDay && Config.CHUNK_DELETION.get() && Fear.RNG.nextInt(4) == 0)
            Scheduler.later(p, 20 * (120 + Fear.RNG.nextInt(900)), sp -> Corruption.eatChunk(sp));
        if (day >= 20 && Config.ALLOW_HOUSE_DAMAGE.get() && Fear.RNG.nextInt(4) == 0)
            Scheduler.later(p, 20 * (60 + Fear.RNG.nextInt(900)), Slow::ransack);
        // in the middle of it all the world starts to go wrong: patches of corruption, a few at a time
        if (corruptDay > 0 && day >= corruptDay + 6 && Fear.RNG.nextInt(3) == 0)
            Scheduler.later(p, 20 * (120 + Fear.RNG.nextInt(900)), sp -> Corruption.corrupt(sp));
        if (fireDay > 0 && day >= fireDay && Fear.RNG.nextInt(4) == 0)
            Scheduler.later(p, 20 * (60 + Fear.RNG.nextInt(900)), sp -> {
                if (!Human.burnForest(sp)) Human.burnGrass(sp);
            });
        // and things someone left: a pillar, a camp, a chopped tree
        if (day >= 3 && Config.ALLOW_STRUCTURES.get())
            Scheduler.later(p, 20 * (30 + Fear.RNG.nextInt(600)), sp -> {
                String[] h = {"human_pillar", "human_camp", "human_tree", "human_hut", "human_holes", "build"};
                Haunts.trigger(h[Fear.RNG.nextInt(h.length)], sp);
            });
    }

    /** After the totem he never stops: everything, one after another. */
    private static void totemTick(ServerPlayer p, HauntData.PlayerState st, long now) {
        if (st.nextCorrupt == 0) {
            st.nextCorrupt = now + 20 * 30;
            st.nextChunk = now + 20 * 60 * 3;
            st.nextSwarm = now + 20 * 60 * 2;
            st.nextNoise = now + 20 * 90;
            st.nextCrash = now + 20 * 60 * 5;
        }
        if (now % 400 == 0) Fear.unmute(p, p.position(), 64, 20 * 25);
        if (now >= st.nextCorrupt) {
            Corruption.corrupt(p);
            st.nextCorrupt = now + 20 * (30 + Fear.RNG.nextInt(60));
        }
        if (now >= st.nextNoise) {
            Corruption.noise(p);
            st.nextNoise = now + 20 * (90 + Fear.RNG.nextInt(180));
        }
        if (now >= st.nextSwarm) {
            Possession.swarm(p, Math.max(1, Config.MAX_SWARM.get() / 2 + Fear.RNG.nextInt(Config.MAX_SWARM.get() / 2 + 1)));
            st.nextSwarm = now + 20 * 60 * (3 + Fear.RNG.nextInt(4));
        }
        if (now >= st.nextChunk) {
            if (Config.CHUNK_DELETION.get()) Corruption.eatChunk(p);
            st.nextChunk = now + 20 * 60 * (3 + Fear.RNG.nextInt(5));
        }
        if (Fear.RNG.nextInt(180) == 0) Fear.chat(p, Msg.totemChat(p));

        long minutes = st.postTotemTicks / (20 * 60);
        if (now >= st.nextCrash) {
            st.nextCrash = now + 20 * 60 * (6 + Fear.RNG.nextInt(10));
            if (minutes >= Config.CRASH_AFTER_MINUTES.get() && Fear.RNG.nextInt(3) == 0) Corruption.crash(p);
        }
        if (!st.deletionStarted && minutes >= Config.DELETION_AFTER_MINUTES.get() && now >= st.nextDeletionCheck) {
            st.nextDeletionCheck = now + 20 * 60;
            if (Fear.RNG.nextInt(4) == 0) Corruption.takeWorld(p);
        }
    }

    private Director() {}
}
