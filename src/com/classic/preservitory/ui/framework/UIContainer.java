package com.classic.preservitory.ui.framework;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

public class UIContainer extends UIComponent {

    protected final List<UIComponent> children = new ArrayList<>();

    public UIContainer(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public void add(UIComponent component) {
        children.add(component);
    }

    @Override
    public void render(Graphics2D g) {
        if (!visible) return;

        for (UIComponent child : children) {
            if (child.isVisible()) {
                child.render(g);
            }
        }
    }

    @Override
    public void handleClick(int mouseX, int mouseY) {
        for (int i = children.size() - 1; i >= 0; i--) {
            UIComponent child = children.get(i);

            if (child.isVisible() && child.contains(mouseX, mouseY)) {
                child.handleClick(mouseX, mouseY);
                break;
            }
        }
    }
}