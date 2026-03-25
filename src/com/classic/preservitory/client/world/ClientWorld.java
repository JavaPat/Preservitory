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

/**
 * Single source of truth for all server-synced world objects on the client.
 *
 * Responsibilities:
 *   - Trees: full sync (TREES) + delta updates (TREE_ADD / TREE_REMOVE)
 *   - Rocks: full sync (ROCKS) + delta updates (ROCK_ADD / ROCK_REMOVE)
 *   - NPCs:  full sync (NPCS — future)
 *
 * The client NEVER creates objects locally.  All state arrives from the server.
 * No timers, no game logic — pure data + query surface for the renderer.
 */
public class ClientWorld {

    private final ConcurrentHashMap<String, Tree>  trees   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Rock>  rocks   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Enemy> enemies = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Loot>  loot    = new ConcurrentHashMap<>();

    // Insertion-order map so NPCs render in a stable, predictable sequence.
    private final LinkedHashMap<String, NPC> npcs = new LinkedHashMap<>();

    // -----------------------------------------------------------------------
    //  Trees — full state sync
    // -----------------------------------------------------------------------

    /** Replace the entire local tree map with the server's authoritative state. */
    public void updateTrees(Map<String, int[]> data) {
        // Trees absent from the snapshot are dead — mark ONLY if currently alive
        // so no stump visual is ever reset mid-countdown.
        for (Map.Entry<String, Tree> entry : trees.entrySet()) {
            if (!data.containsKey(entry.getKey()) && entry.getValue().isAlive()) {
                entry.getValue().setAlive(false);
            }
        }
        // Apply snapshot — create missing entries, revive existing ones.
        for (Map.Entry<String, int[]> entry : data.entrySet()) {
            String id  = entry.getKey();
            int[]  pos = entry.getValue();
            Tree   t   = trees.get(id);
            if (t == null) {
                t = new Tree(id, pos[0], pos[1]);
                trees.put(id, t);
            } else {
                t.setPosition(pos[0], pos[1]);
            }
            t.setAlive(true);
        }
        System.out.println("[ClientWorld] Full tree sync: " + data.size() + " trees");
    }

    // -----------------------------------------------------------------------
    //  Trees — delta updates
    // -----------------------------------------------------------------------

    /** Server confirms a tree was chopped — show stump. */
    public void chopTree(String id) {
        Tree t = trees.get(id);
        if (t != null) t.setAlive(false);
    }

    /** Server confirms a tree respawned — restore or create it. Parts = [id, x, y]. */
    public void addTree(String[] parts) {
        if (parts.length != 3) return;
        try {
            String id = parts[0];
            int    x  = Integer.parseInt(parts[1]);
            int    y  = Integer.parseInt(parts[2]);
            Tree   t  = trees.get(id);
            if (t == null) {
                trees.put(id, new Tree(id, x, y));
            } else {
                t.setPosition(x, y);
                t.setAlive(true);
            }
        } catch (NumberFormatException ignored) {}
    }

    // -----------------------------------------------------------------------
    //  Rocks — full state sync
    // -----------------------------------------------------------------------

    /** Replace the entire local rock map with the server's authoritative state. */
    public void updateRocks(Map<String, int[]> data) {
        for (Map.Entry<String, Rock> entry : rocks.entrySet()) {
            if (!data.containsKey(entry.getKey()) && entry.getValue().isSolid()) {
                entry.getValue().setAlive(false);
            }
        }
        for (Map.Entry<String, int[]> entry : data.entrySet()) {
            String id  = entry.getKey();
            int[]  pos = entry.getValue();
            Rock   r   = rocks.get(id);
            if (r == null) {
                r = new Rock(id, pos[0], pos[1]);
                rocks.put(id, r);
            } else {
                r.setPosition(pos[0], pos[1]);
            }
            r.setAlive(true);
        }
        System.out.println("[ClientWorld] Full rock sync: " + data.size() + " rocks");
    }

    // -----------------------------------------------------------------------
    //  Rocks — delta updates
    // -----------------------------------------------------------------------

    /** Server confirms a rock was mined — show depleted state. */
    public void mineRock(String id) {
        Rock r = rocks.get(id);
        if (r != null) r.setAlive(false);
    }

    /** Server confirms a rock respawned — restore or create it. Parts = [id, x, y]. */
    public void addRock(String[] parts) {
        if (parts.length != 3) return;
        try {
            String id = parts[0];
            int    x  = Integer.parseInt(parts[1]);
            int    y  = Integer.parseInt(parts[2]);
            Rock   r  = rocks.get(id);
            if (r == null) {
                rocks.put(id, new Rock(id, x, y));
            } else {
                r.setPosition(x, y);
                r.setAlive(true);
            }
        } catch (NumberFormatException ignored) {}
    }

