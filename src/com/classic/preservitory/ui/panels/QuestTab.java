package com.classic.preservitory.ui.panels;

import com.classic.preservitory.ui.framework.TabRenderer;
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
 */
class QuestTab implements TabRenderer {

    private final QuestJournalPanel   questJournalPanel = new QuestJournalPanel();
    private volatile List<QuestEntry> questEntries      = Collections.emptyList();

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

    @Override
    public void handleClick(int sx, int sy, int x, int y, int width, int height) {
        questJournalPanel.handleClick(sx, sy, x, width, questEntries);
    }

    @Override
    public void render(Graphics2D g, int x, int y, int width, int height) {
        questJournalPanel.render(g, x, width, questEntries);
    }
}
