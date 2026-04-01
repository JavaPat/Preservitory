package com.classic.preservitory.client.definitions;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Client-side registry of {@link ObjectDefinition}s loaded once at startup. */
public final class ObjectDefinitionManager {

    private static Map<Integer, ObjectDefinition> registry = Map.of();
    private static Map<String,  ObjectDefinition> byKey    = Map.of();

    private ObjectDefinitionManager() {}

    public static void load(Map<Integer, ObjectDefinition> defs) {
        registry = Map.copyOf(defs);
        Map<String, ObjectDefinition> keys = new LinkedHashMap<>();
        for (ObjectDefinition d : defs.values()) keys.put(d.key, d);
        byKey = Map.copyOf(keys);
        System.out.println("[ObjectDefinitionManager] Loaded " + registry.size() + " object definitions.");
    }

    /** Returns {@link ObjectDefinition#UNKNOWN} (never null) for unrecognised IDs. */
    public static ObjectDefinition get(int id) {
        ObjectDefinition def = registry.get(id);
        if (def == null) {
            System.err.println("[ObjectDefinitionManager] Unknown object id=" + id + " — returning UNKNOWN fallback");
            return ObjectDefinition.UNKNOWN;
        }
        return def;
    }

    public static ObjectDefinition getByKey(String key) {
        return byKey.get(key);
    }

    public static boolean exists(int id) {
        return registry.containsKey(id);
    }

    public static Collection<ObjectDefinition> values() {
        return Collections.unmodifiableCollection(registry.values());
    }
}
