package com.classic.preservitory.ui.panels;

import java.util.List;

final class TabConfig {

    /**
     * All tabs in display order.
     *
     * Top bar    (indices 0..TOP_TABS-1) : COMBAT, INVENTORY, SKILLS, EQUIPMENT, QUESTS, PRAYER
     * Bottom bar (indices TOP_TABS..end) : SETTINGS, LOGOUT
     *
     * The split point is defined by {@link RightPanel#TOP_TAB_COUNT}.
     */
    static final List<TabConfig> TABS = List.of(
            // ---- top bar ----
            new TabConfig(TabType.COMBAT,    "client_screen/combat_tab"),
            new TabConfig(TabType.INVENTORY, "client_screen/inventory_tab"),
            new TabConfig(TabType.SKILLS,    "client_screen/skills_tab"),
            new TabConfig(TabType.EQUIPMENT, "client_screen/equipment_tab"),
            new TabConfig(TabType.QUESTS,    "client_screen/quest_tab"),
            new TabConfig(TabType.PRAYER,    "client_screen/prayer_tab"),
            // ---- bottom bar ----
            new TabConfig(TabType.SETTINGS,  "client_screen/settings_cog"),
            new TabConfig(TabType.LOGOUT,    "client_screen/logout_tab")
    );

    final TabType type;
    final String iconKey;

    TabConfig(TabType type, String iconKey) {
        this.type = type;
        this.iconKey = iconKey;
    }
}
