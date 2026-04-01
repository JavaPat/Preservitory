package com.classic.preservitory.ui.quests;

import com.classic.preservitory.client.definitions.ItemDefinitionManager;
import com.classic.preservitory.util.Constants;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * OSRS-style quest completion overlay.
 *
 * Opened by GamePanel when quest feedback packets arrive after dialogue ends.
 * Purely client-side — no server notification on close.
 *
 * Lifecycle:
 *   open(questName)         — call on QUEST_COMPLETE packet
 *   addReward(itemId, amt)  — call on each QUEST_REWARD packet
 *   addXpReward(skill, xp) — call on each QUEST_XP packet
 *   close() / isOpen()
 *
 * Layout is recomputed on every render() call so the window grows correctly
 * as reward rows are added before the first repaint.
 */
public class QuestCompleteWindow {

    // -----------------------------------------------------------------------
    //  Layout constants
    // -----------------------------------------------------------------------

    private static final int WIN_W      = 310;
    private static final int PAD_X      = 14;
    private static final int TITLE_H    = 32;   // title text + divider
    private static final int NAME_H     = 24;   // quest-name row
    private static final int SEC_GAP    =  8;   // gap before Rewards / XP sections
    private static final int ROW_H      = 17;   // height per reward / xp row
    private static final int BTN_W      = 110;
    private static final int BTN_H      = 24;
    private static final int BTN_TOP    = 10;   // gap above Continue button
    private static final int PAD_BOT    = 10;   // padding below button
    private static final int CLOSE_SZ   = 18;

    // -----------------------------------------------------------------------
    //  State
    // -----------------------------------------------------------------------

    private boolean open      = false;
    private String  questName = "";

    /** [itemId, amount] */
    private final List<int[]>    rewards   = new ArrayList<>();
    /** [skill (lowercase), xp (string)] */
    private final List<String[]> xpRewards = new ArrayList<>();

    // Computed each render
    private int winX, winY, winH;
    private int btnX, btnY;
    private int closeX, closeY;

    private boolean closeHovered = false;
    private boolean btnHovered   = false;

    /** Timestamp (ms) when open() was last called — used by softClose() guard. */
    private long openedAtMs = 0;
    private static final long CLOSE_GUARD_MS = 150;

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    /** Begin showing the window with just the quest name. Call before addReward/addXpReward. */
    public void open(String questName) {
        this.questName = questName != null ? questName : "";
        rewards.clear();
        xpRewards.clear();
        closeHovered = false;
        btnHovered   = false;
        openedAtMs   = System.currentTimeMillis();
        open         = true;
    }

    /** Add an item reward row. Safe to call after open(). */
    public void addReward(int itemId, int amount) {
        rewards.add(new int[]{itemId, amount});
    }

    /** Add an XP reward row. Safe to call after open(). */
    public void addXpReward(String skill, int xp) {
        xpRewards.add(new String[]{skill != null ? skill : "", String.valueOf(xp)});
    }

    /** Unconditional close — use for ESC, logout, disconnect. */
    public void close() { open = false; }

    /**
     * Movement-safe close: ignored if the window was opened within the last
     * {@value CLOSE_GUARD_MS} ms, preventing accidental closure when the
     * player starts moving immediately after a quest completes.
     */
    public void softClose() {
        if (System.currentTimeMillis() - openedAtMs >= CLOSE_GUARD_MS) open = false;
    }

    public boolean isOpen() { return open; }

    public boolean containsPoint(int mx, int my) {
        return open && mx >= winX && mx < winX + WIN_W && my >= winY && my < winY + winH;
    }

    // -----------------------------------------------------------------------
    //  Input
    // -----------------------------------------------------------------------

