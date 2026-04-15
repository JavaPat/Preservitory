package com.classic.preservitory.ui.panels;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns active-tab state and the ordered lists of top/bottom tabs.
 *
 * All tabs live in either {@code topTabs} (rendered in the top bar) or
 * {@code bottomTabs} (rendered in the bottom bar). No tab knowledge is
 * hardcoded here — the lists are set once during RightPanel construction.
 */
class TabManager {

    final List<Tab> topTabs;
    final List<Tab> bottomTabs;

    private Tab activeTab = null;

    TabManager(List<Tab> topTabs, List<Tab> bottomTabs) {
        this.topTabs    = topTabs;
        this.bottomTabs = bottomTabs;
    }

    // -------------------------------------------------------------------------
    //  Active tab access
    // -------------------------------------------------------------------------

    Tab getActiveTab() {
        return activeTab;
    }

    TabType getActiveTabType() {
        return activeTab != null ? activeTab.type : TabType.NONE;
    }

    boolean hasActiveTab() {
        return activeTab != null;
    }

    void setTab(Tab tab) {
        activeTab = tab;
    }

    void clearTab() {
        activeTab = null;
    }

    void toggleTab(Tab tab) {
        activeTab = (activeTab == tab) ? null : tab;
    }

    // -------------------------------------------------------------------------
    //  Lookup helpers
    // -------------------------------------------------------------------------

    /** Returns the Tab with the given type, or {@code null} if not found. */
    Tab findByType(TabType type) {
        for (Tab t : topTabs)    if (t.type == type) return t;
        for (Tab t : bottomTabs) if (t.type == type) return t;
        return null;
    }

    /** Combined ordered list: top tabs first, then bottom tabs. */
    List<Tab> allTabs() {
        List<Tab> all = new ArrayList<>(topTabs.size() + bottomTabs.size());
        all.addAll(topTabs);
        all.addAll(bottomTabs);
        return all;
    }
}
