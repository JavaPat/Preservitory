package com.classic.preservitory.ui.framework.components;

import com.classic.preservitory.ui.framework.UIContainer;
import com.classic.preservitory.ui.framework.UIComponent;

import java.awt.*;

public class UIPanel extends UIContainer {

    private Color backgroundColor = new Color(20, 16, 12, 220);
    private Color borderColor = new Color(90, 75, 35);
    private boolean drawBorder = true;

    public UIPanel(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    @Override
    public void render(Graphics2D g) {
        if (!visible) return;

        // --- Background ---
        g.setColor(backgroundColor);
        g.fillRect(x, y, width, height);

        // --- Border ---
        if (drawBorder) {
            g.setColor(borderColor);
            g.drawRect(x, y, width, height);
        }

        // --- Children ---
        for (UIComponent child : children) {
            if (child.isVisible()) {
                child.render(g);
            }
        }
    }

    @Override
    public void handleClick(int mouseX, int mouseY) {
        // Only handle clicks inside panel
        if (!contains(mouseX, mouseY)) return;

        super.handleClick(mouseX, mouseY);
    }

    @Override
    public void handleMouseMove(int mouseX, int mouseY) {
        if (!visible) return;

        // Only propagate hover if inside panel
        if (contains(mouseX, mouseY)) {
            super.handleMouseMove(mouseX, mouseY);
        } else {
            // Reset hover on children when mouse leaves panel
            for (UIComponent child : children) {
                child.handleMouseMove(-1, -1);
            }
        }
    }

    // --- Styling setters (for flexibility later) ---

    public void setBackgroundColor(Color color) {
        this.backgroundColor = color;
    }

    public void setBorderColor(Color color) {
        this.borderColor = color;
    }

    public void setDrawBorder(boolean drawBorder) {
        this.drawBorder = drawBorder;
    }
}
