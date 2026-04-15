package com.classic.preservitory.ui.panels;

import com.classic.preservitory.client.settings.ClientSettings;
import com.classic.preservitory.entity.Player;
import com.classic.preservitory.ui.framework.UIComponent;
import com.classic.preservitory.ui.framework.assets.AssetManager;
import com.classic.preservitory.ui.quests.QuestEntry;
import com.classic.preservitory.util.Constants;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * UIComponent for the fixed right-side panel.
 *
 * === Hierarchy ===
 *   RightPanel
 *     ├── TopTabBar    (y = 0..TAB_BAR_HEIGHT)
 *     ├── ContentPanel (y = TAB_BAR_HEIGHT..BOTTOM_BAR_Y)
 *     └── BottomTabBar (y = BOTTOM_BAR_Y..SCREEN_HEIGHT)
 *
 * === Render order ===
 *   1. inventory_box  — full panel background
 *   2. topTabBar.render()
 *   3. contentPanel.render()
 *   4. bottomTabBar.render()
 */
public class RightPanel extends UIComponent {

    // -----------------------------------------------------------------------
    //  Layout constants — public so cross-package classes can access them
    // -----------------------------------------------------------------------

    public  static final int TAB_BAR_HEIGHT  = 36;
    public  static final int CONTENT_PADDING = 10;
    public  static final int CONTENT_Y       = TAB_BAR_HEIGHT;
    public  static final int CONTENT_H       = Constants.SCREEN_HEIGHT - (TAB_BAR_HEIGHT * 2);
    public  static final int BOTTOM_BAR_Y    = CONTENT_Y + CONTENT_H;

    // Top bar: all tabs except SETTINGS + LOGOUT; bottom bar: SETTINGS + LOGOUT
    static final int TOP_TAB_COUNT    = TabConfig.TABS.size() - 2;
    static final int BOTTOM_TAB_COUNT = TabConfig.TABS.size() - TOP_TAB_COUNT;

    // -----------------------------------------------------------------------
    //  Tab renderer instances
    // -----------------------------------------------------------------------

    private final CombatTab      combatTab;
    private final InventoryTab   inventoryTab;
    private final SkillsTab      skillsTab;
    private final EquipmentTab   equipmentTab;
    private final QuestTab       questTab;
    private final PrayerTab      prayerTab;
    private final LogoutTab      logoutTab;
    private final SettingsTab    settingsTab;
    private final KeybindingsTab keybindingsTab;

    // -----------------------------------------------------------------------
    //  Component hierarchy
    // -----------------------------------------------------------------------

    final TabManager   tabManager;
    private final TabBar       topTabBar;
    private final ContentPanel contentPanel;
    private final TabBar       bottomTabBar;

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public RightPanel(int panelX, ClientSettings settings) {
        super(panelX, 0, Constants.PANEL_W, Constants.SCREEN_HEIGHT);

        // Build tab renderer instances
        combatTab      = new CombatTab();
        inventoryTab   = new InventoryTab();
        skillsTab      = new SkillsTab();
        equipmentTab   = new EquipmentTab();
        questTab       = new QuestTab();
        prayerTab      = new PrayerTab();
        logoutTab      = new LogoutTab();
        settingsTab    = new SettingsTab(settings);
        keybindingsTab = new KeybindingsTab(settings);

        // Build Tab objects — type + icon key + renderer
        List<Tab> topTabs = new ArrayList<>();
        List<Tab> bottomTabs = new ArrayList<>();

        for (int i = 0; i < TabConfig.TABS.size(); i++) {
            TabConfig cfg = TabConfig.TABS.get(i);
            Tab tab = new Tab(cfg.type, cfg.iconKey, rendererFor(cfg.type));
            if (i < TOP_TAB_COUNT) topTabs.add(tab);
            else                   bottomTabs.add(tab);
        }

        tabManager = new TabManager(topTabs, bottomTabs);

        // Build sub-components
        topTabBar    = new TabBar(panelX + CONTENT_PADDING, 0,
                Constants.PANEL_W - CONTENT_PADDING * 2, TAB_BAR_HEIGHT, topTabs, tabManager);
        contentPanel = new ContentPanel(panelX + CONTENT_PADDING, CONTENT_Y + CONTENT_PADDING,
                Constants.PANEL_W - CONTENT_PADDING * 2, CONTENT_H - CONTENT_PADDING * 2, tabManager);
        bottomTabBar = new TabBar(panelX + CONTENT_PADDING, BOTTOM_BAR_Y,
                Constants.PANEL_W - CONTENT_PADDING * 2, TAB_BAR_HEIGHT, bottomTabs, tabManager);
    }

