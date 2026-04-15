package com.classic.preservitory.ui.panels;

import com.classic.preservitory.client.definitions.ItemDefinitionManager;
import com.classic.preservitory.client.definitions.ItemIds;
import com.classic.preservitory.entity.Player;
import com.classic.preservitory.item.Item;
import com.classic.preservitory.ui.framework.TabRenderer;
import com.classic.preservitory.ui.framework.assets.AssetManager;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/**
 * Renders the Inventory tab content and tracks hover / click state for item slots.
 *
 * Display only — reads Player inventory data, never modifies it.
 * Equip / sell actions are routed through RightPanel and GameInputHandler.
 */
public class InventoryTab implements TabRenderer {

    // Grid geometry — single source of truth for slot size and grid dimensions
    public static final int INV_COLS  = 4;
    public static final int INV_ROWS  = 7;
    public static final int SLOT_SIZE = 44;
    public static final int SLOT_GAP  = 3;
    static final        int SLOT_STEP = SLOT_SIZE + SLOT_GAP;

    // State set before render
    private Player                  player     = null;
    private boolean                 shopOpen   = false;
    private Map<Integer, Integer>   sellPrices = null;

    // Hover state
    private int hoverSlot = -1;

    // Bounds cached from last render — used by external callers
    private int lastX, lastY, lastWidth, lastHeight;

    // -----------------------------------------------------------------------
    //  Context setters (called by RightPanel before each render)
    // -----------------------------------------------------------------------

    void setPlayer(Player p) { this.player = p; }

    void setShopState(boolean shopOpen, Map<Integer, Integer> sellPrices) {
        this.shopOpen   = shopOpen;
        this.sellPrices = sellPrices;
    }

    // -----------------------------------------------------------------------
    //  TabRenderer — input
    // -----------------------------------------------------------------------

