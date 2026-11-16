package net.herobrinemyth.client;

import net.herobrinemyth.HerobrineMyth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Development only. Active when the JVM runs with -Dherobrinemyth.selftest=true
 * (gradlew runClient -Pselftest). Creates a world, runs the scares by command,
 * saves screenshots to run/screenshots/selftest_*.png and closes the game.
 */
@Mod.EventBusSubscriber(modid = HerobrineMyth.MODID, value = Dist.CLIENT)
public final class SelfTest {
    private static final String MODE = System.getProperty("herobrinemyth.selftest", "");
    private static final boolean ON = !MODE.isEmpty();
    /** "crash" or "take" test the endings on a throwaway world instead of the normal run. */
    private static final boolean ENDING = MODE.equals("crash") || MODE.equals("take") || MODE.equals("promo");
    private static final boolean CLASSIC = MODE.equals("classic");
    private static final String WORLD = ENDING ? "HerobrineEndingTest" : "HerobrineSelfTest";
    private static int endingTicks;
    private static int setupTicks, packWait;
    private static boolean subtitles;

    private record Step(int at, Consumer<Minecraft> run) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static boolean started;
    private static int ticks = -1;

    private static void cmd(int at, String command) {
        STEPS.add(new Step(at, mc -> {
            HerobrineMyth.LOGGER.info("[selftest] /{}", command);
            mc.player.connection.sendCommand(command);
        }));
    }

    private static String pendingShot;

    static boolean active() {
        return ON;
    }

    private static void shot(int at, String name) {
        STEPS.add(new Step(at, mc -> pendingShot = name));
    }

