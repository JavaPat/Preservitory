package com.classic.preservitory.ui.framework;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

public class UIScreen {

    private final List<UIComponent> components = new ArrayList<>();
    private boolean visible = true;

    public void add(UIComponent component) {
        components.add(component);
    }

    public void render(Graphics2D g) {
        if (!visible) return;

        for (UIComponent component : components) {
            if (component.isVisible()) {
                component.render(g);
            }
        }
    }

    public void update() {
        if (!visible) return;

        for (UIComponent component : components) {
            component.update();
        }
    }

    public void handleClick(int mouseX, int mouseY) {
        if (!visible) return;

        for (int i = components.size() - 1; i >= 0; i--) {
            UIComponent component = components.get(i);

            if (component.isVisible() && component.contains(mouseX, mouseY)) {
                component.handleClick(mouseX, mouseY);
                break;
            }
        }
    }

    public void handleMouseMove(int mouseX, int mouseY) {
        if (!visible) return;

        for (UIComponent component : components) {
            component.handleMouseMove(mouseX, mouseY);
        }
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}
