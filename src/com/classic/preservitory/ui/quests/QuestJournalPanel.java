package com.classic.preservitory.ui.quests;

import com.classic.preservitory.ui.panels.RightPanel;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the Quest Journal tab content inside the right panel's content area.
 *
 * === Layout (Y values are screen-absolute) ===
 *   CONTENT_Y – DETAIL_DIVIDER_Y : quest list  (~260 px)
 *   DETAIL_DIVIDER_Y – FOOTER_Y  : selected quest detail
 *
 * === Sections ===
 *   ACTIVE    — yellow text, shown first (sorted alphabetically)
 *   COMPLETED — green text, shown after (sorted alphabetically)
 *
 * === Interaction ===
 *   handleClick()     — updates selected quest when a row is clicked
 *   onEntriesUpdated()— called by RightPanel after new data arrives;
 *                       preserves selection if the quest still exists
 */
public class QuestJournalPanel {

    // -----------------------------------------------------------------------
    //  Layout constants — derived from RightPanel so they stay in sync
    // -----------------------------------------------------------------------

    private static final int CONTENT_Y        = RightPanel.CONTENT_Y;
    private static final int DETAIL_DIVIDER_Y = RightPanel.CONTENT_Y + 260;
    private static final int FOOTER_Y         = RightPanel.CONTENT_Y + RightPanel.CONTENT_H;
    private static final int ROW_H            = 16;

    // First list row starts below header + separator
    private static final int LIST_ORIGIN_Y = CONTENT_Y + 22;

    // -----------------------------------------------------------------------
    //  State
    // -----------------------------------------------------------------------

    private int selectedQuestId = -1;

    // -----------------------------------------------------------------------
    //  Public API — data lifecycle
    // -----------------------------------------------------------------------

    /**
     * Called by RightPanel whenever the quest list is replaced.
     * Preserves {@link #selectedQuestId} if the quest still exists in
     * {@code newEntries}; clears it otherwise.
     */
    public void onEntriesUpdated(List<QuestEntry> newEntries) {
        if (selectedQuestId == -1) return;
        for (QuestEntry e : newEntries) {
            if (e.questId == selectedQuestId) return;  // still present — keep
        }
        selectedQuestId = -1;  // quest no longer in log — clear
    }

    // -----------------------------------------------------------------------
    //  Public API — input
    // -----------------------------------------------------------------------