    /** Screenshots are taken after the frame (world + overlays) is completely drawn. */
    @SubscribeEvent
    public static void onRender(TickEvent.RenderTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END || pendingShot == null) return;
        String name = pendingShot;
        pendingShot = null;
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "selftest_" + name + ".png", mc.getMainRenderTarget(),
                c -> HerobrineMyth.LOGGER.info("[selftest] screenshot {}: {}", name, c.getString()));
    }

    static {
        if (MODE.equals("v14")) {
            v14Steps();
        } else if (MODE.equals("v13")) {
            v13Steps();
        } else if (MODE.equals("v12")) {
            v12Steps();
        } else if (MODE.equals("v2")) {
            v2Steps();
        } else if (MODE.equals("promo")) {
            promoSteps();
        } else if (ENDING) {
            cmd(100, "gamemode creative");
            cmd(120, MODE.equals("crash") ? "herobrine trigger crash" : "herobrine trigger take_world");
        } else if (CLASSIC) {
            classicSteps();
        } else {
            normalSteps();
        }
    }

    /** The second version: overcast sky, Steve, possessed herd, tab stranger, blood rain, the new face. */
    private static void v2Steps() {
        int t = 120;
        cmd(t - 12, "gamerule sendCommandFeedback false");
        STEPS.add(new Step(t - 11, mc -> {
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.getToasts().clear();
        }));
        cmd(t - 10, "gamemode creative");
        cmd(t - 9, "tp @s ~ 101 ~ 0 0");
        cmd(t - 8, "fill ~-60 ~-1 ~-60 ~60 ~-1 ~60 grass_block");
        cmd(t - 7, "place feature minecraft:oak ~8 ~ ~14");
        cmd(t - 6, "place feature minecraft:oak ~-9 ~ ~20");
        cmd(t - 5, "gamerule doDaylightCycle false");
        cmd(t - 4, "gamerule doMobSpawning false");
        cmd(t - 3, "time set 6000");
        cmd(t - 2, "weather clear");
        shot(t + 40, "v01_overcast_day");
        cmd(t + 45, "summon herobrinemyth:herobrine ^1 ^ ^4 {HBMode:\"STEVE\",HBLife:2000}");
        shot(t + 70, "v02_steve");
        cmd(t + 75, "kill @e[type=herobrinemyth:herobrine]");
        cmd(t + 80, "time set 13500");
        cmd(t + 85, "summon villager ^-2 ^ ^5 {NoAI:1b}");
        cmd(t + 86, "summon pig ^0 ^ ^5 {NoAI:1b}");
        cmd(t + 87, "summon cow ^2 ^ ^5 {NoAI:1b}");
        cmd(t + 88, "summon sheep ^4 ^ ^6 {NoAI:1b}");
        cmd(t + 89, "summon zombie ^-4 ^ ^6 {NoAI:1b}");
        cmd(t + 95, "herobrine trigger animals");
        cmd(t + 96, "herobrine trigger possess_mobs");
        shot(t + 120, "v03_possessed_herd");
        cmd(t + 125, "kill @e[type=!player]");
        STEVE_TAB(t + 130);
        cmd(t + 160, "time set 18000");
        cmd(t + 165, "summon herobrinemyth:herobrine ^2 ^ ^10 {HBMode:\"ECHO\",HBLife:600}");
        shot(t + 195, "v05_night_no_glow");
        cmd(t + 200, "kill @e[type=herobrinemyth:herobrine]");
        cmd(t + 205, "herobrine trigger blood_rain");
        shot(t + 290, "v06_blood_rain");
        cmd(t + 300, "weather clear");
        cmd(t + 305, "herobrine fx 12 1 0");
        cmd(t + 310, "herobrine jumpscare");
        shot(t + 318, "v07_face");
        cmd(t + 330, "herobrine trigger cave_sound");
        cmd(t + 340, "herobrine trigger footsteps");
        cmd(t + 341, "herobrine trigger mining");
        STEPS.add(new Step(t + 420, mc -> {
            HerobrineMyth.LOGGER.info("[selftest] done");
            mc.stop();
        }));
    }

    /** 1.4: babies and chickens with his eyes, every rain red, a darker day, a row of them, thrown out. */
    private static void v14Steps() {
        int t = 120;
        cmd(t - 12, "gamerule sendCommandFeedback false");
        STEPS.add(new Step(t - 11, mc -> {
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.getToasts().clear();
            mc.gui.getChat().clearMessages(false);
            subtitles = mc.options.showSubtitles().get();
            mc.options.showSubtitles().set(false);
        }));
        cmd(t - 10, "gamemode creative");
        cmd(t - 9, "tp @s ~ 101 ~ 0 0");
        cmd(t - 8, "fill ~-40 ~-1 ~-40 ~40 ~-1 ~40 grass_block");
        cmd(t - 7, "fill ~-30 ~ ~-30 ~30 ~5 ~30 air");
        cmd(t - 6, "place feature minecraft:oak ~3 ~ ~14");
        cmd(t - 5, "place feature minecraft:oak ~-6 ~ ~18");
        cmd(t - 4, "kill @e[type=!player]");
        cmd(t - 3, "gamerule doDaylightCycle false");
        cmd(t - 2, "gamerule doMobSpawning false");
        cmd(t - 1, "time set 6000");
        cmd(t, "weather clear");
        cmd(t + 1, "tp @s ~ ~ ~ 0 8");
        shot(t + 40, "y01_darker_day");
        cmd(t + 45, "weather rain");
        shot(t + 120, "y02_red_rain");
        cmd(t + 125, "weather clear");
        cmd(t + 130, "summon zombie ^-2.4 ^ ^4 {IsBaby:1b,NoAI:1b}");
        cmd(t + 131, "summon zombie_villager ^-1 ^ ^5 {NoAI:1b}");
        cmd(t + 132, "summon zombie_villager ^0.3 ^ ^4 {IsBaby:1b,NoAI:1b}");
        cmd(t + 133, "summon pig ^1.5 ^ ^4 {Age:-24000,NoAI:1b}");
        cmd(t + 134, "summon cow ^2.8 ^ ^5 {Age:-24000,NoAI:1b}");
        cmd(t + 135, "summon chicken ^-0.6 ^ ^3 {NoAI:1b}");
        cmd(t + 136, "summon chicken ^3.6 ^ ^4 {Age:-24000,NoAI:1b}");
        cmd(t + 137, "summon villager ^-3.6 ^ ^5 {Age:-24000,NoAI:1b}");
        cmd(t + 140, "time set 18000");
        cmd(t + 145, "herobrine trigger animals");
        cmd(t + 146, "herobrine trigger possess_mobs");
        shot(t + 175, "y03_eyes");
        cmd(t + 180, "kill @e[type=!player]");
        cmd(t + 185, "herobrine trigger crowd");
        shot(t + 215, "y04_crowd");
        cmd(t + 220, "kill @e[type=!player]");
        cmd(t + 222, "time set 6000");
        cmd(t + 225, "herobrine trigger human_camp");
        cmd(t + 226, "herobrine trigger human_pillar");
        cmd(t + 230, "tp @s ~ ~ ~ 180 8");
        shot(t + 260, "y05_traces");
        STEPS.add(new Step(t + 262, mc -> mc.options.showSubtitles().set(subtitles)));
        cmd(t + 265, "herobrine trigger kick");
        shot(t + 330, "y06_kick");
        STEPS.add(new Step(t + 360, mc -> {
            HerobrineMyth.LOGGER.info("[selftest] done");
            mc.stop();
        }));
    }

    /** 1.3: old textures and light, Steve's hand, blood rain, black eyes, a shape at night. */
    private static void v13Steps() {
        int t = 120;
        cmd(t - 12, "gamerule sendCommandFeedback false");
        STEPS.add(new Step(t - 11, mc -> {
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.getToasts().clear();
        }));
        cmd(t - 10, "gamemode creative");
        cmd(t - 9, "tp @s ~ 101 ~ 0 0");
        cmd(t - 8, "fill ~-40 ~-1 ~-40 ~40 ~-1 ~40 grass_block");
        cmd(t - 7, "place feature minecraft:oak ~3 ~ ~14");
        cmd(t - 6, "place feature minecraft:oak ~-6 ~ ~18");
        cmd(t - 5, "fill ~-4 ~ ~6 ~-3 ~2 ~6 cobblestone");
        cmd(t - 4, "fill ~-2 ~ ~6 ~-1 ~2 ~6 oak_planks");
        cmd(t - 3, "fill ~0 ~ ~6 ~1 ~2 ~6 bricks");
        cmd(t - 2, "fill ~2 ~ ~6 ~3 ~2 ~6 stone");
        cmd(t - 1, "setblock ~4 ~ ~6 crafting_table");
        cmd(t, "setblock ~5 ~ ~6 furnace");
        cmd(t + 1, "gamerule doDaylightCycle false");
        cmd(t + 2, "time set 6000");
        cmd(t + 3, "weather clear");
        cmd(t + 4, "tp @s ~ ~ ~ 0 10");
        shot(t + 50, "x01_old_textures");
        cmd(t + 60, "herobrine trigger blood_rain");
        shot(t + 160, "x02_blood_rain");
        cmd(t + 170, "weather clear");
        cmd(t + 175, "time set 18000");
        cmd(t + 180, "summon herobrinemyth:herobrine ^1 ^ ^12 {HBMode:\"ECHO\",HBLife:600}");
        shot(t + 210, "x03_night");
        cmd(t + 215, "kill @e[type=herobrinemyth:herobrine]");
        cmd(t + 220, "herobrine jumpscare");
        shot(t + 232, "x04_black_eyes");
        STEPS.add(new Step(t + 260, mc -> {
            HerobrineMyth.LOGGER.info("[selftest] done");
            mc.stop();
        }));
    }

    /** 1.2: darker day, glimpse, tree hider, the new eyes. */
    private static void v12Steps() {
        int t = 120;
        cmd(t - 12, "gamerule sendCommandFeedback false");
        STEPS.add(new Step(t - 11, mc -> {
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.getToasts().clear();
        }));
        cmd(t - 10, "gamemode creative");
        cmd(t - 9, "tp @s ~ 101 ~ 0 0");
        cmd(t - 8, "fill ~-60 ~-1 ~-60 ~60 ~-1 ~60 grass_block");
        cmd(t - 7, "place feature minecraft:oak ~3 ~ ~16");
        cmd(t - 6, "place feature minecraft:oak ~-6 ~ ~20");
        cmd(t - 5, "place feature minecraft:oak ~9 ~ ~25");
        cmd(t - 4, "gamerule doDaylightCycle false");
        cmd(t - 3, "time set 6000");
        cmd(t - 2, "weather clear");
        shot(t + 40, "w01_darker_day");
        cmd(t + 45, "herobrine trigger tree");
        shot(t + 60, "w02_tree");
        cmd(t + 80, "herobrine trigger glimpse");
        shot(t + 90, "w03_glimpse");
        cmd(t + 100, "kill @e[type=herobrinemyth:herobrine]");
        cmd(t + 110, "herobrine jumpscare");
        shot(t + 116, "w04_eyes_a");
        shot(t + 124, "w05_eyes_b");
        shot(t + 136, "w06_eyes_c");
        cmd(t + 160, "herobrine jumpscare");
        shot(t + 170, "w07_eyes_d");
        STEPS.add(new Step(t + 200, mc -> mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen())));
        STEPS.add(new Step(t + 205, mc -> {
            HerobrineMyth.LOGGER.info("[selftest] done");
            mc.stop();
        }));
    }

    private static void STEVE_TAB(int at) {
        STEPS.add(new Step(at, mc -> mc.options.keyPlayerList.setDown(true)));
        shot(at + 5, "v04_tab_stranger");
        STEPS.add(new Step(at + 10, mc -> mc.options.keyPlayerList.setDown(false)));
    }

    /** A few pictures for the README, then the crash screen. */
    private static void promoSteps() {
        int t = 120;
        cmd(t - 12, "gamerule sendCommandFeedback false");
        STEPS.add(new Step(t - 11, mc -> {
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.getToasts().clear();
        }));
        cmd(t - 10, "gamemode creative");
        cmd(t - 9, "tp @s ~ 101 ~ 0 0");
        cmd(t - 8, "fill ~-60 ~-1 ~-60 ~60 ~-1 ~60 grass_block");
        cmd(t - 7, "place feature minecraft:oak ~8 ~ ~14");
        cmd(t - 6, "place feature minecraft:oak ~-9 ~ ~20");
        cmd(t - 5, "gamerule doDaylightCycle false");
        cmd(t - 4, "gamerule doMobSpawning false");
        cmd(t - 3, "time set 18000");
        cmd(t - 2, "weather clear");
        cmd(t + 40, "herobrine jumpscare");
        shot(t + 42, "p01_jumpscare");
        cmd(t + 70, "herobrine fx 5 60 1");
        shot(t + 75, "p02_static");
        cmd(t + 120, "herobrine fx 12 1 0");
        cmd(t + 125, "summon herobrinemyth:herobrine ^3 ^ ^14 {HBMode:\"ECHO\",HBLife:600}");
        cmd(t + 126, "summon herobrinemyth:herobrine ^-6 ^ ^20 {HBMode:\"ECHO\",HBLife:600,HBEyes:0b}");
        shot(t + 150, "p03_two_of_them");
        cmd(t + 160, "kill @e[type=herobrinemyth:herobrine]");
        cmd(t + 165, "herobrine trigger eyes");
        cmd(t + 166, "herobrine trigger eyes");
        cmd(t + 167, "herobrine trigger eyes");
        cmd(t + 168, "tp @s ~ ~ ~ 180 0");
        shot(t + 190, "p04_eyes");
        cmd(t + 195, "herobrine fx 0 200 1");
        cmd(t + 200, "summon lightning_bolt ~6 ~ ~6");
        cmd(t + 240, "herobrine trigger crash");
    }

    private static void classicSteps() {
        int t = 200;
        cmd(t - 12, "gamerule sendCommandFeedback false");
        STEPS.add(new Step(t - 11, mc -> {
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.getToasts().clear();
        }));
        cmd(t - 10, "gamemode creative");
        cmd(t - 9, "tp @s ~ 101 ~ 0 0");
        cmd(t - 8, "fill ~-60 ~-1 ~-60 ~60 ~-1 ~60 grass_block");
        cmd(t - 7, "place feature minecraft:oak ~8 ~ ~14");
        cmd(t - 6, "place feature minecraft:oak ~-9 ~ ~20");
        cmd(t - 5, "gamerule doDaylightCycle false");
        cmd(t - 4, "gamerule doMobSpawning false");
        cmd(t - 3, "time set 6000");
        cmd(t - 2, "weather clear");
        shot(t + 40, "c00_classic_textures");
        STEPS.add(new Step(t + 45, mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT)));
        shot(t + 60, "c01_steve");
        STEPS.add(new Step(t + 65, mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON)));
        cmd(t + 70, "herobrine fx 5 60 1");
        shot(t + 80, "c02_tv_static");
        cmd(t + 140, "time set 18000");
        cmd(t + 145, "summon herobrinemyth:herobrine ^ ^ ^12 {HBMode:\"ECHO\",HBLife:400,HandItems:[{id:\"minecraft:redstone_torch\",Count:1b},{}]}");
        shot(t + 175, "c03_ghost_torch");
        cmd(t + 180, "kill @e[type=herobrinemyth:herobrine]");
        cmd(t + 185, "time set 6000");
        cmd(t + 190, "herobrine build temple");
        for (int i = 0; i < 4; i++) {
            cmd(t + 200 + i * 20, "tp @s ~ ~ ~ " + (i * 90) + " 0");
            shot(t + 215 + i * 20, "c04_temple_" + i);
        }
        cmd(t + 290, "kill @e[type=zombie]");
        cmd(t + 300, "herobrine trigger heads");
        cmd(t + 302, "tp @s ~ ~ ~ 0 30");
        shot(t + 310, "c05_heads");
        cmd(t + 330, "herobrine trigger graveyard");
        shot(t + 400, "c06_graveyard_arrive");
        shot(t + 560, "c06_graveyard_closer");
        shot(t + 660, "c06_graveyard_end");
        shot(t + 760, "c07_back");
        STEPS.add(new Step(t + 790, mc -> {
            HerobrineMyth.LOGGER.info("[selftest] done");
            mc.stop();
        }));
    }

    private static void normalSteps() {
        int t = 200;
        cmd(t - 12, "gamerule sendCommandFeedback false");
        STEPS.add(new Step(t - 11, mc -> {
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.getToasts().clear();
        }));
        cmd(t - 10, "gamemode creative");
        cmd(t - 9, "tp @s ~ 101 ~ 0 0");
        cmd(t - 8, "fill ~-60 ~-1 ~-60 ~60 ~-1 ~60 grass_block");
        cmd(t - 7, "place feature minecraft:oak ~8 ~ ~14");
        cmd(t - 6, "place feature minecraft:oak ~-9 ~ ~20");
        cmd(t - 5, "place feature minecraft:birch ~14 ~ ~-6");
        cmd(t - 4, "place feature minecraft:oak ~-15 ~ ~-12");
        cmd(t - 3, "place feature minecraft:oak ~22 ~ ~25");
        cmd(t, "gamerule doDaylightCycle false");
        cmd(t + 1, "gamerule doMobSpawning false");
        cmd(t + 2, "gamerule doWeatherCycle false");
        cmd(t + 3, "weather clear");
        cmd(t + 4, "time set 6000");
        cmd(t + 5, "tp @s ~ ~ ~ 0 0");
        shot(t + 40, "00_world");
        cmd(t + 45, "summon herobrinemyth:herobrine ^ ^ ^5 {HBMode:\"SHRINE\",HBLife:2000}");
        shot(t + 75, "01_herobrine_day");
        cmd(t + 80, "time set 18000");
        shot(t + 130, "02_herobrine_night");
        cmd(t + 135, "kill @e[type=herobrinemyth:herobrine]");
        cmd(t + 140, "summon herobrinemyth:herobrine ^ ^ ^4 {HBMode:\"WATCH\",HBLife:2000,HBLook:100000}");
        cmd(t + 141, "summon herobrinemyth:herobrine ^2 ^ ^7 {HBMode:\"CLONE\",HBLife:2000,HBLook:100000}");
        cmd(t + 142, "summon herobrinemyth:herobrine ^-2 ^ ^9 {HBMode:\"DARK\",HBLife:2000,HBLook:100000}");
        shot(t + 170, "03_watchers");
        cmd(t + 175, "kill @e[type=herobrinemyth:herobrine]");
        cmd(t + 180, "summon zombie ^1 ^ ^4 {NoAI:1b}");
        cmd(t + 181, "summon skeleton ^-1 ^ ^4 {NoAI:1b}");
        cmd(t + 190, "herobrine trigger possess_mobs");
        shot(t + 215, "04_possessed");
        cmd(t + 220, "kill @e[type=!player]");
        cmd(t + 225, "herobrine trigger eyes");
        cmd(t + 226, "herobrine trigger eyes");
        cmd(t + 227, "herobrine trigger eyes");
        cmd(t + 230, "tp @s ~ ~ ~ 180 0");
        shot(t + 260, "05_eyes_back");
        cmd(t + 262, "tp @s ~ ~ ~ 0 0");
        shot(t + 280, "05_eyes_front");
        cmd(t + 285, "herobrine fx 1 300 1");
        shot(t + 400, "06_fog");
        cmd(t + 405, "herobrine fx 12 1 0");
        cmd(t + 420, "herobrine jumpscare");
        shot(t + 425, "07_jumpscare");
        cmd(t + 460, "herobrine fx 5 80 1");
        shot(t + 470, "08_glitch");
        cmd(t + 520, "herobrine trigger clones");
        cmd(t + 530, "tp @s ~ ~ ~ 90 0");
        shot(t + 545, "09_clones_a");
        cmd(t + 550, "tp @s ~ ~ ~ 270 0");
        shot(t + 565, "09_clones_b");
        cmd(t + 570, "kill @e[type=herobrinemyth:herobrine]");
        cmd(t + 575, "herobrine build face");
        cmd(t + 576, "herobrine build pyramid");
        cmd(t + 577, "herobrine build cross");
        cmd(t + 578, "herobrine build grave");
        cmd(t + 580, "time set 6000");
        for (int i = 0; i < 4; i++) {
            cmd(t + 590 + i * 20, "tp @s ~ ~ ~ " + (i * 90) + " 0");
            shot(t + 605 + i * 20, "10_structures_" + i);
        }
        cmd(t + 700, "herobrine trigger chat");
        cmd(t + 701, "herobrine trigger joined");
        cmd(t + 702, "herobrine trigger whisper");
        shot(t + 720, "11_chat");
        cmd(t + 722, "tp @s ~ ~ ~ 0 10");
        cmd(t + 723, "fill ~-1 ~ ~5 ~1 ~ ~7 gold_block");
        cmd(t + 724, "setblock ~ ~1 ~6 mossy_cobblestone");
        cmd(t + 725, "setblock ~1 ~1 ~6 redstone_torch");
        cmd(t + 726, "setblock ~-1 ~1 ~6 redstone_torch");
        cmd(t + 727, "setblock ~ ~1 ~5 redstone_torch");
        cmd(t + 728, "setblock ~ ~1 ~7 redstone_torch");
        cmd(t + 729, "setblock ~ ~2 ~6 netherrack");
        cmd(t + 730, "setblock ~ ~3 ~6 fire");
        shot(t + 734, "12_totem_built");
        cmd(t + 735, "herobrine totem activate");
        shot(t + 790, "12_totem_lightning");
        shot(t + 842, "12_totem_scare");
        shot(t + 900, "12_totem_after");
        cmd(t + 940, "tp @s ~ ~ ~ 0 0");
        cmd(t + 950, "herobrine trigger corrupt");
        cmd(t + 951, "herobrine trigger chunk");
        shot(t + 1100, "13_corruption");
        cmd(t + 1110, "herobrine totem reset");
        cmd(t + 1111, "herobrine calm");
        STEPS.add(new Step(t + 1140, mc -> {
            HerobrineMyth.LOGGER.info("[selftest] done");
            mc.stop();
        }));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (MODE.equals("setup")) {
            setupTicks++;
            if (setupTicks == 60) net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(mc, mc.screen);
            if (setupTicks == 160) pendingShot = "w00_setup_screen";
            if (setupTicks == 200) mc.stop();
            return;
        }
        if ((MODE.equals("v13") || MODE.equals("v14")) && !started) {
            // wait until the old textures pack has been built and switched on
            if (!java.nio.file.Files.exists(ClassicPack.dir(mc).resolve("herobrinemyth-pack-v1")) || ++packWait < 200) return;
        }
        if (!started && mc.screen instanceof TitleScreen) {
            started = true;
            if (mc.getLevelSource().levelExists(WORLD)) {
                mc.createWorldOpenFlows().loadLevel(mc.screen, WORLD);
            } else {
                LevelSettings settings = new LevelSettings(WORLD, GameType.SURVIVAL, false, Difficulty.NORMAL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(666L, true, false),
                        reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions());
            }
            return;
        }
        if (ENDING && (mc.screen instanceof Endings.CrashScreen || mc.screen instanceof Endings.TakenScreen)) {
            endingTicks++;
            if (endingTicks == 110 || endingTicks == 240) {
                Screenshot.grab(mc.gameDirectory, "selftest_ending_" + MODE + "_" + endingTicks + ".png", mc.getMainRenderTarget(),
                        c -> HerobrineMyth.LOGGER.info("[selftest] {}", c.getString()));
            }
            if (endingTicks == 250) {
                HerobrineMyth.LOGGER.info("[selftest] ending done");
                mc.stop();
            }
            return;
        }
        if (MODE.equals("v14") && mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen) {
            endingTicks++;
            if (endingTicks == 40) pendingShot = "y06_kick";
            if (endingTicks == 80) mc.stop();
            return;
        }
        if (mc.player == null || mc.level == null) return;
        ticks++;
        for (Step s : STEPS) {
            if (s.at == ticks) {
                try {
                    s.run.accept(mc);
                } catch (Exception e) {
                    HerobrineMyth.LOGGER.error("[selftest] step failed", e);
                }
            }
        }
    }

    private SelfTest() {}
}
