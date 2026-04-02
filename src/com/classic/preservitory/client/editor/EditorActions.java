package com.classic.preservitory.client.editor;

import com.classic.preservitory.client.world.map.MapIO;
import com.classic.preservitory.client.world.map.TileMap;
import com.classic.preservitory.ui.framework.assets.AssetManager;

import java.io.IOException;
import java.util.List;

/**
 * All editor actions: painting, new map, save, load, object placement.
 * No rendering, no UI drawing, no game-loop logic.
 */
public class EditorActions {

    private final EditorHistory history = new EditorHistory();

    // -------------------------------------------------------------------------
    //  Undo / Redo (delegate to history)
    // -------------------------------------------------------------------------

    public void undo(TileMap tileMap, EditorState state) {
        history.undo(tileMap, state);
    }

    public void redo(TileMap tileMap, EditorState state) {
        history.redo(tileMap, state);
    }

    // -------------------------------------------------------------------------
    //  Modifying actions — each saves a snapshot first
    // -------------------------------------------------------------------------

    public void paintTile(EditorState state, TileMap tileMap, int x, int y) {
        if (x < 0 || y < 0 || x >= tileMap.getWidth() || y >= tileMap.getHeight()) return;
        int newTile = state.getSelectedTileId();
        if (tileMap.getTile(x, y) == newTile) return;
        history.saveState(tileMap, state);
        tileMap.setTile(x, y, newTile);
    }

    public void placeObject(EditorState state, int tileX, int tileY, TileMap tileMap) {
        if (state.getSelectedObjectKey() == null) return;
        history.saveState(tileMap, state);
        state.getObjects().add(new EditorObject(
                state.getSelectedObjectKey(), tileX, tileY, state.getSelectedRotation()));
    }

    /** Resets the existing tileMap in-place to a blank state (undoable). */
    public void newMap(TileMap tileMap, EditorState state) {
        history.saveState(tileMap, state);
        for (int x = 0; x < tileMap.getWidth(); x++)
            for (int y = 0; y < tileMap.getHeight(); y++)
                tileMap.setTile(x, y, 0);
        state.getObjects().clear();
    }

    // -------------------------------------------------------------------------
    //  Save / Load
    // -------------------------------------------------------------------------

    public List<String> loadAvailableObjects() {
        return AssetManager.getLoadedImageKeys().stream()
                .filter(k -> k.contains("tree") || k.contains("rock"))
                .sorted()
                .collect(java.util.stream.Collectors.toList());
    }

    /** @return status message for display */
    public String saveMap(TileMap tileMap, List<EditorObject> objects, String filePath) {
        try {
            MapIO.save(tileMap, objects, filePath);
            return "Map saved to " + filePath;
        } catch (IOException e) {
            return "Save failed: " + e.getMessage();
        }
    }

    /**
     * Loads tiles into the given tileMap in-place and replaces objectsOut contents.
     * @return true on success
     */
    public boolean loadMap(TileMap tileMap, EditorState state, String filePath) {
        try {
            TileMap loaded = MapIO.load(filePath);
            for (int x = 0; x < Math.min(tileMap.getWidth(), loaded.getWidth()); x++)
                for (int y = 0; y < Math.min(tileMap.getHeight(), loaded.getHeight()); y++)
                    tileMap.setTile(x, y, loaded.getTile(x, y));
            state.getObjects().clear();
            state.getObjects().addAll(MapIO.loadObjects(filePath));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
