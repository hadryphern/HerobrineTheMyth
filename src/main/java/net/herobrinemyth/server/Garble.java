package net.herobrinemyth.server;

/** iLoveYou: after the altar, every command you type answers in garbage for a while. */
public final class Garble {
    private static long until;

    public static void start(int ticks) {
        until = Math.max(until, Scheduler.now() + ticks);
    }

    public static boolean active() {
        return Scheduler.now() < until;
    }

    private Garble() {}
}