    private com.classic.preservitory.ui.framework.TabRenderer rendererFor(TabType type) {
        return switch (type) {
            case COMBAT      -> combatTab;
            case INVENTORY   -> inventoryTab;
            case SKILLS      -> skillsTab;
            case EQUIPMENT   -> equipmentTab;
            case QUESTS      -> questTab;
            case PRAYER      -> prayerTab;
            case LOGOUT      -> logoutTab;
            case SETTINGS    -> settingsTab;
            case KEYBINDINGS -> keybindingsTab;
            default          -> (g2, cx, cy, cw, ch) -> {};
        };
    }

    // -----------------------------------------------------------------------
    //  Context update — call before render each frame
    // -----------------------------------------------------------------------

    public void setPlayer(Player player) {
        inventoryTab .setPlayer(player);
        skillsTab    .setPlayer(player);
        equipmentTab .setPlayer(player);
        prayerTab    .setPlayer(player);
    }

    public void setShopState(boolean shopOpen, Map<Integer, Integer> sellPrices) {
        inventoryTab.setShopState(shopOpen, sellPrices);
    }

    // -----------------------------------------------------------------------
    //  UIComponent — render
    // -----------------------------------------------------------------------

    @Override
    public void render(Graphics2D g) {
        syncPanelX(x);
        // 1. Full panel background
        drawPanelBackground(g);
        // 2. Top tab bar (sprite + icons)
        topTabBar.render(g);
        // 2a. Bridge the seam between the active top tab and the content area
        drawActiveTabConnection(g);
        // 3. Active tab content
        contentPanel.render(g);
        // 4. Bottom tab bar (sprite + icons)
        bottomTabBar.render(g);
    }

    public void renderChrome(Graphics2D g) {
        syncPanelX(x);
        drawPanelBackground(g);
    }

    public void renderContentOnly(Graphics2D g) {
        syncPanelX(x);
        contentPanel.render(g);
    }

    // -----------------------------------------------------------------------
    //  UIComponent — input
    // -----------------------------------------------------------------------

    @Override
    public void handleClick(int sx, int sy) {
        if (sx < x || sx >= x + width) return;
        // Tab bars are handled externally (GameInputHandler) via getTabBounds
        // Content area
        if (sy >= CONTENT_Y && sy < BOTTOM_BAR_Y) {
            contentPanel.handleClick(sx, sy);
        }
    }

    @Override
    public void handleMouseMove(int sx, int sy) {
        if (sx < x || sx >= x + width) return;
        topTabBar   .handleMouseMove(sx, sy);
        bottomTabBar.handleMouseMove(sx, sy);
        if (sy >= CONTENT_Y && sy < BOTTOM_BAR_Y) {
            contentPanel.handleMouseMove(sx, sy);
        }
    }

    /** Overload accepting panelX for backwards-compatible call sites. panelX is unused. */
    public void handleMouseMove(int sx, int sy, int panelX) {
        syncPanelX(panelX);
        handleMouseMove(sx, sy);
    }

    // -----------------------------------------------------------------------
    //  Public API — tab management
    // -----------------------------------------------------------------------

    public TabType getActiveTab() {
        return tabManager.getActiveTabType();
    }

    public boolean hasActiveTab() {
        return tabManager.hasActiveTab();
    }

    public void closeActiveTab() {
        tabManager.clearTab();
    }

