package com.classic.preservitory.ui.framework.assets;

import com.classic.preservitory.cache.SpriteCache;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AssetManager {

    /** In-memory sprite cache — populated on demand by {@link #getOrLoad}. */
    private static final Map<String, BufferedImage> images = new HashMap<>();

    // -----------------------------------------------------------------------
    //  Initialisation
    // -----------------------------------------------------------------------

    /**
     * Formerly pre-loaded all sprites from loose files on disk.
     *
     * Now a no-op: sprites are loaded on demand from the packed sprite cache
     * ({@code sprites.dat} / {@code sprites.idx}) the first time
     * {@link #getImage} or {@link #getOrLoad} is called with a given ID.
     *
     * <p>The call-site in {@code GamePanel} is kept intentionally so callers
     * do not need to be updated.
     */
    public static void load() {
        // Intentionally empty — all sprites come from SpriteCache on demand.
    }

    // -----------------------------------------------------------------------
    //  Sprite retrieval
    // -----------------------------------------------------------------------

    /**
     * Returns the sprite with the given ID.
     *
     * <p>Delegates to {@link #getOrLoad}; provided as a stable API for the
     * many call-sites that already use {@code getImage(key)}.
     *
     * @param key sprite ID (e.g. {@code "bronze_axe"}, {@code "login_bg"})
     * @return decoded image, or {@code null} if the sprite is not in the cache
     */
    public static BufferedImage getImage(String key) {
        return getOrLoad(key);
    }

    /**
     * Returns the sprite with the given ID, loading it from the packed cache
     * on first access and promoting it into the in-memory map for future calls.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>In-memory map (result of a previous call for this ID).</li>
     *   <li>Packed sprite cache ({@link SpriteCache} reading {@code sprites.dat}).</li>
     *   <li>{@code null} — sprite not found; caller should use a fallback.</li>
     * </ol>
     *
     * @param spriteId sprite ID matching a packed cache entry
     *                 (e.g. {@code "bronze_axe"}, {@code "raw_lobster"})
     * @return decoded image, or {@code null} if not found
     */
    public static BufferedImage getOrLoad(String spriteId) {
        if (spriteId == null) return null;

        BufferedImage img = images.get(spriteId);
        if (img != null) return img;

        img = SpriteCache.getSprite(spriteId);
        if (img != null) images.put(spriteId, img);
        return img;
    }

    public static Set<String> getAvailableKeys() {
        return Collections.unmodifiableSet(images.keySet());
    }

    public static Set<String> getLoadedImageKeys() {
        return Collections.unmodifiableSet(images.keySet());
    }

    // -----------------------------------------------------------------------
    //  Coin-stack rendering (shared by InventoryTab and Loot)
    // -----------------------------------------------------------------------

    /**
     * Draw an OSRS-style coin stack at the given position.
     *
     * @param amount      coin count — controls which sprite is chosen
     * @param x           for inventory: top-left x of the 44 px slot;
     *                    for ground:    tile-centre x in screen space
     * @param y           same convention, vertical axis
     * @param isInventory true → 28×28 sprite centred inside 44 px slot;
     *                    false → 20×20 sprite centred on (x, y)
     */
    public static void drawCoinStack(Graphics2D graphics, int amount, int x, int y, boolean isInventory) {
        String key = amount == 1 ? "items/coins/coin" : amount <= 99 ? "items/coins/multiple_coins" : "items/coins/coin_stack";
        BufferedImage img = getOrLoad(key);

        int iconSize = isInventory ? 28 : 20;
        int drawX, drawY;
        if (isInventory) {
            int slotSize = 44;
            drawX = x + (slotSize - iconSize) / 2;
            drawY = y + (slotSize - iconSize) / 2;
        } else {
            drawX = x - iconSize / 2;
            drawY = y - iconSize / 2;
        }

        if (img != null) {
            graphics.drawImage(img, drawX, drawY, iconSize, iconSize, null);
        } else {
            // Fallback: golden oval when the sprite is not in the packed cache.
            graphics.setColor(new Color(255, 210, 0));
            graphics.fillOval(drawX + 2, drawY + 2, iconSize - 4, iconSize - 4);
            graphics.setColor(new Color(180, 140, 0));
            graphics.drawOval(drawX + 2, drawY + 2, iconSize - 4, iconSize - 4);
        }

        if (amount <= 1) return;

        String text = formatStackAmount(amount);
        Color textColor = amount < 100_000    ? new Color(255, 255, 0)
                        : amount < 10_000_000 ? Color.WHITE
                        :                       new Color(0, 255, 0);

        graphics.setFont(new Font("Arial", Font.BOLD, isInventory ? 9 : 7));
        FontMetrics fm = graphics.getFontMetrics();

        int textX = drawX + iconSize - fm.stringWidth(text) - 1;
        int textY = drawY + iconSize - 2;

        graphics.setColor(Color.BLACK);
        graphics.drawString(text, textX - 1, textY);
        graphics.drawString(text, textX + 1, textY);
        graphics.drawString(text, textX,     textY - 1);
        graphics.drawString(text, textX,     textY + 1);
        graphics.setColor(textColor);
        graphics.drawString(text, textX, textY);
    }

    /**
     * Format a coin amount:
     *   1–999          → exact  ("250")
     *   1 000–999 999  → K      ("1K", "25K")
     *   1 000 000+     → M      ("1M", "10M")
     */
    public static String formatStackAmount(int amount) {
        if (amount >= 1_000_000) return (amount / 1_000_000) + "M";
        if (amount >= 1_000)     return (amount / 1_000)     + "K";
        return String.valueOf(amount);
    }
}
