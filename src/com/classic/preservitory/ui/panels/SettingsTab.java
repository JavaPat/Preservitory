package com.classic.preservitory.ui.panels;

import com.classic.preservitory.client.settings.ClientSettings;

import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Renders the Settings tab: window and metric toggles.
 *
 * Tracks toggle state locally and fires Runnable listeners on change.
 * No window logic lives here; callers wire the listeners to actual JFrame operations.
 */
class SettingsTab implements Tab {

    private final ClientSettings settings;

    // -----------------------------------------------------------------------
    //  Layout
    // -----------------------------------------------------------------------

    private static final int CONTENT_Y = RightPanel.CONTENT_Y;
    private static final int BTN_H     = 22;
    private static final int BTN_GAP   = 8;
    private static final int KEYBIND_ROW_H = 14;

    // -----------------------------------------------------------------------
    //  State
    // -----------------------------------------------------------------------

    private boolean fullscreen = false;
    private boolean resizable  = false;
    private int fullscreenBtnY  = 0;
    private int resizableBtnY   = 0;
    private int fpsBtnY         = 0;
    private int pingBtnY        = 0;
    private int xpTrackerBtnY   = 0;
    private int keybindingsBtnY = 0;
    private int shiftDropBtnY   = 0;
    private final Rectangle panelBounds = new Rectangle();

    // -----------------------------------------------------------------------
    //  Configuration
    // -----------------------------------------------------------------------

    private Runnable fullscreenListener  = null;
    private Runnable resizableListener   = null;
    private Runnable fpsListener         = null;
    private Runnable pingListener        = null;
    private Runnable totalXpListener     = null;
    private Runnable keybindingsListener = null;
    private Runnable shiftDropListener   = null;

    void setFullscreenListener(Runnable listener)  { this.fullscreenListener  = listener; }
    void setResizableListener(Runnable listener)   { this.resizableListener   = listener; }
    void setFpsListener(Runnable listener)         { this.fpsListener         = listener; }
    void setPingListener(Runnable listener)        { this.pingListener        = listener; }
    void setTotalXpListener(Runnable listener)     { this.totalXpListener     = listener; }
    void setKeybindingsListener(Runnable listener) { this.keybindingsListener = listener; }
    void setShiftDropListener(Runnable listener)   { this.shiftDropListener   = listener; }
    void setFullscreen(boolean fullscreen)        { this.fullscreen = fullscreen; }
    void setResizable(boolean resizable)          { this.resizable = resizable; }
    void setShowFps(boolean showFps)              {}
    void setShowPing(boolean showPing)            {}

    SettingsTab(ClientSettings settings) {
        this.settings = settings;
    }

    // -----------------------------------------------------------------------
    //  Input
    // -----------------------------------------------------------------------

