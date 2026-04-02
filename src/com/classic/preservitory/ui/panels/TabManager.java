package com.classic.preservitory.ui.panels;

/**
 * Owns the active tab state for the right panel.
 *
 * Intentionally minimal — just holds which tab is selected and allows it to change.
 * Business logic for each tab lives in the corresponding Tab class.
 */
class TabManager {

    private TabType activeTab = TabType.NONE;

    TabType getActiveTab() {
        return activeTab;
    }

    void setTab(TabType tab) {
        activeTab = tab;
    }

    void clearTab() {
        activeTab = TabType.NONE;
    }

    boolean hasActiveTab() {
        return activeTab != TabType.NONE;
    }

    void toggleTab(TabType tab) {
        activeTab = (activeTab == tab) ? TabType.NONE : tab;
    }
}
