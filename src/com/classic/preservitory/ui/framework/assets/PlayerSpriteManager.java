package com.classic.preservitory.ui.framework.assets;

import com.classic.preservitory.util.Constants;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Preloads and caches local-player sprite assets from an external sprite pack.
 * Rendering never allocates images; it only reuses the cached frames.
 */
public final class PlayerSpriteManager {

    private static final Logger LOGGER = Logger.getLogger(PlayerSpriteManager.class.getName());
    private static final String DEFAULT_DIRECTION = "south";
    private static final long WALK_FRAME_MS = 100L;

    private static final Map<String, SpriteFrame>              idleFrames   = new LinkedHashMap<>();
    private static final Map<String, List<SpriteFrame>>        walkFrames   = new LinkedHashMap<>();
    /** All named animations keyed by animation-name → direction → frames. */
    private static final Map<String, Map<String, List<SpriteFrame>>> actionFrames = new LinkedHashMap<>();

    private static volatile boolean loaded;
    private static volatile Path loadedRoot;

    private PlayerSpriteManager() {}

    public static synchronized void load() {
        if (loaded) {
            return;
        }

        Path spriteRoot = findSpriteRoot();
        if (spriteRoot == null) {
            LOGGER.warning("Player sprite pack not found. Falling back to legacy player renderer.");
            return;
        }

        try {
            loadFrom(spriteRoot);
            loadedRoot = spriteRoot;
            loaded = !idleFrames.isEmpty() || !walkFrames.isEmpty();
            if (!loaded) {
                LOGGER.warning("Player sprite pack loaded no usable frames. Falling back to legacy player renderer.");
            }
        } catch (Exception e) {
            idleFrames.clear();
            walkFrames.clear();
            loaded = false;
            LOGGER.log(Level.WARNING, "Failed to load player sprite pack from " + spriteRoot, e);
        }
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static Path getLoadedRoot() {
        return loadedRoot;
    }

    public static void drawPlayer(Graphics2D g2, int footX, int footY,
                                  String direction, boolean moving, double animationTimerSeconds) {
        SpriteFrame frame = resolveFrame(direction, moving, animationTimerSeconds);
        if (frame == null) {
            return;
        }
        g2.drawImage(frame.image, footX + frame.drawOffsetX, footY + frame.drawOffsetY, null);
    }

    /**
     * Draw a specific frame of a named action animation (e.g. "punch").
     * Falls back to the "south" direction when {@code direction} has no frames.
     *
     * @param animName   animation key in {@code frames.animations} (e.g. "punch")
     * @param direction  facing direction string (e.g. "north-east")
     * @param frameIndex 0-based index into the frame list — clamped automatically
     */
    public static void drawPlayerAction(Graphics2D g2, int footX, int footY,
                                        String animName, String direction, int frameIndex) {
        List<SpriteFrame> frames = getActionFrames(animName, normalizeDirection(direction));
        if (frames == null || frames.isEmpty()) return;
        int idx = Math.max(0, Math.min(frameIndex, frames.size() - 1));
        SpriteFrame frame = frames.get(idx);
        g2.drawImage(frame.image, footX + frame.drawOffsetX, footY + frame.drawOffsetY, null);
    }

    /**
     * Total frame count for the given animation + direction combination.
     * Returns 0 if the animation doesn't exist (safe to check before starting an attack).
     */
    public static int getAttackFrameCount(String animName, String direction) {
        List<SpriteFrame> frames = getActionFrames(animName, normalizeDirection(direction));
        return frames == null ? 0 : frames.size();
    }

    private static List<SpriteFrame> getActionFrames(String animName, String direction) {
        Map<String, List<SpriteFrame>> dirMap = actionFrames.get(animName);
        if (dirMap == null) return null;
        List<SpriteFrame> frames = dirMap.get(direction);
        if (frames != null && !frames.isEmpty()) return frames;
        // Fallback to "south"
        frames = dirMap.get(DEFAULT_DIRECTION);
        if (frames != null && !frames.isEmpty()) return frames;
        return dirMap.values().stream().filter(f -> !f.isEmpty()).findFirst().orElse(null);
    }

    private static SpriteFrame resolveFrame(String direction, boolean moving, double animationTimerSeconds) {
        String normalizedDirection = normalizeDirection(direction);
        if (moving) {
            List<SpriteFrame> frames = getWalkFrames(normalizedDirection);
            if (frames != null && !frames.isEmpty()) {
                long frameClockMs = Math.max(0L, (long) (animationTimerSeconds * 1000.0));
                int frameIndex = (int) ((frameClockMs / WALK_FRAME_MS) % frames.size());
                return frames.get(frameIndex);
            }
        }
        return getIdleFrame(normalizedDirection);
    }

    private static SpriteFrame getIdleFrame(String direction) {
        SpriteFrame frame = idleFrames.get(direction);
        if (frame != null) {
            return frame;
        }
        frame = idleFrames.get(DEFAULT_DIRECTION);
        if (frame != null) {
            return frame;
        }
        return idleFrames.values().stream().findFirst().orElse(null);
    }

    private static List<SpriteFrame> getWalkFrames(String direction) {
        List<SpriteFrame> frames = walkFrames.get(direction);
        if (frames != null && !frames.isEmpty()) {
            return frames;
        }
        frames = walkFrames.get(DEFAULT_DIRECTION);
        if (frames != null && !frames.isEmpty()) {
            return frames;
        }
        return walkFrames.values().stream().filter(list -> !list.isEmpty()).findFirst().orElse(null);
    }

    private static void loadFrom(Path spriteRoot) throws IOException {
        idleFrames.clear();
        walkFrames.clear();
        actionFrames.clear();

        Path metadataPath = spriteRoot.resolve("metadata.json");
        String json = Files.readString(metadataPath, StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(json);
        JSONObject frames = root.getJSONObject("frames");

        JSONObject rotations = frames.getJSONObject("rotations");
        for (String direction : rotations.keySet()) {
            String relativePath = rotations.getString(direction);
            SpriteFrame frame = loadFrame(spriteRoot.resolve(relativePath));
            if (frame != null) {
                idleFrames.put(normalizeDirection(direction), frame);
            }
        }

        JSONObject animations = frames.optJSONObject("animations");
        if (animations != null) {
            for (String animName : animations.keySet()) {
                JSONObject animDirs = animations.optJSONObject(animName);
                if (animDirs == null) continue;

                Map<String, List<SpriteFrame>> dirMap = new LinkedHashMap<>();
                for (String direction : animDirs.keySet()) {
                    JSONArray framePaths = animDirs.getJSONArray(direction);
                    List<SpriteFrame> loadedFrames = new ArrayList<>();
                    for (int i = 0; i < framePaths.length(); i++) {
                        SpriteFrame frame = loadFrame(spriteRoot.resolve(framePaths.getString(i)));
                        if (frame != null) {
                            loadedFrames.add(frame);
                        }
                    }
                    if (!loadedFrames.isEmpty()) {
                        dirMap.put(normalizeDirection(direction), List.copyOf(loadedFrames));
                    }
                }

                if (!dirMap.isEmpty()) {
                    actionFrames.put(animName, dirMap);
                    // Keep the legacy walkFrames map populated for the existing walk renderer.
                    if ("walk".equals(animName)) {
                        walkFrames.putAll(dirMap);
                    }
                }
            }
        }
    }

    private static SpriteFrame loadFrame(Path imagePath) {
        try {
            BufferedImage image = ImageIO.read(imagePath.toFile());
            if (image == null) {
                LOGGER.warning("Unsupported sprite image: " + imagePath);
                return null;
            }
            return new SpriteFrame(image, computeDrawOffsetX(image), computeDrawOffsetY(image));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read sprite image: " + imagePath, e);
            return null;
        }
    }

    private static int computeDrawOffsetX(BufferedImage image) {
        AlphaBounds bounds = findOpaqueBounds(image);
        int anchorX = bounds.minX + (bounds.width() / 2);
        return -anchorX;
    }

    private static int computeDrawOffsetY(BufferedImage image) {
        AlphaBounds bounds = findOpaqueBounds(image);
        int anchorY = bounds.maxY + 1;
        return -anchorY;
    }

    private static AlphaBounds findOpaqueBounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = (image.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha == 0) {
                    continue;
                }
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
        }

        if (maxX < minX || maxY < minY) {
            return new AlphaBounds(0, 0, image.getWidth() - 1, image.getHeight() - 1);
        }
        return new AlphaBounds(minX, minY, maxX, maxY);
    }

