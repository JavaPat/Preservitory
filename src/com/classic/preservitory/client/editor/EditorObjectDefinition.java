package com.classic.preservitory.client.editor;

/**
 * Describes a placeable editor object.
 * id        — canonical object ID stored in EditorObject.key (e.g. "oak_tree")
 * spriteKey — AssetManager key used for rendering (e.g. "tree_oak")
 * category  — display category shown in the editor panel (e.g. "Trees")
 *
 * To add a new object: add one line to EditorActions.DEFINITIONS.
 * No other code needs changing.
 */
public final class EditorObjectDefinition {

    public final String id;
    public final String spriteKey;
    public final String category;

    public EditorObjectDefinition(String id, String spriteKey, String category) {
        this.id        = id;
        this.spriteKey = spriteKey;
        this.category  = category;
    }
}
