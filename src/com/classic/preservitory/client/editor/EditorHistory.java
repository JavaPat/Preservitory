package com.classic.preservitory.client.editor;

import com.classic.preservitory.client.world.map.TileMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Full-state snapshot undo/redo.
 * Before any modifying action, call saveState(). Undo/redo restore complete snapshots.
 */
public class EditorHistory {

    private static class EditorSnapshot {
        final int[][] tiles;
        final List<EditorObject> objects;

        EditorSnapshot(int[][] tiles, List<EditorObject> objects) {
            this.tiles   = tiles;
            this.objects = objects;
        }
    }

    private final Stack<EditorSnapshot> undoStack = new Stack<>();
    private final Stack<EditorSnapshot> redoStack = new Stack<>();

    /** Call BEFORE any modifying action to save a restorable snapshot. */
    public void saveState(TileMap tileMap, EditorState state) {
        undoStack.push(snapshot(tileMap, state));
        redoStack.clear();
    }

    public void undo(TileMap tileMap, EditorState state) {
        if (undoStack.isEmpty()) return;
        EditorSnapshot saved = undoStack.pop();
        redoStack.push(snapshot(tileMap, state));
        apply(tileMap, state, saved);
    }

    public void redo(TileMap tileMap, EditorState state) {
        if (redoStack.isEmpty()) return;
        EditorSnapshot saved = redoStack.pop();
        undoStack.push(snapshot(tileMap, state));
        apply(tileMap, state, saved);
    }

    // -------------------------------------------------------------------------

    private EditorSnapshot snapshot(TileMap tileMap, EditorState state) {
        int w = tileMap.getWidth();
        int h = tileMap.getHeight();
        int[][] tiles = new int[w][h];
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                tiles[x][y] = tileMap.getTile(x, y);

        List<EditorObject> objects = new ArrayList<>();
        for (EditorObject o : state.getObjects())
            objects.add(new EditorObject(o.key, o.tileX, o.tileY, o.rotation));

        return new EditorSnapshot(tiles, objects);
    }

    private void apply(TileMap tileMap, EditorState state, EditorSnapshot snap) {
        int w = tileMap.getWidth();
        int h = tileMap.getHeight();
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                tileMap.setTile(x, y, snap.tiles[x][y]);

        state.getObjects().clear();
        state.getObjects().addAll(snap.objects);
    }
}
