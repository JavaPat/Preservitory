package com.classic.preservitory.ui.panels;

import com.classic.preservitory.ui.framework.TabRenderer;
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
class CombatTab implements TabRenderer {

    // -----------------------------------------------------------------------
    //  State
    // -----------------------------------------------------------------------

    private String           activeCombatStyle   = "ACCURATE";
    private Consumer<String> combatStyleListener = null;

    private boolean                  autoRetaliate         = true;
    private Consumer<Boolean>        autoRetaliateListener = null;

    /** Screen Y of the style buttons row (set during render, used for click detection). */
    private int styleButtonsY      = 0;
    /** Screen Y of the auto-retaliate toggle row (set during render, used for click detection). */
    private int autoRetaliateY     = 0;

    // Cached bounds from last render — used for click/hover hit-testing
    private int lastX, lastY, lastWidth;

    // -----------------------------------------------------------------------
    //  Configuration
    // -----------------------------------------------------------------------

    void setCombatStyleListener(Consumer<String> listener) {
        this.combatStyleListener = listener;
    }

    void setAutoRetaliateListener(Consumer<Boolean> listener) {
        this.autoRetaliateListener = listener;
    }

    // -----------------------------------------------------------------------
    //  Input
    // -----------------------------------------------------------------------

    @Override
    public void handleClick(int sx, int sy, int x, int y, int width, int height) {
        // Attack style buttons
        if (styleButtonsY > 0 && sy >= styleButtonsY && sy < styleButtonsY + 22) {
            int bx   = x + 8;
            int bw   = width - 16;
            int btnW = (bw - 4) / 3;
            if      (sx < bx + btnW)         select("ACCURATE");
            else if (sx < bx + 2 * btnW + 2) select("AGGRESSIVE");
            else                             select("DEFENSIVE");
            return;
        }

        // Auto-retaliate toggle
        if (autoRetaliateY > 0 && sy >= autoRetaliateY && sy < autoRetaliateY + 18) {
            autoRetaliate = !autoRetaliate;
            if (autoRetaliateListener != null) autoRetaliateListener.accept(autoRetaliate);
        }
    }

    @Override
    public String getHoveredLabel(int sx, int sy, int x, int y, int width, int height) {
        if (styleButtonsY <= 0 || sy < styleButtonsY || sy >= styleButtonsY + 22) return null;
        int bx   = x + 8;
        int bw   = width - 16;
        int btnW = (bw - 4) / 3;
        if (sx < bx || sx >= bx + bw) return null;
        if (sx < bx + btnW)         return "Accurate attack style";
        if (sx < bx + 2 * btnW + 2) return "Aggressive attack style";
        return "Defensive attack style";
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    @Override
    public void render(Graphics2D g, int x, int y, int width, int height) {
        lastX = x; lastY = y; lastWidth = width;

        int bx = x + 8;
        int bw = width - 16;
        int cy = y + 10;

        g.setFont(new Font("Arial", Font.BOLD, 10));
        drawOutlined(g, "ATTACK STYLE", x + width / 2 - 30, cy + 2,
                new Color(200, 185, 100), new Color(0, 0, 0, 160));
        cy += 18;

        styleButtonsY = cy;

        String[] labels = { "ACC", "AGG", "DEF" };
        String[] values = { "ACCURATE", "AGGRESSIVE", "DEFENSIVE" };
        int btnW = (bw - 4) / 3;

        for (int i = 0; i < 3; i++) {
            int     bx2 = bx + i * (btnW + 2);
            boolean on  = values[i].equals(activeCombatStyle);

            g.setColor(on ? new Color(80, 60, 20) : new Color(35, 28, 15));
            g.fillRoundRect(bx2, cy, btnW, 22, 5, 5);

            if (on) {
                g.setColor(new Color(200, 170, 70));
                g.drawLine(bx2 + 1, cy, bx2 + btnW - 2, cy);
            }

            g.setColor(on ? new Color(140, 110, 45) : new Color(70, 56, 28));
            g.drawRoundRect(bx2, cy, btnW, 22, 5, 5);

            g.setFont(new Font("Arial", Font.BOLD, 9));
            FontMetrics fm = g.getFontMetrics();
            int lw = fm.stringWidth(labels[i]);
            g.setColor(on ? new Color(220, 200, 110) : new Color(130, 115, 65));
            g.drawString(labels[i], bx2 + (btnW - lw) / 2, cy + 14);
        }
        cy += 34;

        g.setFont(new Font("Arial", Font.PLAIN, 9));
        String desc = switch (activeCombatStyle) {
            case "ACCURATE"   -> "Improves accuracy";
            case "AGGRESSIVE" -> "Improves strength";
            case "DEFENSIVE"  -> "Improves defence";
            default           -> "";
        };
        g.setColor(new Color(150, 140, 100));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(desc, bx + (bw - fm.stringWidth(desc)) / 2, cy + 2);
        cy += 16;

        cy += 8;
        g.setFont(new Font("Arial", Font.BOLD, 10));
        drawOutlined(g, "AUTO RETALIATE", x + width / 2 - 36, cy + 2,
                new Color(200, 185, 100), new Color(0, 0, 0, 160));
        cy += 16;

        autoRetaliateY = cy;

        boolean on = autoRetaliate;
        int toggleW = bw;
        int toggleH = 18;
        g.setColor(on ? new Color(30, 80, 30) : new Color(60, 20, 20));
        g.fillRoundRect(bx, cy, toggleW, toggleH, 5, 5);
        g.setColor(on ? new Color(60, 150, 60) : new Color(110, 50, 50));
        g.drawRoundRect(bx, cy, toggleW, toggleH, 5, 5);
        if (on) {
            g.setColor(new Color(60, 150, 60));
            g.drawLine(bx + 1, cy, bx + toggleW - 2, cy);
        }
        g.setFont(new Font("Arial", Font.BOLD, 9));
        String label = on ? "ON" : "OFF";
        FontMetrics tfm = g.getFontMetrics();
        int lw = tfm.stringWidth(label);
        g.setColor(on ? new Color(120, 220, 120) : new Color(200, 100, 100));
        g.drawString(label, bx + (toggleW - lw) / 2, cy + 12);
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
