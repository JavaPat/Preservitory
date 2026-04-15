package com.classic.preservitory.client.editor;

import com.classic.preservitory.cache.SpriteCache;
import com.classic.preservitory.client.world.map.MapIO;
import com.classic.preservitory.client.world.map.TileMap;

import java.io.File;
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

    // -------------------------------------------------------------------------
    //  Object definition registry
    //  To add a new placeable object: add one line here. Nothing else changes.
    // -------------------------------------------------------------------------

    private static final List<EditorObjectDefinition> DEFINITIONS;
    static {
        DEFINITIONS = java.util.Arrays.asList(
            // Trees
            new EditorObjectDefinition("tree",         "objects/trees/tree",           "Trees"),
            new EditorObjectDefinition("oak_tree",     "objects/trees/oak",            "Trees"),
            new EditorObjectDefinition("willow_tree",  "objects/trees/willow",         "Trees"),
            new EditorObjectDefinition("maple_tree",   "objects/trees/maple",          "Trees"),
            new EditorObjectDefinition("yew_tree",     "objects/trees/yew",            "Trees"),
            // Rocks
            new EditorObjectDefinition("tin_rock",     "objects/rocks/tin_rocks",      "Rocks"),
            new EditorObjectDefinition("copper_rock",  "objects/rocks/copper_rocks",   "Rocks"),
            new EditorObjectDefinition("iron_rock",    "objects/rocks/iron_rocks",     "Rocks"),
            new EditorObjectDefinition("gold_rock",    "objects/rocks/gold_rocks",     "Rocks"),
            new EditorObjectDefinition("mithril_rock", "objects/rocks/mithril_rocks",  "Rocks"),
            new EditorObjectDefinition("adamant_rock", "objects/rocks/adamant_rocks",  "Rocks"),
            new EditorObjectDefinition("runite_rock",  "objects/rocks/runite_rocks",   "Rocks")
        );
    }

    /**
     * Returns the AssetManager sprite key for a definition ID.
     * Falls back to the id itself if not found, so old map files still render.
     */
    public static String getSpriteKey(String id) {
        for (EditorObjectDefinition def : DEFINITIONS) {
            if (def.id.equals(id)) return def.spriteKey;
        }
        return id;
    }

    /** Returns IDs of all definitions whose sprite is present in the packed sprite cache. */
    public List<String> loadAvailableObjects() {
        java.util.Set<String> available = SpriteCache.getIds();
        List<String> ids = new java.util.ArrayList<>();
        for (EditorObjectDefinition def : DEFINITIONS) {
            if (available.contains(def.spriteKey)) ids.add(def.id);
        }
        System.out.println("Loaded objects: " + ids);
        return ids;
    }

    /**
     * Groups definition IDs into their display categories.
     * All categories always appear, even when empty (failsafe).
     */
    public java.util.Map<String, List<String>> buildObjectCategories(List<String> ids) {
        java.util.Map<String, List<String>> categories = new java.util.LinkedHashMap<>();
        // Pre-populate in declaration order so category order is stable
        for (EditorObjectDefinition def : DEFINITIONS) {
            categories.computeIfAbsent(def.category, k -> new java.util.ArrayList<>());
        }
        for (String id : ids) {
            for (EditorObjectDefinition def : DEFINITIONS) {
                if (def.id.equals(id)) {
                    categories.get(def.category).add(id);
                    break;
                }
            }
        }
        categories.forEach((cat, list) -> System.out.println(cat + ": " + list.size()));
        return categories;
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
            System.out.println("Loading from: " + filePath);

            TileMap loaded = MapIO.load(filePath);

            for (int x = 0; x < Math.min(tileMap.getWidth(), loaded.getWidth()); x++)
                for (int y = 0; y < Math.min(tileMap.getHeight(), loaded.getHeight()); y++)
                    tileMap.setTile(x, y, loaded.getTile(x, y));

            state.getObjects().clear();
            state.getObjects().addAll(MapIO.loadObjects(filePath));

            return true;

        } catch (Exception e) {
            e.printStackTrace(); // 🔥 THIS IS CRITICAL
            return false;
        }
    }
}