    @Override
    public void handleClick(int sx, int sy, int px, int pw) {
        if (fullscreenBtnY > 0 && sy >= fullscreenBtnY && sy < fullscreenBtnY + BTN_H) {
            fullscreen = !fullscreen;
            if (fullscreenListener != null) fullscreenListener.run();
        } else if (resizableBtnY > 0 && sy >= resizableBtnY && sy < resizableBtnY + BTN_H) {
            resizable = !resizable;
            if (resizableListener != null) resizableListener.run();
        } else if (fpsBtnY > 0 && sy >= fpsBtnY && sy < fpsBtnY + BTN_H) {
            if (fpsListener != null) fpsListener.run();
        } else if (pingBtnY > 0 && sy >= pingBtnY && sy < pingBtnY + BTN_H) {
            if (pingListener != null) pingListener.run();
        } else if (xpTrackerBtnY > 0 && sy >= xpTrackerBtnY && sy < xpTrackerBtnY + BTN_H) {
            if (totalXpListener != null) totalXpListener.run();
        } else if (keybindingsBtnY > 0 && sy >= keybindingsBtnY && sy < keybindingsBtnY + BTN_H) {
            if (keybindingsListener != null) keybindingsListener.run();
        } else if (shiftDropBtnY > 0 && sy >= shiftDropBtnY && sy < shiftDropBtnY + BTN_H) {
            if (shiftDropListener != null) shiftDropListener.run();
        }
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    void render(Graphics2D g, int px, int pw) {
        int x  = px + 8;
        int bw = pw - 16;
        int y  = CONTENT_Y + 10;
        int startY = y;

        g.setFont(new Font("Arial", Font.BOLD, 10));
        drawOutlined(g, "SETTINGS", px + pw / 2 - 22, y + 2,
                new Color(200, 185, 100), new Color(0, 0, 0, 160));
        y += 22;

        fullscreenBtnY = y;
        drawToggle(g, x, y, bw, "Fullscreen", fullscreen);
        y += BTN_H + BTN_GAP;

        resizableBtnY = y;
        drawToggle(g, x, y, bw, "Resizable Window", resizable);
        y += BTN_H + BTN_GAP;

        fpsBtnY = y;
        drawToggle(g, x, y, bw, "Show FPS", settings.isShowFps());
        y += BTN_H + BTN_GAP;

        pingBtnY = y;
        drawToggle(g, x, y, bw, "Show Ping", settings.isShowPing());
        y += BTN_H + BTN_GAP;

        xpTrackerBtnY = y;
        drawToggle(g, x, y, bw, "Show Total XP", settings.isShowTotalXp());
        y += BTN_H + BTN_GAP;

        keybindingsBtnY = y;
        drawToggle(g, x, y, bw, "Keybindings", false);
        y += BTN_H + BTN_GAP;

        shiftDropBtnY = y;
        drawToggle(g, x, y, bw, "Shift-click Drop", settings.isShiftClickDrop());
        panelBounds.setBounds(x, startY, bw, (y + BTN_H) - startY);
    }

    boolean containsPoint(int sx, int sy) {
        return panelBounds.contains(sx, sy);
    }

    String getHoveredButtonLabel(int sy) {
        if (fullscreenBtnY > 0 && sy >= fullscreenBtnY && sy < fullscreenBtnY + BTN_H) return "Toggle fullscreen";
        if (resizableBtnY > 0 && sy >= resizableBtnY && sy < resizableBtnY + BTN_H) return "Toggle resizable window";
        if (fpsBtnY > 0 && sy >= fpsBtnY && sy < fpsBtnY + BTN_H) return "Toggle FPS overlay";
        if (pingBtnY > 0 && sy >= pingBtnY && sy < pingBtnY + BTN_H) return "Toggle ping overlay";
        if (xpTrackerBtnY > 0 && sy >= xpTrackerBtnY && sy < xpTrackerBtnY + BTN_H) return "Toggle total XP display";
        if (keybindingsBtnY > 0 && sy >= keybindingsBtnY && sy < keybindingsBtnY + BTN_H) return "Open keybindings";
        if (shiftDropBtnY > 0 && sy >= shiftDropBtnY && sy < shiftDropBtnY + BTN_H) return "Shift+click to drop items";
        return null;
    }

    // -----------------------------------------------------------------------
    //  Private helpers
    // -----------------------------------------------------------------------

    private void drawToggle(Graphics2D g, int x, int y, int bw, String label, boolean on) {
        g.setColor(new Color(35, 28, 15));
        g.fillRoundRect(x, y, bw, BTN_H, 5, 5);

        g.setColor(new Color(70, 56, 28));
        g.drawRoundRect(x, y, bw, BTN_H, 5, 5);

        g.setFont(new Font("Arial", Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        String labelText = label + ":";
        String valueText = on ? "ON" : "OFF";
        int spacing = 6;
        int totalWidth = fm.stringWidth(labelText) + spacing + fm.stringWidth(valueText);
        int textX = x + (bw - totalWidth) / 2;
        int textY = y + 14;

        g.setColor(new Color(170, 150, 95));
        g.drawString(labelText, textX, textY);
        g.setColor(on ? new Color(120, 185, 110) : new Color(150, 95, 90));
        g.drawString(valueText, textX + fm.stringWidth(labelText) + spacing, textY);
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
