package com.classic.preservitory.cache;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runtime reader for the packed sprite cache ({@code sprites.dat} + {@code sprites.idx}).
 *
 * <p>The index is read once on first access; individual sprites are decoded from
 * {@code sprites.dat} on demand and then held in a memory map so subsequent
 * lookups are instant.
 *
 * <p>Falls back gracefully when no packed cache exists — {@link #isAvailable()}
 * returns {@code false} and {@link #getSprite(String)} returns {@code null},
 * allowing callers to fall through to the legacy loose-file loader.
 *
 * <p>All public methods are thread-safe.
 */
public final class SpriteCache {

    private static final Logger LOGGER = Logger.getLogger(SpriteCache.class.getName());

    private static final String INDEX_FILENAME = "sprites.idx";
    private static final String DATA_FILENAME  = "sprites.dat";

    /** Guarded by {@code SpriteCache.class} monitor. */
    private static boolean initialised = false;
    private static boolean available   = false;

    private static Map<String, SpriteEntry>    index   = new HashMap<>();
    private static final Map<String, BufferedImage> decoded = new HashMap<>();

    private SpriteCache() {}

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    /**
     * Returns the sprite for the given ID, decoded to a {@link BufferedImage}.
     *
     * <p>The first call for a given ID reads from {@code sprites.dat} and caches
     * the result.  Subsequent calls return the cached image.
     *
     * @param id the sprite ID (matches the key in the packed index)
     * @return the sprite image, or {@code null} if not found / cache unavailable
     */
    public static BufferedImage getSprite(String id) {
        ensureInitialised();
        if (!available || id == null) return null;

        synchronized (SpriteCache.class) {
            BufferedImage cached = decoded.get(id);
            if (cached != null) return cached;

            SpriteEntry entry = index.get(id);
            if (entry == null) return null;

            BufferedImage img = decode(entry);
            if (img != null) decoded.put(id, img);
            return img;
        }
    }

    /**
     * Returns {@code true} if the packed cache was found and loaded successfully.
     * When {@code false}, all {@link #getSprite} calls return {@code null}.
     */
    public static boolean isAvailable() {
        ensureInitialised();
        return available;
    }

    /** Returns the number of sprites registered in the index. */
    public static int size() {
        ensureInitialised();
        return index.size();
    }

    /** Returns an unmodifiable view of all sprite IDs in the index. */
    public static Set<String> getIds() {
        ensureInitialised();
        return Collections.unmodifiableSet(index.keySet());
    }

    /**
     * Discards the in-memory state and re-reads the index file.
     * Call this after the {@link com.classic.preservitory.tools.SpritePackerTool}
     * has written a new pack.
     */
    public static synchronized void reload() {
        initialised = false;
        available   = false;
        index.clear();
        decoded.clear();
        ensureInitialised();
    }

    // -----------------------------------------------------------------------
    //  Internal
    // -----------------------------------------------------------------------

    private static synchronized void ensureInitialised() {
        if (initialised) return;
        initialised = true;

        Path idxPath = resolvePath(INDEX_FILENAME);
        if (idxPath == null) {
            LOGGER.fine("[SpriteCache] No sprites.idx found — packed cache unavailable.");
            return;
        }

        try {
            List<SpriteEntry> entries = SpriteIndex.read(idxPath);
            Map<String, SpriteEntry> map = new HashMap<>(entries.size() * 2);
            for (SpriteEntry e : entries) map.put(e.id, e);
            index     = Collections.unmodifiableMap(map);
            available = !index.isEmpty();
            LOGGER.info("[SpriteCache] Loaded index: " + index.size() + " sprites from " + idxPath);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "[SpriteCache] Failed to read index: " + idxPath, e);
        }
    }

    /** Read PNG bytes for {@code entry} from sprites.dat and decode. */
    private static BufferedImage decode(SpriteEntry entry) {
        Path datPath = resolvePath(DATA_FILENAME);
        if (datPath == null) {
            LOGGER.warning("[SpriteCache] sprites.dat not found while trying to decode '" + entry.id + "'");
            return null;
        }
        try (RandomAccessFile raf = new RandomAccessFile(datPath.toFile(), "r")) {
            byte[] buf = new byte[entry.length];
            raf.seek(entry.offset);
            raf.readFully(buf);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(buf));
            if (img == null) {
                LOGGER.warning("[SpriteCache] ImageIO could not decode sprite '" + entry.id + "'");
            }
            return img;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "[SpriteCache] Error decoding sprite '" + entry.id + "'", e);
            return null;
        }
    }

    /**
     * Resolves a cache filename against candidate directories, returning the first
     * path that actually exists, or {@code null} if none found.
     *
     * Search order:
     *   1. User's local cache  (~/.preservitory/cache/)
     *   2. Project-relative    (cache/)
     */
    static Path resolvePath(String filename) {
        Path userCache = Paths.get(CacheConfig.CACHE_DIR, filename);
        if (Files.exists(userCache)) return userCache;

        Path projectCache = Paths.get("cache", filename).toAbsolutePath();
        if (Files.exists(projectCache)) return projectCache;

        return null;
    }
}