    private static Path findSpriteRoot() {
        List<Path> candidates = List.of(
                Path.of(System.getProperty("user.home"), "." + Constants.GAME_NAME_TO_LOWER, "cache", "sprites"),
                Path.of(System.getProperty("user.home"), "IdeaProjects", Constants.GAME_NAME, "cache", "sprites"),
                Path.of(System.getProperty("user.home"), "Desktop", "animations"),
                Path.of("cache", "sprites").toAbsolutePath()
        );

        for (Path candidate : candidates) {
            // 1. Check root (old behaviour)
            if (Files.isRegularFile(candidate.resolve("metadata.json"))) {
                return candidate;
            }

            // 2. NEW: Check subdirectories (player, npc, etc.)
            try (var stream = Files.list(candidate)) {
                for (Path sub : stream.toList()) {
                    if (Files.isDirectory(sub) && Files.isRegularFile(sub.resolve("metadata.json"))) {
                        return sub; // <-- THIS is your player folder
                    }
                }
            } catch (Exception ignored) {}
        }

        return null;

    }


    private static String normalizeDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return DEFAULT_DIRECTION;
        }
        return direction.trim().toLowerCase();
    }

    private static final class SpriteFrame {
        private final BufferedImage image;
        private final int drawOffsetX;
        private final int drawOffsetY;

        private SpriteFrame(BufferedImage image, int drawOffsetX, int drawOffsetY) {
            this.image = image;
            this.drawOffsetX = drawOffsetX;
            this.drawOffsetY = drawOffsetY;
        }
    }

    private static final class AlphaBounds {
        private final int minX;
        private final int minY;
        private final int maxX;
        private final int maxY;

        private AlphaBounds(int minX, int minY, int maxX, int maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        private int width() {
            return maxX - minX + 1;
        }
    }
}
