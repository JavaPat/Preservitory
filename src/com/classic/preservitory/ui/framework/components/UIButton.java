package com.classic.preservitory.ui.framework.components;

import com.classic.preservitory.ui.framework.UIComponent;

import java.awt.*;

public class UIButton extends UIComponent {

    private String text;
    private Runnable onClick;

    public UIButton(int x, int y, int width, int height, String text, Runnable onClick) {
        super(x, y, width, height);
        this.text = text;
        this.onClick = onClick;
    }

    @Override
    public void render(Graphics2D g) {
        if (!visible) return;

        // --- Background ---
        Color bg = hovered
                ? new Color(70, 60, 35)
                : new Color(45, 38, 22);

        g.setColor(bg);
        g.fillRect(x, y, width, height);

        // --- Border ---
        g.setColor(hovered
                ? new Color(200, 170, 70)
                : new Color(100, 80, 40));
        g.drawRect(x, y, width, height);

        // --- Text ---
        g.setFont(new Font("Monospaced", Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();

        int textWidth = fm.stringWidth(text);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height / 2) + fm.getAscent() / 2 - 2;

        // Shadow
        g.setColor(new Color(0, 0, 0, 200));
        g.drawString(text, textX + 1, textY + 1);

        // Text
        g.setColor(new Color(220, 200, 120));
        g.drawString(text, textX, textY);
    }

    public void setText(String text) { this.text = text; }

    @Override
    public void handleClick(int mouseX, int mouseY) {
        if (!visible) return;

        if (contains(mouseX, mouseY) && onClick != null) {
            onClick.run();
        }
    }
}