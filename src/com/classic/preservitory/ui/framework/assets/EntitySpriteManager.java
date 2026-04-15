package com.classic.preservitory.ui.framework.assets;

import com.classic.preservitory.cache.SpriteCache;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Per-entity sprite loader and renderer.
 *
 * Loads sprites exclusively from the packed sprite cache (sprites.dat / sprites.idx)
 * via {@link SpriteCache}.
 *
 * Expected ID conventions in the cache (produced by SpritePackerTool folder import):
 * <ul>
 *   <li>Idle:         {@code {category}/{id}/rotations/{dir}}</li>
 *   <li>Animation:    {@code {category}/{id}/{anim}/{dir}/frame_{n}}</li>
 *   <li>Player (no sub-id): {@code player/rotations/{dir}},
 *                            {@code player/animations/{anim}/{dir}/frame_{n}}</li>
 * </ul>
 *
 * Direction strings accepted: north, south, east, west.
 * Diagonal strings (north-east, etc.) are mapped to the nearest cardinal.
 *
 * Rendering falls back gracefully:
 *   - Unknown direction → south
 *   - Missing walk frames → idle frame
 *   - No sprite loaded → {@link #isLoaded()} returns false; caller uses legacy drawing
 */
public final class EntitySpriteManager {

    private static final Logger LOGGER = Logger.getLogger(EntitySpriteManager.class.getName());

    private static final String DEFAULT_DIR  = "south";
    private static final long   WALK_FRAME_MS = 100L;

    private static final Pattern FRAME_NUM_PATTERN = Pattern.compile("frame_(\\d+)");
    private static final Pattern DIGIT_PATTERN     = Pattern.compile("(\\d+)");

    // -----------------------------------------------------------------------
    //  Cached frames
    // -----------------------------------------------------------------------

    private final Map<String, SpriteFrame>                         idleFrames   = new LinkedHashMap<>();
    private final Map<String, List<SpriteFrame>>                   walkFrames   = new LinkedHashMap<>();
    private final Map<String, Map<String, List<SpriteFrame>>>      actionFrames = new LinkedHashMap<>();

    private final boolean loaded;

    /**
     * Optional fallback manager consulted when this entity has no walk/action frames
     * for a requested animation.
     */
    private final EntitySpriteManager fallback;

    // -----------------------------------------------------------------------
    //  Construction — loads immediately from SpriteCache
    // -----------------------------------------------------------------------

    /**
     * @param category "player", "npc", or "enemy"
     * @param id       sprite folder name within the category (e.g. "goblin"), or empty for player
     */
    public EntitySpriteManager(String category, String id) {
        this(category, id, null);
    }

    /**
     * @param category "player", "npc", or "enemy"
     * @param id       sprite folder name within the category (e.g. "goblin"), or empty/null for player
     * @param fallback manager to consult for walk/attack frames missing from this entity's sprites
     */
    public EntitySpriteManager(String category, String id, EntitySpriteManager fallback) {
        this.fallback = fallback;
        boolean ok = false;
        try {
            loadFromSpriteCache(category, id);
            ok = !idleFrames.isEmpty() || !walkFrames.isEmpty();
            if (!ok) {
                LOGGER.fine("[Sprites] " + category + "/" + id + " — not found in SpriteCache.");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[Sprites] SpriteCache load failed for " + category + "/" + id, e);
        }
        this.loaded = ok;
    }

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    /** True if at least one sprite frame was loaded successfully. */
    public boolean isLoaded() { return loaded; }

    /**
     * Draw the entity's idle or walk animation at the given foot position.
     *
     * @param footX              screen X of the foot anchor (bottom-centre of tile)
     * @param footY              screen Y of the foot anchor
     * @param direction          server-reported direction string
     * @param moving             true while the entity is mid-step
     * @param animationTimerSecs accumulated animation timer in seconds
     */
    public void draw(Graphics2D g, int footX, int footY,
                     String direction, boolean moving, double animationTimerSecs) {
        SpriteFrame frame = resolveFrame(normalizeDir(direction), moving, animationTimerSecs);
        if (frame == null) return;
        g.drawImage(frame.image, footX + frame.offsetX, footY + frame.offsetY, null);
    }

    /**
     * Draw a specific walk frame by absolute frame index (wraps to available frames).
     * Falls back to the idle sprite if no walk frames are loaded.
     *
     * @param frameIndex render-tick-based index; wrapped modulo frame count
     */
    public void drawWalkFrame(Graphics2D g, int footX, int footY, String direction, int frameIndex) {
        String dir = normalizeDir(direction);
        List<SpriteFrame> frames = getWalkFrames(dir);
        if (frames != null && !frames.isEmpty()) {
            SpriteFrame frame = frames.get(frameIndex % frames.size());
            g.drawImage(frame.image, footX + frame.offsetX, footY + frame.offsetY, null);
        } else {
            SpriteFrame idle = getIdleFrame(dir);
            if (idle != null) g.drawImage(idle.image, footX + idle.offsetX, footY + idle.offsetY, null);
        }
    }

    /**
     * Draw a specific frame of a named action animation (e.g. "punch").
     *
     * @param animName   animation key in the cache (e.g. "punch")
     * @param direction  facing direction
     * @param frameIndex 0-based frame index (clamped to valid range)
     */
    public void drawAction(Graphics2D g, int footX, int footY,
                           String animName, String direction, int frameIndex) {
        List<SpriteFrame> frames = resolveActionFrames(animName, normalizeDir(direction));
        if (frames == null || frames.isEmpty()) return;
        SpriteFrame frame = frames.get(Math.max(0, Math.min(frameIndex, frames.size() - 1)));
        g.drawImage(frame.image, footX + frame.offsetX, footY + frame.offsetY, null);
    }

    /**
     * Total frame count for the given action animation + direction.
     * Returns 0 if the animation does not exist.
     */
    public int getActionFrameCount(String animName, String direction) {
        List<SpriteFrame> frames = resolveActionFrames(animName, normalizeDir(direction));
        return frames == null ? 0 : frames.size();
    }

    // -----------------------------------------------------------------------
    //  Frame resolution
    // -----------------------------------------------------------------------

    private SpriteFrame resolveFrame(String dir, boolean moving, double animSecs) {
        if (moving) {
            List<SpriteFrame> frames = getWalkFrames(dir);
            if (frames != null && !frames.isEmpty()) {
                long ms  = Math.max(0L, (long)(animSecs * 1000.0));
                int  idx = (int)((ms / WALK_FRAME_MS) % frames.size());
                return frames.get(idx);
            }
        }
        return getIdleFrame(dir);
    }

    private SpriteFrame getIdleFrame(String dir) {
        SpriteFrame f = idleFrames.get(dir);
        if (f != null) return f;
        f = idleFrames.get(DEFAULT_DIR);
        if (f != null) return f;
        return idleFrames.values().stream().findFirst().orElse(null);
    }

    private List<SpriteFrame> getWalkFrames(String dir) {
        List<SpriteFrame> f = walkFrames.get(dir);
        if (f != null && !f.isEmpty()) return f;
        f = walkFrames.get(DEFAULT_DIR);
        if (f != null && !f.isEmpty()) return f;
        f = walkFrames.values().stream().filter(l -> !l.isEmpty()).findFirst().orElse(null);
        if (f != null) return f;
        return fallback != null ? fallback.getWalkFrames(dir) : null;
    }

    private List<SpriteFrame> resolveActionFrames(String animName, String dir) {
        Map<String, List<SpriteFrame>> dirMap = actionFrames.get(animName);
        if (dirMap != null) {
            List<SpriteFrame> f = dirMap.get(dir);
            if (f != null && !f.isEmpty()) return f;
            f = dirMap.get(DEFAULT_DIR);
            if (f != null && !f.isEmpty()) return f;
            f = dirMap.values().stream().filter(l -> !l.isEmpty()).findFirst().orElse(null);
            if (f != null) return f;
        }
        return fallback != null ? fallback.resolveActionFrames(animName, dir) : null;
    }

    // -----------------------------------------------------------------------
    //  SpriteCache-based loading (sole loading path)
    // -----------------------------------------------------------------------

    /**
     * Scans {@link SpriteCache} for all sprite IDs whose prefix matches
     * {@code {category}/{id}/} (or {@code {category}/} when {@code id} is empty)
     * and populates {@link #idleFrames}, {@link #walkFrames}, and
     * {@link #actionFrames} from them.
     *
     * Supported ID formats:
     * <ul>
     *   <li>Idle:           {@code rotations/{dir}}</li>
     *   <li>Animation:      {@code {anim}/{dir}/frame_{n}}</li>
     *   <li>Player format:  {@code animations/{anim}/{dir}/frame_{n}}</li>
     * </ul>
     */
    private void loadFromSpriteCache(String category, String id) {
        String prefix = (id == null || id.isEmpty())
                ? category + "/"
                : category + "/" + id + "/";

        Set<String> ids = SpriteCache.getIds();
        if (ids.isEmpty()) return;

        // Collect animation frames: animName → dir → frameNum → frame
        Map<String, Map<String, Map<Integer, SpriteFrame>>> animBuffer = new LinkedHashMap<>();

        for (String spriteId : ids) {
            if (!spriteId.startsWith(prefix)) continue;
            String rel   = spriteId.substring(prefix.length());
            String[] parts = rel.split("/");

            if (parts.length == 2 && "rotations".equals(parts[0])) {
                // Idle: rotations/{dir}
                String dir = normalizeDir(parts[1]);
                SpriteFrame frame = loadFrameFromCache(spriteId);
                if (frame != null) idleFrames.put(dir, frame);

            } else if (parts.length == 3) {
                // Standard animation: {anim}/{dir}/frame_{n}
                String anim = parts[0];
                String dir  = normalizeDir(parts[1]);
                int    num  = extractFrameNum(parts[2]);
                SpriteFrame frame = loadFrameFromCache(spriteId);
                if (frame != null) {
                    animBuffer
                            .computeIfAbsent(anim, k -> new LinkedHashMap<>())
                            .computeIfAbsent(dir,  k -> new TreeMap<>())
                            .put(num, frame);
                }

            } else if (parts.length == 4 && "animations".equals(parts[0])) {
                // Player layout: animations/{anim}/{dir}/frame_{n}
                String anim = parts[1];
                String dir  = normalizeDir(parts[2]);
                int    num  = extractFrameNum(parts[3]);
                SpriteFrame frame = loadFrameFromCache(spriteId);
                if (frame != null) {
                    animBuffer
                            .computeIfAbsent(anim, k -> new LinkedHashMap<>())
                            .computeIfAbsent(dir,  k -> new TreeMap<>())
                            .put(num, frame);
                }
            }
        }

        // Promote buffered frames into actionFrames / walkFrames
        for (Map.Entry<String, Map<String, Map<Integer, SpriteFrame>>> animEntry : animBuffer.entrySet()) {
            String animName = animEntry.getKey();
            Map<String, List<SpriteFrame>> dirMap = new LinkedHashMap<>();
            for (Map.Entry<String, Map<Integer, SpriteFrame>> dirEntry : animEntry.getValue().entrySet()) {
                List<SpriteFrame> frames = new ArrayList<>(dirEntry.getValue().values());
                if (!frames.isEmpty()) dirMap.put(dirEntry.getKey(), List.copyOf(frames));
            }
            if (!dirMap.isEmpty()) {
                actionFrames.put(animName, dirMap);
                if ("walk".equals(animName)) walkFrames.putAll(dirMap);
            }
        }
    }

    private static SpriteFrame loadFrameFromCache(String spriteId) {
        BufferedImage img = SpriteCache.getSprite(spriteId);
        if (img == null) return null;
        int[] bounds = findOpaqueBounds(img);
        int anchorX = bounds[0] + (bounds[2] / 2);
        int anchorY = bounds[1] + bounds[3];
        return new SpriteFrame(img, -anchorX, -anchorY);
    }

    private static int extractFrameNum(String name) {
        Matcher m = FRAME_NUM_PATTERN.matcher(name);
        if (m.find()) return Integer.parseInt(m.group(1));
        m = DIGIT_PATTERN.matcher(name);
        if (m.find()) return Integer.parseInt(m.group(1));
        return 0;
    }

    // -----------------------------------------------------------------------
    //  Anchor computation
    // -----------------------------------------------------------------------

    /**
     * Returns {minX, minY, width, height} of the opaque pixel region.
     */
    private static int[] findOpaqueBounds(BufferedImage img) {
        int minX = img.getWidth(), minY = img.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) == 0) continue;
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
        }
        if (maxX < minX) { minX = 0; minY = 0; maxX = img.getWidth() - 1; maxY = img.getHeight() - 1; }
        return new int[]{ minX, minY, maxX - minX + 1, maxY - minY + 1 };
    }

    // -----------------------------------------------------------------------
    //  Direction normalisation — cardinal only, no diagonals
    // -----------------------------------------------------------------------

    static String normalizeDir(String dir) {
        if (dir == null || dir.isBlank()) return DEFAULT_DIR;
        return switch (dir.trim().toLowerCase()) {
            case "north"                    -> "north";
            case "south"                    -> "south";
            case "east"                     -> "east";
            case "west"                     -> "west";
            case "north-east", "north-west" -> "north";
            case "south-east", "south-west" -> "south";
            default                         -> DEFAULT_DIR;
        };
    }

    // -----------------------------------------------------------------------
    //  Internal types
    // -----------------------------------------------------------------------

    private static final class SpriteFrame {
        final BufferedImage image;
        final int offsetX;
        final int offsetY;
        SpriteFrame(BufferedImage image, int offsetX, int offsetY) {
            this.image   = image;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }
}