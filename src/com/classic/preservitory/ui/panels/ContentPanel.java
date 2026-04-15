package com.classic.preservitory.ui.panels;

import com.classic.preservitory.ui.framework.UIComponent;

import java.awt.*;

/**
 * UIComponent that delegates rendering and input to the currently active tab's
 * {@link com.classic.preservitory.ui.framework.TabRenderer}.
 *
 * It owns no visual state of its own — it is purely a routing layer that passes
 * its bounds (x, y, width, height) to whichever renderer is active.
 */
class ContentPanel extends UIComponent {

    private final TabManager tabManager;

    ContentPanel(int x, int y, int width, int height, TabManager tabManager) {
        super(x, y, width, height);
        this.tabManager = tabManager;
    }

    // -----------------------------------------------------------------------
    //  UIComponent — render
    // -----------------------------------------------------------------------

    @Override
    public void render(Graphics2D g) {
        Tab active = tabManager.getActiveTab();
        if (active == null) return;
        active.renderer.render(g, x, y, width, height);
    }

    // -----------------------------------------------------------------------
    //  UIComponent — input
    // -----------------------------------------------------------------------

    @Override
    public void handleClick(int sx, int sy) {
        Tab active = tabManager.getActiveTab();
        if (active == null) return;
        active.renderer.handleClick(sx, sy, x, y, width, height);
    }

    @Override
    public void handleMouseMove(int sx, int sy) {
        Tab active = tabManager.getActiveTab();
        if (active == null) return;
        active.renderer.handleMouseMove(sx, sy, x, y, width, height);
    }

    void handleMouseWheel(int direction) {
        Tab active = tabManager.getActiveTab();
        if (active == null) return;
        active.renderer.handleMouseWheel(direction);
    }

    String getHoveredLabel(int sx, int sy) {
        Tab active = tabManager.getActiveTab();
        if (active == null) return null;
        return active.renderer.getHoveredLabel(sx, sy, x, y, width, height);
    }
}
