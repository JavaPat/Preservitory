package com.classic.preservitory.client.definitions;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Client-side registry of {@link EnemyDefinition}s loaded once at startup. */
public final class EnemyDefinitionManager {

    private static Map<Integer, EnemyDefinition> registry = Map.of();
    private static Map<String,  EnemyDefinition> byKey    = Map.of();

    private EnemyDefinitionManager() {}

    public static void load(Map<Integer, EnemyDefinition> defs) {
        registry = Map.copyOf(defs);
        Map<String, EnemyDefinition> keys = new LinkedHashMap<>();
        for (EnemyDefinition d : defs.values()) keys.put(d.key, d);
        byKey = Map.copyOf(keys);
        System.out.println("[EnemyDefinitionManager] Loaded " + registry.size() + " enemy definitions.");
    }

    /** Returns {@link EnemyDefinition#UNKNOWN} (never null) for unrecognised IDs. */
    public static EnemyDefinition get(int id) {
        EnemyDefinition def = registry.get(id);
        if (def == null) {
            System.err.println("[EnemyDefinitionManager] Unknown enemy id=" + id + " — returning UNKNOWN fallback");
            return EnemyDefinition.UNKNOWN;
        }
        return def;
    }

    public static EnemyDefinition getByKey(String key) {
        return byKey.get(key);
    }

    public static boolean exists(int id) {
        return registry.containsKey(id);
    }

    public static Collection<EnemyDefinition> values() {
        return Collections.unmodifiableCollection(registry.values());
    }
}