    // -----------------------------------------------------------------------
    //  Enemies — full state sync (called when server sends ENEMIES message)
    // -----------------------------------------------------------------------

    /**
     * Replace local enemy state with the server's authoritative snapshot.
     *
     * Enemies absent from the snapshot are dead — mark them so they stop
     * rendering.  Enemies present are alive — create or update them.
     * The client never moves enemies or runs respawn timers.
     */
    public void updateEnemies(Map<String, EnemyData> data) {
        // Enemies missing from the snapshot are dead
        for (Map.Entry<String, Enemy> entry : enemies.entrySet()) {
            if (!data.containsKey(entry.getKey())) {
                entry.getValue().setHp(0);
            }
        }
        // Apply snapshot
        for (Map.Entry<String, EnemyData> entry : data.entrySet()) {
            String    id = entry.getKey();
            EnemyData d  = entry.getValue();
            Enemy     e  = enemies.get(id);
            if (e == null) {
                Goblin g = new Goblin(d.x, d.y);
                g.setId(id);
                enemies.put(id, g);
                e = g;
            }
            e.setHp(d.hp);
        }
    }

    // -----------------------------------------------------------------------
    //  NPCs — full state sync (called when server sends NPCS message)
    // -----------------------------------------------------------------------

    /**
     * Replace the entire NPC map with the server's authoritative state.
     *
     * NPC objects are constructed here from typed {@link NPCData} records —
     * no NPC is ever created anywhere else on the client.
     */
    public void updateNpcs(Map<String, NPCData> data) {
        npcs.clear();
        for (Map.Entry<String, NPCData> entry : data.entrySet()) {
            NPCData d = entry.getValue();
            npcs.put(entry.getKey(), new NPC(d.x, d.y, d.name, d.shopkeeper));
        }
        System.out.println("[ClientWorld] Full NPC sync: " + npcs.size() + " NPCs");
    }

    // -----------------------------------------------------------------------
    //  Queries — trees
    // -----------------------------------------------------------------------

    public Collection<Tree> getTrees() {
        return Collections.unmodifiableCollection(trees.values());
    }

    public Tree getTreeAt(int px, int py) {
        for (Tree t : trees.values()) {
            if (t.containsPoint(px, py)) return t;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    //  Queries — rocks
    // -----------------------------------------------------------------------

    public Collection<Rock> getRocks() {
        return Collections.unmodifiableCollection(rocks.values());
    }

    public Rock getRockAt(int px, int py) {
        for (Rock r : rocks.values()) {
            if (r.containsPoint(px, py)) return r;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    //  Queries — enemies
    // -----------------------------------------------------------------------

    public Collection<Enemy> getEnemies() {
        return Collections.unmodifiableCollection(enemies.values());
    }

    public Enemy getEnemyAt(int px, int py) {
        for (Enemy e : enemies.values()) {
            if (e.containsPoint(px, py)) return e;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    //  Queries — NPCs
    // -----------------------------------------------------------------------

    public Collection<NPC> getNpcs() {
        return Collections.unmodifiableCollection(npcs.values());
    }

    public NPC getNpcAt(int px, int py) {
        for (NPC n : npcs.values()) {
            if (n.containsPoint(px, py)) return n;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    //  Loot — full state sync + delta updates
    // -----------------------------------------------------------------------

    /** Replace the entire loot map with the server's authoritative state. */
    public void updateLoot(Map<String, LootData> data) {
        loot.clear();
        for (Map.Entry<String, LootData> entry : data.entrySet()) {
            LootData d = entry.getValue();
            loot.put(d.id, new Loot(d.id, d.x, d.y, d.itemName, d.count));
        }
        System.out.println("[ClientWorld] Full loot sync: " + data.size() + " items");
    }

    /** Server spawned a new loot item — create it. */
    public void addLoot(LootData d) {
        loot.put(d.id, new Loot(d.id, d.x, d.y, d.itemName, d.count));
    }

    /** Server removed a loot item (picked up). */
    public void removeLoot(String id) {
        loot.remove(id);
    }

    public Collection<Loot> getLoot() {
        return Collections.unmodifiableCollection(loot.values());
    }

    public Loot getLootAt(int px, int py) {
        for (Loot l : loot.values()) {
            if (l.containsPoint(px, py)) return l;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    //  Pathfinding — combined obstacle check
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} if an alive tree or solid rock occupies the given
     * tile column/row.  Used by Pathfinding as an injected walkability predicate.
     */
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
