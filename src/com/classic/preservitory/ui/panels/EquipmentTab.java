package com.classic.preservitory.ui.panels;

import com.classic.preservitory.client.definitions.ItemDefinitionManager;
import com.classic.preservitory.entity.Player;
import com.classic.preservitory.util.Constants;

import java.awt.*;
import java.util.function.Consumer;

/**
 * Renders the Equipment tab content: equipped weapon and helmet slots.
 *
 * Display only — reads Player equipped item data, never modifies it.
 * Unequip actions fire a callback registered via setUnequipListener.
 */
class EquipmentTab implements Tab {

    // -----------------------------------------------------------------------
    //  Layout
    // -----------------------------------------------------------------------

    private static final int CONTENT_Y = 110;

    // -----------------------------------------------------------------------
    //  State — screen-absolute Y of each row, set during render
    // -----------------------------------------------------------------------

    private int              weaponRowY      = 0;
    private int              helmetRowY      = 0;
    private Consumer<String> unequipListener = null;

    // -----------------------------------------------------------------------
    //  Configuration
    // -----------------------------------------------------------------------

    void setUnequipListener(Consumer<String> listener) {
        this.unequipListener = listener;
    }

    // -----------------------------------------------------------------------
    //  Input
    // -----------------------------------------------------------------------

    /**
     * Handle a click inside the equipment tab content area.
     * No scroll offset — rows are rendered at screen-absolute Y positions.
     */
    @Override
    public void handleClick(int sx, int sy, int px, int pw) {
        if (unequipListener == null) return;
        if (weaponRowY > 0 && sy >= weaponRowY && sy < weaponRowY + 16)
            unequipListener.accept("WEAPON");
        if (helmetRowY > 0 && sy >= helmetRowY && sy < helmetRowY + 16)
            unequipListener.accept("HELMET");
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    void render(Graphics2D g, Player player, int px, int pw) {
        int x  = px + 8;
        int bw = pw - 16;
        int y  = CONTENT_Y + 10;

        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        drawOutlined(g, "EQUIPMENT", px + pw / 2 - 24, y + 2,
                new Color(200, 185, 100), new Color(0, 0, 0, 160));
        y += 14;

        weaponRowY = y;
        y = drawEquipRow(g, x, y, bw, "Weapon", player.getEquippedItemId("WEAPON"));
        helmetRowY = y;
        y = drawEquipRow(g, x, y, bw, "Helmet", player.getEquippedItemId("HELMET"));
    }

    // -----------------------------------------------------------------------
    //  Private draw helpers
    // -----------------------------------------------------------------------

    private int drawEquipRow(Graphics2D g, int x, int y, int bw, String slotLabel, int itemId) {
        boolean equipped = itemId != -1;
        String  itemName = equipped ? ItemDefinitionManager.get(itemId).name : "Empty";

        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(new Color(160, 150, 100));
        g.drawString(slotLabel + ":", x, y + 11);

        FontMetrics fm = g.getFontMetrics();
        int iw = fm.stringWidth(itemName);
        g.setColor(equipped ? new Color(220, 210, 150) : new Color(90, 85, 65));
        g.drawString(itemName, x + bw - iw, y + 11);

        if (equipped) {
            g.setColor(new Color(140, 110, 45, 100));
            g.drawLine(x + bw - iw, y + 12, x + bw, y + 12);
        }

        return y + 16;
    }

    private static void drawOutlined(Graphics2D g, String text, int x, int y,
                                     Color fg, Color shadow) {
        g.setColor(shadow);
        g.drawString(text, x + 1, y + 1);
        g.drawString(text, x - 1, y + 1);
        g.drawString(text, x + 1, y - 1);
        g.drawString(text, x - 1, y - 1);
        g.setColor(fg);
        g.drawString(text, x, y);
    }
}
