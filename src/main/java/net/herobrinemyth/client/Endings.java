package net.herobrinemyth.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.herobrinemyth.HerobrineMyth;
import net.herobrinemyth.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The "crash" and the end. The world is always saved and closed properly first:
 * nothing here ever corrupts a save. What happens to it afterwards is up to the config.
 */
public final class Endings {

    /** Save, leave the world, show the crash. */
    static void crash() {
        Config.CrashMode mode = Config.CRASH_MODE.get();
        if (mode == Config.CrashMode.OFF) return;
        Minecraft mc = Minecraft.getInstance();
        leaveWorld(mc);
        String report = writeCrashReport(mc);
        mc.setScreen(new CrashScreen(report, mode == Config.CrashMode.CLOSE_GAME));
    }

    /** Herobrine takes the world. */
    static void takeWorld() {
        Config.DeletionMode mode = Config.DELETION_MODE.get();
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (mode == Config.DeletionMode.OFF || server == null) return;
        Path dir = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        String name = server.getWorldData().getLevelName();
        leaveWorld(mc);
        mc.setScreen(new TakenScreen(dir, name, mode));
    }

    private static void leaveWorld(Minecraft mc) {
        ClientHorror.clear();
        boolean local = mc.isLocalServer();
        if (mc.level != null) mc.level.disconnect();
        if (local) mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        else mc.clearLevel();
    }

