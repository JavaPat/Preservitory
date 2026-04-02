package com.classic.preservitory.ui.framework.assets;

import com.classic.preservitory.cache.CacheLoader;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public class AssetManager {

    private static final Map<String, BufferedImage> images = new HashMap<>();

    public static void load() {
        //loading and login screen.
        loadImage("login_bg", "sprites/login_screen/background.png");
        loadImage("logo", "sprites/login_screen/logo.png");
        loadImage("login_box", "sprites/login_screen/box.png");
        loadImage("login_button", "sprites/login_screen/button.png");
        loadImage("mute", "sprites/login_screen/mute.png");
        loadImage("unmute", "sprites/login_screen/unmute.png");
        // Tab bar icons (24x24 PNG with transparency)
        loadImage("tab_combat",    "sprites/inventory/combat_tab.png");
        loadImage("tab_inventory", "sprites/inventory/inventory_tab.png");
        loadImage("tab_skills",    "sprites/inventory/skills_tab.png");
        loadImage("tab_equipment", "sprites/inventory/equipment_tab.png");
        loadImage("tab_quests",    "sprites/inventory/quest_tab.png");

        //settings.
        loadImage("settings", "sprites/settings/settings_cog.png");

        //shop window.
        loadImage("shop_window", "sprites/shop/window.png");
        loadImage("close", "sprites/shop/close_button.png");
        loadImage("close_hover", "sprites/shop/close_button_hover.png");
        loadImage("grid_slot", "sprites/shop/grid_button.png");

        //items.
        loadImage("coin", "sprites/items/coins/coin.png");
        loadImage("multiple_coins", "sprites/items/coins/multiple_coins.png");
        loadImage("coin_stack", "sprites/items/coins/coin_stack.png");

        //tree's
        loadImage("tree", "sprites/objects/trees/tree.png");
        loadImage("oak_tree", "sprites/objects/trees/oak.png");
        loadImage("willow_tree", "sprites/objects/trees/willow.png");
        loadImage("maple_tree", "sprites/objects/trees/maple.png");
        loadImage("yew_tree", "sprites/objects/trees/yew.png");
        //rocks
        loadImage("tin_rock", "sprites/objects/rocks/tin_rocks.png");
        loadImage("copper_rock", "sprites/objects/rocks/copper_rocks.png");
        loadImage("iron_rock", "sprites/objects/rocks/iron_rocks.png");
        loadImage("gold_rock", "sprites/objects/rocks/gold_rocks.png");
        loadImage("mithril_rock", "sprites/objects/rocks/mithril_rocks.png");
        loadImage("adamant_rock", "sprites/objects/rocks/adamant_rocks.png");
        loadImage("runite_rock", "sprites/objects/rocks/runite_rocks.png");
    }

    private static void loadImage(String key, String path) {
        try {
            byte[] data = CacheLoader.getFile(path);
            if (data == null) return;

            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            images.put(key, img);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static BufferedImage getImage(String key) {
        return images.get(key);
    }

    public static java.util.Set<String> getAvailableKeys() {
        return java.util.Collections.unmodifiableSet(images.keySet());
    }

    public static java.util.Set<String> getLoadedImageKeys() {
        return java.util.Collections.unmodifiableSet(images.keySet());
    }

    // -----------------------------------------------------------------------
    //  Coin-stack rendering (shared by InventoryTab and Loot)
    // -----------------------------------------------------------------------

    /**
     * Draw an OSRS-style coin stack at the given position.
     *
     * @param g          graphics context
     * @param amount     coin count — controls which sprite is chosen
     * @param x          for inventory: top-left x of the 44 px slot.
     *                   for ground:    tile-centre x in screen space.
     * @param y          same convention, vertical axis.
     * @param isInventory true → 28×28 sprite centred inside 44 px slot.
     *                    false → 20×20 sprite centred on (x, y).
     */
    public static void drawCoinStack(Graphics2D g, int amount, int x, int y, boolean isInventory) {
        String key = amount == 1 ? "coin" : amount <= 99 ? "multiple_coins" : "coin_stack";
        BufferedImage img = getImage(key);

        int iconSize = isInventory ? 28 : 20;
        int drawX, drawY;
        if (isInventory) {
            // Centre the icon inside the 44 px inventory slot.
            int slotSize = 44;
            drawX = x + (slotSize - iconSize) / 2;
            drawY = y + (slotSize - iconSize) / 2;
        } else {
            // (x, y) is the tile centre; place icon centred there.
            drawX = x - iconSize / 2;
            drawY = y - iconSize / 2;
        }

        if (img != null) {
            g.drawImage(img, drawX, drawY, iconSize, iconSize, null);
        } else {
            // Fallback: golden oval when sprites are not loaded.
            g.setColor(new Color(255, 210, 0));
            g.fillOval(drawX + 2, drawY + 2, iconSize - 4, iconSize - 4);
            g.setColor(new Color(180, 140, 0));
            g.drawOval(drawX + 2, drawY + 2, iconSize - 4, iconSize - 4);
        }

        if (amount <= 1) return;

        String text = formatStackAmount(amount);
        Color textColor = amount < 100_000    ? new Color(255, 255, 0)
                        : amount < 10_000_000 ? Color.WHITE
                        :                       new Color(0, 255, 0);

        g.setFont(new Font("Arial", Font.BOLD, isInventory ? 9 : 7));
        FontMetrics fm = g.getFontMetrics();

        // Bottom-right corner of the icon.
        int textX = drawX + iconSize - fm.stringWidth(text) - 1;
        int textY = drawY + iconSize - 2;

        // Black outline for readability.
        g.setColor(Color.BLACK);
        g.drawString(text, textX - 1, textY);
        g.drawString(text, textX + 1, textY);
        g.drawString(text, textX,     textY - 1);
        g.drawString(text, textX,     textY + 1);
        // Coloured text on top.
        g.setColor(textColor);
        g.drawString(text, textX, textY);
    }

    /**
     * Format a coin amount OSRS-style:
     *   1–999        → exact number  ("250")
     *   1 000–999 999 → thousands    ("1K", "25K")
     *   1 000 000+   → millions      ("1M", "10M")
     */
    public static String formatStackAmount(int amount) {
        if (amount >= 1_000_000) return (amount / 1_000_000) + "M";
        if (amount >= 1_000)     return (amount / 1_000)     + "K";
        return String.valueOf(amount);
    }
}
