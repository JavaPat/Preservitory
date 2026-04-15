package com.classic.preservitory.tools;

import com.classic.preservitory.cache.CacheConfig;
import com.classic.preservitory.cache.SpriteCache;
import com.classic.preservitory.ui.framework.assets.AssetManager;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Exports all packed sprites to PNG files using their existing string keys.
 *
 * Output layout:
 *   cache/sprites_dump/<sprite_key>.png
 *   cache/sprites_dump/sprite_list.txt
 */
public final class SpriteDumpTool {

    private static final Path DUMP_DIR = Paths.get(CacheConfig.CACHE_DIR, "sprites_dump");
    private static final Path LIST_FILE = DUMP_DIR.resolve("sprite_list.txt");

    private SpriteDumpTool() {}

    public static void main(String[] args) throws IOException {
        dumpAllSprites();
    }

    public static void dumpAllSprites() throws IOException {
        Files.createDirectories(DUMP_DIR);

        Set<String> ids = SpriteCache.getIds();
        List<String> sortedIds = new ArrayList<>(ids);
        sortedIds.sort(Comparator.naturalOrder());

        try (Writer writer = Files.newBufferedWriter(LIST_FILE, StandardCharsets.UTF_8)) {
            for (String spriteName : sortedIds) {
                BufferedImage sprite = AssetManager.getImage(spriteName);
                if (sprite == null) {
                    continue;
                }

                BufferedImage argbImage = toArgbImage(sprite);
                Path spritePath = DUMP_DIR.resolve(spriteName.replace('\\', '/') + ".png");
                Path parent = spritePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                ImageIO.write(argbImage, "png", spritePath.toFile());
                writer.write(spriteName + " | " + argbImage.getWidth() + "x" + argbImage.getHeight());
                writer.write(System.lineSeparator());
            }
        }
    }

    private static BufferedImage toArgbImage(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }

        BufferedImage copy = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = copy.createGraphics();
        try {
            g2.drawImage(source, 0, 0, null);
        } finally {
            g2.dispose();
        }
        return copy;
    }
}
