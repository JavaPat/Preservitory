package com.classic.preservitory.system;

import com.classic.preservitory.entity.NPC;

/**
 * Manages an active dialogue session with an NPC.
 *
 * Usage:
 *   open(npc, lines)  — begin a conversation
 *   advance()         — move to the next line (call when not isLastLine())
 *   isLastLine()      — true when the final line is displayed
 *   close()           — end the dialogue and clear state
 */
public class DialogueSystem {

    private NPC      activeNPC;
    private String[] lines;
    private int      lineIndex;

    /** Start a new dialogue with the given NPC and text lines. */
    public void open(NPC npc, String[] dialogueLines) {
        this.activeNPC = npc;
        this.lines     = dialogueLines;
        this.lineIndex = 0;
    }

    /** Advance to the next line.  Has no effect when already on the last line. */
    public void advance() {
        if (lines != null && lineIndex < lines.length - 1) lineIndex++;
    }

    /** Returns true when the last line is currently displayed. */
    public boolean isLastLine() {
        return lines == null || lineIndex >= lines.length - 1;
    }

    /** Returns true when a dialogue session is active. */
    public boolean isOpen() {
        return activeNPC != null && lines != null;
    }

    /** The dialogue line currently on screen, or "" if no dialogue is active. */
    public String getCurrentLine() {
        if (!isOpen()) return "";
        return lines[lineIndex];
    }

    /** The NPC that is currently talking, or null. */
    public NPC getNPC() { return activeNPC; }

    /** Close the dialogue session and reset state. */
    public void close() {
        activeNPC = null;
        lines     = null;
        lineIndex = 0;
    }
}
