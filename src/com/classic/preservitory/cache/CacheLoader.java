package com.classic.preservitory.cache;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CacheLoader {

    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

    // -----------------------------------------------------------------------
    //  Loose-file loader (original — used for UI assets, music, etc.)
    // -----------------------------------------------------------------------

    /**
     * Returns the raw bytes of a file from the user's local cache directory.
     *
     * @param name path relative to the cache root
     *             (e.g. {@code "sprites/login_screen/background.png"})
     * @return file bytes, or {@code null} if the file is missing
     */
    public static byte[] getFile(String name) {
        try {
            String path = CacheConfig.CACHE_DIR + name;
            return Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            System.err.println("Missing asset: " + name);
            return null;
        }
    }

    // -----------------------------------------------------------------------
    //  Packed sprite cache
    // -----------------------------------------------------------------------

    /**
     * Returns the sprite with the given ID from the packed sprite cache
     * ({@code sprites.dat} / {@code sprites.idx}), or {@code null} if not found.
     *
     * <p>This is the preferred path once sprites have been packed with the
     * {@link com.classic.preservitory.tools.SpritePackerTool}.
     * Falls back gracefully when no packed cache exists.
     *
     * @param id sprite ID matching the key used when packing (e.g. {@code "bronze_axe"})
     * @return decoded {@link BufferedImage}, or {@code null}
     */
    public static BufferedImage getSprite(String id) {
        return SpriteCache.getSprite(id);
    }

    // -----------------------------------------------------------------------
    //  Definition lookup
    // -----------------------------------------------------------------------

    /**
     * Returns the raw JSON string for the definition of the given type and numeric id,
     * or {@code null} if not found.
     *
     * <p>Searches the user's local cache directory first, then falls back to
     * the project-relative {@code cache/} directory.
     *
     * <p>Example: {@code getDefinitionJson("items", 100)} scans
     * {@code ~/.preservitory/cache/items/*.json} for a file whose {@code "id"} field
     * equals {@code 100} and returns its full contents.
     *
     * @param type sub-directory name under the cache root (e.g. {@code "items"}, {@code "enemies"})
     * @param id   numeric definition id to search for
     * @return JSON string, or {@code null} if no matching definition was found
     */
    public static String getDefinitionJson(String type, int id) {
        String idStr = String.valueOf(id);
        for (Path dir : definitionDirs(type)) {
            if (!Files.isDirectory(dir)) continue;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path file : stream) {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    // Quick pre-check before running the full regex
                    if (!json.contains(idStr)) continue;
                    Matcher m = ID_PATTERN.matcher(json);
                    if (m.find() && Integer.parseInt(m.group(1)) == id) return json;
                }
            } catch (IOException e) {
                System.err.println("[CacheLoader] Failed reading " + type + " definitions: " + e.getMessage());
            }
            // Stop at the first directory that exists and was successfully read
            return null;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    //  Internal helpers
    // -----------------------------------------------------------------------

    private static Path[] definitionDirs(String type) {
        return new Path[]{
            Paths.get(CacheConfig.CACHE_DIR, type),
            Paths.get("cache", type),
            Paths.get("..", "Preservitory-Server", "cache", type),
            Paths.get("..", "Preservitory",        "cache", type)
        };
    }
}