    @Override
    public void handleMouseMove(int sx, int sy, int x, int y, int width, int height) {
        lastX = x; lastY = y; lastWidth = width; lastHeight = height;
        hoverSlot = -1;
        if (sy < y || sy >= y + height) return;
        if (sx < x) return;

        int gridX = x + (width - INV_COLS * SLOT_STEP) / 2;
        int gridY = y + 30;

        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                int slotX = gridX + col * SLOT_STEP;
                int slotY = gridY + row * SLOT_STEP;
                if (sx >= slotX && sx < slotX + SLOT_SIZE
                 && sy >= slotY && sy < slotY + SLOT_SIZE) {
                    hoverSlot = row * INV_COLS + col;
                    return;
                }
            }
        }
    }

    @Override
    public void render(Graphics2D g, int x, int y, int width, int height) {
        lastX = x; lastY = y; lastWidth = width; lastHeight = height;
        if (player == null) return;

        int bx = x + 8;
        int contentStartY = y + 6;

        int invCount = player.getInventory().getSlotCount();
        g.setFont(new Font("Arial", Font.BOLD, 10));
        drawOutlined(g, "INVENTORY   " + invCount + " / 28",
                bx, contentStartY + 12,
                new Color(200, 185, 100), new Color(0, 0, 0, 160));

        int gridX = x + (width - INV_COLS * SLOT_STEP) / 2;
        int gridY = contentStartY + 24;
        List<Item> slots = player.getInventory().getSlots();

        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                int idx       = row * INV_COLS + col;
                int slotX     = gridX + col * SLOT_STEP;
                int slotY     = gridY + row * SLOT_STEP;
                Item item     = idx < slots.size() ? slots.get(idx) : null;
                boolean hov   = (idx == hoverSlot);
                Integer price = (shopOpen && item != null && sellPrices != null)
                        ? sellPrices.get(item.getItemId()) : null;
                drawSlot(g, slotX, slotY, SLOT_SIZE, item, hov, false, price);
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Package-private helpers called from RightPanel / GameInputHandler
    // -----------------------------------------------------------------------

    /**
     * Returns the itemId of the slot at (sx, sy), or -1 if none.
     * Uses stored bounds from the last render call.
     */
    int getClickedItemId(int sx, int sy, Player player, int panelX) {
        if (lastHeight == 0) return -1;
        if (sx < lastX || sy < lastY || sy >= lastY + lastHeight) return -1;

        int gridX = lastX + (lastWidth - INV_COLS * SLOT_STEP) / 2;
        int gridY = lastY + 30;
        List<Item> slots = player.getInventory().getSlots();

        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                int idx   = row * INV_COLS + col;
                if (idx >= slots.size()) return -1;
                int slotX = gridX + col * SLOT_STEP;
                int slotY = gridY + row * SLOT_STEP;
                if (sx >= slotX && sx < slotX + SLOT_SIZE
                 && sy >= slotY && sy < slotY + SLOT_SIZE) {
                    Item slotItem = slots.get(idx);
                    return slotItem != null ? slotItem.getItemId() : -1;
                }
            }
        }
        return -1;
    }

    String getHoveredItemName(Player player) {
        List<Item> slots = player.getInventory().getSlots();
        if (hoverSlot < 0 || hoverSlot >= slots.size()) return null;
        Item item = slots.get(hoverSlot);
        return item != null ? item.getName() : null;
    }

    // -----------------------------------------------------------------------
    //  Private draw helpers
    // -----------------------------------------------------------------------

    public static void drawSlot(Graphics2D g, int x, int y, int slotSize,
                                Item item, boolean hovered, boolean selected, Integer sellPrice) {
        Color bg = hovered
                ? (item != null ? new Color(74, 58, 30, 232) : new Color(52, 40, 22, 224))
                : (item != null ? new Color(36, 28, 16, 224) : new Color(24, 18, 12, 214));
        g.setColor(bg);
        g.fillRoundRect(x, y, slotSize, slotSize, 4, 4);

        g.setColor(new Color(18, 12, 8, 180));
        g.drawRoundRect(x, y, slotSize, slotSize, 4, 4);

        g.setColor(hovered ? new Color(126, 96, 48, 140) : new Color(84, 62, 34, 110));
        g.drawRoundRect(x + 1, y + 1, slotSize - 2, slotSize - 2, 3, 3);
        g.setColor(new Color(132, 104, 60, 55));
        g.drawLine(x + 3, y + 2, x + slotSize - 4, y + 2);
        g.drawLine(x + 2, y + 3, x + 2, y + slotSize - 4);
        g.setColor(new Color(6, 4, 2, 145));
        g.drawLine(x + 3, y + slotSize - 2, x + slotSize - 3, y + slotSize - 2);
        g.drawLine(x + slotSize - 2, y + 3, x + slotSize - 2, y + slotSize - 3);

        if (selected) {
            g.setColor(new Color(255, 208, 72, 240));
            g.drawRoundRect(x - 1, y - 1, slotSize + 2, slotSize + 2, 5, 5);
            g.setColor(new Color(88, 62, 28, 200));
            g.drawRoundRect(x + 1, y + 1, slotSize - 2, slotSize - 2, 3, 3);
        }

        if (item == null) return;

        if (item.getItemId() == ItemIds.COINS) {
            AssetManager.drawCoinStack(g, item.getCount(), x, y, true);
        } else {
            String spriteKey = ItemDefinitionManager.get(item.getItemId()).spriteKey;
            BufferedImage sprite = spriteKey != null ? AssetManager.getImage(spriteKey) : null;
            int pad = Math.max(4, slotSize / 7);
            int iSize = slotSize - pad * 2;
            if (sprite != null) {
                g.drawImage(sprite, x + pad, y + pad, iSize, iSize, null);
            } else {
                Color ic = iconColorFor(item.getName());
                int ih = iSize - 4;
                g.setColor(ic);
                g.fillRoundRect(x + pad, y + pad, iSize, ih, 5, 5);
                g.setColor(ic.brighter().brighter());
                g.drawLine(x + pad + 2, y + pad + 2, x + pad + iSize / 3, y + pad + 2);
                g.setColor(ic.darker().darker());
                g.drawRoundRect(x + pad, y + pad, iSize, ih, 5, 5);
            }

            if (item.isStackable() && item.getCount() > 1) {
                String cnt = item.getCount() >= 1000 ? (item.getCount() / 1000) + "k"
                           : String.valueOf(item.getCount());
                g.setFont(new Font("Arial", Font.BOLD, 9));
                FontMetrics fm = g.getFontMetrics();
                int textX = x + slotSize - fm.stringWidth(cnt) - 3;
                int textY = y + slotSize - 3;
                g.setColor(new Color(0, 0, 0, 210));
                g.drawString(cnt, textX + 1, textY + 1);
                g.drawString(cnt, textX + 1, textY);
                g.setColor(Color.WHITE);
                g.drawString(cnt, textX, textY);
            }
        }

        if (sellPrice != null) {
            String price = sellPrice + "c";
            g.setFont(new Font("Arial", Font.BOLD, 8));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(0, 0, 0, 180));
            g.drawString(price, x + slotSize - fm.stringWidth(price) - 3 + 1, y + 10 + 1);
            g.setColor(new Color(140, 220, 140));
            g.drawString(price, x + slotSize - fm.stringWidth(price) - 3, y + 10);
        }

    }

    private static Color iconColorFor(String name) {
        switch (name) {
            case "Logs":   return new Color(139,  90,  43);
            case "Ore":    return new Color(160,  88,  65);
            case "Coins":  return new Color(240, 200,  40);
            case "Stone":  return new Color(130, 130, 130);
            case "Candle": return new Color(240, 230, 120);
            case "Rope":   return new Color(160, 130,  80);
            case "Gem":    return new Color( 80, 180, 220);
            default:       return new Color(180, 180,  60);
        }
    }

    public static void drawOutlined(Graphics2D g, String text, int x, int y, Color fg, Color shadow) {
        g.setColor(shadow);
        g.drawString(text, x + 1, y + 1);
        g.drawString(text, x - 1, y + 1);
        g.drawString(text, x + 1, y - 1);
        g.drawString(text, x - 1, y - 1);
        g.setColor(fg);
        g.drawString(text, x, y);
    }
}
