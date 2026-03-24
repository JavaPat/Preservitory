package com.classic.preservitory.entity;

/**
 * Represents a single levelled skill (e.g. Woodcutting).
 *
 * XP formula: xpForNextLevel = level^3
 *   Level 1 → 2 needs   1 XP
 *   Level 2 → 3 needs   8 XP
 *   Level 5 → 6 needs 125 XP
 *   Level 10→11 needs 1000 XP
 *
 * XP carries over on level-up, so gaining a large chunk can push
 * through multiple levels at once.
 */
public class Skill {

    /** Hard cap, same as RuneScape. */
    private static final int MAX_LEVEL = 99;

    private final String name;
    private int level;
    private int xp;

    public Skill(String name) {
        this.name  = name;
        this.level = 1;
        this.xp    = 0;
    }

    /**
     * Add XP and automatically level up as many times as needed.
     *
     * @param amount positive XP value to add
     */
    public void addXp(int amount) {
        if (level >= MAX_LEVEL) return;
        xp += amount;

        // Loop: one level-up may leave enough XP for another
        while (level < MAX_LEVEL && xp >= xpForNextLevel()) {
            xp    -= xpForNextLevel();
            level += 1;
        }
    }

    /**
     * XP required to advance from the current level to the next.
     * Formula: level^3
     */
    public int xpForNextLevel() {
        return level * level * level;
    }

    /**
     * Progress through the current level as a 0.0–1.0 fraction.
     * Useful for drawing a progress bar.
     */
    public float getProgressFraction() {
        if (level >= MAX_LEVEL) return 1.0f;
        return (float) xp / xpForNextLevel();
    }

    /**
     * Directly set level and XP — used by SaveSystem when loading a save file.
     * Does NOT trigger level-up events.
     */
    public void resetTo(int level, int xp) {
        this.level = Math.max(1, Math.min(MAX_LEVEL, level));
        this.xp    = Math.max(0, xp);
    }

    // --- Getters ---

    public String getName()  { return name; }
    public int    getLevel() { return level; }
    public int    getXp()    { return xp; }
}
