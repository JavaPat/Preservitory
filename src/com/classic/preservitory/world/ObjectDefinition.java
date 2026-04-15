package com.classic.preservitory.world;

/**
 * Data-driven definition for a placeable world object (tree, rock, etc.).
 *
 * Loaded at startup by {@link DefinitionLoader} from {@code cache/objects/*.json}.
 * Tree and Rock constructors reference these instead of hardcoding dimensions.
 */
public final class ObjectDefinition {

    public final String  id;
    public final int     width;
    public final int     height;
    public final boolean blocksMovement;
    /** AssetManager sprite key for rendering this object, or {@code null} if none. */
    public final String  spriteKey;

    public ObjectDefinition(String id, int width, int height, boolean blocksMovement, String spriteKey) {
        this.id             = id;
        this.width          = width;
        this.height         = height;
        this.blocksMovement = blocksMovement;
        this.spriteKey      = spriteKey;
    }

    @Override
    public String toString() {
        return "ObjectDefinition{id='" + id + "', size=" + width + "x" + height
                + ", blocksMovement=" + blocksMovement + ", spriteKey='" + spriteKey + "'}";
    }
}
