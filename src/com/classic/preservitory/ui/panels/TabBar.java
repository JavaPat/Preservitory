package com.classic.preservitory.ui.panels;

import com.classic.preservitory.ui.framework.UIComponent;
import com.classic.preservitory.ui.framework.assets.AssetManager;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * UIComponent representing one tab bar (top or bottom).
 *
 * Renders:
 *   1. tabs_bar background sprite scaled to fill the bar
 *   2. Evenly-distributed tab slots with icon highlights and active/hover states
 *
 * All positioning is derived from the component bounds (x, y, width, height)
 * — no hardcoded panel-absolute coordinates.
 */
class TabBar extends UIComponent {

    static final int   ICON_SIZE         = 24;
    static final Color ACTIVE_SLOT_COLOR = new Color(72, 58, 32, 200);

    private final List<Tab>  tabs;
    private final TabManager tabManager;
    private int hoveredIndex = -1;   // index within this bar's tab list, or -1

    TabBar(int x, int y, int width, int height, List<Tab> tabs, TabManager tabManager) {
        super(x, y, width, height);
        this.tabs       = tabs;
        this.tabManager = tabManager;
    }

    // -----------------------------------------------------------------------
    //  UIComponent — render
    // -----------------------------------------------------------------------

    @Override
    public void render(Graphics2D g) {
        // 1. Background sprite
        BufferedImage barSprite = AssetManager.getImage("tabs_bar");
        if (barSprite != null) {
            g.drawImage(barSprite, x, y, width, height, null);
        } else {
            g.setColor(new Color(28, 22, 14));
            g.fillRect(x, y, width, height);
        }

        // 2. Tab slots — evenly distributed across the full bar width
        int count    = tabs.size();
        int slotW    = width / count;
        int iconY    = y + (height - ICON_SIZE) / 2;
        TabType activeType = tabManager.getActiveTabType();

        for (int i = 0; i < count; i++) {
            Tab     tab     = tabs.get(i);
            int     slotX   = x + i * slotW;
            int     iconX   = slotX + (slotW - ICON_SIZE) / 2;
            boolean active  = tab.type == activeType;
            boolean hovered = i == hoveredIndex;
            drawTabSlot(g, tab, slotX, slotW, iconX, iconY, height, active, hovered);
        }
    }

    // -----------------------------------------------------------------------
    //  UIComponent — input
    // -----------------------------------------------------------------------

    @Override
    public void handleMouseMove(int mx, int my) {
        hoveredIndex = -1;
        if (!contains(mx, my)) return;
        int count = tabs.size();
        int slotW = width / count;
        for (int i = 0; i < count; i++) {
            int slotX = x + i * slotW;
            if (mx >= slotX && mx < slotX + slotW) {
                hoveredIndex = i;
                return;
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    /** Returns the bounds of tab slot {@code index} within this bar (screen-absolute). */
    Rectangle getTabBounds(int index) {
        int slotW = width / tabs.size();
        int slotX = x + index * slotW;
        return new Rectangle(slotX, y, slotW, height);
    }

    /**
     * Returns the slot bounds of the first tab whose type matches {@code type},
     * or {@code null} if this bar contains no such tab.
     */
    Rectangle getActiveSlotBounds(TabType type) {
        int slotW = width / tabs.size();
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).type == type) {
                return new Rectangle(x + i * slotW, y, slotW, height);
            }
        }
        return null;
    }

    List<Tab> getTabs() { return tabs; }

    int getHoveredIndex()          { return hoveredIndex; }
    void setHoveredIndex(int idx)  { hoveredIndex = idx; }

    // -----------------------------------------------------------------------
    //  Private rendering
    // -----------------------------------------------------------------------

    /**
     * @param slotX   left edge of the evenly-distributed slot (screen-absolute)
     * @param slotW   width of the slot (= bar.width / count)
     * @param iconX   pre-computed icon left edge (centered in slot)
     * @param iconY   pre-computed icon top edge (centered in bar height)
     * @param barH    full height of this bar — used to open the bottom of the active tab
     */
    private static void drawTabSlot(Graphics2D g, Tab tab,
                                    int slotX, int slotW,
                                    int iconX, int iconY, int barH,
                                    boolean active, boolean hovered) {
        int barY = iconY - (barH - ICON_SIZE) / 2;   // top of the bar row

        // Active: slightly brighter fill to lift the tab from the background
        if (active) {
            g.setColor(ACTIVE_SLOT_COLOR);
            g.fillRect(slotX + 1, barY + 1, slotW - 2, barH - 1);   // bottom open
        }

        // Hover overlay — visible but not distracting (only when not active)
        if (hovered && !active) {
            g.setColor(new Color(255, 245, 210, 38));
            g.fillRect(slotX + 1, barY + 1, slotW - 2, barH - 2);
        }

        // Active: two-line gold highlight along the top edge (OSRS style)
        if (active) {
            g.setColor(new Color(220, 185, 80));
            g.drawLine(slotX + 2, barY,     slotX + slotW - 3, barY);
            g.setColor(new Color(170, 138, 52, 120));
            g.drawLine(slotX + 3, barY + 1, slotX + slotW - 4, barY + 1);
        }

        // Thin vertical dividers between slots — lets tabs_bar show through otherwise
        g.setColor(active  ? new Color(130, 104, 52)
                 : hovered ? new Color( 90,  72, 38)
                 :            new Color( 55,  44, 22));
        // Left divider (skip for first slot — bar edge handles it)
        if (slotX > 0) {
            g.drawLine(slotX, barY + 2, slotX, barY + barH - 3);
        }

        // Icon lifted 2px when active (gives the "pressed in" feel)
        int drawY = active ? iconY - 2 : iconY;
        drawTabIcon(g, tab, iconX, drawY, active, hovered);
    }

    private static void drawTabIcon(Graphics2D g, Tab tab, int x, int y,
                                    boolean active, boolean hovered) {
        java.awt.image.BufferedImage icon = AssetManager.getImage(tab.iconKey);
        if (icon == null) return;

        Object savedHint    = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        Composite savedComp = g.getComposite();

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // Active: full opacity; hovered: mostly opaque; inactive: dimmed
        if (!active) {
            float alpha = hovered ? 0.92f : 0.68f;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        }

        g.drawImage(icon, x, y, ICON_SIZE, ICON_SIZE, null);

        g.setComposite(savedComp);
        if (savedHint != null)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, savedHint);
    }
}
