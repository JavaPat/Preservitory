package com.classic.preservitory.ui.panels;

import com.classic.preservitory.ui.framework.TabRenderer;

import java.awt.*;

/**
 * Renders the Logout tab content and handles the two-step confirm flow.
 *
 * State machine:
 *   idle       — shows "Click to Logout" button
 *   confirming — shows "Click again to confirm" + countdown, resets if not clicked
 */
class LogoutTab implements TabRenderer {

    static final double CONFIRM_TIMEOUT = 3.0;

    private static final int BTN_H = 22;

    private double   confirmTimer = 0;
    private int      buttonY      = 0;
    private Runnable logoutAction;

    void setLogoutListener(Runnable action) {
        this.logoutAction = action;
    }

    void tick(double deltaTime) {
        if (confirmTimer > 0) confirmTimer = Math.max(0, confirmTimer - deltaTime);
    }

    void resetConfirm() {
        confirmTimer = 0;
    }

    boolean isConfirming() {
        return confirmTimer > 0;
    }

    @Override
    public void handleClick(int sx, int sy, int x, int y, int width, int height) {
        if (buttonY <= 0) return;
        int bx = x + RightPanel.CONTENT_PADDING;
        int bw = width - RightPanel.CONTENT_PADDING * 2;
        if (sx >= bx && sx < bx + bw && sy >= buttonY && sy < buttonY + BTN_H) {
            if (confirmTimer > 0) {
                confirmTimer = 0;
                if (logoutAction != null) logoutAction.run();
            } else {
                confirmTimer = CONFIRM_TIMEOUT;
            }
        }
    }

    @Override
    public void render(Graphics2D g, int x, int y, int width, int height) {
        int bx = x + RightPanel.CONTENT_PADDING;
        int bw = width - RightPanel.CONTENT_PADDING * 2;
        int cy = y + RightPanel.CONTENT_PADDING + 8;

        g.setFont(new Font("Arial", Font.PLAIN, 9));
        FontMetrics fm = g.getFontMetrics();
        String prompt = "Do you wish to log out? If so, click below.";
        g.setColor(new Color(180, 180, 180));
        g.drawString(prompt, x + (width - fm.stringWidth(prompt)) / 2, cy);
        cy += 20;

        boolean confirming = confirmTimer > 0;
        buttonY = cy;

        g.setColor(confirming ? new Color(160, 40, 40, 230) : new Color(55, 20, 20, 220));
        g.fillRoundRect(bx, cy, bw, BTN_H, 5, 5);

        if (confirming) {
            g.setColor(new Color(220, 80, 80));
            g.drawLine(bx + 1, cy, bx + bw - 2, cy);
        }

        g.setColor(confirming ? new Color(220, 80, 80) : new Color(110, 50, 50));
        g.drawRoundRect(bx, cy, bw, BTN_H, 5, 5);

        g.setFont(new Font("Arial", Font.BOLD, 9));
        FontMetrics btnFm = g.getFontMetrics();
        String btnLabel = confirming
                ? "Click again to confirm (" + (int) Math.ceil(confirmTimer) + "s)"
                : "Click to Logout";
        int lw = btnFm.stringWidth(btnLabel);
        g.setColor(confirming ? new Color(255, 180, 180) : new Color(220, 140, 140));
        g.drawString(btnLabel, bx + (bw - lw) / 2, cy + BTN_H - 5);
    }
}
