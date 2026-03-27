package com.classic.preservitory.client.world;

import com.classic.preservitory.entity.Enemy;
import com.classic.preservitory.entity.NPC;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.world.objects.Goblin;
import com.classic.preservitory.world.objects.Loot;
import com.classic.preservitory.world.objects.Rock;
import com.classic.preservitory.world.objects.Tree;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ClientWorld {

    private final ConcurrentHashMap<String, Tree>  trees   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Rock>  rocks   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Enemy> enemies = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Loot>  loot    = new ConcurrentHashMap<>();

    private final LinkedHashMap<String, NPC> npcs = new LinkedHashMap<>();

    private Consumer<DamageEvent> damageListener;

    public void setDamageListener(java.util.function.Consumer<DamageEvent> listener) {
        this.damageListener = listener;
    }

    public void handleDamage(double x, double y, int amount) {
        if (damageListener != null) {
            damageListener.accept(new DamageEvent(x, y, amount));
        }
    }

    // ✅ NEW: damage event
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
        for (Map.Entry<String, Tree> entry : trees.entrySet()) {
            if (!data.containsKey(entry.getKey()) && entry.getValue().isAlive()) {
                entry.getValue().setAlive(false);
            }
        }

        for (Map.Entry<String, ObjectStateData> entry : data.entrySet()) {
            String id  = entry.getKey();
            ObjectStateData state = entry.getValue();

            Tree t = trees.get(id);
            if (t == null) {
                t = new Tree(id, state.typeId, state.x, state.y);
                trees.put(id, t);
            } else {
                t.setPosition(state.x, state.y);
            }
            t.setAlive(true);
        }
    }

    public void chopTree(String id) {
        Tree t = trees.get(id);
        if (t != null) t.setAlive(false);
    }

    public void addTree(String[] parts) {
        if (parts.length != 4) return;
        try {
            String id = parts[0];
            String typeId = parts[1];
            int x = Integer.parseInt(parts[2]);
            int y = Integer.parseInt(parts[3]);

            Tree t = trees.get(id);
            if (t == null) {
                trees.put(id, new Tree(id, typeId, x, y));
            } else {
                t.setPosition(x, y);
                t.setAlive(true);
            }
        } catch (NumberFormatException ignored) {}
    }

    // -----------------------------------------------------------------------
    //  Rocks
    // -----------------------------------------------------------------------

    public void updateRocks(Map<String, ObjectStateData> data) {
        for (Map.Entry<String, Rock> entry : rocks.entrySet()) {
            if (!data.containsKey(entry.getKey()) && entry.getValue().isSolid()) {
                entry.getValue().setAlive(false);
            }
        }

        for (Map.Entry<String, ObjectStateData> entry : data.entrySet()) {
            String id  = entry.getKey();
            ObjectStateData state = entry.getValue();

            Rock r = rocks.get(id);
            if (r == null) {
                r = new Rock(id, state.typeId, state.x, state.y);
                rocks.put(id, r);
            } else {
                r.setPosition(state.x, state.y);
            }
            r.setAlive(true);
        }
    }

    public void mineRock(String id) {
        Rock r = rocks.get(id);
        if (r != null) r.setAlive(false);
    }

    public void addRock(String[] parts) {
        if (parts.length != 4) return;
        try {
            String id = parts[0];
            String typeId = parts[1];
            int x = Integer.parseInt(parts[2]);
            int y = Integer.parseInt(parts[3]);

            Rock r = rocks.get(id);
            if (r == null) {
                rocks.put(id, new Rock(id, typeId, x, y));
            } else {
                r.setPosition(x, y);
                r.setAlive(true);
            }
        } catch (NumberFormatException ignored) {}
    }

    // -----------------------------------------------------------------------
    //  Enemies (FIXED)
    // -----------------------------------------------------------------------

    public void updateEnemies(Map<String, EnemyData> data) {
        // Mark missing enemies as dead
        for (Map.Entry<String, Enemy> entry : enemies.entrySet()) {
            if (!data.containsKey(entry.getKey())) {
                entry.getValue().setHp(0);
            }
        }

        for (Map.Entry<String, EnemyData> entry : data.entrySet()) {
            String id = entry.getKey();
            EnemyData d = entry.getValue();

            Enemy e = enemies.get(id);

            if (e == null) {
                Goblin g = new Goblin(d.x, d.y);
                g.setId(id);
                enemies.put(id, g);
                e = g;
            }

            int oldHp = e.getHp();
            int newHp = d.hp;

            // ✅ DAMAGE DETECTION
            if (newHp < oldHp) {
                int damage = oldHp - newHp;

                if (damageListener != null) {
                    damageListener.accept(
                            new DamageEvent(
                                    e.getCenterX(),
                                    e.getY() - 4,
                                    damage
                            )
                    );
                }
            }

            e.setHp(newHp);
        }
    }

    // -----------------------------------------------------------------------
    //  NPCs
    // -----------------------------------------------------------------------

    public void updateNpcs(Map<String, NPCData> data) {
        npcs.clear();
        for (Map.Entry<String, NPCData> entry : data.entrySet()) {
            NPCData d = entry.getValue();
            NPC npc = new NPC(d.x, d.y, d.name, d.shopkeeper);
            npc.setId(d.id);
            npcs.put(entry.getKey(), npc);
        }
    }

    // -----------------------------------------------------------------------
    //  Loot
    // -----------------------------------------------------------------------

    public void updateLoot(Map<String, LootData> data) {
        loot.clear();
        for (Map.Entry<String, LootData> entry : data.entrySet()) {
            LootData d = entry.getValue();
            loot.put(d.id, new Loot(d.id, d.x, d.y, d.itemName, d.count));
        }
    }

    public void addLoot(LootData d) {
        loot.put(d.id, new Loot(d.id, d.x, d.y, d.itemName, d.count));
    }

    public void removeLoot(String id) {
        loot.remove(id);
    }

    // -----------------------------------------------------------------------
    //  Queries
    // -----------------------------------------------------------------------

    public Collection<Tree> getTrees() { return Collections.unmodifiableCollection(trees.values()); }
    public Collection<Rock> getRocks() { return Collections.unmodifiableCollection(rocks.values()); }
    public Collection<Enemy> getEnemies() { return Collections.unmodifiableCollection(enemies.values()); }
    public Collection<NPC> getNpcs() { return Collections.unmodifiableCollection(npcs.values()); }
    public Collection<Loot> getLoot() { return Collections.unmodifiableCollection(loot.values()); }

    public Tree getTreeAt(int px, int py) {
        for (Tree t : trees.values()) if (t.containsPoint(px, py)) return t;
        return null;
    }

    public Rock getRockAt(int px, int py) {
        for (Rock r : rocks.values()) if (r.containsPoint(px, py)) return r;
        return null;
    }

    public Enemy getEnemyAt(int px, int py) {
        for (Enemy e : enemies.values()) if (e.containsPoint(px, py)) return e;
        return null;
    }

    public NPC getNpcAt(int px, int py) {
        for (NPC n : npcs.values()) if (n.containsPoint(px, py)) return n;
        return null;
    }

    public NPC getNpc(String id) {
        return npcs.get(id);
    }

    public Loot getLootAt(int px, int py) {
        for (Loot l : loot.values()) if (l.containsPoint(px, py)) return l;
        return null;
    }

    // -----------------------------------------------------------------------
    //  Pathfinding
    // -----------------------------------------------------------------------

    public boolean isBlocked(int col, int row) {
        for (Tree t : trees.values()) {
            if (t.isAlive()
                    && (int)(t.getX() / Constants.TILE_SIZE) == col
                    && (int)(t.getY() / Constants.TILE_SIZE) == row) {
                return true;
            }
        }

        for (Rock r : rocks.values()) {
            if (r.isSolid()
                    && (int)(r.getX() / Constants.TILE_SIZE) == col
                    && (int)(r.getY() / Constants.TILE_SIZE) == row) {
                return true;
            }
        }

        return false;
    }
}
