package com.classic.preservitory.ui.quests;

/** Immutable snapshot of a single quest's state received from the server. */
public class QuestEntry {

    public final int        questId;
    public final String     name;
    public final QuestState state;
    /** Current stage id (0 for single-stage / legacy quests). */
    public final int        currentStageId;
    /** Description of the current stage shown in the detail pane. Empty string = none. */
    public final String     description;
    /** How many items gathered toward the current GATHER objective (0 if not applicable). */
    public final int        progressAmount;
    /** Total required for the current GATHER objective (0 if not applicable). */
    public final int        requiredAmount;

    /** Convenience constructor — stageId=0, description="", no progress. */
    public QuestEntry(int questId, String name, QuestState state) {
        this(questId, name, state, 0, "", 0, 0);
    }

    /** Convenience constructor — stageId=0, no progress. */
    public QuestEntry(int questId, String name, QuestState state, String description) {
        this(questId, name, state, 0, description, 0, 0);
    }

    /** Convenience constructor — no progress. */
    public QuestEntry(int questId, String name, QuestState state, int currentStageId, String description) {
        this(questId, name, state, currentStageId, description, 0, 0);
    }

    public QuestEntry(int questId, String name, QuestState state, int currentStageId,
                      String description, int progressAmount, int requiredAmount) {
        this.questId        = questId;
        this.name           = name;
        this.state          = state;
        this.currentStageId = currentStageId;
        this.description    = (description != null) ? description : "";
        this.progressAmount = Math.max(0, progressAmount);
        this.requiredAmount = Math.max(0, requiredAmount);
    }
}
