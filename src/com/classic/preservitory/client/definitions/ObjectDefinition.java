package com.classic.preservitory.client.definitions;

/**
 * Client-side definition of a world object (tree, rock, etc.).
 * Loaded from {@code cache/objects/*.json} — mirrors the server definition.
 */
public final class ObjectDefinition {

    public enum Type { TREE, ROCK }

    public final int    id;
    public final String key;
    public final String name;
    public final Type   type;
    public final int    resourceItemId;
    public final int    xp;
    public final long   respawnMs;

    /**
     * Key used to look up the sprite in {@link com.classic.preservitory.ui.framework.assets.AssetManager}.
     * Defaults to {@link #key} when not specified in JSON.
     */
    public final String spriteKey;

    /** Sentinel id used by {@link #UNKNOWN} — never a valid loaded id. */
    public static final int INVALID_ID = -1;

    public static final ObjectDefinition UNKNOWN =
            new ObjectDefinition(INVALID_ID, "unknown", "Unknown", Type.TREE, 0, 0, 10_000L, "unknown");

    public ObjectDefinition(int id, String key, String name, Type type,
                            int resourceItemId, int xp, long respawnMs, String spriteKey) {
        this.id             = id;
        this.key            = key;
        this.name           = name;
        this.type           = type;
        this.resourceItemId = resourceItemId;
        this.xp             = xp;
        this.respawnMs      = respawnMs;
        this.spriteKey      = spriteKey;
    }
}
