package com.classic.preservitory.client.world;

import com.classic.preservitory.client.definitions.EnemyDefinition;
import com.classic.preservitory.client.definitions.EnemyDefinitionManager;
import com.classic.preservitory.entity.Enemy;
import com.classic.preservitory.entity.NPC;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.world.objects.Loot;
import com.classic.preservitory.world.objects.Rock;
import com.classic.preservitory.world.objects.Tree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class ClientWorld {

    // IMPORTANT:
    // Do not access map values directly from callers.
    // Always use the snapshot getters below to ensure thread safety.
    private final AtomicReference<Map<String, Tree>> trees =
            new AtomicReference<>(new LinkedHashMap<>());
    private final AtomicReference<Map<String, Rock>> rocks =
            new AtomicReference<>(new LinkedHashMap<>());
    private final AtomicReference<Map<String, Enemy>> enemies =
            new AtomicReference<>(new LinkedHashMap<>());
    private final AtomicReference<Map<String, NPC>> npcs =
            new AtomicReference<>(new LinkedHashMap<>());
    private final AtomicReference<Map<String, Loot>> loot =
            new AtomicReference<>(new LinkedHashMap<>());

    /**
     * Active in-flight projectiles received from the server.
     * Keyed by projectile ID.  Expired entries are pruned on each update.
     */
    private final ConcurrentHashMap<String, ClientProjectile> projectiles =
            new ConcurrentHashMap<>();

    private Consumer<DamageEvent> damageListener;

    // -----------------------------------------------------------------------
    //  Projectiles
    // -----------------------------------------------------------------------

    /**
     * Merge a new server snapshot into the local projectile map.
     *
     * New projectiles are added; expired ones are removed.
     * Existing in-flight projectiles are left untouched so their visual
     * animation completes even if the server has already removed them
     * (the server removes them the tick they land).
     *
     * @param snapshot list of projectiles from the latest server snapshot
     */
    public void updateProjectiles(List<ClientProjectile> snapshot) {
        // Add any projectile from the snapshot that we haven't seen yet
        for (ClientProjectile p : snapshot) {
            projectiles.putIfAbsent(p.id, p);
        }
        // Remove any that have visually expired
        projectiles.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    /** Returns a snapshot of all currently active (not yet expired) projectiles. */
    public List<ClientProjectile> getProjectiles() {
        List<ClientProjectile> result = new ArrayList<>();
        for (ClientProjectile p : projectiles.values()) {
            if (!p.isExpired()) result.add(p);
        }
        return result;
    }

    public void setDamageListener(java.util.function.Consumer<DamageEvent> listener) {
        this.damageListener = listener;
    }

    public void handleDamage(double x, double y, int amount) {
        if (damageListener != null) {
            damageListener.accept(new DamageEvent(x, y, amount));
        }
    }

    // damage event
    public static class DamageEvent {
        public final double x;
        public final double y;
        public final int amount;

        public DamageEvent(double x, double y, int amount) {
            this.x = x;
            this.y = y;
            this.amount = amount;
        }
    }

    // -----------------------------------------------------------------------
    //  Trees
    // -----------------------------------------------------------------------

    public void updateTrees(Map<String, ObjectStateData> data) {
        updateSnapshot(trees, next -> {
            for (Map.Entry<String, Tree> entry : next.entrySet()) {
                if (!data.containsKey(entry.getKey()) && entry.getValue().isAlive()) {
                    entry.getValue().setAlive(false);
                }
            }

            for (Map.Entry<String, ObjectStateData> entry : data.entrySet()) {
                String id = entry.getKey();
                ObjectStateData state = entry.getValue();

                Tree t = next.get(id);
                if (t == null) {
                    t = new Tree(id, state.typeId, state.x, state.y);
                    next.put(id, t);
                } else {
                    t.setPosition(state.x, state.y);
                }
                t.setAlive(true);
            }
        });
    }

    public void chopTree(String id) {
        updateSnapshot(trees, next -> {
            Tree t = next.get(id);
            if (t != null) t.setAlive(false);
        });
    }

    public void addTree(String[] parts) {
        if (parts.length != 4) return;
        try {
            String id = parts[0];
            String typeId = parts[1];
            int x = Integer.parseInt(parts[2]);
            int y = Integer.parseInt(parts[3]);

            updateSnapshot(trees, next -> {
                Tree t = next.get(id);
                if (t == null) {
                    next.put(id, new Tree(id, typeId, x, y));
                } else {
                    t.setPosition(x, y);
                    t.setAlive(true);
                }
            });
        } catch (NumberFormatException ignored) {}
    }

    // -----------------------------------------------------------------------
    //  Rocks
    // -----------------------------------------------------------------------

    public void updateRocks(Map<String, ObjectStateData> data) {
        updateSnapshot(rocks, next -> {
            for (Map.Entry<String, Rock> entry : next.entrySet()) {
                if (!data.containsKey(entry.getKey()) && entry.getValue().isSolid()) {
                    entry.getValue().setAlive(false);
                }
            }

            for (Map.Entry<String, ObjectStateData> entry : data.entrySet()) {
                String id = entry.getKey();
                ObjectStateData state = entry.getValue();

                Rock r = next.get(id);
                if (r == null) {
                    r = new Rock(id, state.typeId, state.x, state.y);
                    next.put(id, r);
                } else {
                    r.setPosition(state.x, state.y);
                }
                r.setAlive(true);
            }
        });
    }

    public void mineRock(String id) {
        updateSnapshot(rocks, next -> {
            Rock r = next.get(id);
            if (r != null) r.setAlive(false);
        });
    }

    public void addRock(String[] parts) {
        if (parts.length != 4) return;
        try {
            String id = parts[0];
            String typeId = parts[1];
            int x = Integer.parseInt(parts[2]);
            int y = Integer.parseInt(parts[3]);

            updateSnapshot(rocks, next -> {
                Rock r = next.get(id);
                if (r == null) {
                    next.put(id, new Rock(id, typeId, x, y));
                } else {
                    r.setPosition(x, y);
                    r.setAlive(true);
                }
            });
        } catch (NumberFormatException ignored) {}
    }

    // -----------------------------------------------------------------------
    //  Enemies (FIXED)
    // -----------------------------------------------------------------------

    public void updateEnemies(Map<String, EnemyData> data) {
        updateSnapshot(enemies, next -> {
            for (Map.Entry<String, Enemy> entry : next.entrySet()) {
                if (!data.containsKey(entry.getKey())) {
                    entry.getValue().setHp(0);
                }
            }

            for (Map.Entry<String, EnemyData> entry : data.entrySet()) {
                String id = entry.getKey();
                EnemyData d = entry.getValue();

                Enemy e = next.get(id);

                if (e == null) {
                    String key = id.replaceAll("_\\d+$", "");
                    EnemyDefinition def = EnemyDefinitionManager.getByKey(key);
                    int definitionId = (def != null) ? def.id : EnemyDefinition.INVALID_ID;
                    Enemy enemy = new Enemy(definitionId, d.x, d.y);
                    enemy.setId(id);
                    next.put(id, enemy);
                    e = enemy;
                }

                e.syncPosition(d.x, d.y, d.direction, d.moving);
                if (d.attacking) e.startAttack();

                int oldHp = e.getHp();
                int newHp = d.hp;

                if (newHp < oldHp && damageListener != null) {
                    int damage = oldHp - newHp;
                    damageListener.accept(new DamageEvent(
                            e.getCenterX(),
                            e.getY() - 4,
                            damage
                    ));
                }

                e.setHp(newHp);
            }
        });
    }

    // -----------------------------------------------------------------------
    //  NPCs
    // -----------------------------------------------------------------------

    public void updateNpcs(Map<String, NPCData> data) {
        updateSnapshot(npcs, next -> {
            for (Map.Entry<String, NPCData> entry : data.entrySet()) {
                NPCData d = entry.getValue();
                NPC npc = next.get(entry.getKey());
                if (npc == null) {
                    npc = new NPC(d.x, d.y, d.name, d.shopkeeper);
                    npc.setId(d.id);
                    next.put(entry.getKey(), npc);
                } else {
                    npc.syncPosition(d.x, d.y, d.direction, d.moving);
                }
            }
            next.keySet().retainAll(data.keySet());
        });
    }

    // -----------------------------------------------------------------------
    //  Loot
    // -----------------------------------------------------------------------

    public void updateLoot(Map<String, LootData> data) {
        Map<String, Loot> next = new LinkedHashMap<>();
        for (Map.Entry<String, LootData> entry : data.entrySet()) {
            LootData d = entry.getValue();
            next.put(d.id, new Loot(d.id, d.x, d.y, d.itemId, d.count));
        }
        loot.set(next);
    }

    public void addLoot(LootData d) {
        updateSnapshot(loot, next ->
                next.put(d.id, new Loot(d.id, d.x, d.y, d.itemId, d.count)));
    }

    public void removeLoot(String id) {
        updateSnapshot(loot, next -> next.remove(id));
    }

    // -----------------------------------------------------------------------
    //  Queries
    // -----------------------------------------------------------------------

    public List<NPC> getNpcs() {
        return new ArrayList<>(npcs.get().values());
    }

    public List<Enemy> getEnemies() {
        return new ArrayList<>(enemies.get().values());
    }

    public List<Tree> getTrees() {
        return new ArrayList<>(trees.get().values());
    }

    public List<Rock> getRocks() {
        return new ArrayList<>(rocks.get().values());
    }

    public List<Loot> getLoot() {
        return new ArrayList<>(loot.get().values());
    }

    public Tree getTreeAt(int px, int py) {
        for (Tree t : getTrees()) if (t.containsPoint(px, py)) return t;
        return null;
    }

    public Tree getTree(String id) {
        return trees.get().get(id);
    }

    public Rock getRockAt(int px, int py) {
        for (Rock r : getRocks()) if (r.containsPoint(px, py)) return r;
        return null;
    }

    public Rock getRock(String id) {
        return rocks.get().get(id);
    }

    public Enemy getEnemyAt(int px, int py) {
        for (Enemy e : getEnemies()) if (e.containsPoint(px, py)) return e;
        return null;
    }

    public NPC getNpcAt(int px, int py) {
        for (NPC n : getNpcs()) if (n.containsPoint(px, py)) return n;
        return null;
    }

    public NPC getNpc(String id) {
        return npcs.get().get(id);
    }

    public Loot getLootAt(int px, int py) {
        for (Loot l : getLoot()) if (l.containsPoint(px, py)) return l;
        return null;
    }

    // -----------------------------------------------------------------------
    //  Pathfinding
    // -----------------------------------------------------------------------

    public boolean isBlocked(int col, int row) {
        for (Tree t : getTrees()) {
            if (t.isAlive()
                    && (int)(t.getX() / Constants.TILE_SIZE) == col
                    && (int)(t.getY() / Constants.TILE_SIZE) == row) {
                return true;
            }
        }

        for (Rock r : getRocks()) {
            if (r.isSolid()
                    && (int)(r.getX() / Constants.TILE_SIZE) == col
                    && (int)(r.getY() / Constants.TILE_SIZE) == row) {
                return true;
            }
        }

        return false;
    }

    private static <K, V> void updateSnapshot(AtomicReference<Map<K, V>> ref, Consumer<Map<K, V>> mutator) {
        while (true) {
            Map<K, V> current = ref.get();
            Map<K, V> next = new LinkedHashMap<>(current);
            mutator.accept(next);
            if (ref.compareAndSet(current, next)) {
                return;
            }
        }
    }
}
