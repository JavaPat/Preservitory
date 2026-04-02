package com.classic.preservitory.ui.panels;

import com.classic.preservitory.client.definitions.ItemIds;
import com.classic.preservitory.entity.Player;
import com.classic.preservitory.item.Item;
import com.classic.preservitory.ui.framework.assets.AssetManager;
import com.classic.preservitory.util.Constants;

import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Renders the Inventory tab content and tracks hover / click state for item slots.
 *
 * Display only — reads Player inventory data, never modifies it.
 * Equip / sell actions are routed through RightPanel and GameInputHandler.
 */
class InventoryTab implements Tab {

    // -----------------------------------------------------------------------
    //  Layout (mirrors RightPanel layout constants)
    // -----------------------------------------------------------------------

    private static final int CONTENT_Y = 110;
    private static final int FOOTER_Y  = 520;

    // -----------------------------------------------------------------------
    //  Grid geometry
    // -----------------------------------------------------------------------

    private static final int INV_COLS  = 4;
    private static final int INV_ROWS  = 7;
    private static final int SLOT_SIZE = 44;
    private static final int SLOT_GAP  = 3;
    private static final int SLOT_STEP = SLOT_SIZE + SLOT_GAP;  // 47 px per slot

    // -----------------------------------------------------------------------
    //  State
    // -----------------------------------------------------------------------

    private int hoverSlot = -1;

    // -----------------------------------------------------------------------
    //  Input
    // -----------------------------------------------------------------------

    /** Update the hovered slot index from the current mouse position. */
    void handleMouseMove(int sx, int sy, int panelX) {
        hoverSlot = -1;
        if (sy < CONTENT_Y || sy >= FOOTER_Y) return;
        if (sx < panelX) return;

        int gridX = panelX + (Constants.PANEL_W - INV_COLS * SLOT_STEP) / 2;
        int gridY = CONTENT_Y + 30;

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

    /**
     * Returns the itemId of the slot at (sx, sy), or -1 if no slot is there.
     * Called by RightPanel to support equip / shop-sell clicks.
     */
    int getClickedItemId(int sx, int sy, Player player, int panelX) {
        if (sx < panelX || sy < CONTENT_Y || sy >= FOOTER_Y) return -1;

        int gridX = panelX + (Constants.PANEL_W - INV_COLS * SLOT_STEP) / 2;
        int gridY = CONTENT_Y + 30;
        List<Item> slots = player.getInventory().getSlots();

        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                int idx = row * INV_COLS + col;
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
        if (hoverSlot < 0 || hoverSlot >= slots.size()) {
            return null;
        }
        Item item = slots.get(hoverSlot);
        return item != null ? item.getName() : null;
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    void render(Graphics2D g, int panelX, Player player, boolean shopOpen, Map<Integer, Integer> sellPrices) {
        int px = panelX;
        int pw = Constants.PANEL_W;
        int bx = px + 8;
        int contentStartY = CONTENT_Y + 6;

        // Header
        int invCount = player.getInventory().getSlotCount();
        g.setFont(new Font("Arial", Font.BOLD, 10));
        drawOutlined(g, "INVENTORY   " + invCount + " / 28",
                bx, contentStartY + 12,
                new Color(200, 185, 100), new Color(0, 0, 0, 160));
        if (shopOpen) {
            g.setFont(new Font("Arial", Font.PLAIN, 9));
            g.setColor(new Color(120, 180, 120));
            //g.drawString("Click an item to sell", bx, contentStartY + 24);
        }

        // Grid
        int gridX = px + (pw - INV_COLS * SLOT_STEP) / 2;
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
                drawSlot(g, slotX, slotY, item, hov, price);
            }
        }

    }

    // -----------------------------------------------------------------------
    //  Private draw helpers
    // -----------------------------------------------------------------------

    private void drawSlot(Graphics2D g, int x, int y, Item item, boolean hovered, Integer sellPrice) {
        // Background
        Color bg = hovered
                ? (item != null ? new Color(90, 75, 38, 230) : new Color(60, 50, 28, 220))
                : (item != null ? new Color(45, 38, 22, 220) : new Color(22, 18, 12, 210));
        g.setColor(bg);
        g.fillRect(x, y, SLOT_SIZE, SLOT_SIZE);

        // Outer border
        g.setColor(hovered ? new Color(210, 180, 70, 240) : new Color(72, 60, 30, 200));
        g.drawRect(x, y, SLOT_SIZE, SLOT_SIZE);

        // Inner bevel
        g.setColor(hovered ? new Color(255, 225, 100, 80) : new Color(38, 32, 16, 140));
        g.drawRect(x + 1, y + 1, SLOT_SIZE - 2, SLOT_SIZE - 2);

        if (item == null) return;

        if (item.getItemId() == ItemIds.COINS) {
            // OSRS-style coin stack sprite + formatted amount text.
            AssetManager.drawCoinStack(g, item.getCount(), x, y, true);
        } else {
            // Item icon — rounded coloured rectangle
            Color ic  = iconColorFor(item.getName());
            int   pad = 7;
            int   iw  = SLOT_SIZE - pad * 2;
            int   ih  = SLOT_SIZE - pad * 2 - 4;
            g.setColor(ic);
            g.fillRoundRect(x + pad, y + pad, iw, ih, 5, 5);
            g.setColor(ic.brighter().brighter());
            g.drawLine(x + pad + 2, y + pad + 2, x + pad + iw / 3, y + pad + 2);
            g.setColor(ic.darker().darker());
            g.drawRoundRect(x + pad, y + pad, iw, ih, 5, 5);

            // Stack count
            if (item.isStackable() && item.getCount() > 1) {
                String cnt = item.getCount() >= 1000 ? (item.getCount() / 1000) + "k"
                           : String.valueOf(item.getCount());
                g.setFont(new Font("Arial", Font.BOLD, 9));
                g.setColor(new Color(0, 0, 0, 210));
                g.drawString(cnt, x + 3 + 1, y + SLOT_SIZE - 3 + 1);
                g.setColor(new Color(255, 230, 0));
                g.drawString(cnt, x + 3, y + SLOT_SIZE - 3);
            }
        }

        // Sell price overlay
        if (sellPrice != null) {
            String price = sellPrice + "c";
            g.setFont(new Font("Arial", Font.BOLD, 8));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(0, 0, 0, 180));
            g.drawString(price, x + SLOT_SIZE - fm.stringWidth(price) - 3 + 1, y + 10 + 1);
            g.setColor(new Color(140, 220, 140));
            g.drawString(price, x + SLOT_SIZE - fm.stringWidth(price) - 3, y + 10);
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

    private static void drawOutlined(Graphics2D g, String text, int x, int y, Color fg, Color shadow) {
        g.setColor(shadow);
        g.drawString(text, x + 1, y + 1);
        g.drawString(text, x - 1, y + 1);
        g.drawString(text, x + 1, y - 1);
        g.drawString(text, x - 1, y - 1);
        g.setColor(fg);
        g.drawString(text, x, y);
    }
}
