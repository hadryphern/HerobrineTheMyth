package net.herobrinemyth.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Delayed server tasks. Sequences of horror are built out of these. */
public final class Scheduler {
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();
    private static long now;
    /** Count of delayed tasks that threw. Used by the GameTests. */
    public static int failures;

    private record Task(long due, Runnable run) {}

    public static long now() {
        return now;
    }

    public static void later(int ticks, Runnable r) {
        PENDING.add(new Task(now + Math.max(1, ticks), r));
    }

    /** Runs only if the player is still online, alive and in the same dimension. */
    public static void later(ServerPlayer p, int ticks, Consumer<ServerPlayer> c) {
        UUID id = p.getUUID();
        MinecraftServer server = p.getServer();
        var dim = p.level().dimension();
        later(ticks, () -> {
            if (server == null) return;
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp == null || !sp.isAlive() || sp.level().dimension() != dim) return;
            c.accept(sp);
        });
    }

    public static void tick() {
        now++;
        if (!PENDING.isEmpty()) {
            TASKS.addAll(PENDING);
            PENDING.clear();
        }
        Iterator<Task> it = TASKS.iterator();
        List<Task> due = new ArrayList<>();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.due <= now) {
                due.add(t);
                it.remove();
            }
        }
        for (Task t : due) {
            try {
                t.run.run();
            } catch (Exception e) {
                failures++;
                net.herobrinemyth.HerobrineMyth.LOGGER.error("Herobrine task failed", e);
            }
        }
    }

    public static void clear() {
        TASKS.clear();
        PENDING.clear();
    }

    private Scheduler() {}
}
