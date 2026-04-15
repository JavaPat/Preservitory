package com.classic.preservitory.ui.panels;

import com.classic.preservitory.client.settings.ClientSettings;
import com.classic.preservitory.ui.framework.TabRenderer;

import java.awt.*;

/**
 * Renders the Settings tab: window and metric toggles.
 */
class SettingsTab implements TabRenderer {

    private final ClientSettings settings;

    private static final int BTN_H   = 22;
    private static final int BTN_GAP = 8;

    private boolean fullscreen = false;
    private boolean resizable  = false;

    private int fullscreenBtnY   = 0;
    private int resizableBtnY    = 0;
    private int fpsBtnY          = 0;
    private int pingBtnY         = 0;
    private int xpTrackerBtnY    = 0;
    private int keybindingsBtnY  = 0;
    private int shiftDropBtnY    = 0;
    private int minimapBtnY      = 0;
    private int directionBtnY    = 0;

    private final Rectangle panelBounds = new Rectangle();

    private Runnable fullscreenListener  = null;
    private Runnable resizableListener   = null;
    private Runnable fpsListener         = null;
    private Runnable pingListener        = null;
    private Runnable totalXpListener     = null;
    private Runnable keybindingsListener = null;
    private Runnable shiftDropListener   = null;
    private Runnable minimapListener     = null;
    private Runnable directionListener   = null;

    void setFullscreenListener(Runnable l)  { fullscreenListener  = l; }
    void setResizableListener(Runnable l)   { resizableListener   = l; }
    void setFpsListener(Runnable l)         { fpsListener         = l; }
    void setPingListener(Runnable l)        { pingListener        = l; }
    void setTotalXpListener(Runnable l)     { totalXpListener     = l; }
    void setKeybindingsListener(Runnable l) { keybindingsListener = l; }
    void setShiftDropListener(Runnable l)   { shiftDropListener   = l; }
    void setMinimapListener(Runnable l)     { minimapListener     = l; }
    void setDirectionListener(Runnable l)   { directionListener   = l; }
    void setFullscreen(boolean v)           { fullscreen = v; }
    void setResizable(boolean v)            { resizable  = v; }
    void setShowFps(boolean v)              {}
    void setShowPing(boolean v)             {}

    SettingsTab(ClientSettings settings) {
        this.settings = settings;
    }

    @Override
    public void handleClick(int sx, int sy, int x, int y, int width, int height) {
        if (fullscreenBtnY  > 0 && sy >= fullscreenBtnY  && sy < fullscreenBtnY  + BTN_H) { fullscreen = !fullscreen; if (fullscreenListener  != null) fullscreenListener .run(); }
        else if (resizableBtnY   > 0 && sy >= resizableBtnY   && sy < resizableBtnY   + BTN_H) { resizable  = !resizable;  if (resizableListener   != null) resizableListener  .run(); }
        else if (fpsBtnY         > 0 && sy >= fpsBtnY         && sy < fpsBtnY         + BTN_H) { if (fpsListener         != null) fpsListener        .run(); }
        else if (pingBtnY        > 0 && sy >= pingBtnY        && sy < pingBtnY        + BTN_H) { if (pingListener        != null) pingListener       .run(); }
        else if (xpTrackerBtnY   > 0 && sy >= xpTrackerBtnY   && sy < xpTrackerBtnY   + BTN_H) { if (totalXpListener     != null) totalXpListener    .run(); }
        else if (keybindingsBtnY > 0 && sy >= keybindingsBtnY && sy < keybindingsBtnY + BTN_H) { if (keybindingsListener != null) keybindingsListener.run(); }
        else if (shiftDropBtnY   > 0 && sy >= shiftDropBtnY   && sy < shiftDropBtnY   + BTN_H) { if (shiftDropListener   != null) shiftDropListener  .run(); }
        else if (minimapBtnY     > 0 && sy >= minimapBtnY     && sy < minimapBtnY     + BTN_H) { if (minimapListener     != null) minimapListener    .run(); }
        else if (directionBtnY   > 0 && sy >= directionBtnY   && sy < directionBtnY   + BTN_H) { if (directionListener   != null) directionListener  .run(); }
    }

