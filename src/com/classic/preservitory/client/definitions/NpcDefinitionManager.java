package com.classic.preservitory.client.definitions;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Client-side registry of {@link NpcDefinition}s loaded once at startup. */
public final class NpcDefinitionManager {

    private static Map<Integer, NpcDefinition> registry = Map.of();
    private static Map<String,  NpcDefinition> byKey    = Map.of();

    private NpcDefinitionManager() {}

    public static void load(Map<Integer, NpcDefinition> defs) {
        registry = Map.copyOf(defs);
        Map<String, NpcDefinition> keys = new LinkedHashMap<>();
        for (NpcDefinition d : defs.values()) keys.put(d.key, d);
        byKey = Map.copyOf(keys);
        System.out.println("[NpcDefinitionManager] Loaded " + registry.size() + " NPC definitions.");
    }

    /** Returns {@link NpcDefinition#UNKNOWN} (never null) for unrecognised IDs. */
    public static NpcDefinition get(int id) {
        NpcDefinition def = registry.get(id);
        if (def == null) {
            System.err.println("[NpcDefinitionManager] Unknown NPC id=" + id + " — returning UNKNOWN fallback");
            return NpcDefinition.UNKNOWN;
        }
        return def;
    }

    public static NpcDefinition getByKey(String key) {
        return byKey.get(key);
    }

    public static boolean exists(int id) {
        return registry.containsKey(id);
    }

    public static Collection<NpcDefinition> values() {
        return Collections.unmodifiableCollection(registry.values());
    }
}