    public void openTab(TabType type) {
        Tab tab = tabManager.findByType(type);
        if (tab != null) tabManager.setTab(tab);
        if (type == TabType.SKILLS) skillsTab.resetScroll();
    }

    public void toggleTab(TabType type) {
        Tab tab = tabManager.findByType(type);
        if (tab != null) tabManager.toggleTab(tab);
        if (tabManager.getActiveTabType() == TabType.SKILLS) skillsTab.resetScroll();
    }

    public void onTabSelected(TabType type) {
        if (type == TabType.SKILLS) skillsTab.resetScroll();
    }

    // -----------------------------------------------------------------------
    //  Public API — input routing
    // -----------------------------------------------------------------------

    /** Returns the click bounds of a tab slot by absolute index across all tabs. */
    public Rectangle getTabBounds(int index, int panelX) {
        syncPanelX(panelX);
        if (index < TOP_TAB_COUNT) {
            return topTabBar.getTabBounds(index);
        } else {
            return bottomTabBar.getTabBounds(index - TOP_TAB_COUNT);
        }
    }

    public int getHoveredTabIndex() {
        int hi = topTabBar.getHoveredIndex();
        if (hi >= 0) return hi;
        int bi = bottomTabBar.getHoveredIndex();
        if (bi >= 0) return TOP_TAB_COUNT + bi;
        return -1;
    }

    public void setHoveredTabIndex(int hoveredTabIndex) {
        if (hoveredTabIndex < 0) {
            topTabBar   .setHoveredIndex(-1);
            bottomTabBar.setHoveredIndex(-1);
        } else if (hoveredTabIndex < TOP_TAB_COUNT) {
            topTabBar   .setHoveredIndex(hoveredTabIndex);
            bottomTabBar.setHoveredIndex(-1);
        } else {
            topTabBar   .setHoveredIndex(-1);
            bottomTabBar.setHoveredIndex(hoveredTabIndex - TOP_TAB_COUNT);
        }
    }

    public void handleMouseWheel(int direction) {
        contentPanel.handleMouseWheel(direction);
    }

    public String getHoveredButtonLabel(int sx, int sy, int panelX) {
        syncPanelX(panelX);
        if (sx < x || sx >= x + width) return null;
        return contentPanel.getHoveredLabel(sx, sy);
    }

    public int getClickedInventoryItemId(int sx, int sy, Player player, int panelX) {
        syncPanelX(panelX);
        return inventoryTab.getClickedItemId(sx, sy, player, panelX);
    }

    public String getHoveredInventoryItemName(Player player) {
        if (tabManager.getActiveTabType() != TabType.INVENTORY) return null;
        return inventoryTab.getHoveredItemName(player);
    }

    public boolean isInsideSettingsPanel(int sx, int sy) {
        return tabManager.getActiveTabType() == TabType.SETTINGS
                && settingsTab.containsPoint(sx, sy);
    }

    // -----------------------------------------------------------------------
    //  Public API — configuration delegates
    // -----------------------------------------------------------------------

    public void setQuestEntries(List<QuestEntry> entries) {
        questTab.setQuestEntries(entries);
    }

    public void setCombatStyleListener(Consumer<String> listener) {
        combatTab.setCombatStyleListener(listener);
    }

    public void setAutoRetaliateListener(Consumer<Boolean> listener) {
        combatTab.setAutoRetaliateListener(listener);
    }

    public void setUnequipListener(Consumer<String> listener) {
        equipmentTab.setUnequipListener(listener);
    }

