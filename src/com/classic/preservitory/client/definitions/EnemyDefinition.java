package com.classic.preservitory.client.definitions;

/**
 * Client-side definition of an enemy type.
 * Loaded from {@code cache/enemies/*.json} — mirrors the server definition.
 * Used for display-only purposes (name, HP bar colour, etc.) and combat rolls.
 */
public final class EnemyDefinition {

    public final int    id;
    public final String key;
    public final String name;
    public final int    maxHp;
    public final int    attackLevel;
    public final int    defenceLevel;

    /** Sentinel id used by {@link #UNKNOWN} — never a valid loaded id. */
    public static final int INVALID_ID = -1;

    public static final EnemyDefinition UNKNOWN =
            new EnemyDefinition(INVALID_ID, "unknown", "Unknown", 1, 1, 1);

    public EnemyDefinition(int id, String key, String name, int maxHp,
                           int attackLevel, int defenceLevel) {
        this.id           = id;
        this.key          = key;
        this.name         = name;
        this.maxHp        = maxHp;
        this.attackLevel  = attackLevel;
        this.defenceLevel = defenceLevel;
    }
}
