package com.classic.preservitory.ui.panels;

import com.classic.preservitory.client.settings.ClientSettings;
import com.classic.preservitory.ui.framework.TabRenderer;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

class KeybindingsTab implements TabRenderer {

    private static final int ROW_H   = 24;
    private static final int ROW_GAP = 8;

    private final ClientSettings settings;
    private final Map<ClientSettings.Action, Rectangle> rowBounds = new EnumMap<>(ClientSettings.Action.class);
    private Consumer<ClientSettings.Action> rebindListener;
    private ClientSettings.Action listeningAction;

    KeybindingsTab(ClientSettings settings) {
        this.settings = settings;
    }

    void setRebindListener(Consumer<ClientSettings.Action> listener) {
        this.rebindListener = listener;
    }

    void setListeningAction(ClientSettings.Action action) {
        this.listeningAction = action;
    }

    @Override
    public void handleClick(int sx, int sy, int x, int y, int width, int height) {
        for (Map.Entry<ClientSettings.Action, Rectangle> entry : rowBounds.entrySet()) {
            if (entry.getValue().contains(sx, sy) && rebindListener != null) {
                rebindListener.accept(entry.getKey());
                return;
            }
        }
    }

    @Override
    public String getHoveredLabel(int sx, int sy, int x, int y, int width, int height) {
        for (Map.Entry<ClientSettings.Action, Rectangle> entry : rowBounds.entrySet()) {
            if (entry.getValue().contains(sx, sy)) {
                return "Rebind " + entry.getKey().getLabel();
            }
        }
        return null;
    }

    @Override
    public void render(Graphics2D g, int x, int y, int width, int height) {
        int bx = x + 8;
        int bw = width - 16;
        int cy = y + 10;

        g.setFont(new Font("Arial", Font.BOLD, 10));
        drawOutlined(g, "KEYBINDINGS", x + width / 2 - 30, cy + 2,
                new Color(200, 185, 100), new Color(0, 0, 0, 160));
        cy += 22;

        for (ClientSettings.Action action : ClientSettings.Action.values()) {
            Rectangle bounds = new Rectangle(bx, cy, bw, ROW_H);
            rowBounds.put(action, bounds);

            boolean listening = action == listeningAction;
            g.setColor(listening ? new Color(80, 60, 20) : new Color(35, 28, 15));
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 5, 5);
            g.setColor(listening ? new Color(200, 170, 70) : new Color(70, 56, 28));
            g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 5, 5);

            g.setFont(new Font("Arial", Font.PLAIN, 10));
            g.setColor(new Color(220, 210, 150));
            g.drawString(action.getLabel(), bx + 8, cy + 15);

            String value = listening
                    ? "Press a key..."
                    : KeyEvent.getKeyText(settings.getKeyBinding(action));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(listening ? new Color(255, 225, 140) : new Color(180, 170, 120));
            g.drawString(value, bx + bw - fm.stringWidth(value) - 8, cy + 15);

            cy += ROW_H + ROW_GAP;
        }
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
