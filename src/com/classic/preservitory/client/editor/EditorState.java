package com.classic.preservitory.client.editor;

/**
 * Holds all mutable editor-only state.
 * No logic — getters and setters only.
 */
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EditorState {

    private int selectedTileId = 0;
    private boolean painting = false;
    private String hoverTooltip = null;

    private List<EditorObject> objects = new ArrayList<>();
    private List<String> availableObjects = new ArrayList<>();
    private String selectedObjectKey = null;
    private int selectedRotation = 0;

    // ---- Objects panel UI state ----
    private boolean objectsExpanded = false;
    private String expandedCategory = null;
    private int objectPanelScrollY = 0;
    private Map<String, List<String>> objectCategories = new LinkedHashMap<>();

    // ---- Settings state ----
    private boolean showMinimap = false;

    public int getSelectedTileId() { return selectedTileId; }
    public void setSelectedTileId(int id) { this.selectedTileId = id; }

    public boolean isPainting() { return painting; }
    public void setPainting(boolean painting) { this.painting = painting; }

    public String getHoverTooltip() { return hoverTooltip; }
    public void setHoverTooltip(String tooltip) { this.hoverTooltip = tooltip; }

    public List<EditorObject> getObjects() { return objects; }
    public void setObjects(List<EditorObject> objects) { this.objects = objects; }

    public List<String> getAvailableObjects() { return availableObjects; }
    public void setAvailableObjects(List<String> keys) { this.availableObjects = keys; }

    public String getSelectedObjectKey() { return selectedObjectKey; }
    public void setSelectedObjectKey(String key) { this.selectedObjectKey = key; }

    public int getSelectedRotation() { return selectedRotation; }
    public void setSelectedRotation(int rotation) { this.selectedRotation = rotation; }

    public boolean isObjectsExpanded() { return objectsExpanded; }
    public void setObjectsExpanded(boolean v) { this.objectsExpanded = v; }

    public String getExpandedCategory() { return expandedCategory; }
    public void setExpandedCategory(String cat) { this.expandedCategory = cat; }

    public int getObjectPanelScrollY() { return objectPanelScrollY; }
    public void setObjectPanelScrollY(int y) { this.objectPanelScrollY = y; }

    public Map<String, List<String>> getObjectCategories() { return objectCategories; }
    public void setObjectCategories(Map<String, List<String>> cats) { this.objectCategories = cats; }

    public boolean isShowMinimap() { return showMinimap; }
    public void setShowMinimap(boolean v) { this.showMinimap = v; }
}
