package net.herobrinemyth;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Settings that belong to one world (saved in the world's serverconfig folder).
 * The "new world" screen fills these in when the world is created.
 */
public final class ServerConfig {
    public enum Mode { DAYS, MINUTES }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<Mode> MODE;
    public static final ForgeConfigSpec.IntValue DAYS_PER_STEP;
    public static final ForgeConfigSpec.IntValue MINUTES_PER_STEP;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("How fast Herobrine gets stronger in this world.").push("progression");
        MODE = b.comment("DAYS: he gets stronger every 'daysPerStep' Minecraft days.",
                        "MINUTES: he gets stronger every 'minutesPerStep' minutes of play.")
                .defineEnum("mode", Mode.DAYS);
        DAYS_PER_STEP = b.defineInRange("daysPerStep", 1, 1, 10000);
        MINUTES_PER_STEP = b.defineInRange("minutesPerStep", 10, 1, 100000);
        b.pop();
        SPEC = b.build();
    }

    /** Chosen on the "new world" screen, applied when that world's server starts. */
    public static volatile Mode pendingMode;
    public static volatile int pendingAmount;

    private ServerConfig() {}
}
