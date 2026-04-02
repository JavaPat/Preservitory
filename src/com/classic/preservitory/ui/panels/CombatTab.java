package com.classic.preservitory.ui.panels;

import com.classic.preservitory.util.Constants;

import java.awt.*;
import java.util.function.Consumer;

/**
 * Renders the Combat tab: Accurate / Aggressive / Defensive style buttons.
 *
 * Display only — tracks active style locally and fires a listener on change.
 * No combat logic lives here; the caller wires the listener to
 * clientConnection.sendCombatStyle().
 */
class CombatTab implements Tab {

    // -----------------------------------------------------------------------
    //  Layout
    // -----------------------------------------------------------------------

    private static final int CONTENT_Y = 110;

    // -----------------------------------------------------------------------
    //  State
    // -----------------------------------------------------------------------

    private String           activeCombatStyle   = "ACCURATE";
    private Consumer<String> combatStyleListener = null;

    /** Screen Y of the style buttons row (set during render, used for click detection). */
    private int styleButtonsY = 0;

    // -----------------------------------------------------------------------
    //  Configuration
    // -----------------------------------------------------------------------

    void setCombatStyleListener(Consumer<String> listener) {
        this.combatStyleListener = listener;
    }

    // -----------------------------------------------------------------------
    //  Input
    // -----------------------------------------------------------------------

    /**
     * Handle a click inside the combat tab content area.
     * No scroll offset — content is rendered at fixed screen Y positions.
     */
    @Override
    public void handleClick(int sx, int sy, int px, int pw) {
        if (styleButtonsY <= 0) return;
        if (sy < styleButtonsY || sy >= styleButtonsY + 16) return;

        int bx   = px + 8;
        int bw   = pw - 16;
        int btnW = (bw - 4) / 3;
        if      (sx < bx + btnW)         select("ACCURATE");
        else if (sx < bx + 2 * btnW + 2) select("AGGRESSIVE");
        else                             select("DEFENSIVE");
    }

    String getHoveredButtonLabel(int sx, int sy, int px, int pw) {
        if (styleButtonsY <= 0 || sy < styleButtonsY || sy >= styleButtonsY + 22) return null;
        int bx   = px + 8;
        int bw   = pw - 16;
        int btnW = (bw - 4) / 3;
        if (sx < bx || sx >= bx + bw) return null;
        if (sx < bx + btnW)         return "Accurate attack style";
        if (sx < bx + 2 * btnW + 2) return "Aggressive attack style";
        return "Defensive attack style";
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    void render(Graphics2D g, int px, int pw) {
        int x  = px + 8;
        int bw = pw - 16;
        int y  = CONTENT_Y + 10;

        g.setFont(new Font("Arial", Font.BOLD, 10));
        drawOutlined(g, "ATTACK STYLE", px + pw / 2 - 30, y + 2,
                new Color(200, 185, 100), new Color(0, 0, 0, 160));
        y += 18;

        styleButtonsY = y;

        String[] labels = { "ACC", "AGG", "DEF" };
        String[] values = { "ACCURATE", "AGGRESSIVE", "DEFENSIVE" };
        int btnW = (bw - 4) / 3;

        for (int i = 0; i < 3; i++) {
            int     bx2 = x + i * (btnW + 2);
            boolean on  = values[i].equals(activeCombatStyle);

            // Button fill
            g.setColor(on ? new Color(80, 60, 20) : new Color(35, 28, 15));
            g.fillRoundRect(bx2, y, btnW, 22, 5, 5);

            // Active top-highlight stripe
            if (on) {
                g.setColor(new Color(200, 170, 70));
                g.drawLine(bx2 + 1, y, bx2 + btnW - 2, y);
            }

            // Border
            g.setColor(on ? new Color(140, 110, 45) : new Color(70, 56, 28));
            g.drawRoundRect(bx2, y, btnW, 22, 5, 5);

            // Label
            g.setFont(new Font("Arial", Font.BOLD, 9));
            FontMetrics fm = g.getFontMetrics();
            int lw = fm.stringWidth(labels[i]);
            g.setColor(on ? new Color(220, 200, 110) : new Color(130, 115, 65));
            g.drawString(labels[i], bx2 + (btnW - lw) / 2, y + 14);
        }
        y += 34;

        // Description line under selected style
        g.setFont(new Font("Arial", Font.PLAIN, 9));
        String desc = switch (activeCombatStyle) {
            case "ACCURATE"   -> "Improves accuracy";
            case "AGGRESSIVE" -> "Improves strength";
            case "DEFENSIVE"  -> "Improves defence";
            default           -> "";
        };
        g.setColor(new Color(150, 140, 100));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(desc, x + (bw - fm.stringWidth(desc)) / 2, y + 2);
    }

    // -----------------------------------------------------------------------
    //  Private helpers
    // -----------------------------------------------------------------------

    private void select(String style) {
        activeCombatStyle = style;
        if (combatStyleListener != null) combatStyleListener.accept(style);
    }

    private static void drawOutlined(Graphics2D g, String text, int x, int y,
                                     Color fg, Color shadow) {
        g.setColor(shadow);
        g.drawString(text, x + 1, y + 1);
        g.drawString(text, x - 1, y + 1);
        g.drawString(text, x + 1, y - 1);
        g.drawString(text, x - 1, y - 1);
        g.setColor(fg);
        g.drawString(text, x, y);
    }
}
