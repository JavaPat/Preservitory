package com.classic.preservitory.client.settings;

import com.classic.preservitory.util.Constants;
import org.json.JSONObject;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ClientSettings {

    public enum Action {
        COMBAT("Combat", KeyEvent.VK_F1),
        SKILLS("Skills", KeyEvent.VK_F2),
        QUESTS("Quests", KeyEvent.VK_F3),
        INVENTORY("Inventory", KeyEvent.VK_F4),
        EQUIPMENT("Equipment", KeyEvent.VK_F5),
        SETTINGS("Settings", KeyEvent.VK_F6);

        private final String label;
        private final int defaultKeyCode;

        Action(String label, int defaultKeyCode) {
            this.label = label;
            this.defaultKeyCode = defaultKeyCode;
        }

        public String getLabel() {
            return label;
        }

        public int getDefaultKeyCode() {
            return defaultKeyCode;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ClientSettings.class.getName());
    private static final Path BASE_DIR = Path.of(System.getProperty("user.home"), "." + Constants.GAME_NAME_TO_LOWER);
    private static final Path SETTINGS_PATH = BASE_DIR.resolve("settings.json");

    private boolean showFps;
    private boolean showPing;
    private boolean showTotalXp;
    private boolean shiftClickDrop;
    private boolean showMinimap;
    private boolean showDirectionIndicator;
    private final EnumMap<Action, Integer> keyBindings = new EnumMap<>(Action.class);

    private ClientSettings() {}

    public static ClientSettings load() {
        ClientSettings defaults = defaults();
        try {
            Files.createDirectories(BASE_DIR);
            if (!Files.exists(SETTINGS_PATH)) {
                defaults.save();
                return defaults;
            }

            String json = Files.readString(SETTINGS_PATH, StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(json);

            ClientSettings settings = defaults();
            settings.showFps = root.optBoolean("showFPS", false);
            settings.showPing = root.optBoolean("showPing", false);
            settings.showTotalXp = root.optBoolean("showTotalXp", true);
            settings.shiftClickDrop = root.optBoolean("shiftClickDrop", false);
            settings.showMinimap = root.optBoolean("showMinimap", false);
            settings.showDirectionIndicator = root.optBoolean("showDirectionIndicator", false);

            JSONObject keyBindingsJson = root.optJSONObject("keyBindings");
            if (keyBindingsJson != null) {
                for (Action action : Action.values()) {
                    if (keyBindingsJson.has(action.name())) {
                        settings.keyBindings.put(action, keyBindingsJson.getInt(action.name()));
                    }
                }
            }

            settings.save();
            return settings;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load client settings. Resetting to defaults.", e);
            try {
                defaults.save();
            } catch (RuntimeException saveException) {
                LOGGER.log(Level.WARNING, "Failed to write default client settings.", saveException);
            }
            return defaults;
        }
    }

    public static ClientSettings defaults() {
        ClientSettings settings = new ClientSettings();
        settings.showFps = false;
        settings.showPing = false;
        settings.showTotalXp = true;
        settings.shiftClickDrop = false;
        for (Action action : Action.values()) {
            settings.keyBindings.put(action, action.getDefaultKeyCode());
        }
        return settings;
    }

    public void save() {
        try {
            Files.createDirectories(SETTINGS_PATH.getParent());
            JSONObject root = new JSONObject();
            root.put("showFPS", showFps);
            root.put("showPing", showPing);
            root.put("showTotalXp", showTotalXp);
            root.put("shiftClickDrop", shiftClickDrop);
            root.put("showMinimap", showMinimap);
            root.put("showDirectionIndicator", showDirectionIndicator);

            JSONObject keyBindingsJson = new JSONObject();
            for (Map.Entry<Action, Integer> entry : keyBindings.entrySet()) {
                keyBindingsJson.put(entry.getKey().name(), entry.getValue());
            }
            root.put("keyBindings", keyBindingsJson);

            Files.writeString(SETTINGS_PATH, root.toString(2), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save client settings.", e);
        }
    }

    public boolean isShowFps() {
        return showFps;
    }

    public void setShowFps(boolean showFps) {
        this.showFps = showFps;
    }

    public boolean isShowPing() {
        return showPing;
    }

    public void setShowPing(boolean showPing) {
        this.showPing = showPing;
    }

    public boolean isShowTotalXp() {
        return showTotalXp;
    }

    public void setShowTotalXp(boolean showTotalXp) {
        this.showTotalXp = showTotalXp;
    }

    public boolean isShiftClickDrop() {
        return shiftClickDrop;
    }

    public void setShiftClickDrop(boolean shiftClickDrop) {
        this.shiftClickDrop = shiftClickDrop;
    }

    public boolean isShowMinimap() {
        return showMinimap;
    }

    public void setShowMinimap(boolean showMinimap) {
        this.showMinimap = showMinimap;
    }

    public boolean isShowDirectionIndicator() {
        return showDirectionIndicator;
    }

    public void setShowDirectionIndicator(boolean showDirectionIndicator) {
        this.showDirectionIndicator = showDirectionIndicator;
    }

    public int getKeyBinding(Action action) {
        Integer key = keyBindings.get(action);
        return key != null ? key : getDefaultKey(action);
    }

    public void setKeyBinding(Action action, int keyCode) {
        keyBindings.put(action, keyCode);
    }

    public Map<Action, Integer> getKeyBindings() {
        return Collections.unmodifiableMap(keyBindings);
    }

    private static int getDefaultKey(Action action) {
        return action.getDefaultKeyCode();
    }
}
