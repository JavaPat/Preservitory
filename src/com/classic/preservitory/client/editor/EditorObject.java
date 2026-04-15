package com.classic.preservitory.client.editor;

/**
 * A placed object on the editor map (tree, rock, etc.).
 * Pure data — no logic.
 */
public class EditorObject {
    public String key;
    public int tileX;
    public int tileY;
    public int rotation;

    public EditorObject(String key, int tileX, int tileY, int rotation) {
        this.key = key;
        this.tileX = tileX;
        this.tileY = tileY;
        this.rotation = rotation;
    }
}
