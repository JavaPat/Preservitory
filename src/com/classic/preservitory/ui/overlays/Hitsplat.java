package com.classic.preservitory.ui.overlays;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/**
 * OSRS-style hitsplat: a colored circular badge with a damage number centered inside.
 *
 * Extends {@link FloatingText} so it can live in the same {@code floatingTexts} list
 * and be ticked / removed by the same loop — no duplicated lifecycle code.
 *
 * Variants:
 *   red   — damage received by the local player
 *   gold  — damage dealt to an enemy
 *   grey  — zero-damage hit (miss / blocked)
 */
public final class Hitsplat extends FloatingText {

    /** Radius of the circular badge in pixels. */
    private static final int RADIUS = 10;

    public Hitsplat(double x, double y, String text, Color color) {
        super(x, y, text, color);
    }

    /**
     * Draw the hitsplat badge: filled circle + dark outline + centered number.
     * The caller must set a font on {@code g} before invoking this.
     */
    @Override
    public void renderInContext(Graphics2D g) {
        int a = (int)(alpha * 255);
        if (a <= 0) return;

        int cx = (int) x;
        int cy = (int) y;

        // Filled badge
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), a));
        g.fillOval(cx - RADIUS, cy - RADIUS, RADIUS * 2, RADIUS * 2);

        // Dark outline
        g.setColor(new Color(0, 0, 0, Math.min(a, 220)));
        g.drawOval(cx - RADIUS, cy - RADIUS, RADIUS * 2, RADIUS * 2);

        // Number centered in the badge
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(text);
        int th = fm.getAscent();
        g.setColor(new Color(255, 255, 255, a));
        g.drawString(text, cx - tw / 2, cy + th / 2 - 2);
    }
}
