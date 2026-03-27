package com.classic.preservitory.ui.framework;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

public class UIManager {

    private final List<UIScreen> screens = new ArrayList<>();

    public void add(UIScreen screen) {
        screens.add(screen);
    }

    public void remove(UIScreen screen) {
        screens.remove(screen);
    }

    public void clear() {
        screens.clear();
    }

    public void render(Graphics2D g) {
        for (UIScreen screen : screens) {
            screen.render(g);
        }
    }

    public void update() {
        for (UIScreen screen : screens) {
            screen.update();
        }
    }

    public void handleClick(int mouseX, int mouseY) {
        for (int i = screens.size() - 1; i >= 0; i--) {
            screens.get(i).handleClick(mouseX, mouseY);
        }
    }

    public void handleMouseMove(int mouseX, int mouseY) {
        for (UIScreen screen : screens) {
            screen.handleMouseMove(mouseX, mouseY);
        }
    }

}