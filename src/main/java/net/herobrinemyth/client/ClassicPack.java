package net.herobrinemyth.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.herobrinemyth.HerobrineMyth;
import net.herobrinemyth.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The old textures, made on the player's own computer.
 *
 * Nothing from Mojang ships inside this mod. The pack is built from Minecraft Beta and 1.7.10 that the
 * player already has installed (official launcher, Prism, MultiMC), or, if they are missing, from the
 * same official Mojang servers the launcher downloads them from. The result is a normal resource pack
 * in the player's resourcepacks folder, switched on automatically.
 */
public final class ClassicPack {
    static final String FOLDER = "HerobrineTheMyth-OldTextures";
    private static final String STAMP = "herobrinemyth-pack-v1";
    private static volatile boolean running;

    static Path dir(Minecraft mc) {
        return mc.gameDirectory.toPath().resolve("resourcepacks").resolve(FOLDER);
    }

    /** Called once on the title screen. Builds the pack if needed, then selects it. */
    static void ensure(Minecraft mc) {
        if (!Config.CLASSIC_PACK.get() || running) return;
        Path dir = dir(mc);
        if (Files.exists(dir.resolve(STAMP))) {
            select(mc);
            return;
        }
        running = true;
        Thread t = new Thread(() -> {
            try {
                build(mc, dir);
                mc.execute(() -> select(mc));
            } catch (Exception e) {
                HerobrineMyth.LOGGER.warn("Could not build the old textures pack: {}", e.toString());
            } finally {
                running = false;
            }
        }, "Herobrine old textures");
        t.setDaemon(true);
        t.start();
    }

    private static void select(Minecraft mc) {
        PackRepository repo = mc.getResourcePackRepository();
        repo.reload();
        String id = "file/" + FOLDER;
        if (!repo.getAvailableIds().contains(id) || repo.getSelectedIds().contains(id)) return;
        List<String> selected = new ArrayList<>(repo.getSelectedIds());
        int at = Math.max(selected.indexOf("programmer_art"), selected.indexOf("vanilla"));
        selected.add(at < 0 ? selected.size() : at + 1, id);
        repo.setSelected(selected);
        mc.options.updateResourcePacks(repo);
        HerobrineMyth.LOGGER.info("Old textures enabled.");
    }

    // ------------------------------------------------------------------ building