    public void setFullscreenListener(Runnable listener)    { settingsTab.setFullscreenListener(listener); }
    public void setResizableListener(Runnable listener)     { settingsTab.setResizableListener(listener); }
    public void setFpsListener(Runnable listener)           { settingsTab.setFpsListener(listener); }
    public void setPingListener(Runnable listener)          { settingsTab.setPingListener(listener); }
    public void setTotalXpListener(Runnable listener)       { settingsTab.setTotalXpListener(listener); }
    public void setKeybindingsListener(Runnable listener)   { settingsTab.setKeybindingsListener(listener); }
    public void setShiftDropListener(Runnable listener)     { settingsTab.setShiftDropListener(listener); }
    public void setMinimapListener(Runnable listener)       { settingsTab.setMinimapListener(listener); }
    public void setDirectionListener(Runnable listener)     { settingsTab.setDirectionListener(listener); }
    public void setKeybindingRebindListener(Consumer<ClientSettings.Action> listener) {
        keybindingsTab.setRebindListener(listener);
    }
    public void setPrayerToggleListener(Consumer<String> listener) {
        prayerTab.setPrayerToggleListener(listener);
    }
    public void setLogoutListener(Runnable listener) {
        logoutTab.setLogoutListener(listener);
    }

    public void tickLogout(double deltaTime)    { logoutTab.tick(deltaTime); }
    public void resetLogoutConfirm()            { logoutTab.resetConfirm(); }

    public void setListeningKeybindingAction(ClientSettings.Action action) {
        keybindingsTab.setListeningAction(action);
    }

    public void setFullscreen(boolean fullscreen)  { settingsTab.setFullscreen(fullscreen); }
    public void setResizable(boolean resizable)    { settingsTab.setResizable(resizable); }
    public void setShowFps(boolean showFps)        { settingsTab.setShowFps(showFps); }
    public void setShowPing(boolean showPing)      { settingsTab.setShowPing(showPing); }

    // -----------------------------------------------------------------------
    //  Private helpers
    // -----------------------------------------------------------------------

    /**
     * Paints the active top-tab's fill color over the gap between the tab bar bottom
     * and the content panel top, erasing the visible seam from the inventory_box sprite.
     * When no top tab is active nothing is drawn, leaving the background visible.
     */
    private void drawActiveTabConnection(Graphics2D g) {
        TabType activeType = tabManager.getActiveTabType();
        if (activeType == null) return;
        Rectangle slot = topTabBar.getActiveSlotBounds(activeType);
        if (slot == null) return;   // active tab is in the bottom bar — no connection needed
        // Fill from 1px above the bar bottom to 1px into the content area,
        // erasing the inventory_box texture that would otherwise show as a seam.
        int fillY = TAB_BAR_HEIGHT - 1;
        int fillH = CONTENT_PADDING + 2;
        g.setColor(TabBar.ACTIVE_SLOT_COLOR);
        g.fillRect(slot.x + 1, fillY, slot.width - 2, fillH);
        // Gold rule continues down from the tab highlight, reinforcing the join
        g.setColor(new Color(130, 104, 52, 80));
        g.drawLine(slot.x + 1, TAB_BAR_HEIGHT, slot.x + slot.width - 2, TAB_BAR_HEIGHT);
    }

    private void drawPanelBackground(Graphics2D g) {
        BufferedImage bg = AssetManager.getImage("inventory_box");
        if (bg == null) {
            g.setColor(new Color(20, 16, 12));
            g.fillRect(x, 0, width, Constants.SCREEN_HEIGHT);
        } else {
            g.drawImage(bg, x, 0, width, Constants.SCREEN_HEIGHT, null);
        }
    }

    void syncPanelX(int panelX) {
        setBounds(panelX, 0, Constants.PANEL_W, height);
        topTabBar.setBounds(panelX + CONTENT_PADDING, 0,
                Constants.PANEL_W - CONTENT_PADDING * 2, TAB_BAR_HEIGHT);
        contentPanel.setBounds(panelX + CONTENT_PADDING, CONTENT_Y + CONTENT_PADDING,
                Constants.PANEL_W - CONTENT_PADDING * 2, CONTENT_H - CONTENT_PADDING * 2);
        bottomTabBar.setBounds(panelX + CONTENT_PADDING, BOTTOM_BAR_Y,
                Constants.PANEL_W - CONTENT_PADDING * 2, TAB_BAR_HEIGHT);
    }
}