    /**
     * Handle a click inside the quest tab content area.
     * Call this only when the QUESTS tab is active.
     */
    public void handleClick(int sx, int sy, int px, int pw, List<QuestEntry> entries) {
        if (entries == null || entries.isEmpty()) return;
        if (sy < LIST_ORIGIN_Y || sy >= DETAIL_DIVIDER_Y) return;

        for (QuestRowInfo row : buildRows(entries)) {
            if (sy >= row.y && sy < row.y + ROW_H) {
                selectedQuestId = row.entry.questId;
                return;
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    /**
     * Draw the quest journal inside the right panel's content area.
     *
     * @param g       graphics context (screen space)
     * @param px      left edge of the right panel
     * @param pw      width of the right panel
     * @param entries sorted quest list from RightPanel (may be empty, never null)
     */
    public void render(Graphics2D g, int px, int pw, List<QuestEntry> entries) {
        int bx = px + 8;
        int bw = pw - 16;

        // ---- Header ----
        g.setFont(new Font("Arial", Font.BOLD, 10));
        drawOutlined(g, "QUEST JOURNAL", bx, CONTENT_Y + 14,
                new Color(200, 185, 100), new Color(0, 0, 0, 160));

        g.setColor(new Color(80, 65, 35));
        g.drawLine(px + 4, CONTENT_Y + 17, px + pw - 4, CONTENT_Y + 17);

        // ---- List area ----
        if (entries.isEmpty()) {
            renderEmptyState(g, px, pw);
        } else {
            renderList(g, px, bx, bw, entries);
        }

        // ---- Divider ----
        g.setColor(new Color(80, 65, 35));
        g.drawLine(px + 4, DETAIL_DIVIDER_Y,     px + pw - 4, DETAIL_DIVIDER_Y);
        g.setColor(new Color(35, 28, 14));
        g.drawLine(px + 4, DETAIL_DIVIDER_Y + 1, px + pw - 4, DETAIL_DIVIDER_Y + 1);

        // ---- Detail pane ----
        renderDetail(g, px, pw, bx, bw, entries);
    }

    // -----------------------------------------------------------------------
    //  Empty state
    // -----------------------------------------------------------------------

    private void renderEmptyState(Graphics2D g, int px, int pw) {
        g.setFont(new Font("Arial", Font.PLAIN, 10));
        g.setColor(new Color(85, 80, 60));
        FontMetrics fm  = g.getFontMetrics();
        String      msg = "No quests available.";
        int centerX = px + pw / 2 - fm.stringWidth(msg) / 2;
        int centerY = (LIST_ORIGIN_Y + DETAIL_DIVIDER_Y) / 2;
        g.drawString(msg, centerX, centerY);
    }

    // -----------------------------------------------------------------------
    //  List rendering
    // -----------------------------------------------------------------------

    private void renderList(Graphics2D g, int px, int bx, int bw, List<QuestEntry> entries) {
        boolean hasActive    = false;
        boolean hasCompleted = false;
        for (QuestEntry e : entries) {
            if (e.state == QuestState.IN_PROGRESS) hasActive    = true;
            if (e.state == QuestState.COMPLETED)   hasCompleted = true;
        }

        int y = LIST_ORIGIN_Y;

        if (hasActive) {
            drawSectionLabel(g, bx, y, "ACTIVE");
            y += 13;
            for (QuestEntry e : entries) {
                if (e.state != QuestState.IN_PROGRESS) continue;
                if (y + ROW_H > DETAIL_DIVIDER_Y) break;
                drawQuestRow(g, bx, y, bw, e, e.questId == selectedQuestId);
                y += ROW_H;
            }
            y += 5;
        }

        if (hasCompleted && y + ROW_H <= DETAIL_DIVIDER_Y) {
            drawSectionLabel(g, bx, y, "COMPLETED");
            y += 13;
            for (QuestEntry e : entries) {
                if (e.state != QuestState.COMPLETED) continue;
                if (y + ROW_H > DETAIL_DIVIDER_Y) break;
                drawQuestRow(g, bx, y, bw, e, e.questId == selectedQuestId);
                y += ROW_H;
            }
        }
    }

    private void drawSectionLabel(Graphics2D g, int bx, int y, String label) {
        g.setFont(new Font("Arial", Font.BOLD, 9));
        g.setColor(new Color(150, 140, 90));
        g.drawString(label, bx, y + 9);
    }

    private void drawQuestRow(Graphics2D g, int x, int y, int w, QuestEntry e, boolean selected) {
        // Selection highlight
        if (selected) {
            g.setColor(new Color(70, 58, 28, 200));
            g.fillRect(x - 2, y, w + 4, ROW_H - 1);
            g.setColor(new Color(160, 130, 45, 150));
            g.drawRect(x - 2, y, w + 4, ROW_H - 1);
        }

        // Status dot
        Color dot = (e.state == QuestState.IN_PROGRESS)
                ? new Color(240, 210, 60) : new Color(80, 200, 80);
        g.setColor(dot);
        g.fillOval(x + 1, y + 4, 6, 6);
        g.setColor(dot.darker());
        g.drawOval(x + 1, y + 4, 6, 6);

        // Quest name — truncated if too wide
        g.setFont(new Font("Arial", Font.PLAIN, 10));
        Color textColor = selected
                ? new Color(255, 240, 150)
                : (e.state == QuestState.IN_PROGRESS ? new Color(220, 205, 120) : new Color(100, 195, 100));
        g.setColor(textColor);
        g.drawString(truncate(g, e.name, w - 14), x + 11, y + 11);
    }

    // -----------------------------------------------------------------------
    //  Detail pane rendering
    // -----------------------------------------------------------------------

    private void renderDetail(Graphics2D g, int px, int pw, int bx, int bw,
                               List<QuestEntry> entries) {
        QuestEntry selected = null;
        for (QuestEntry e : entries) {
            if (e.questId == selectedQuestId) { selected = e; break; }
        }

        if (selected == null) {
            renderDetailEmpty(g, px, pw);
            return;
        }

        int y = DETAIL_DIVIDER_Y + 10;

        // Quest name (bold gold, truncated to panel width)
        g.setFont(new Font("Arial", Font.BOLD, 10));
        drawOutlined(g, truncate(g, selected.name, bw),
                bx, y + 10, new Color(220, 200, 120), new Color(0, 0, 0, 160));

        // Status (coloured)
        g.setFont(new Font("Arial", Font.PLAIN, 10));
        y += 24;
        switch (selected.state) {
            case IN_PROGRESS:
                g.setColor(new Color(240, 215, 65));
                g.drawString("Status: In Progress", bx, y + 10);
                break;
            case COMPLETED:
                g.setColor(new Color(80, 205, 80));
                g.drawString("Status: Completed", bx, y + 10);
                break;
            default:
                g.setColor(new Color(160, 160, 160));
                g.drawString("Status: Not Started", bx, y + 10);
                break;
        }

        // Objective / description (word-wrapped, muted colour) — only if non-empty
        if (!selected.description.isEmpty()) {
            g.setFont(new Font("Arial", Font.BOLD, 9));
            g.setColor(new Color(140, 130, 80));
            int descY = y + 26;
            String label = selected.state == QuestState.IN_PROGRESS ? "Objective:" : "Notes:";
            g.drawString(label, bx, descY);
            descY += 12;

            // Append live progress counter when gathering is tracked (e.g. "Chop 5 logs (3/5)")
            String displayDesc = selected.description;
            if (selected.state == QuestState.IN_PROGRESS && selected.requiredAmount > 0) {
                int clamped = Math.min(selected.progressAmount, selected.requiredAmount);
                displayDesc = displayDesc + " (" + clamped + "/" + selected.requiredAmount + ")";
            }

            g.setFont(new Font("Arial", Font.PLAIN, 9));
            g.setColor(new Color(170, 165, 130));
            for (String line : wordWrap(g, displayDesc, bw)) {
                if (descY > FOOTER_Y - 8) break;
                g.drawString(line, bx, descY);
                descY += 12;
            }
        }
    }

    /** Centered placeholder shown when no quest is selected. */
    private void renderDetailEmpty(Graphics2D g, int px, int pw) {
        g.setFont(new Font("Arial", Font.PLAIN, 9));
        g.setColor(new Color(90, 85, 65));
        FontMetrics fm      = g.getFontMetrics();
        String[]    lines   = { "Select a quest", "to view details." };
        int         centerX = px + pw / 2;
        int         centerY = (DETAIL_DIVIDER_Y + FOOTER_Y) / 2 - (lines.length * 12) / 2;
        for (String line : lines) {
            g.drawString(line, centerX - fm.stringWidth(line) / 2, centerY);
            centerY += 14;
        }
    }

    // -----------------------------------------------------------------------
    //  Row position calculation (shared by render and handleClick)
    // -----------------------------------------------------------------------

    /**
     * Returns (entry, rowTopY) pairs in the same order as renderList, so that
     * handleClick and the visual rows always agree on positions.
     */
    private List<QuestRowInfo> buildRows(List<QuestEntry> entries) {
        List<QuestRowInfo> rows = new ArrayList<>();
        int y = LIST_ORIGIN_Y;

        boolean hasActive    = false;
        boolean hasCompleted = false;
        for (QuestEntry e : entries) {
            if (e.state == QuestState.IN_PROGRESS) hasActive    = true;
            if (e.state == QuestState.COMPLETED)   hasCompleted = true;
        }

        if (hasActive) {
            y += 13;
            for (QuestEntry e : entries) {
                if (e.state != QuestState.IN_PROGRESS) continue;
                if (y + ROW_H > DETAIL_DIVIDER_Y) break;
                rows.add(new QuestRowInfo(e, y));
                y += ROW_H;
            }
            y += 5;
        }
        if (hasCompleted && y + ROW_H <= DETAIL_DIVIDER_Y) {
            y += 13;
            for (QuestEntry e : entries) {
                if (e.state != QuestState.COMPLETED) continue;
                if (y + ROW_H > DETAIL_DIVIDER_Y) break;
                rows.add(new QuestRowInfo(e, y));
                y += ROW_H;
            }
        }

        return rows;
    }

    private static final class QuestRowInfo {
        final QuestEntry entry;
        final int        y;
        QuestRowInfo(QuestEntry e, int y) { this.entry = e; this.y = y; }
    }

    // -----------------------------------------------------------------------
    //  Text helpers
    // -----------------------------------------------------------------------

    /** Truncate {@code text} to fit within {@code maxWidth} pixels, appending ".." if cut. */
    private static String truncate(Graphics2D g, String text, int maxWidth) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= maxWidth) return text;
        while (text.length() > 2 && fm.stringWidth(text + "..") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "..";
    }

    /** Word-wrap {@code text} into lines no wider than {@code maxWidth} pixels. */
    private static List<String> wordWrap(Graphics2D g, String text, int maxWidth) {
        FontMetrics  fm    = g.getFontMetrics();
        List<String> lines = new ArrayList<>();
        String[]     words = text.split(" ");
        StringBuilder line  = new StringBuilder();
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(candidate) <= maxWidth) {
                if (line.length() > 0) line.append(' ');
                line.append(word);
            } else {
                if (line.length() > 0) lines.add(line.toString());
                line = new StringBuilder(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    // -----------------------------------------------------------------------
    //  Drawing helper
    // -----------------------------------------------------------------------

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
