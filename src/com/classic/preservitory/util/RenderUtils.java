package com.classic.preservitory.util;

import java.awt.Color;
import java.awt.Graphics2D;

/** Shared rendering utilities used by both world and UI renderers. */
public final class RenderUtils {

    private RenderUtils() {}

    /**
     * Draws text with a one-pixel outline in all four diagonal directions,
     * producing a readable label over any background.
     */
    public static void drawOutlinedString(Graphics2D g, String text, int x, int y,
                                          Color textColor, Color outlineColor) {
        g.setColor(outlineColor);
        g.drawString(text, x + 1, y + 1);
        g.drawString(text, x - 1, y + 1);
        g.drawString(text, x + 1, y - 1);
        g.drawString(text, x - 1, y - 1);
        g.setColor(textColor);
        g.drawString(text, x, y);
    }
}
