package com.classic.preservitory.entity;

/**
 * Represents a single levelled skill (e.g. Woodcutting).
 *
 * Level and XP are set exclusively by the server via {@link #resetTo(int, int)}.
 * The {@code xp} field stores the server's <em>total cumulative XP</em> value.
 * All display helpers (progress bar, XP-to-next) use the OSRS XP table.
 *
 * The client NEVER calculates levels — that is the server's responsibility.
 */
public class Skill {

    /** Hard cap, same as RuneScape. */
    public static final int MAX_LEVEL = 99;

    /**
     * OSRS cumulative XP thresholds.
     * Index = level (1–99); value = total XP required to reach that level.
     */
    private static final int[] OSRS_XP = buildOsrsTable();

    private static int[] buildOsrsTable() {
        int[] table = new int[MAX_LEVEL + 1];
        table[1] = 0;
        double points = 0;
        for (int lvl = 1; lvl < MAX_LEVEL; lvl++) {
            points += Math.floor(lvl + 300.0 * Math.pow(2.0, lvl / 7.0));
            table[lvl + 1] = (int) (points / 4);
        }
        return table;
    }

    private final String name;
    private int level;
    private int xp; // total cumulative XP as received from the server

    public Skill(String name) {
        this.name  = name;
        this.level = 1;
        this.xp    = 0;
    }

    /**
     * Apply an authoritative server snapshot.
     * {@code xp} is the server's total cumulative XP value.
     * Level is set directly — no client-side recalculation.
     */
    public void resetTo(int level, int xp) {
        this.level = Math.max(1, Math.min(MAX_LEVEL, level));
        this.xp    = Math.max(0, xp);
    }

    /**
     * Total OSRS cumulative XP required to reach the next level.
     * Used for display only — never for level calculation.
     */
    public int xpForNextLevel() {
        if (level >= MAX_LEVEL) return OSRS_XP[MAX_LEVEL];
        return OSRS_XP[level + 1];
    }

    /** Total OSRS cumulative XP required to reach the current level. */
    public int xpForCurrentLevel() {
        return OSRS_XP[Math.max(1, Math.min(MAX_LEVEL, level))];
    }

    /**
     * Progress through the current level as a 0.0–1.0 fraction.
     * Computed from total XP using the OSRS table.
     */
    public float getProgressFraction() {
        if (level >= MAX_LEVEL) return 1.0f;
        int current = xpForCurrentLevel();
        int next    = xpForNextLevel();
        int range   = next - current;
        if (range <= 0) return 1.0f;
        return Math.min(1.0f, Math.max(0f, (float)(xp - current) / range));
    }

    // --- Getters ---

    public String getName()  { return name; }
    public int    getLevel() { return level; }
    /** Total cumulative XP as received from the server. */
    public int    getXp()    { return xp; }
}
