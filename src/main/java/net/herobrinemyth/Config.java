package net.herobrinemyth;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    public enum CrashMode { OFF, CRASH_SCREEN, CLOSE_GAME }
    public enum DeletionMode { OFF, HIDE, DELETE }
    public enum Progression { INGAME_DAYS, REAL_MINUTES }

    public static final ForgeConfigSpec SPEC;

    // haunting
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue HAUNT_CREATIVE;
    public static final ForgeConfigSpec.IntValue GRACE_SECONDS;
    public static final ForgeConfigSpec.DoubleValue FREQUENCY;
    public static final ForgeConfigSpec.IntValue SIGHTING_SECONDS;
    public static final ForgeConfigSpec.BooleanValue FAKE_CHAT;
    public static final ForgeConfigSpec.BooleanValue TAB_STRANGER;

    // progression
    public static final ForgeConfigSpec.DoubleValue DAY_SCALE;
    public static final ForgeConfigSpec.IntValue MAX_MINOR_PER_DAY;
    public static final ForgeConfigSpec.IntValue MAX_MAJOR_PER_DAY;

    // what he may do
    public static final ForgeConfigSpec.BooleanValue ALLOW_GRIEFING;
    public static final ForgeConfigSpec.BooleanValue ALLOW_STRUCTURES;
    public static final ForgeConfigSpec.BooleanValue ALLOW_HOUSE_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue ALLOW_KILL;
    public static final ForgeConfigSpec.BooleanValue ALLOW_PLAYER_POSSESSION;
    public static final ForgeConfigSpec.BooleanValue ALLOW_CAVE_TELEPORT;
    public static final ForgeConfigSpec.BooleanValue ALLOW_BLOCK_SCARE;
    public static final ForgeConfigSpec.IntValue CORRUPTION_START_DAY;
    public static final ForgeConfigSpec.IntValue FIRE_START_DAY;
    public static final ForgeConfigSpec.IntValue CHUNK_DELETION_START_DAY;
    public static final ForgeConfigSpec.BooleanValue CHUNK_DELETION;
    public static final ForgeConfigSpec.BooleanValue KICKS, BLOOD_MOON, SHARED_EVENTS, PUNISH_BREAKING, RANDOM_DAMAGE, BURN_VILLAGES;

    // totem
    public static final ForgeConfigSpec.BooleanValue TOTEM_ENABLED;
    public static final ForgeConfigSpec.DoubleValue TOTEM_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MAX_SWARM;
    public static final ForgeConfigSpec.BooleanValue ETERNAL_NIGHT;
    public static final ForgeConfigSpec.EnumValue<CrashMode> CRASH_MODE;
    public static final ForgeConfigSpec.BooleanValue TOTEM_BOSSES;
    public static final ForgeConfigSpec.IntValue CRASH_AFTER_MINUTES;
    public static final ForgeConfigSpec.EnumValue<DeletionMode> DELETION_MODE;
    public static final ForgeConfigSpec.IntValue DELETION_AFTER_MINUTES;

    // atmosphere (client)
    public static final ForgeConfigSpec.DoubleValue HOSTILE_SPAWNS, PASSIVE_SPAWNS;
    public static final ForgeConfigSpec.DoubleValue NIGHT_ACTIVITY;
    public static final ForgeConfigSpec.DoubleValue GAME_VOLUME;
    public static final ForgeConfigSpec.BooleanValue CLASSIC_SOUNDS;
    public static final ForgeConfigSpec.BooleanValue SLOW_CAVE_SOUNDS;
    public static final ForgeConfigSpec.BooleanValue REVERB;
    public static final ForgeConfigSpec.BooleanValue QUIET_SCREAMS;
    public static final ForgeConfigSpec.BooleanValue OVERCAST;
    public static final ForgeConfigSpec.BooleanValue ALWAYS_BLOOD_RAIN;
    public static final ForgeConfigSpec.BooleanValue GLOWING_EYES;
    public static final ForgeConfigSpec.BooleanValue OLD_TEXTURES;
    public static final ForgeConfigSpec.BooleanValue CLASSIC_PACK;
    public static final ForgeConfigSpec.BooleanValue OLD_LIGHTING;
    public static final ForgeConfigSpec.BooleanValue STEVE_ONLY;
    public static final ForgeConfigSpec.ConfigValue<String> VERSION_LABEL;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("He is always watching.").push("haunting");
        ENABLED = b.comment("Master switch. If false Herobrine never acts on his own (commands still work).")
                .define("enabled", true);
        HAUNT_CREATIVE = b.comment("Haunt players in creative/spectator mode too.")
                .define("hauntCreative", false);
        GRACE_SECONDS = b.comment("Seconds of play before anything at all can happen on a new world.")
                .defineInRange("graceSeconds", 240, 0, 36000);
        FREQUENCY = b.comment("Multiplies how many events happen per day. 2.0 = twice as many.")
                .defineInRange("frequency", 1.0, 0.05, 50.0);
        SIGHTING_SECONDS = b.comment("Longest time (seconds) he stays when he is only watching you.")
                .defineInRange("sightingSeconds", 60, 3, 3600);
        FAKE_CHAT = b.comment("Fake chat messages ('<Herobrine> ...').")
                .define("fakeChat", true);
        TAB_STRANGER = b.comment("A second player with no name and a black skin is always in the TAB list.")
                .define("tabStranger", true);
        b.pop();

        b.comment("How fast it gets worse. Days or minutes per step are chosen per world (serverconfig/herobrinemyth-server.toml).").push("progression");
        DAY_SCALE = b.comment("Stretches the whole progression. 2.0 = everything unlocks twice as late (good for 100 days).",
                        "0.5 = twice as fast.")
                .defineInRange("dayScale", 1.0, 0.05, 20.0);
        MAX_MINOR_PER_DAY = b.comment("Upper limit of small events (sounds, signs, little things) per Minecraft day.")
                .defineInRange("minorEventsPerDayLimit", 40, 0, 500);
        MAX_MAJOR_PER_DAY = b.comment("Upper limit of big events (sightings, chases, attacks) per Minecraft day.")
                .defineInRange("majorEventsPerDayLimit", 12, 0, 200);
        b.pop();

        b.comment("What he is allowed to do to you and your world.").push("permissions");
        ALLOW_GRIEFING = b.comment("Break torches, strip leaves, open doors, dig holes.")
                .define("allowGriefing", true);
        ALLOW_STRUCTURES = b.comment("Build his structures (pyramids, tunnels, crosses, graves, signs...).")
                .define("allowStructures", true);
        ALLOW_HOUSE_DAMAGE = b.comment("Damage your house (windows, doors, walls) in later days.")
                .define("allowHouseDamage", true);
        ALLOW_KILL = b.comment("His attacks may kill you. If false they always leave you with half a heart.")
                .define("allowKill", true);
        ALLOW_PLAYER_POSSESSION = b.comment("Take control of the player's body for a few seconds.")
                .define("allowPlayerPossession", true);
        ALLOW_CAVE_TELEPORT = b.comment("Drag the player into a dark cave (also splits players up in multiplayer).")
                .define("allowCaveTeleport", true);
        ALLOW_BLOCK_SCARE = b.comment("Wait behind the block you mine.")
                .define("allowBlockScare", true);
        CORRUPTION_START_DAY = b.comment("Day the world starts to rot slowly (netherrack, dead plants). 0 = only after the totem.")
                .defineInRange("corruptionStartDay", 12, 0, 100000);
        FIRE_START_DAY = b.comment("Day he starts burning things. 0 = only after the totem.")
                .defineInRange("fireStartDay", 10, 0, 100000);
        CHUNK_DELETION_START_DAY = b.comment("Day he starts eating whole chunks, slowly. 0 = only after the totem.")
                .defineInRange("chunkDeletionStartDay", 20, 0, 100000);
        CHUNK_DELETION = b.comment("Master switch for chunk eating.")
                .define("chunkDeletion", true);
        BURN_VILLAGES = b.comment("He burns forests and villages (from fireStartDay).")
                .define("burnVillages", true);
        KICKS = b.comment("Before the totem: sometimes he throws you out to the title screen ('Banned by Herobrine'). The world is saved.")
                .define("herobrineKicks", true);
        BLOOD_MOON = b.comment("Some nights are a blood moon: one night of total chaos. Never deletes or fully corrupts the world.")
                .define("bloodMoon", true);
        SHARED_EVENTS = b.comment("Multiplayer: some of his messages, sounds, sightings and jumpscares reach everyone, not only his target.")
                .define("sharedEvents", true);
        PUNISH_BREAKING = b.comment("Breaking a block of one of his structures is punished (lightning, him, lava, silverfish, a cave...).")
                .define("punishStructureBreaking", true);
        RANDOM_DAMAGE = b.comment("Small damage from nobody, now and then.")
                .define("randomDamage", true);
        b.pop();

        b.comment("Gold 3x3, mossy cobblestone, 4 redstone torches, netherrack, fire. Everything at once, one after another.").push("totem");
        TOTEM_ENABLED = b.define("totemEnabled", true);
        TOTEM_MULTIPLIER = b.comment("How much more often events happen after the totem.")
                .defineInRange("totemFrequencyMultiplier", 6.0, 1.0, 100.0);
        MAX_SWARM = b.comment("Maximum mobs per swarm.")
                .defineInRange("maxSwarmMobs", 28, 0, 200);
        ETERNAL_NIGHT = b.comment("After the totem the sun never rises again and it never stops storming.")
                .define("eternalNight", true);
        CRASH_MODE = b.comment("OFF, CRASH_SCREEN (saves, fake crash screen), CLOSE_GAME (saves, fake crash, closes Minecraft).")
                .defineEnum("crashMode", CrashMode.CLOSE_GAME);
        TOTEM_BOSSES = b.comment("After the totem he may summon the Wither, the Warden and the Ender Dragon.")
                .define("totemBosses", true);
        CRASH_AFTER_MINUTES = b.defineInRange("crashAfterMinutes", 12, 0, 100000);
        DELETION_MODE = b.comment("The end. Singleplayer only.",
                        "OFF = never. HIDE = the world moves to .minecraft/herobrine_took_your_worlds (recoverable).",
                        "DELETE = the world folder is permanently deleted. There is no undo.")
                .defineEnum("worldDeletionMode", DeletionMode.HIDE);
        DELETION_AFTER_MINUTES = b.defineInRange("worldDeletionAfterMinutes", 75, 1, 100000);
        b.pop();

        b.comment("An empty world.").push("world");
        HOSTILE_SPAWNS = b.comment("Fraction of natural hostile mob spawns that still happen (1.0 = vanilla).")
                .defineInRange("hostileSpawnRate", 0.08, 0.0, 1.0);
        PASSIVE_SPAWNS = b.comment("Fraction of natural animal spawns that still happen (1.0 = vanilla).")
                .defineInRange("passiveSpawnRate", 0.2, 0.0, 1.0);
        NIGHT_ACTIVITY = b.comment("How much stronger he is at night: events happen this many times more often after dark.")
                .defineInRange("nightStrength", 1.7, 1.0, 20.0);
        b.pop();

        b.comment("Client side: how the world feels.").push("atmosphere");
        GAME_VOLUME = b.comment("Volume of the game's own sounds (his sounds are not affected).")
                .defineInRange("gameSoundVolume", 0.6, 0.0, 1.0);
        CLASSIC_SOUNDS = b.comment("Mute most modern sounds so the game sounds as empty as it did back then.")
                .define("classicSoundsOnly", true);
        SLOW_CAVE_SOUNDS = b.comment("Cave sounds play slowed down.")
                .define("slowedCaveSounds", true);
        REVERB = b.comment("Cave sounds and his sounds get a long echo (OpenAL EFX).")
                .define("reverb", true);
        QUIET_SCREAMS = b.comment("When it has been completely quiet for a while... something screams.")
                .define("quietScreams", true);
        OVERCAST = b.comment("The sky is always grey and overcast. No sun, no stars.")
                .define("overcastSky", true);
        ALWAYS_BLOOD_RAIN = b.comment("Every rain falls red, not only his blood rain.")
                .define("alwaysBloodRain", true);
        GLOWING_EYES = b.comment("His eyes glow. Off by default: you only see a shape in the dark.")
                .define("glowingEyes", false);
        OLD_TEXTURES = b.comment("Turn on Minecraft's built-in classic textures (Programmer Art).")
                .define("classicTextures", true);
        CLASSIC_PACK = b.comment("Build a resource pack with the real Beta and 1.7.10 textures (and the old hurt sound) from the old",
                        "Minecraft versions installed on this computer, or from Mojang's servers if they are missing. Nothing is shipped in the mod.")
                .define("oldVersionTextures", true);
        OLD_LIGHTING = b.comment("Old lighting: flat (no smooth lighting) and grey, without the modern warm tint.")
                .define("oldLighting", true);
        STEVE_ONLY = b.comment("Every player is rendered as Steve. No custom skins, no capes.")
                .define("steveOnly", true);
        VERSION_LABEL = b.comment("Text in the top-left corner like the old versions had. Empty to hide it.")
                .define("versionLabel", "Minecraft Alpha v1.0.16");
        b.pop();

        SPEC = b.build();
    }

    private Config() {}
}
