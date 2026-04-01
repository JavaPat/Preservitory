package com.classic.preservitory.ui.panels;

import java.util.List;

final class TabConfig {

    static final List<TabConfig> TABS = List.of(
            new TabConfig(TabType.COMBAT, "tab_combat"),
            new TabConfig(TabType.INVENTORY, "tab_inventory"),
            new TabConfig(TabType.SKILLS, "tab_skills"),
            new TabConfig(TabType.EQUIPMENT, "tab_equipment"),
            new TabConfig(TabType.QUESTS, "tab_quests")
    );

    final TabType type;
    final String iconKey;

    TabConfig(TabType type, String iconKey) {
        this.type = type;
        this.iconKey = iconKey;
    }
}
