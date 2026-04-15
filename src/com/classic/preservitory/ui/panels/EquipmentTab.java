package com.classic.preservitory.ui.panels;

import com.classic.preservitory.client.definitions.ItemDefinitionManager;
import com.classic.preservitory.entity.Player;
import com.classic.preservitory.ui.framework.TabRenderer;

import java.awt.*;
import java.util.function.Consumer;

/**
 * Renders the Equipment tab content: equipped weapon and helmet slots.
 */
class EquipmentTab implements TabRenderer {

    private Player            player          = null;
    private int               weaponRowY      = 0;
    private int               helmetRowY      = 0;
    private boolean           weaponEquipped  = false;
    private boolean           helmetEquipped  = false;
    private Consumer<String>  unequipListener = null;

    void setPlayer(Player p)                         { this.player = p; }
    void setUnequipListener(Consumer<String> listener) { unequipListener = listener; }

    @Override
    public void handleClick(int sx, int sy, int x, int y, int width, int height) {
        if (unequipListener == null) return;
        if (weaponRowY > 0 && sy >= weaponRowY && sy < weaponRowY + 16)
            unequipListener.accept("WEAPON");
        if (helmetRowY > 0 && sy >= helmetRowY && sy < helmetRowY + 16)
            unequipListener.accept("HELMET");
    }

    @Override
    public String getHoveredLabel(int sx, int sy, int x, int y, int width, int height) {
        if (weaponEquipped && weaponRowY > 0 && sy >= weaponRowY && sy < weaponRowY + 16)
            return "Unequip weapon";
        if (helmetEquipped && helmetRowY > 0 && sy >= helmetRowY && sy < helmetRowY + 16)
            return "Unequip helmet";
        return null;
    }

    @Override
    public void render(Graphics2D g, int x, int y, int width, int height) {
        if (player == null) return;

        int bx = x + 8;
        int bw = width - 16;
        int cy = y + 10;

        g.setFont(new Font("Arial", Font.BOLD, 10));
        drawOutlined(g, "EQUIPMENT", x + width / 2 - 24, cy + 2,
                new Color(200, 185, 100), new Color(0, 0, 0, 160));
        cy += 14;

        weaponRowY     = cy;
        weaponEquipped = player.getEquippedItemId("WEAPON") != -1;
        cy = drawEquipRow(g, bx, cy, bw, "Weapon", player.getEquippedItemId("WEAPON"));

        helmetRowY     = cy;
        helmetEquipped = player.getEquippedItemId("HELMET") != -1;
        cy = drawEquipRow(g, bx, cy, bw, "Helmet", player.getEquippedItemId("HELMET"));
    }

    private int drawEquipRow(Graphics2D g, int x, int y, int bw, String slotLabel, int itemId) {
        boolean equipped = itemId != -1;
        String  itemName = equipped ? ItemDefinitionManager.get(itemId).name : "Empty";

        g.setFont(new Font("Arial", Font.PLAIN, 10));
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
