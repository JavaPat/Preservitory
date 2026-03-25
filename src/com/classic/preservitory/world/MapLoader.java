package com.classic.preservitory.world;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Reads tree positions from {@code cache/maps/starter_map.json} —
 * the exact same file the server's TreeManager consumes.
 *
 * Tree IDs are assigned T0, T1, … in JSON array order, matching the server's
 * assignment so that server-issued IDs always line up with locally cached ones.
 *
 * This lets the client display trees offline before the server connects.
 * When the server sends its authoritative TREES snapshot it overwrites this
 * cached state.
 *
 * Rocks and NPCs are NOT loaded here — they are strictly server-authoritative
 * and arrive only via the network (ROCKS / NPCS messages).
 */
public final class MapLoader {

    private static final String MAP_NAME = "starter_map.json";

    private static final Pattern OBJECT_PATTERN = Pattern.compile("\\{([^{}]*)\\}");
    private static final Pattern ID_PATTERN     = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern X_PATTERN      = Pattern.compile("\"x\"\\s*:\\s*(-?\\d+)");
    private static final Pattern Y_PATTERN      = Pattern.compile("\"y\"\\s*:\\s*(-?\\d+)");

    private MapLoader() {}

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    /**
     * Load tree positions from the cached map file.
     *
     * @return id → [x, y] for every tree entry, preserving insertion order.
     *         Returns an empty map if the file is absent or cannot be parsed
     *         (never throws).
     */
    public static Map<String, int[]> loadTrees() {
        Path path = resolvePath();
        if (path == null) {
            System.out.println("[MapLoader] " + MAP_NAME + " not found — no offline trees.");
            return Collections.emptyMap();
        }

        try {
            String json    = Files.readString(path, StandardCharsets.UTF_8);
            String objects = extractNamedArray(json, "objects");
            if (objects == null) return Collections.emptyMap();

            Map<String, int[]> result = new LinkedHashMap<>();
            Matcher m     = OBJECT_PATTERN.matcher(objects);
            int     index = 0;

            while (m.find()) {
                String obj = m.group(1);
                String id  = extractString(obj, ID_PATTERN);
                if (!"tree".equals(id)) continue;

                int x = extractInt(obj, X_PATTERN);
                int y = extractInt(obj, Y_PATTERN);
                result.put("T" + index++, new int[]{x, y});
            }

            System.out.println("[MapLoader] Loaded " + result.size()
                    + " trees from " + path);
            return result;

        } catch (IOException | IllegalStateException e) {
            System.err.println("[MapLoader] Failed to load map: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    // -----------------------------------------------------------------------
    //  Path resolution
    // -----------------------------------------------------------------------

    private static Path resolvePath() {
        List<Path> candidates = List.of(
                Paths.get("cache", "maps", MAP_NAME),
                Paths.get("..", "Preservitory-Server", "cache", "maps", MAP_NAME)
        );
        for (Path p : candidates) {
            if (Files.exists(p)) return p;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    //  JSON helpers
    // -----------------------------------------------------------------------

    private static String extractNamedArray(String json, String arrayName) {
        int marker = json.indexOf("\"" + arrayName + "\"");
        if (marker == -1) return null;
        int start = json.indexOf('[', marker);
        if (start == -1) return null;
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            if (c == ']' && --depth == 0) return json.substring(start + 1, i);
        }
        return null;
    }

    private static String extractString(String obj, Pattern p) {
        Matcher m = p.matcher(obj);
        return m.find() ? m.group(1) : null;
    }

    private static int extractInt(String obj, Pattern p) {
        Matcher m = p.matcher(obj);
        if (!m.find()) throw new IllegalStateException(
                "Missing numeric field matching " + p.pattern());
        return Integer.parseInt(m.group(1));
    }
}
