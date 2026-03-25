package com.classic.preservitory.world;

import com.classic.preservitory.util.Constants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Loads {@link ObjectDefinition} records from {@code cache/objects/*.json}.
 *
 * Each file is expected to contain a single JSON object:
 * <pre>
 * {
 *   "id": "tree",
 *   "width": 32,
 *   "height": 32,
 *   "blocksMovement": true
 * }
 * </pre>
 *
 * Missing numeric fields fall back to {@link Constants#TILE_SIZE}.
 * Missing {@code blocksMovement} defaults to {@code false}.
 */
public final class DefinitionLoader {

    private static final Pattern ID_PATTERN  = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern W_PATTERN   = Pattern.compile("\"width\"\\s*:\\s*(\\d+)");
    private static final Pattern H_PATTERN   = Pattern.compile("\"height\"\\s*:\\s*(\\d+)");
    private static final Pattern BM_PATTERN  = Pattern.compile("\"blocksMovement\"\\s*:\\s*(true|false)");

    private DefinitionLoader() {}

    /**
     * Load all definitions from the objects cache directory.
     *
     * @return id → ObjectDefinition map; empty if directory is absent or unreadable.
     */
    public static Map<String, ObjectDefinition> loadAll() {
        Map<String, ObjectDefinition> defs = new LinkedHashMap<>();
        Path dir = Paths.get("cache", "objects");

        if (!Files.isDirectory(dir)) {
            System.out.println("[DefinitionLoader] cache/objects not found — no definitions loaded.");
            return defs;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : stream) {
                try {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    ObjectDefinition def = parse(json);
                    if (def != null) defs.put(def.id, def);
                } catch (IOException | IllegalStateException e) {
                    System.err.println("[DefinitionLoader] Failed to load " + file.getFileName()
                            + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[DefinitionLoader] Could not list " + dir + ": " + e.getMessage());
        }

        System.out.println("[DefinitionLoader] Loaded " + defs.size() + " definitions.");
        return defs;
    }

    private static ObjectDefinition parse(String json) {
        String id    = match(json, ID_PATTERN);
        if (id == null) return null;

        String wStr  = match(json, W_PATTERN);
        String hStr  = match(json, H_PATTERN);
        String bmStr = match(json, BM_PATTERN);

        int     width          = wStr  != null ? Integer.parseInt(wStr)  : Constants.TILE_SIZE;
        int     height         = hStr  != null ? Integer.parseInt(hStr)  : Constants.TILE_SIZE;
        boolean blocksMovement = "true".equals(bmStr);

        return new ObjectDefinition(id, width, height, blocksMovement);
    }

    private static String match(String text, Pattern p) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
