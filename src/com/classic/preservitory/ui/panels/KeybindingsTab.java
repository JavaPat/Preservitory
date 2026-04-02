package com.classic.preservitory.ui.panels;

import com.classic.preservitory.client.settings.ClientSettings;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

class KeybindingsTab implements Tab {

    private static final int CONTENT_Y = RightPanel.CONTENT_Y;
    private static final int ROW_H = 24;
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
    public void handleClick(int sx, int sy, int px, int pw) {
        for (Map.Entry<ClientSettings.Action, Rectangle> entry : rowBounds.entrySet()) {
            if (entry.getValue().contains(sx, sy) && rebindListener != null) {
                rebindListener.accept(entry.getKey());
                return;
            }
        }
    }

    void render(Graphics2D g, int px, int pw) {
        int x = px + 8;
        int bw = pw - 16;
        int y = CONTENT_Y + 10;

        g.setFont(new Font("Arial", Font.BOLD, 10));
        drawOutlined(g, "KEYBINDINGS", px + pw / 2 - 30, y + 2,
                new Color(200, 185, 100), new Color(0, 0, 0, 160));
        y += 22;

        for (ClientSettings.Action action : ClientSettings.Action.values()) {
            Rectangle bounds = new Rectangle(x, y, bw, ROW_H);
            rowBounds.put(action, bounds);

            boolean listening = action == listeningAction;
            g.setColor(listening ? new Color(80, 60, 20) : new Color(35, 28, 15));
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 5, 5);
            g.setColor(listening ? new Color(200, 170, 70) : new Color(70, 56, 28));
            g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 5, 5);

            g.setFont(new Font("Arial", Font.PLAIN, 10));
            g.setColor(new Color(220, 210, 150));
            g.drawString(action.getLabel(), x + 8, y + 15);

            String value = listening
                    ? "Press a key..."
                    : KeyEvent.getKeyText(settings.getKeyBinding(action));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(listening ? new Color(255, 225, 140) : new Color(180, 170, 120));
            g.drawString(value, x + bw - fm.stringWidth(value) - 8, y + 15);

            y += ROW_H + ROW_GAP;
        }
    }

    String getHoveredButtonLabel(int sx, int sy) {
        for (Map.Entry<ClientSettings.Action, Rectangle> entry : rowBounds.entrySet()) {
            if (entry.getValue().contains(sx, sy)) {
                return "Rebind " + entry.getKey().getLabel();
            }
        }
        return null;
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
