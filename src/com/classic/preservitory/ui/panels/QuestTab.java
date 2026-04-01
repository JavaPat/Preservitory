package com.classic.preservitory.ui.panels;

import com.classic.preservitory.ui.quests.QuestEntry;
import com.classic.preservitory.ui.quests.QuestJournalPanel;
import com.classic.preservitory.ui.quests.QuestState;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Thin wrapper around QuestJournalPanel that owns the quest entry list and
 * handles the client-side sort: IN_PROGRESS first, COMPLETED after,
 * alphabetically within each group.
 *
 * Display only — reads quest data, never modifies it.
 */
class QuestTab implements Tab {

    // -----------------------------------------------------------------------
    //  State
    // -----------------------------------------------------------------------

    private final QuestJournalPanel           questJournalPanel = new QuestJournalPanel();
    private volatile List<QuestEntry>         questEntries      = Collections.emptyList();

    // -----------------------------------------------------------------------
    //  Data
    // -----------------------------------------------------------------------

    /**
     * Replace the displayed quest list.  Entries are sorted client-side:
     * IN_PROGRESS first, then COMPLETED; alphabetically within each group.
     * The original server list is not modified.
     */
    void setQuestEntries(List<QuestEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            questEntries = Collections.emptyList();
            questJournalPanel.onEntriesUpdated(questEntries);
            return;
        }
        List<QuestEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator
                .comparingInt((QuestEntry e) -> e.state == QuestState.COMPLETED ? 1 : 0)
                .thenComparing(e -> e.name));
        questEntries = sorted;
        questJournalPanel.onEntriesUpdated(sorted);
    }

    // -----------------------------------------------------------------------
    //  Input
    // -----------------------------------------------------------------------
    @Override
    public void handleClick(int sx, int sy, int px, int pw) {
        questJournalPanel.handleClick(sx, sy, px, pw, questEntries);
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    void render(Graphics2D g, int px, int pw) {
        questJournalPanel.render(g, px, pw, questEntries);
    }
}
