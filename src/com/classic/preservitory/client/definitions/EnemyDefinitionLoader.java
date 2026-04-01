package com.classic.preservitory.client.definitions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads {@code cache/enemies/*.json} and returns enemy definitions keyed by int id. */
public final class EnemyDefinitionLoader {

    private static final Pattern ID_PATTERN      = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern NAME_PATTERN    = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MAX_HP_PATTERN  = Pattern.compile("\"maxHp\"\\s*:\\s*(\\d+)");
    private static final Pattern ATTACK_PATTERN  = Pattern.compile("\"attack\"\\s*:\\s*(\\d+)");
    private static final Pattern DEFENSE_PATTERN = Pattern.compile("\"defense\"\\s*:\\s*(\\d+)");

    private EnemyDefinitionLoader() {}

    public static Map<Integer, EnemyDefinition> loadAll() {
        Map<Integer, EnemyDefinition> defs = new LinkedHashMap<>();

        for (Path dir : candidateDirs()) {
            if (!Files.isDirectory(dir)) continue;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path file : stream) {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    String key  = fileKey(file);
                    EnemyDefinition def = parse(json, key);
                    if (def != null) defs.put(def.id, def);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load enemy definitions from " + dir, e);
            }
            if (!defs.isEmpty()) return defs;
        }

        return defs;
    }

    private static EnemyDefinition parse(String json, String key) {
        String idStr = match(json, ID_PATTERN);
        String name  = match(json, NAME_PATTERN);
        if (idStr == null || name == null) return null;

        try {
            int id           = Integer.parseInt(idStr);
            int maxHp        = parseInt(match(json, MAX_HP_PATTERN),  10);
            int attackLevel  = parseInt(match(json, ATTACK_PATTERN),   1);
            int defenceLevel = parseInt(match(json, DEFENSE_PATTERN),  1);
            return new EnemyDefinition(id, key, name, maxHp, attackLevel, defenceLevel);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String match(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static int parseInt(String value, int fallback) {
        return value != null ? Integer.parseInt(value) : fallback;
    }

    private static String fileKey(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }

    private static Path[] candidateDirs() {
        return new Path[]{
            Paths.get("cache", "enemies"),
            Paths.get("..", "Preservitory-Server", "cache", "enemies"),
            Paths.get("..", "Preservitory",        "cache", "enemies")
        };
    }
}
