package com.classic.preservitory.ui.overlays;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

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
    //  Layout constants — dialogue options
    // -----------------------------------------------------------------------

    private static final int OPT_ROW_H = 13; // height of each option row in pixels

    // -----------------------------------------------------------------------
    //  Internal state
    // -----------------------------------------------------------------------

    private final Deque<ChatEntry> entries = new ArrayDeque<>();

    /** Non-null while an NPC dialogue is active. */
    private String       dialogueNpcName = null;
    private String       dialogueText    = null;
    /** Player-selectable options for the current dialogue node. Empty = none. */
    private List<String> dialogueOptions = new ArrayList<>();

    /** Cached top-y of the first option row within the chatbox, set during render. */
    private int optionsStartY = -1;

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    /** Enter dialogue mode — replaces the message log with a single NPC line. */
    public void setDialogue(String npcName, String text) {
        this.dialogueNpcName = npcName;
        this.dialogueText    = text;
        this.dialogueOptions.clear();
        this.optionsStartY   = -1;
    }

    /**
     * Set the player-selectable options for the current dialogue node.
     * Must be called after {@link #setDialogue(String, String)}.
     */
    public void setDialogueOptions(List<String> options) {
        this.dialogueOptions = options != null ? new ArrayList<>(options) : new ArrayList<>();
        this.optionsStartY   = -1;
    }

    /** Exit dialogue mode — resume showing the normal message log. */
    public void clearDialogue() {
        this.dialogueNpcName = null;
        this.dialogueText    = null;
        this.dialogueOptions.clear();
        this.optionsStartY   = -1;
    }

    /** True when the current dialogue node has player-selectable options. */
    public boolean hasOptions() {
        return dialogueNpcName != null && !dialogueOptions.isEmpty();
    }

    /** Number of options currently shown. */
    public int getOptionCount() { return dialogueOptions.size(); }

    /**
     * Returns the 0-based index of the option row under the given y-coordinate
     * (relative to the top of the chatbox widget), or {@code -1} if no option
     * is there.  Only meaningful after the first {@link #render} call that drew options.
     *
     * @param chatBoxY   y coordinate of the click, relative to the chatbox top edge
     */
    public int getOptionIndexAtY(int chatBoxY) {
        if (optionsStartY < 0 || dialogueOptions.isEmpty()) return -1;
        int relY = chatBoxY - optionsStartY;
        if (relY < 0) return -1;
        int idx = relY / OPT_ROW_H;
        return (idx < dialogueOptions.size()) ? idx : -1;
    }

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

        // ---- Dialogue mode: show NPC name + current line ----
        if (dialogueNpcName != null && dialogueText != null) {
            int padX = 7;

            // NPC name (cyan, bold)
            g.setFont(new Font("Monospaced", Font.BOLD, 11));
            FontMetrics fmBold = g.getFontMetrics();
            int nameY = y + fmBold.getAscent() + 5;
            g.setColor(new Color(0, 0, 0, 200));
            g.drawString(dialogueNpcName + ":", x + padX + 1, nameY + 1);
            g.setColor(new Color(60, 215, 215));
            g.drawString(dialogueNpcName + ":", x + padX, nameY);

            // Dialogue text (white)
            g.setFont(new Font("Monospaced", Font.PLAIN, 11));
            FontMetrics fm = g.getFontMetrics();
            int textY = nameY + fm.getHeight() + 2;
            g.setColor(new Color(0, 0, 0, 200));
            g.drawString(dialogueText, x + padX + 1, textY + 1);
            g.setColor(Color.WHITE);
            g.drawString(dialogueText, x + padX, textY);

            if (!dialogueOptions.isEmpty()) {
                // ---- Option rows ----
                g.setFont(new Font("Monospaced", Font.PLAIN, 10));
                FontMetrics fmOpt = g.getFontMetrics();
                int optCursor = textY + fmOpt.getHeight() + 2;
                optionsStartY = optCursor - y; // store relative to chatbox top
                int maxLabelW = w - 2 * padX - 2; // maximum pixel width for option text
                int bottomBound = y + msgAreaH - 6; // don't render below this
                for (int i = 0; i < dialogueOptions.size() && i < 4; i++) {
                    String label = "[" + (i + 1) + "] " + dialogueOptions.get(i);
                    label = truncateText(label, fmOpt, maxLabelW);
                    int oy = optCursor + fmOpt.getAscent();
                    if (oy > bottomBound) break; // no space left — stop drawing
                    g.setColor(new Color(0, 0, 0, 180));
                    g.drawString(label, x + padX + 1, oy + 1);
                    g.setColor(new Color(255, 215, 80));
                    g.drawString(label, x + padX, oy);
                    optCursor += OPT_ROW_H;
                }

                // Hint (bottom-right)
                String prompt = "[Press 1-" + Math.min(dialogueOptions.size(), 4) + " or click]";
                g.setFont(new Font("Monospaced", Font.ITALIC, 10));
                FontMetrics fmHint = g.getFontMetrics();
                int hintX = x + w - fmHint.stringWidth(prompt) - padX;
                int hintY = y + msgAreaH - 5;
                g.setColor(new Color(0, 0, 0, 180));
                g.drawString(prompt, hintX + 1, hintY + 1);
                g.setColor(new Color(160, 160, 160));
                g.drawString(prompt, hintX, hintY);
            } else {
                // "[Click to continue]" hint (gray, bottom-right)
                String prompt = "[Click to continue]";
                g.setFont(new Font("Monospaced", Font.ITALIC, 10));
                FontMetrics fmHint = g.getFontMetrics();
                int hintX = x + w - fmHint.stringWidth(prompt) - padX;
                int hintY = y + msgAreaH - 5;
                g.setColor(new Color(0, 0, 0, 180));
                g.drawString(prompt, hintX + 1, hintY + 1);
                g.setColor(new Color(160, 160, 160));
                g.drawString(prompt, hintX, hintY);
            }

        // ---- Normal mode: scrolling message log ----
        } else if (!entries.isEmpty()) {
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
    //  Rendering helpers
    // -----------------------------------------------------------------------

    /**
     * Truncates {@code text} with {@code "..."} if it exceeds {@code maxWidth} pixels.
     * Returns the original string unchanged if it fits.
     */
    private static String truncateText(String text, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisW = fm.stringWidth(ellipsis);
        int avail = maxWidth - ellipsisW;
        if (avail <= 0) return ellipsis;
        int len = text.length();
        while (len > 0 && fm.stringWidth(text.substring(0, len)) > avail) len--;
        return text.substring(0, len) + ellipsis;
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
