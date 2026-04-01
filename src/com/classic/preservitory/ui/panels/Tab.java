package com.classic.preservitory.ui.panels;

/**
 * Common contract for all right-panel tab classes.
 *
 * Tabs that have no clickable content-area elements rely on the default no-op.
 * Tabs that do (CombatTab, EquipmentTab, QuestTab) override handleClick.
 */
interface Tab {
    default void handleClick(int sx, int sy, int px, int pw) {}
}
