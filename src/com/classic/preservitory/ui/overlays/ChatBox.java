package com.classic.preservitory.ui.overlays;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Scrolling message log shown as a semi-transparent overlay at the bottom of
 * the game viewport, plus a real-time chat input bar when the player is typing.
 *
 * === Message colours ===
 *   COLOR_DEFAULT  white       — generic / system
 *   COLOR_SKILL    green       — skill events (+XP, got item)
 *   COLOR_COMBAT   red         — combat events (attack, death)
 *   COLOR_LEVEL    gold        — level-up / milestone
 *   COLOR_QUEST    orange      — quest progress
 *   COLOR_SYSTEM   light-blue  — save/load/connection notices
 *   COLOR_CHAT     cyan        — multiplayer chat messages
 *
 * === Normal rendering ===
 *   chatBox.render(g2, 0, 512, 566, 88);
 *
 * === Typing mode ===
 *   chatBox.render(g2, 0, 512, 566, 88, currentInput);
 *   Pass null (or use the 5-arg overload) to hide the input bar.
 */
public class ChatBox {

    // -----------------------------------------------------------------------
    //  Predefined message colours
    // -----------------------------------------------------------------------

    public static final Color COLOR_DEFAULT = new Color(230, 230, 230);
    public static final Color COLOR_SKILL   = new Color( 90, 215,  90);
    public static final Color COLOR_COMBAT  = new Color(230,  85,  85);
    public static final Color COLOR_LEVEL   = new Color(255, 210,  40);
    public static final Color COLOR_QUEST   = new Color(255, 165,  50);
    public static final Color COLOR_SYSTEM  = new Color(160, 185, 230);
    /** Player chat messages — cyan, clearly distinct from all game-event colours. */
    public static final Color COLOR_CHAT    = new Color( 60, 215, 215);

    // -----------------------------------------------------------------------
    //  Layout constants
    // -----------------------------------------------------------------------

    private static final int MAX_MESSAGES  = 6;
    /** Height of the typing input bar in pixels (shown only in typing mode). */
    private static final int INPUT_BAR_H   = 18;

    // -----------------------------------------------------------------------
    //  Internal state
    // -----------------------------------------------------------------------

    private final Deque<ChatEntry> entries = new ArrayDeque<>();

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    /** Add a white message. */
    public void post(String text) {
        post(text, COLOR_DEFAULT);
    }

    /** Add a coloured message. */
    public void post(String text, Color color) {
        if (entries.size() >= MAX_MESSAGES) entries.pollFirst();
        entries.addLast(new ChatEntry(text, color));
    }

    // -----------------------------------------------------------------------
    //  Rendering — no typing indicator (normal / game-event messages only)
    // -----------------------------------------------------------------------

    /**
     * Draw the chat box without a typing bar (normal game-event messages).
     * Delegates to the full overload with {@code typingInput = null}.
     */
    public void render(Graphics2D g, int x, int y, int w, int h) {
        render(g, x, y, w, h, null);
    }

    // -----------------------------------------------------------------------
    //  Rendering — full overload (typing mode when typingInput != null)
    // -----------------------------------------------------------------------

    /**
     * Draw the chat box at screen-space rect (x, y, w, h).
     * Must be called AFTER the camera transform is restored to screen space.
     *
     * @param typingInput  Current typed text (non-null = show input bar, null = hide it)
     */
    public void render(Graphics2D g, int x, int y, int w, int h, String typingInput) {
        boolean typing = (typingInput != null);

        // ---- Background ----
        g.setColor(new Color(8, 6, 4, 185));
        g.fillRect(x, y, w, h);

        // ---- Top border — two-tone gold line (RuneScape style) ----
        g.setColor(new Color(150, 125, 45));
        g.drawLine(x, y, x + w - 1, y);
        g.setColor(new Color(80, 65, 20));
        g.drawLine(x, y + 1, x + w - 1, y + 1);

        // ---- Left / right thin borders ----
        g.setColor(new Color(60, 50, 20));
        g.drawLine(x,         y, x,         y + h - 1);
        g.drawLine(x + w - 1, y, x + w - 1, y + h - 1);

        // ---- Compute message area (shrink if input bar is visible) ----
        int msgAreaH = typing ? h - INPUT_BAR_H : h;

        // ---- Messages (newest at bottom, older scrolling upward) ----
        if (!entries.isEmpty()) {
            g.setFont(new Font("Monospaced", Font.PLAIN, 11));
            FontMetrics fm  = g.getFontMetrics();
            int lineH       = fm.getHeight() + 1;
            int padX        = 7;
            int bottomY     = y + msgAreaH - 5;

            ChatEntry[] arr = entries.toArray(new ChatEntry[0]);
            for (int i = arr.length - 1; i >= 0; i--) {
                int textY = bottomY - (arr.length - 1 - i) * lineH;
                if (textY - lineH < y + 4) break;   // clipped — stop drawing

                ChatEntry e = arr[i];
                // Drop shadow for readability on any tile background
                g.setColor(new Color(0, 0, 0, 200));
                g.drawString(e.text, x + padX + 1, textY + 1);
                // Coloured text
                g.setColor(e.color);
                g.drawString(e.text, x + padX, textY);
            }
        }

        // ---- Input bar (visible only in typing mode) ----
        if (typing) {
            int barY = y + h - INPUT_BAR_H;

            // Slightly lighter background to distinguish from message log
            g.setColor(new Color(22, 18, 12, 220));
            g.fillRect(x, barY, w, INPUT_BAR_H);

            // Top divider line
            g.setColor(new Color(150, 125, 45));
            g.drawLine(x, barY, x + w - 1, barY);

            // Blinking cursor: visible for first 500 ms of each 800 ms cycle
            boolean showCursor = (System.currentTimeMillis() % 800) < 500;
            String display = "> " + typingInput + (showCursor ? "|" : " ");

            g.setFont(new Font("Monospaced", Font.PLAIN, 11));
            int padX   = 7;
            int textY  = barY + INPUT_BAR_H - 4;

            // Drop shadow
            g.setColor(new Color(0, 0, 0, 200));
            g.drawString(display, x + padX + 1, textY + 1);
            // Gold/yellow input text — familiar "chat mode" cue
            g.setColor(new Color(255, 215, 80));
            g.drawString(display, x + padX, textY);
        }
    }

    // -----------------------------------------------------------------------
    //  Inner record
    // -----------------------------------------------------------------------

    private static final class ChatEntry {
        final String text;
        final Color  color;
        ChatEntry(String text, Color color) { this.text = text; this.color = color; }
    }
}
