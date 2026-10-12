package net.kasax.bobbot.build;

import net.fabricmc.loader.api.FabricLoader;
import net.kasax.bobbot.BobBot;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Where blueprints are found: Litematica's own folder ({@code schematics}), WorldEdit's
 * ({@code config/worldedit/schematics}), and Bob's ({@code config/bobbot/blueprints}, where copies
 * from the world go). Read once, kept while the file stays the same.
 */
public final class Blueprints {
    private Blueprints() {
    }

    private record Cached(Blueprint bp, long modified) {
    }

    private static final Map<Path, Cached> CACHE = new java.util.HashMap<>();

    public static List<Path> folders() {
        Path game = FabricLoader.getInstance().getGameDir(), config = FabricLoader.getInstance().getConfigDir();
        return List.of(own(), game.resolve("schematics"), config.resolve("worldedit").resolve("schematics"));
    }

    /** Bob's own folder (copies from the world are saved here). */
    public static Path own() {
        return FabricLoader.getInstance().getConfigDir().resolve("bobbot").resolve("blueprints");
    }

    private static boolean readable(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".litematic") || n.endsWith(".nbt") || n.endsWith(".schem") || n.endsWith(".schematic");
    }

    /** Every blueprint there is, by name (lower case, without the ending); the first folder wins a name. */
    public static Map<String, Path> all() {
        Map<String, Path> out = new TreeMap<>();
        for (Path folder : folders()) {
            if (!Files.isDirectory(folder)) continue;
            try (Stream<Path> files = Files.walk(folder, 2)) {
                files.filter(Files::isRegularFile).filter(Blueprints::readable).forEach(p -> {
                    String name = p.getFileName().toString().replaceAll("\\.[^.]+$", "").toLowerCase(Locale.ROOT).replace(' ', '_');
                    out.putIfAbsent(name, p);
                });
            } catch (IOException e) {
                BobBot.LOG.warn("[Bob] cannot read {}", folder, e);
            }
        }
        return out;
    }

    /** The blueprint of that name (or whose name starts so), read or from the cache; null if none. */
    public static Blueprint get(MinecraftServer server, String name) {
        String key = name.toLowerCase(Locale.ROOT).replace(' ', '_');
        Map<String, Path> all = all();
        Path p = all.get(key);
        if (p == null) {
            List<String> starts = new ArrayList<>();
            for (String n : all.keySet()) if (n.startsWith(key) || n.contains(key)) starts.add(n);
            if (starts.size() == 1) p = all.get(starts.get(0));
        }
        // ("a house of wood": one of the words is the name.)
        if (p == null) {
            for (String word : key.split("[_\\s]+")) {
                if (word.length() < 3) continue;
                if (all.containsKey(word)) {
                    p = all.get(word);
                    break;
                }
            }
        }
        if (p == null) return null;
        try {
            long modified = Files.getLastModifiedTime(p).toMillis();
            Cached c = CACHE.get(p);
            if (c != null && c.modified() == modified) return c.bp();
            Blueprint bp = Blueprint.read(server, p);
            CACHE.put(p, new Cached(bp, modified));
            return bp;
        } catch (IOException | RuntimeException e) {
            BobBot.LOG.warn("[Bob] cannot read blueprint {}", p, e);
            return null;
        }
    }

    public static Path savePath(String name) {
        return own().resolve(name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_") + ".nbt");
    }
}