    @Override
    public String getHoveredLabel(int sx, int sy, int x, int y, int width, int height) {
        if (fullscreenBtnY  > 0 && sy >= fullscreenBtnY  && sy < fullscreenBtnY  + BTN_H) return "Toggle fullscreen";
        if (resizableBtnY   > 0 && sy >= resizableBtnY   && sy < resizableBtnY   + BTN_H) return "Toggle resizable window";
        if (fpsBtnY         > 0 && sy >= fpsBtnY         && sy < fpsBtnY         + BTN_H) return "Toggle FPS overlay";
        if (pingBtnY        > 0 && sy >= pingBtnY        && sy < pingBtnY        + BTN_H) return "Toggle ping overlay";
        if (xpTrackerBtnY   > 0 && sy >= xpTrackerBtnY   && sy < xpTrackerBtnY   + BTN_H) return "Toggle total XP display";
        if (keybindingsBtnY > 0 && sy >= keybindingsBtnY && sy < keybindingsBtnY + BTN_H) return "Open keybindings";
        if (shiftDropBtnY   > 0 && sy >= shiftDropBtnY   && sy < shiftDropBtnY   + BTN_H) return "Shift+click to drop items";
        if (minimapBtnY     > 0 && sy >= minimapBtnY     && sy < minimapBtnY     + BTN_H) return "Toggle minimap overlay";
        if (directionBtnY   > 0 && sy >= directionBtnY   && sy < directionBtnY   + BTN_H) return "Toggle direction indicator";
        return null;
    }

    @Override
    public void render(Graphics2D g, int x, int y, int width, int height) {
        int bx     = x + 8;
        int bw     = width - 16;
        int cy     = y + 10;
        int startY = cy;

        g.setFont(new Font("Arial", Font.BOLD, 10));
        drawOutlined(g, "SETTINGS", x + width / 2 - 22, cy + 2,
                new Color(200, 185, 100), new Color(0, 0, 0, 160));
        cy += 22;

        fullscreenBtnY  = cy; drawToggle(g, bx, cy, bw, "Fullscreen",          fullscreen);               cy += BTN_H + BTN_GAP;
        resizableBtnY   = cy; drawToggle(g, bx, cy, bw, "Resizable Window",    resizable);                cy += BTN_H + BTN_GAP;
        fpsBtnY         = cy; drawToggle(g, bx, cy, bw, "Show FPS",            settings.isShowFps());     cy += BTN_H + BTN_GAP;
        pingBtnY        = cy; drawToggle(g, bx, cy, bw, "Show Ping",           settings.isShowPing());    cy += BTN_H + BTN_GAP;
        xpTrackerBtnY   = cy; drawToggle(g, bx, cy, bw, "Show Total XP",       settings.isShowTotalXp()); cy += BTN_H + BTN_GAP;
        keybindingsBtnY = cy; drawToggle(g, bx, cy, bw, "Keybindings",         false);                    cy += BTN_H + BTN_GAP;
        shiftDropBtnY   = cy; drawToggle(g, bx, cy, bw, "Shift-click Drop",    settings.isShiftClickDrop()); cy += BTN_H + BTN_GAP;
        minimapBtnY     = cy; drawToggle(g, bx, cy, bw, "Show Minimap",        settings.isShowMinimap()); cy += BTN_H + BTN_GAP;
        directionBtnY   = cy; drawToggle(g, bx, cy, bw, "Direction Indicator", settings.isShowDirectionIndicator());

        panelBounds.setBounds(bx, startY, bw, (cy + BTN_H) - startY);
    }

    boolean containsPoint(int sx, int sy) {
        return panelBounds.contains(sx, sy);
    }

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
