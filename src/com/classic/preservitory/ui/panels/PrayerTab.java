package com.classic.preservitory.ui.panels;

import com.classic.preservitory.entity.Player;
import com.classic.preservitory.ui.framework.TabRenderer;
import com.classic.preservitory.ui.framework.assets.AssetManager;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;

/**
 * Prayer tab — icon grid of all available prayers.
 *
 * === Grid layout ===
 *   ICON_SIZE    = 32 px
 *   GRID_COLUMNS = 4
 *   GRID_SPACING = 8 px
 *
 *   innerX     = contentX + CONTENT_PADDING
 *   innerY     = contentY + CONTENT_PADDING
 *   innerWidth = contentWidth - (CONTENT_PADDING × 2)
 *   gridWidth  = (GRID_COLUMNS × ICON_SIZE) + ((GRID_COLUMNS-1) × GRID_SPACING)
 *   gridStartX = innerX + (innerWidth - gridWidth) / 2
 */
class PrayerTab implements TabRenderer {

    private static final int ICON_SIZE    = 32;
    private static final int GRID_COLUMNS = 4;
    private static final int GRID_SPACING = 8;
    private static final int ICON_STEP    = ICON_SIZE + GRID_SPACING;

    private Player            player        = null;
    private Consumer<String>  toggleListener;
    private int               hoveredIndex  = -1;

    // Bounds cached from the last render — used by click/hover helpers
    private int lastX, lastY, lastWidth;

    void setPlayer(Player p) { this.player = p; }

    void setPrayerToggleListener(Consumer<String> listener) {
        this.toggleListener = listener;
    }

    @Override
    public void handleMouseMove(int sx, int sy, int x, int y, int width, int height) {
        lastX = x; lastY = y; lastWidth = width;
        hoveredIndex = indexAt(sx, sy, x, y, width);
    }

    @Override
    public String getHoveredLabel(int sx, int sy, int x, int y, int width, int height) {
        int idx = indexAt(sx, sy, x, y, width);
        if (idx < 0) return null;
        PrayerDefinition def = PrayerDefinitionRegistry.PRAYERS.get(idx);
        return def.name + " (Lv." + def.levelRequired + ") \u2014 " + def.description;
    }

    @Override
    public void handleClick(int sx, int sy, int x, int y, int width, int height) {
        int idx = indexAt(sx, sy, x, y, width);
        if (idx < 0 || idx >= PrayerDefinitionRegistry.PRAYERS.size()) return;
        if (toggleListener != null) {
            toggleListener.accept(PrayerDefinitionRegistry.PRAYERS.get(idx).id);
        }
    }

    @Override
    public void render(Graphics2D g, int x, int y, int width, int height) {
        lastX = x; lastY = y; lastWidth = width;
        if (player == null) return;

        int prayerLevel = 1;
        com.classic.preservitory.entity.Skill prayerSkill =
                player.getSkillSystem().getSkill("prayer");
        if (prayerSkill != null) prayerLevel = prayerSkill.getLevel();

        List<PrayerDefinition> prayers = PrayerDefinitionRegistry.PRAYERS;
        int gsx = gridStartX(x, y, width);
        int gsy = y + RightPanel.CONTENT_PADDING;

        for (int i = 0; i < prayers.size(); i++) {
            PrayerDefinition prayer = prayers.get(i);
            int col   = i % GRID_COLUMNS;
            int row   = i / GRID_COLUMNS;
            int iconX = gsx + col * ICON_STEP;
            int iconY = gsy + row * ICON_STEP;

            boolean active  = player.isPrayerActive(prayer.id);
            boolean locked  = prayerLevel < prayer.levelRequired;
            boolean hovered = (i == hoveredIndex);

            drawPrayerIcon(g, prayer, iconX, iconY, active, locked, hovered);
        }
    }

    // -----------------------------------------------------------------------
    //  Private draw helpers
    // -----------------------------------------------------------------------