    private static void build(Minecraft mc, Path dir) throws Exception {
        Path cache = mc.gameDirectory.toPath().resolve("herobrinemyth").resolve("cache");
        Files.createDirectories(cache);
        Path release = jar("1.7.10", cache);
        Path beta = jar("b1.8.1", cache);
        if (beta == null) beta = jar("b1.7.3", cache);
        if (release == null && beta == null) throw new IOException("no old game files found or downloadable");

        Path tex = dir.resolve("assets/minecraft/textures");
        Files.createDirectories(tex.resolve("block"));
        Files.createDirectories(tex.resolve("item"));
        Files.writeString(dir.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":15,\"description\":\"Herobrine: The Myth - old textures (made from your own game files)\"}}",
                StandardCharsets.UTF_8);

        List<String[]> modern = new ArrayList<>(), tiles = new ArrayList<>();
        try (InputStream in = ClassicPack.class.getResourceAsStream("/assets/herobrinemyth/classic/texture_map.txt");
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] p = line.trim().split(" ");
                if (p[0].equals("beta")) tiles.add(new String[]{p[1], p[2]});
                else modern.add(p);
            }
        }

        int copied = 0;
        // 1.7.10 first...
        if (release != null) {
            try (ZipFile zip = new ZipFile(release.toFile())) {
                for (String[] m : modern) {
                    ZipEntry e = zip.getEntry("assets/minecraft/textures/" + m[0] + ".png");
                    if (e == null) continue;
                    try (InputStream in = zip.getInputStream(e)) {
                        Files.copy(in, tex.resolve(m[1] + ".png"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        copied++;
                    }
                }
            }
        }
        // ...then the Beta ones on top, for the blocks that matter most
        if (beta != null) {
            try (ZipFile zip = new ZipFile(beta.toFile())) {
                ZipEntry e = zip.getEntry("terrain.png");
                if (e != null) {
                    try (InputStream in = zip.getInputStream(e); NativeImage atlas = NativeImage.read(in)) {
                        int size = atlas.getWidth() / 16;
                        for (String[] t : tiles) {
                            int i = Integer.parseInt(t[0]);
                            int x0 = (i % 16) * size, y0 = (i / 16) * size;
                            try (NativeImage tile = new NativeImage(size, size, false)) {
                                for (int y = 0; y < size; y++)
                                    for (int x = 0; x < size; x++) tile.setPixelRGBA(x, y, atlas.getPixelRGBA(x0 + x, y0 + y));
                                tile.writeToFile(tex.resolve(t[1] + ".png"));
                                copied++;
                            }
                        }
                    }
                }
            }
        }
        // the old "oof"
        byte[] hurt = asset("1.7.10", "minecraft/sounds/random/classic_hurt.ogg", cache);
        if (hurt != null) {
            Path snd = dir.resolve("assets/minecraft/sounds/herobrinemyth");
            Files.createDirectories(snd);
            Files.write(snd.resolve("classic_hurt.ogg"), hurt);
            String entry = "{\"replace\":true,\"sounds\":[\"herobrinemyth/classic_hurt\"]}";
            Files.writeString(dir.resolve("assets/minecraft/sounds.json"), "{"
                    + "\"entity.player.hurt\":" + entry + ",\"entity.player.hurt_on_fire\":" + entry
                    + ",\"entity.player.hurt_drown\":" + entry + ",\"entity.player.hurt_freeze\":" + entry
                    + ",\"entity.player.hurt_sweet_berry_bush\":" + entry + ",\"entity.player.death\":" + entry + "}", StandardCharsets.UTF_8);
        }
        Files.writeString(dir.resolve(STAMP), "made by Herobrine: The Myth from your own game files", StandardCharsets.UTF_8);
        HerobrineMyth.LOGGER.info("Old textures pack built: {} textures{}", copied, hurt != null ? " + the old hurt sound" : "");
    }

    // ------------------------------------------------------------------ where the old game files are

    private static List<Path> roots() {
        List<Path> r = new ArrayList<>();
        String appdata = System.getenv("APPDATA");
        String home = System.getProperty("user.home");
        if (appdata != null) {
            r.add(Path.of(appdata, ".minecraft"));
            r.add(Path.of(appdata, "PrismLauncher"));
            r.add(Path.of(appdata, "MultiMC"));
        }
        if (home != null) {
            r.add(Path.of(home, ".minecraft"));
            r.add(Path.of(home, "Library", "Application Support", "minecraft"));
            r.add(Path.of(home, ".local", "share", "PrismLauncher"));
        }
        return r;
    }

    /** A client jar of that version: installed somewhere, or downloaded from Mojang into the cache. */
    private static Path jar(String version, Path cache) {
        for (Path root : roots()) {
            Path[] candidates = {
                    root.resolve("versions").resolve(version).resolve(version + ".jar"),
                    root.resolve("libraries/com/mojang/minecraft").resolve(version).resolve("minecraft-" + version + "-client.jar")
            };
            for (Path c : candidates) if (Files.isRegularFile(c)) return c;
        }
        Path cached = cache.resolve(version + ".jar");
        if (Files.isRegularFile(cached)) return cached;
        try {
            JsonObject v = versionJson(version);
            if (v == null) return null;
            String url = v.getAsJsonObject("downloads").getAsJsonObject("client").get("url").getAsString();
            Files.write(cached, get(url));
            return cached;
        } catch (Exception e) {
            return null;
        }
    }

    /** One file of the old game assets: from an installed launcher, or from Mojang's resource server. */
    private static byte[] asset(String version, String name, Path cache) {
        try {
            String hash = null;
            for (Path root : roots()) {
                Path idx = root.resolve("assets/indexes").resolve(version + ".json");
                if (!Files.isRegularFile(idx)) continue;
                JsonObject objects = JsonParser.parseString(Files.readString(idx)).getAsJsonObject().getAsJsonObject("objects");
                if (objects.has(name)) {
                    hash = objects.getAsJsonObject(name).get("hash").getAsString();
                    Path obj = root.resolve("assets/objects").resolve(hash.substring(0, 2)).resolve(hash);
                    if (Files.isRegularFile(obj)) return Files.readAllBytes(obj);
                }
            }
            if (hash == null) {
                JsonObject v = versionJson(version);
                if (v == null) return null;
                JsonObject idx = JsonParser.parseString(new String(get(v.getAsJsonObject("assetIndex").get("url").getAsString()),
                        StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("objects");
                if (!idx.has(name)) return null;
                hash = idx.getAsJsonObject(name).get("hash").getAsString();
            }
            return get("https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash);
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonObject versionJson(String version) throws Exception {
        JsonObject manifest = JsonParser.parseString(new String(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        for (var e : manifest.getAsJsonArray("versions")) {
            JsonObject o = e.getAsJsonObject();
            if (o.get("id").getAsString().equals(version))
                return JsonParser.parseString(new String(get(o.get("url").getAsString()), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        return null;
    }

    private static byte[] get(String url) throws Exception {
        HttpClient c = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpResponse<byte[]> r = c.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "herobrinemyth").build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode());
        return r.body();
    }

    private ClassicPack() {}
}
