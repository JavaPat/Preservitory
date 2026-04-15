package com.classic.preservitory.ui.framework;

import java.awt.Graphics2D;

/**
 * Contract that every tab's rendering and input logic must satisfy.
 *
 * Bounds (x, y, width, height) are the content area supplied by ContentPanel
 * each frame — implementations must NOT hardcode panel-absolute coordinates.
 */
public interface TabRenderer {

    /** Render the full tab content into the given content-area bounds. */
    void render(Graphics2D g, int x, int y, int width, int height);

    /** Handle a click at (sx, sy) within the given content-area bounds. */
    default void handleClick(int sx, int sy, int x, int y, int width, int height) {}

    /** Handle mouse movement at (sx, sy) within the given content-area bounds. */
    default void handleMouseMove(int sx, int sy, int x, int y, int width, int height) {}

    /** Handle mouse-wheel scroll (direction = -1 up, +1 down). */
    default void handleMouseWheel(int direction) {}

    /**
     * Returns the label of the button/element currently under (sx, sy),
     * or {@code null} if nothing is hovered.
     */
    default String getHoveredLabel(int sx, int sy, int x, int y, int width, int height) {
        return null;
    }
}