    private static String writeCrashReport(Minecraft mc) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String text = String.join("\n",
                "---- Minecraft Crash Report ----",
                "// You should not have built it.",
                "",
                "Time: " + time,
                "Description: Ticking player",
                "",
                "java.lang.IllegalStateException: Herobrine",
                "\tat net.minecraft.world.entity.player.Player.tick(Player.java:666)",
                "\tat net.minecraft.server.level.ServerPlayer.doTick(ServerPlayer.java:666)",
                "\tat net.minecraft.server.level.ServerLevel.tickNonPassenger(ServerLevel.java:666)",
                "\tat net.minecraft.world.level.Level.guardEntityTick(Level.java:666)",
                "\tat herobrine.Herobrine.watch(Herobrine.java:1)",
                "\tat herobrine.Herobrine.watch(Herobrine.java:1)",
                "\tat herobrine.Herobrine.watch(Herobrine.java:1)",
                "",
                "A detailed walkthrough of the error, its code path and all known details is as follows:",
                "---------------------------------------------------------------------------------------",
                "",
                "-- Head --",
                "Thread: Render thread",
                "Stacktrace:",
                "\tHe is in the walls.",
                "",
                "-- Player being ticked --",
                "Details:",
                "\tEntity Type: herobrine (net.minecraft.world.entity.player.Player)",
                "\tEntity Name: " + mc.getUser().getName(),
                "\tEntity's Block location: behind you",
                "",
                "-- System Details --",
                "\tMinecraft Version: 1.20.1",
                "\tHerobrine: present",
                "\tHerobrine: present",
                "\tHerobrine: present",
                "");
        try {
            Path dir = mc.gameDirectory.toPath().resolve("crash-reports");
            Files.createDirectories(dir);
            String file = "crash-" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) + "-client.txt";
            Files.writeString(dir.resolve(file), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            HerobrineMyth.LOGGER.debug("Could not write crash report", e);
        }
        return text;
    }

    // ================================================================== screens

    static final class CrashScreen extends Screen {
        private final List<String> lines = new ArrayList<>();
        private final boolean closeGame;
        private int ticks;

        CrashScreen(String report, boolean closeGame) {
            super(Component.literal("Minecraft"));
            this.closeGame = closeGame;
            for (String s : report.split("\n")) lines.add(s.replace("\t", "    "));
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }

        @Override
        public void tick() {
            ticks++;
            if (ticks == 120 && !closeGame) {
                this.addRenderableWidget(Button.builder(Component.translatable("gui.toTitle"),
                        b -> this.minecraft.setScreen(new TitleScreen())).bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
            }
            if (closeGame && ticks == 170) this.minecraft.stop();
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
            g.fill(0, 0, this.width, this.height, 0xFF000000);
            int y = 10;
            boolean corrupt = ticks > 70;
            for (String s : lines) {
                Component c = Component.literal(s);
                if (corrupt && ClientHorror.RNG.nextInt(Math.max(2, 110 - ticks)) == 0)
                    c = Component.literal(s).withStyle(ChatFormatting.OBFUSCATED);
                int col = s.startsWith("----") || s.startsWith("java.") ? 0xFFFF5555 : 0xFFAAAAAA;
                g.drawString(this.font, c, 10, y, col, false);
                y += 10;
                if (y > this.height - 40) break;
            }
            if (ticks > 95) {
                float a = Math.min(1F, (ticks - 95) / 40F);
                int size = (int) (Math.min(this.width, this.height) * 0.35F);
                int x = this.width - size - 20, yy = this.height - size - 40;
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(0.25F, 0.25F, 0.25F, a);
                g.blit(EyeQuads.STEVE, x, yy, size, size, 8, 8, 8, 8, 64, 64);
                RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
                float px = size / 8F;
                int white = ((int) (a * 255) << 24) | 0xFFFFFF;
                g.fill(x + (int) px, yy + (int) (4 * px), x + (int) (3 * px), yy + (int) (5 * px), white);
                g.fill(x + (int) (5 * px), yy + (int) (4 * px), x + (int) (7 * px), yy + (int) (5 * px), white);
                RenderSystem.disableBlend();
            }
            super.render(g, mouseX, mouseY, partial);
        }
    }

    static final class TakenScreen extends Screen {
        private final Path dir;
        private final String name;
        private final Config.DeletionMode mode;
        private int ticks;
        private volatile boolean done;
        private volatile boolean failed;
        private boolean started;

        TakenScreen(Path dir, String name, Config.DeletionMode mode) {
            super(Component.literal(name));
            this.dir = dir;
            this.name = name;
            this.mode = mode;
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }

        @Override
        public void tick() {
            ticks++;
            if (!started && ticks > 40) {
                started = true;
                Thread t = new Thread(this::work, "Herobrine");
                t.setDaemon(true);
                t.start();
            }
            if (done && ticks > 200 && this.children().isEmpty()) {
                this.addRenderableWidget(Button.builder(Component.translatable("gui.toTitle"),
                        b -> this.minecraft.setScreen(new TitleScreen())).bounds(this.width / 2 - 100, this.height / 2 + 60, 200, 20).build());
            }
        }

        private void work() {
            try {
                Path saves = this.minecraft.gameDirectory.toPath().resolve("saves").toAbsolutePath().normalize();
                // never touch anything that is not a world folder inside saves
                if (!dir.startsWith(saves) || dir.equals(saves) || !Files.exists(dir.resolve("level.dat"))) {
                    failed = true;
                    return;
                }
                for (int attempt = 0; attempt < 10; attempt++) {
                    try {
                        if (mode == Config.DeletionMode.DELETE) deleteTree(dir);
                        else hide();
                        break;
                    } catch (IOException e) {
                        if (attempt == 9) throw e;
                        Thread.sleep(500);
                    }
                }
            } catch (Exception e) {
                HerobrineMyth.LOGGER.error("Herobrine could not take the world", e);
                failed = true;
            } finally {
                done = true;
            }
        }

        private void hide() throws IOException {
            Path vault = this.minecraft.gameDirectory.toPath().resolve("herobrine_took_your_worlds");
            Files.createDirectories(vault);
            String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            Path target = vault.resolve(dir.getFileName().toString() + "_" + stamp);
            Files.move(dir, target);
            Files.writeString(vault.resolve("README.txt"), String.join("\n",
                    "He took your worlds. They are here.",
                    "",
                    "To get a world back, move its folder into your 'saves' folder.",
                    "To stop this from happening, set worldDeletionMode = \"OFF\" in config/herobrinemyth-common.toml",
                    ""), StandardCharsets.UTF_8);
        }

        private static void deleteTree(Path root) throws IOException {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
            g.fill(0, 0, this.width, this.height, 0xFF000000);
            int cx = this.width / 2, cy = this.height / 2;
            float progress = Math.min(1F, ticks / 180F);
            if (!done || ticks <= 200) {
                Component title = Component.translatable("herobrine.taken.deleting", name);
                g.drawCenteredString(this.font, title, cx, cy - 30, 0xFFFFFFFF);
                int bw = 200;
                g.fill(cx - bw / 2 - 1, cy - 1, cx + bw / 2 + 1, cy + 9, 0xFF808080);
                g.fill(cx - bw / 2, cy, cx + bw / 2, cy + 8, 0xFF000000);
                g.fill(cx - bw / 2, cy, cx - bw / 2 + (int) (bw * progress), cy + 8, 0xFF8A0000);
                if (ClientHorror.RNG.nextInt(8) == 0) {
                    g.drawCenteredString(this.font, Component.literal("HEROBRINE").withStyle(ChatFormatting.OBFUSCATED),
                            cx + ClientHorror.RNG.nextInt(200) - 100, cy + 20 + ClientHorror.RNG.nextInt(40), 0x60FFFFFF);
                }
            } else {
                Component a = failed ? Component.translatable("herobrine.taken.failed")
                        : Component.translatable(mode == Config.DeletionMode.DELETE ? "herobrine.taken.gone" : "herobrine.taken.hidden", name);
                g.drawCenteredString(this.font, a, cx, cy - 20, 0xFFAA0000);
                g.drawCenteredString(this.font, Component.translatable("herobrine.taken.sub"), cx, cy, 0xFF777777);
            }
            super.render(g, mouseX, mouseY, partial);
        }
    }

    private Endings() {}
}
