package com.classic.preservitory.ui.framework;

import java.awt.Graphics2D;

public abstract class UIComponent {

    protected int x, y, width, height;
    protected boolean visible = true;
    protected boolean hovered = false;

    public UIComponent(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public abstract void render(Graphics2D g);

    public void update() {}

    public void handleClick(int mouseX, int mouseY) {}

    public void handleMouseMove(int mouseX, int mouseY) {
        hovered = contains(mouseX, mouseY);
    }

    public boolean contains(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width &&
                mouseY >= y && mouseY <= y + height;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
}
