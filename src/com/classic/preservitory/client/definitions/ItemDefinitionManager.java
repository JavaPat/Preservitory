package com.classic.preservitory.client.definitions;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Client-side item definition registry.
 * Loaded once at startup via {@link #load(Map)}.
 * {@link #get(int)} never returns null — returns {@link ItemDefinition#UNKNOWN} and logs a warning
 * for unrecognised IDs so the client degrades gracefully instead of crashing.
 */
public final class ItemDefinitionManager {

    private static Map<Integer, ItemDefinition> registry = Map.of();

    private ItemDefinitionManager() {}

    public static void load(Map<Integer, ItemDefinition> defs) {
        registry = Map.copyOf(defs);
        System.out.println("[ItemDefinitionManager] Loaded " + registry.size() + " item definitions.");
    }

    /**
     * Return the definition for {@code id}.
     * Returns {@link ItemDefinition#UNKNOWN} (never null) for unrecognised IDs.
     */
    public static ItemDefinition get(int id) {
        ItemDefinition def = registry.get(id);
        if (def == null) {
            System.err.println("[ItemDefinitionManager] Unknown item id=" + id + " — returning UNKNOWN fallback");
            return ItemDefinition.UNKNOWN;
        }
        return def;
    }

    public static boolean exists(int id) {
        return registry.containsKey(id);
    }

    public static Collection<ItemDefinition> values() {
        return Collections.unmodifiableCollection(registry.values());
    }
}