    /**
     * Handles any click while the window is open.
     *
     * Outside window   → close (accidental-movement guard does NOT apply here;
     *                    the player explicitly clicked away)
     * Inside window    → only the Continue button or X button close it;
     *                    clicking elsewhere inside is a no-op
     */
    public void handleClick(int mx, int my) {
        if (!open) return;

        if (!containsPoint(mx, my)) {
            close();
            return;
        }

        // Close button
        if (mx >= closeX && mx < closeX + CLOSE_SZ
         && my >= closeY && my < closeY + CLOSE_SZ) {
            close();
            return;
        }

        // Continue button
        if (mx >= btnX && mx < btnX + BTN_W
         && my >= btnY && my < btnY + BTN_H) {
            close();
        }
        // Any other inside-click is intentionally consumed but does not close.
    }

    public void handleMouseMove(int mx, int my) {
        if (!open) return;
        closeHovered = mx >= closeX && mx < closeX + CLOSE_SZ
                    && my >= closeY && my < closeY + CLOSE_SZ;
        btnHovered   = mx >= btnX   && mx < btnX   + BTN_W
                    && my >= btnY   && my < btnY   + BTN_H;
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    public void render(Graphics2D g) {
        if (!open) return;
        computeLayout();
        drawBackground(g);
        drawTitle(g);
        drawContent(g);
        drawCloseButton(g);
        drawContinueButton(g);
    }

    // -----------------------------------------------------------------------
    //  Layout
    // -----------------------------------------------------------------------

    private void computeLayout() {
        int contentRows = 0;
        if (!rewards.isEmpty())   contentRows += 1 + rewards.size();   // "Rewards:" header + rows
        if (!xpRewards.isEmpty()) contentRows += xpRewards.size();

        int sectionsH = contentRows == 0 ? 0 : SEC_GAP + contentRows * ROW_H;
        winH = TITLE_H + NAME_H + sectionsH + BTN_TOP + BTN_H + PAD_BOT;

        int availH = Constants.VIEWPORT_H - 88; // 88 = CHAT_H
        winX = (Constants.VIEWPORT_W - WIN_W) / 2;
        // Position at ~40% of the available height so the window sits visibly
        // above centre, giving comfortable separation from the chatbox.
        winY = Math.max(6, (availH - winH) * 2 / 5);

        btnX   = winX + (WIN_W - BTN_W) / 2;
        btnY   = winY + winH - BTN_H - PAD_BOT;
        closeX = winX + WIN_W - CLOSE_SZ - 8;
        closeY = winY + 6;
    }

    private void drawBackground(Graphics2D g) {
        // Main fill
        g.setColor(new Color(14, 10, 5, 252));
        g.fillRoundRect(winX, winY, WIN_W, winH, 8, 8);
        // Outer border — gold
        g.setColor(new Color(140, 110, 35));
        g.drawRoundRect(winX, winY, WIN_W, winH, 8, 8);
        // Inner border — dark gold
        g.setColor(new Color(55, 44, 14));
        g.drawRoundRect(winX + 1, winY + 1, WIN_W - 2, winH - 2, 7, 7);
    }

    private void drawTitle(Graphics2D g) {
        // Title text — gold, bold
        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        FontMetrics fm  = g.getFontMetrics();
        String title    = "Quest Complete!";
        int tx = winX + (WIN_W - fm.stringWidth(title)) / 2;
        int ty = winY + fm.getAscent() + 6;
        g.setColor(new Color(0, 0, 0, 170));
        g.drawString(title, tx + 1, ty + 1);
        g.setColor(new Color(255, 210, 40));
        g.drawString(title, tx, ty);

        // Divider below title
        int divY = winY + TITLE_H - 4;
        g.setColor(new Color(80, 63, 20));
        g.drawLine(winX + PAD_X, divY, winX + WIN_W - PAD_X, divY);
        g.setColor(new Color(38, 30, 10));
        g.drawLine(winX + PAD_X, divY + 1, winX + WIN_W - PAD_X, divY + 1);

        // Quest name — orange, centred, below divider
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        fm = g.getFontMetrics();
        int nx = winX + (WIN_W - fm.stringWidth(questName)) / 2;
        int ny = winY + TITLE_H + fm.getAscent() + 2;
        g.setColor(new Color(0, 0, 0, 150));
        g.drawString(questName, nx + 1, ny + 1);
        g.setColor(new Color(255, 165, 50));
        g.drawString(questName, nx, ny);
    }

    private void drawContent(Graphics2D g) {
        if (rewards.isEmpty() && xpRewards.isEmpty()) return;

        int cursor = winY + TITLE_H + NAME_H + SEC_GAP;

        // ---- Rewards ----
        if (!rewards.isEmpty()) {
            g.setFont(new Font("Monospaced", Font.BOLD, 10));
            FontMetrics fmBold = g.getFontMetrics();
            g.setColor(new Color(185, 163, 80));
            g.drawString("Rewards:", winX + PAD_X, cursor + fmBold.getAscent());
            cursor += ROW_H;

            g.setFont(new Font("Monospaced", Font.PLAIN, 10));
            FontMetrics fm = g.getFontMetrics();
            for (int[] reward : rewards) {
                String name = ItemDefinitionManager.exists(reward[0])
                        ? ItemDefinitionManager.get(reward[0]).name
                        : "Unknown (id: " + reward[0] + ")";
                String line = "  " + name + " x" + reward[1];
                int ly = cursor + fm.getAscent();
                g.setColor(new Color(0, 0, 0, 140));
                g.drawString(line, winX + PAD_X + 1, ly + 1);
                g.setColor(new Color(90, 215, 90));
                g.drawString(line, winX + PAD_X, ly);
                cursor += ROW_H;
            }
        }

        // ---- XP ----
        if (!xpRewards.isEmpty()) {
            g.setFont(new Font("Monospaced", Font.PLAIN, 10));
            FontMetrics fm = g.getFontMetrics();
            for (String[] xp : xpRewards) {
                String skill = xp[0].isEmpty() ? "XP"
                        : xp[0].substring(0, 1).toUpperCase() + xp[0].substring(1).toLowerCase();
                int xpVal;
                try { xpVal = Integer.parseInt(xp[1]); } catch (NumberFormatException e) { continue; }
                String line = "  " + xpVal + " " + skill + " XP";
                int ly = cursor + fm.getAscent();
                g.setColor(new Color(0, 0, 0, 140));
                g.drawString(line, winX + PAD_X + 1, ly + 1);
                g.setColor(new Color(90, 215, 90));
                g.drawString(line, winX + PAD_X, ly);
                cursor += ROW_H;
            }
        }
    }

    private void drawContinueButton(Graphics2D g) {
        g.setColor(btnHovered ? new Color(70, 56, 18) : new Color(38, 30, 8));
        g.fillRoundRect(btnX, btnY, BTN_W, BTN_H, 5, 5);
        g.setColor(btnHovered ? new Color(220, 185, 65) : new Color(120, 96, 32));
        g.drawRoundRect(btnX, btnY, BTN_W, BTN_H, 5, 5);

        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();
        String label = "Continue";
        int lx = btnX + (BTN_W - fm.stringWidth(label)) / 2;
        int ly = btnY + (BTN_H - fm.getHeight()) / 2 + fm.getAscent();
        g.setColor(btnHovered ? new Color(255, 230, 95) : new Color(195, 170, 80));
        g.drawString(label, lx, ly);
    }

    private void drawCloseButton(Graphics2D g) {
        g.setColor(closeHovered ? new Color(200, 55, 55) : new Color(110, 30, 30));
        g.fillRoundRect(closeX, closeY, CLOSE_SZ, CLOSE_SZ, 4, 4);
        g.setColor(new Color(190, 160, 110));
        g.drawRoundRect(closeX, closeY, CLOSE_SZ, CLOSE_SZ, 4, 4);
        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Color.WHITE);
        g.drawString("X", closeX + (CLOSE_SZ - fm.stringWidth("X")) / 2, closeY + CLOSE_SZ - 4);
    }
}
