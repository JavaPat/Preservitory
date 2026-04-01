package com.classic.preservitory.ui.panels;

import com.classic.preservitory.entity.Player;
import com.classic.preservitory.ui.quests.QuestEntry;
import com.classic.preservitory.util.Constants;

import java.awt.*;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Draws and handles input for the fixed right-side panel.
 *
 * === Layout (all Y values are screen-absolute) ===
 *   0  –  82 : Stats bar — HP progress bar + current activity label
 *   82 – 110 : Tab icons — [CMB] [INV] [SKL] [EQP] [QST]
 *  110 – 520 : Tab content — delegated to the active tab class
 *  520 – 600 : Footer — keyboard shortcuts
 *
 * === Tab routing ===
 *   GameInputHandler detects tab-bar clicks, resolves the clicked tab index,
 *   and updates TabManager.
 *   Content-area clicks are routed via handleClick() to the active tab.
 */
public class RightPanel {

    private int hoveredTabIndex = -1;

    // -----------------------------------------------------------------------
    //  Layout constants — package-private so GameInputHandler can reference them
    // -----------------------------------------------------------------------

    static final int STATS_H   = 82;
    static final int TAB_Y     = STATS_H;           //  82
    static final int TAB_H     = 24;
    static final int CONTENT_Y = TAB_Y + TAB_H;     // 110
    static final int CONTENT_H = 410;
    static final int FOOTER_Y  = CONTENT_Y + CONTENT_H; // 520

    /** Number of tabs; used by GameRenderer and GameInputHandler. */
    static final int TAB_COUNT = TabConfig.TABS.size();

    // -----------------------------------------------------------------------
    //  Tab management + tab instances
    // -----------------------------------------------------------------------

    /** Package-private so GameInputHandler can call tabManager.setTab() directly. */
    final TabManager tabManager = new TabManager();

    private final CombatTab     combatTab     = new CombatTab();
    private final InventoryTab  inventoryTab  = new InventoryTab();
    private final SkillsTab     skillsTab     = new SkillsTab();
    private final EquipmentTab  equipmentTab  = new EquipmentTab();
    private final QuestTab      questTab      = new QuestTab();

    /** Single dispatch map — all content-area clicks go through Tab.handleClick(). */
    private final Map<TabType, Tab> tabHandlers = new EnumMap<>(TabType.class);
    {
        tabHandlers.put(TabType.COMBAT,     combatTab);
        tabHandlers.put(TabType.INVENTORY,  inventoryTab);
        tabHandlers.put(TabType.SKILLS,     skillsTab);
        tabHandlers.put(TabType.EQUIPMENT,  equipmentTab);
        tabHandlers.put(TabType.QUESTS,     questTab);
    }

    // -----------------------------------------------------------------------
    //  Configuration — delegate to tabs
    // -----------------------------------------------------------------------

    public void setQuestEntries(List<QuestEntry> entries) {
        questTab.setQuestEntries(entries);
    }

    public void setCombatStyleListener(Consumer<String> listener) {
        combatTab.setCombatStyleListener(listener);
    }

    public void setUnequipListener(Consumer<String> listener) {
        equipmentTab.setUnequipListener(listener);
    }

    // -----------------------------------------------------------------------
    //  Public API — input
    // -----------------------------------------------------------------------

    public int getTabIndexAtScreenX(int sx) {
        int px = Constants.PANEL_X;
        int pw = Constants.PANEL_W;
        if (sx < px || sx >= px + pw) return -1;

        int tabW = pw / TAB_COUNT;
        int rel  = sx - px;
        return Math.min(TAB_COUNT - 1, rel / tabW);
    }

    public Rectangle getTabBounds(int index) {
        int px    = Constants.PANEL_X;
        int pw    = Constants.PANEL_W;
        int tabW  = pw / TAB_COUNT;
        int slotX = px + index * tabW;
        int slotW = (index == TAB_COUNT - 1) ? pw - index * tabW : tabW;
        return new Rectangle(slotX, TAB_Y, slotW, TAB_H);
    }

    public TabType getActiveTab() {
        return tabManager.getActiveTab();
    }

    public int getHoveredTabIndex() {
        return hoveredTabIndex;
    }

    public void setHoveredTabIndex(int hoveredTabIndex) {
        this.hoveredTabIndex = hoveredTabIndex;
    }

    public void onTabSelected(TabType tab) {
        if (tab == TabType.SKILLS) {
            skillsTab.resetScroll();
        }
    }

    /**
     * Called by GameInputHandler for content-area clicks (below the tab bar).
     * Returns true if the click was consumed.
     */
    public boolean handleClick(int sx, int sy) {
        int px = Constants.PANEL_X;
        int pw = Constants.PANEL_W;
        if (sx < px || sx >= px + pw) return false;

        tabHandlers.get(tabManager.getActiveTab()).handleClick(sx, sy, px, pw);
        return true;
    }

    /**
     * Update hover state for the inventory grid.
     */
    public void handleMouseMove(int sx, int sy) {
        if (tabManager.getActiveTab() == TabType.INVENTORY) {
            inventoryTab.handleMouseMove(sx, sy);
        }
    }

    /**
     * Scroll the Skills tab up (direction = -1) or down (direction = +1).
     */
    public void handleMouseWheel(int direction) {
        if (tabManager.getActiveTab() == TabType.SKILLS) {
            skillsTab.handleMouseWheel(direction);
        }
    }

