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

/** Reads {@code cache/npcs/*.json} and returns NPC definitions keyed by int id. */
public final class NpcDefinitionLoader {

    private static final Pattern ID_PATTERN        = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern NAME_PATTERN      = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SHOPKEEPER_PATTERN = Pattern.compile("\"shopkeeper\"\\s*:\\s*(true|false)");

    private NpcDefinitionLoader() {}

    public static Map<Integer, NpcDefinition> loadAll() {
        Map<Integer, NpcDefinition> defs = new LinkedHashMap<>();

        for (Path dir : candidateDirs()) {
            if (!Files.isDirectory(dir)) continue;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path file : stream) {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    String key  = fileKey(file);
                    NpcDefinition def = parse(json, key);
                    if (def != null) defs.put(def.id, def);
                }
                if (!defs.isEmpty()) return defs;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load NPC definitions from " + dir, e);
            }
        }

        return defs;
    }

    private static NpcDefinition parse(String json, String key) {
        String idStr = match(json, ID_PATTERN);
        String name  = match(json, NAME_PATTERN);
        if (idStr == null || name == null) return null;

        try {
            int     id         = Integer.parseInt(idStr);
            boolean shopkeeper = "true".equals(match(json, SHOPKEEPER_PATTERN));
            return new NpcDefinition(id, key, name, shopkeeper);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String match(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String fileKey(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }

    private static Path[] candidateDirs() {
        return new Path[]{
            Paths.get("cache", "npcs"),
            Paths.get("..", "Preservitory-Server", "cache", "npcs"),
            Paths.get("..", "Preservitory",        "cache", "npcs")
        };
    }
}