    private void drawPrayerIcon(Graphics2D g, PrayerDefinition prayer,
                                int x, int y,
                                boolean active, boolean locked, boolean hovered) {
        Color bgColor = active  ? new Color(30, 70, 30, 230)
                      : hovered ? new Color(50, 50, 30, 220)
                      :           new Color(22, 18, 12, 210);
        g.setColor(bgColor);
        g.fillRect(x, y, ICON_SIZE, ICON_SIZE);

        BufferedImage sprite = AssetManager.getImage(prayer.spriteKey);
        if (sprite != null) {
            g.drawImage(sprite, x + 2, y + 2, ICON_SIZE - 4, ICON_SIZE - 4, null);
            if (locked) {
                g.setColor(new Color(0, 0, 0, 130));
                g.fillRect(x + 2, y + 2, ICON_SIZE - 4, ICON_SIZE - 4);
            }
        } else {
            Color iconColor = locked ? new Color(60, 55, 45) : new Color(80, 130, 80);
            g.setColor(iconColor);
            g.fillRoundRect(x + 3, y + 3, ICON_SIZE - 6, ICON_SIZE - 6, 4, 4);
            g.setFont(new Font("Arial", Font.BOLD, 9));
            String letter = prayer.name.substring(0, 1);
            FontMetrics fm = g.getFontMetrics();
            g.setColor(locked ? new Color(100, 90, 70) : Color.WHITE);
            g.drawString(letter,
                    x + (ICON_SIZE - fm.stringWidth(letter)) / 2,
                    y + (ICON_SIZE + fm.getAscent() - fm.getDescent()) / 2);
        }

        if (active) {
            g.setColor(new Color(200, 170, 50));
            g.drawRect(x, y, ICON_SIZE - 1, ICON_SIZE - 1);
            g.setColor(new Color(255, 220, 80, 120));
            g.drawRect(x + 1, y + 1, ICON_SIZE - 3, ICON_SIZE - 3);
        } else if (hovered && !locked) {
            g.setColor(new Color(180, 170, 100, 200));
            g.drawRect(x, y, ICON_SIZE - 1, ICON_SIZE - 1);
        } else {
            g.setColor(new Color(55, 44, 22, 180));
            g.drawRect(x, y, ICON_SIZE - 1, ICON_SIZE - 1);
        }

        if (locked) {
            g.setFont(new Font("Arial", Font.BOLD, 7));
            String lvl = String.valueOf(prayer.levelRequired);
            FontMetrics fm = g.getFontMetrics();
            int lx = x + ICON_SIZE - fm.stringWidth(lvl) - 2;
            int ly = y + ICON_SIZE - 2;
            g.setColor(new Color(0, 0, 0, 180));
            g.drawString(lvl, lx + 1, ly + 1);
            g.setColor(new Color(200, 150, 80));
            g.drawString(lvl, lx, ly);
        }
    }

    // -----------------------------------------------------------------------
    //  Grid geometry (spec-defined formulas)
    // -----------------------------------------------------------------------

    private static int gridStartX(int x, int y, int width) {
        int innerX     = x + RightPanel.CONTENT_PADDING;
        int innerWidth = width - (RightPanel.CONTENT_PADDING * 2);
        int gridWidth  = (GRID_COLUMNS * ICON_SIZE) + ((GRID_COLUMNS - 1) * GRID_SPACING);
        return innerX + (innerWidth - gridWidth) / 2;
    }

    private int indexAt(int sx, int sy, int x, int y, int width) {
        int gsx = gridStartX(x, y, width);
        int gsy = y + RightPanel.CONTENT_PADDING;
        List<PrayerDefinition> prayers = PrayerDefinitionRegistry.PRAYERS;

        for (int i = 0; i < prayers.size(); i++) {
            int col   = i % GRID_COLUMNS;
            int row   = i / GRID_COLUMNS;
            int iconX = gsx + col * ICON_STEP;
            int iconY = gsy + row * ICON_STEP;
            if (sx >= iconX && sx < iconX + ICON_SIZE
             && sy >= iconY && sy < iconY + ICON_SIZE) {
                return i;
            }
        }
        return -1;
    }
}