    /**
     * Returns the itemId of the inventory slot at the given screen position,
     * or -1 if no slot is there.
     */
    public int getClickedInventoryItemId(int sx, int sy, Player player) {
        return inventoryTab.getClickedItemId(sx, sy, player);
    }

    public String getHoveredInventoryItemName(Player player) {
        if (tabManager.getActiveTab() != TabType.INVENTORY) {
            return null;
        }
        return inventoryTab.getHoveredItemName(player);
    }

    // -----------------------------------------------------------------------
    //  Rendering entry point
    // -----------------------------------------------------------------------

    public void render(Graphics2D g, Player player,
                       boolean shopOpen,
                       Map<Integer, Integer> sellPrices,
                       boolean isChopping, boolean isMining,
                       boolean isInCombat, String combatTarget) {
        int px = Constants.PANEL_X;
        int pw = Constants.PANEL_W;

        // Panel background
        g.setColor(new Color(20, 16, 12));
        g.fillRect(px, 0, pw, Constants.SCREEN_HEIGHT);

        // Inner bevelled border
        g.setColor(new Color(65, 52, 30));
        g.drawLine(px + 2, 2, px + pw - 3, 2);
        g.drawLine(px + 2, 2, px + 2, Constants.SCREEN_HEIGHT - 3);
        g.setColor(new Color(30, 24, 14));
        g.drawLine(px + pw - 3, 2, px + pw - 3, Constants.SCREEN_HEIGHT - 3);

        drawStatsSection(g, px, pw, player, isChopping, isMining, isInCombat, combatTarget);
        GameRenderer.drawTabBar(g, this);
        drawContentDivider(g, px, pw);

        switch (tabManager.getActiveTab()) {
            case COMBAT:    combatTab   .render(g, px, pw);                       break;
            case INVENTORY: inventoryTab.render(g, player, shopOpen, sellPrices); break;
            case SKILLS:    skillsTab   .render(g, player, px, pw);               break;
            case EQUIPMENT: equipmentTab.render(g, player, px, pw);               break;
            case QUESTS:    questTab    .render(g, px, pw);                       break;
        }

        drawFooter(g, px, pw);
    }

    // -----------------------------------------------------------------------
    //  Stats section  (Y 0–82)
    // -----------------------------------------------------------------------

    private void drawStatsSection(Graphics2D g, int px, int pw, Player player,
                                   boolean isChopping, boolean isMining,
                                   boolean isInCombat, String combatTarget) {
        int bx = px + 8;
        int bw = pw - 16;

        g.setFont(new Font("Monospaced", Font.BOLD, 11));
        drawOutlined(g, "STATS", px + pw / 2 - 18, 15,
                new Color(220, 200, 120), new Color(0, 0, 0, 180));

        float hpFrac  = (float) player.getHp() / player.getMaxHp();
        Color hpColor = hpFrac > 0.5f  ? new Color( 55, 175,  55)
                      : hpFrac > 0.25f ? new Color(215, 145,  25)
                      :                   new Color(195,  35,  35);

        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g.setColor(new Color(200, 200, 200));
        g.drawString("HP:  " + player.getHp() + " / " + player.getMaxHp(), bx, 33);

        g.setColor(new Color(40, 0, 0));
        g.fillRect(bx, 37, bw, 9);
        g.setColor(hpColor);
        g.fillRect(bx, 37, (int)(bw * hpFrac), 9);
        g.setColor(new Color(70, 70, 70));
        g.drawRect(bx, 37, bw, 9);

        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        String status;
        Color  statusColor;
        if (isInCombat && combatTarget != null) {
            status      = "Fighting: " + combatTarget;
            statusColor = new Color(220, 95, 95);
        } else if (isChopping) {
            status      = "Chopping tree...";
            statusColor = new Color(180, 205, 100);
        } else if (isMining) {
            status      = "Mining rock...";
            statusColor = new Color(130, 155, 220);
        } else {
            status      = "Idle";
            statusColor = new Color(120, 120, 120);
        }
        g.setColor(statusColor);
        g.drawString(status, bx, 62);

        g.setColor(new Color(80, 65, 35));
        g.drawLine(px + 4, STATS_H - 2, px + pw - 4, STATS_H - 2);
    }

    private void drawContentDivider(Graphics2D g, int px, int pw) {
        g.setColor(new Color(90, 72, 38));
        g.drawLine(px + 3, CONTENT_Y, px + pw - 3, CONTENT_Y);
        g.setColor(new Color(35, 28, 14));
        g.drawLine(px + 3, CONTENT_Y + 1, px + pw - 3, CONTENT_Y + 1);
    }

    // -----------------------------------------------------------------------
    //  Footer  (Y 520–600)
    // -----------------------------------------------------------------------

    private void drawFooter(Graphics2D g, int px, int pw) {
        int bx = px + 8;

        g.setColor(new Color(80, 65, 35));
        g.drawLine(px + 3, FOOTER_Y, px + pw - 3, FOOTER_Y);
        g.setColor(new Color(35, 28, 14));
        g.drawLine(px + 3, FOOTER_Y + 1, px + pw - 3, FOOTER_Y + 1);

        g.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g.setColor(new Color(95, 90, 65));
        g.drawString("[Enter] Chat  [D]ebug  [M]ute", bx,
                Math.max(FOOTER_Y + 14, Constants.SCREEN_HEIGHT - 8));
    }

    // -----------------------------------------------------------------------
    //  Helper
    // -----------------------------------------------------------------------

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
