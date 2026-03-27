package com.classic.preservitory.ui.framework.components;

import com.classic.preservitory.ui.framework.UIComponent;

import java.awt.*;

public class UILabel extends UIComponent {

    private String text;
    private Color color;

    public UILabel(int x, int y, String text, Color color) {
        super(x, y, 0, 0);
        this.text = text;
        this.color = color;
    }

    @Override
    public void render(Graphics2D g) {
        if (!visible) return;

        g.setFont(new Font("Monospaced", Font.BOLD, 14));

        // Shadow
        g.setColor(new Color(0, 0, 0, 200));
        g.drawString(text, x + 1, y + 1);

        // Text
        g.setColor(color);
        g.drawString(text, x, y);
    }

    public void setText(String text) {
        this.text = text;
    }
}
