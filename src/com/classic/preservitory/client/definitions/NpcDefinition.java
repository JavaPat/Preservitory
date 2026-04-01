package com.classic.preservitory.client.definitions;

/**
 * Client-side definition of an NPC.
 * Loaded from {@code cache/npcs/*.json} — mirrors the server definition.
 * Only the fields needed for rendering are included.
 */
public final class NpcDefinition {

    public final int     id;
    public final String  key;
    public final String  name;
    public final boolean shopkeeper;

    /** Sentinel id used by {@link #UNKNOWN} — never a valid loaded id. */
    public static final int INVALID_ID = -1;

    public static final NpcDefinition UNKNOWN =
            new NpcDefinition(INVALID_ID, "unknown", "Unknown", false);

    public NpcDefinition(int id, String key, String name, boolean shopkeeper) {
        this.id         = id;
        this.key        = key;
        this.name       = name;
        this.shopkeeper = shopkeeper;
    }
}
