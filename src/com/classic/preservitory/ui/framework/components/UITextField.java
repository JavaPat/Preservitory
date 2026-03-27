package com.classic.preservitory.ui.framework.components;

import com.classic.preservitory.ui.framework.UIComponent;

import java.awt.*;
import java.awt.event.KeyEvent;

public class UITextField extends UIComponent {

    private final String  label;
    private final boolean password;
    private final int     maxLength;

    private String  value   = "";
    private boolean focused = false;

    public UITextField(int x, int y, int width, int height,
                       String label, boolean password, int maxLength) {
        super(x, y, width, height);
        this.label     = label;
        this.password  = password;
        this.maxLength = maxLength;
    }

    @Override
    public void render(Graphics2D g) {
        if (!visible) return;

        // Field background
        g.setColor(focused ? new Color(38, 52, 66) : new Color(22, 30, 40));
        g.fillRoundRect(x, y, width, height, 8, 8);

        // Border — highlight when focused
        g.setColor(focused ? new Color(180, 230, 255) : new Color(80, 110, 140));
        g.drawRoundRect(x, y, width, height, 8, 8);

        // Label above field
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g.setColor(new Color(220, 200, 120));
        g.drawString(label, x, y - 5);

        // Value text (masked if password)
        String display = password ? "*".repeat(value.length()) : value;
        boolean blink  = focused && (System.currentTimeMillis() / 500) % 2 == 0;
        if (blink) display += "_";

        g.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g.setColor(Color.WHITE);
        g.drawString(display, x + 10, y + height / 2 + 5);
    }

    @Override
    public void handleClick(int mouseX, int mouseY) {
        // Focus is managed externally via setFocused()
    }

    public void handleKey(int keyCode) {
        if (!focused) return;
        if (keyCode == KeyEvent.VK_BACK_SPACE && !value.isEmpty()) {
            value = value.substring(0, value.length() - 1);
        }
    }

    public void handleChar(char c) {
        if (!focused) return;
        if (c < 32 || c > 126) return;
        if (value.length() < maxLength) value += c;
    }

    public void setFocused(boolean focused) { this.focused = focused; }
    public boolean isFocused()              { return focused; }
    public String  getText()                { return value; }
    public void    clear()                  { value = ""; }
}
