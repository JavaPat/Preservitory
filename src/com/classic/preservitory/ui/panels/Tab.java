package com.classic.preservitory.ui.panels;

import com.classic.preservitory.ui.framework.TabRenderer;

/**
 * Immutable data class representing a single tab in the right panel.
 *
 * Each tab has:
 *   - {@code type}     — enum identity used for equality checks and packet routing
 *   - {@code iconKey}  — AssetManager key for the 24×24 tab icon sprite
 *   - {@code renderer} — handles rendering and input for this tab's content area
 */
final class Tab {

    final TabType    type;
    final String     iconKey;
    final TabRenderer renderer;

    Tab(TabType type, String iconKey, TabRenderer renderer) {
        this.type     = type;
        this.iconKey  = iconKey;
        this.renderer = renderer;
    }
}
