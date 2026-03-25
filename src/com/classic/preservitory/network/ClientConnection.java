package com.classic.preservitory.network;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import com.classic.preservitory.client.world.EnemyData;
import com.classic.preservitory.client.world.LootData;
import com.classic.preservitory.client.world.NPCData;

/**
 * Manages the TCP connection between a game client and the Preservitory server.
 *
 * Clean architecture:
 *   - This class handles ONLY networking
 *   - Game/world state is passed out via listeners
 */
public class ClientConnection {

    private static final String HOST             = "localhost";
    private static final int    PORT             = 5555;
    private static final long   SEND_INTERVAL_MS = 50;

    // -----------------------------------------------------------------------
    //  Socket / writer state
    // -----------------------------------------------------------------------

    private Socket      socket;
    private PrintWriter out;

    private volatile boolean connected = false;
    private volatile String  myId      = null;

    // -----------------------------------------------------------------------
    //  Remote-player snapshot
    // -----------------------------------------------------------------------

    private final ConcurrentHashMap<String, int[]> remotePlayers = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    //  Outgoing throttle state
    // -----------------------------------------------------------------------

    private int  lastSentX    = Integer.MIN_VALUE;
    private int  lastSentY    = Integer.MIN_VALUE;
    private long lastSentTime = 0;

    // -----------------------------------------------------------------------
    //  Update-rate tracking
    // -----------------------------------------------------------------------

    private volatile double updatesPerSecond = 0.0;
    private long lastPlayersMsgMs = 0;

    // -----------------------------------------------------------------------
    //  Listeners
    // -----------------------------------------------------------------------

    public interface Listener {
        void onConnected(String assignedId);
        void onDisconnected();
        default void onChat(String fromId, String message) {}
    }

    private Listener listener;

    // Tree listeners
    private Consumer<Map<String, int[]>> treeUpdateListener;
    private Consumer<String>             treeRemoveListener;
    private Consumer<String[]>           treeAddListener;

    // Rock listeners
    private Consumer<Map<String, int[]>> rockUpdateListener;
    private Consumer<String>             rockRemoveListener;
    private Consumer<String[]>           rockAddListener;

    private Consumer<Map<String, NPCData>>   npcUpdateListener;
    private Consumer<Map<String, EnemyData>> enemyUpdateListener;
    private Consumer<Integer>                playerHitListener;
    private Consumer<Integer>                playerHpListener;
    private Consumer<String[]>               skillXpListener;

    // Loot listeners
    private Consumer<Map<String, LootData>> lootUpdateListener;
    private Consumer<LootData>              lootAddListener;
    private Consumer<String>                lootRemoveListener;
    private Consumer<Map<String, Integer>>  inventoryListener;

    public void setListener(Listener l) { this.listener = l; }

    public void setTreeUpdateListener(Consumer<Map<String, int[]>> l) { treeUpdateListener = l; }
    public void setTreeRemoveListener(Consumer<String>             l) { treeRemoveListener = l; }
    public void setTreeAddListener   (Consumer<String[]>           l) { treeAddListener    = l; }

    public void setRockUpdateListener(Consumer<Map<String, int[]>>    l) { rockUpdateListener = l; }
    public void setRockRemoveListener(Consumer<String>                l) { rockRemoveListener = l; }
    public void setRockAddListener   (Consumer<String[]>              l) { rockAddListener    = l; }

    public void setNpcUpdateListener(Consumer<Map<String, NPCData>> l) {
        npcUpdateListener = l;
    }

    public void setEnemyUpdateListener(Consumer<Map<String, EnemyData>> l) {
        enemyUpdateListener = l;
    }

    public void setPlayerHitListener(Consumer<Integer> l) {
        playerHitListener = l;
    }

    public void setPlayerHpListener(Consumer<Integer> l) {
        playerHpListener = l;
    }

    public void setSkillXpListener(Consumer<String[]> l) {
        skillXpListener = l;
    }

    public void setLootUpdateListener   (Consumer<Map<String, LootData>> l) { lootUpdateListener    = l; }
    public void setLootAddListener      (Consumer<LootData>              l) { lootAddListener       = l; }
    public void setLootRemoveListener   (Consumer<String>                l) { lootRemoveListener    = l; }
    public void setInventoryListener(Consumer<Map<String, Integer>> listener) {
        this.inventoryListener = listener;
    }

    public void setInventoryUpdateListener(Consumer<Map<String, Integer>> listener) {
        setInventoryListener(listener);
    }

    // -----------------------------------------------------------------------
    //  Connect
    // -----------------------------------------------------------------------

    public void connect() {
        Thread t = new Thread(this::connectAndListen, "net-client-reader");
        t.setDaemon(true);
        t.start();
    }

    private void connectAndListen() {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(HOST, PORT), 3000);

            out       = new PrintWriter(socket.getOutputStream(), true);
            connected = true;

            System.out.println("[Client] Connected to " + HOST + ":" + PORT);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            String line;
            while ((line = in.readLine()) != null) {
                handleMessage(line.trim());
            }

        } catch (IOException e) {
            System.out.println("[Client] Could not connect to server: " + e.getMessage()
                    + " — running in single-player mode.");
        } finally {
            connected        = false;
            updatesPerSecond = 0.0;
            remotePlayers.clear();

            if (listener != null) listener.onDisconnected();
        }
    }

    // -----------------------------------------------------------------------
    //  Incoming messages
    // -----------------------------------------------------------------------

    private void handleMessage(String line) {

        Listener l = listener; // 🔥 snapshot once

        if (line.startsWith("WELCOME ")) {
            myId = line.substring(8).trim();
            System.out.println("[Client] Assigned player ID: " + myId);

            if (l != null) {
                l.onConnected(myId);
                l.onChat("SYSTEM", "Use /register <name> or /login <name>");
            }

        } else if (line.startsWith("PLAYERS ")) {
            trackUpdateRate();
            parsePlayersMessage(line.substring(8));

        } else if (line.startsWith("DISCONNECT ")) {
            String id = line.substring(11).trim();
            remotePlayers.remove(id);
            System.out.println("[Client] Player left: " + id);

        } else if (line.startsWith("SYSTEM ")) {
            String msg = line.substring(7);
            if (l != null) {
                l.onChat("SYSTEM", msg);
            }

        } else if (line.startsWith("CHAT ")) {
            String rest = line.substring(5);
            int spaceIdx = rest.indexOf(' ');
            if (spaceIdx > 0 && l != null) {
                l.onChat(rest.substring(0, spaceIdx), rest.substring(spaceIdx + 1));
            }

        // ---- Tree messages ----
        } else if (line.startsWith("TREES ")) {
            Map<String, int[]> parsed = parseFullObjectState(line.substring(6));
            if (treeUpdateListener != null) treeUpdateListener.accept(parsed);

        } else if (line.startsWith("TREE_REMOVE ")) {
            String treeId = line.substring(12).trim();
            if (treeRemoveListener != null) treeRemoveListener.accept(treeId);

        } else if (line.startsWith("TREE_ADD ")) {
            String[] parts = line.substring(9).trim().split(" ");
            if (parts.length == 3 && treeAddListener != null) treeAddListener.accept(parts);

        // ---- Rock messages ----
        } else if (line.startsWith("ROCKS ")) {
            Map<String, int[]> parsed = parseFullObjectState(line.substring(6));
            if (rockUpdateListener != null) rockUpdateListener.accept(parsed);

        } else if (line.startsWith("ROCK_REMOVE ")) {
            String rockId = line.substring(12).trim();
            if (rockRemoveListener != null) rockRemoveListener.accept(rockId);

        } else if (line.startsWith("ROCK_ADD ")) {
            String[] parts = line.substring(9).trim().split(" ");
            if (parts.length == 3 && rockAddListener != null) rockAddListener.accept(parts);

        // ---- NPC messages ----
        } else if (line.startsWith("NPCS")) {
            String payload = line.length() > 4 ? line.substring(5) : "";
            Map<String, NPCData> parsed = parseNpcsMessage(payload);
            if (npcUpdateListener != null) npcUpdateListener.accept(parsed);

        // ---- Enemy messages ----
        } else if (line.startsWith("ENEMIES")) {
            String payload = line.length() > 7 ? line.substring(8) : "";
            Map<String, EnemyData> parsed = parseEnemiesMessage(payload);
            if (enemyUpdateListener != null) enemyUpdateListener.accept(parsed);

        } else if (line.startsWith("PLAYER_HIT ")) {
            try {
                int damage = Integer.parseInt(line.substring(11).trim());
                if (playerHitListener != null) playerHitListener.accept(damage);
            } catch (NumberFormatException ignored) {}

        } else if (line.startsWith("PLAYER_HP ")) {
            try {
                int hp = Integer.parseInt(line.substring(10).trim());
                if (playerHpListener != null) playerHpListener.accept(hp);
            } catch (NumberFormatException ignored) {}

        // ---- Skill XP ----
        } else if (line.startsWith("SKILL_XP ")) {
            String[] parts = line.substring(9).trim().split(" ");
            if (parts.length == 2 && skillXpListener != null) {
                skillXpListener.accept(parts);
            }

        // ---- Loot messages ----
        } else if (line.startsWith("LOOT ") || line.equals("LOOT")) {
            String payload = line.length() > 4 ? line.substring(5) : "";
            Map<String, LootData> parsed = parseLootMessage(payload);
            if (lootUpdateListener != null) lootUpdateListener.accept(parsed);

        } else if (line.startsWith("LOOT_ADD ")) {
            LootData d = parseLootAdd(line.substring(9).trim());
            if (d != null && lootAddListener != null) lootAddListener.accept(d);

        } else if (line.startsWith("LOOT_REMOVE ")) {
            String id = line.substring(12).trim();
            if (lootRemoveListener != null) lootRemoveListener.accept(id);

        // ---- Inventory messages ----
        } else if (line.startsWith("INVENTORY")) {
            String payload = line.length() > 9 ? line.substring(10) : "";
            Map<String, Integer> inv = parseInventory(payload);
            if (inventoryListener != null) inventoryListener.accept(inv);
        }
    }

    // -----------------------------------------------------------------------
    //  Enemy parsing
    // -----------------------------------------------------------------------

    /**
     * Parse a full {@code ENEMIES} payload.
     * Format per entry: {@code id x y hp maxHp} separated by {@code ;}.
     */
    private Map<String, EnemyData> parseEnemiesMessage(String payload) {
        Map<String, EnemyData> result = new LinkedHashMap<>();
        for (String entry : payload.split(";")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            String[] parts = entry.split(" ");
            if (parts.length != 5) continue;
            try {
                String id    = parts[0];
                int    x     = Integer.parseInt(parts[1]);
                int    y     = Integer.parseInt(parts[2]);
                int    hp    = Integer.parseInt(parts[3]);
                int    maxHp = Integer.parseInt(parts[4]);
                result.put(id, new EnemyData(x, y, hp, maxHp));
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    // -----------------------------------------------------------------------
    //  NPC parsing
    // -----------------------------------------------------------------------

    /**
     * Parse a full NPCS payload.
     * Format per entry: {@code id x y name shopkeeper} separated by {@code ;}.
     *
     * @return id → NPCData for every valid entry.
     */
    private Map<String, NPCData> parseNpcsMessage(
            String payload) {
        Map<String, NPCData> result = new LinkedHashMap<>();
        for (String entry : payload.split(";")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            String[] parts = entry.split(" ");
            if (parts.length != 5) continue;
            try {
                String  id         = parts[0];
                int     x          = Integer.parseInt(parts[1]);
                int     y          = Integer.parseInt(parts[2]);
                String  name       = parts[3];
                boolean shopkeeper = Boolean.parseBoolean(parts[4]);
                result.put(id, new NPCData(
                        x, y, name, shopkeeper));
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    // -----------------------------------------------------------------------
    //  Parsing
    // -----------------------------------------------------------------------

    private void trackUpdateRate() {
        long now = System.currentTimeMillis();
        if (lastPlayersMsgMs > 0) {
            double dtSec = (now - lastPlayersMsgMs) / 1000.0;
            if (dtSec > 0.001) {
                double instantHz = 1.0 / dtSec;
                updatesPerSecond = updatesPerSecond * 0.8 + instantHz * 0.2;
            }
        }
        lastPlayersMsgMs = now;
    }

    private void parsePlayersMessage(String payload) {
        Map<String, int[]> updated = new HashMap<>();
        for (String entry : payload.split(";")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            String[] parts = entry.split(" ");
            if (parts.length != 3) continue;
            try {
                String id = parts[0];
                int    x  = Integer.parseInt(parts[1]);
                int    y  = Integer.parseInt(parts[2]);
                if (id.equals(myId)) continue;
                updated.put(id, new int[]{x, y});
            } catch (NumberFormatException ignored) {}
        }
        remotePlayers.clear();
        remotePlayers.putAll(updated);
    }

    /**
     * Parse a full object-state payload (used by both TREES and ROCKS).
     * Format per entry: id x y alive  (alive is always 1 — server omits dead objects)
     */
    private Map<String, int[]> parseFullObjectState(String payload) {
        Map<String, int[]> result = new HashMap<>();
        for (String entry : payload.split(";")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            String[] parts = entry.split(" ");
            if (parts.length != 4) continue;
            try {
                String id    = parts[0];
                int    x     = Integer.parseInt(parts[1]);
                int    y     = Integer.parseInt(parts[2]);
                int    alive = Integer.parseInt(parts[3]);
                if (alive == 1) result.put(id, new int[]{x, y});
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    private Map<String, Integer> parseInventory(String payload) {
        Map<String, Integer> map = new HashMap<>();

        if (payload == null || payload.isEmpty()) return map;

        String[] entries = payload.split(";");
        for (String entry : entries) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;

            String[] parts = entry.split(":");
            if (parts.length != 2) continue;

            String item = parts[0].trim();
            try {
                int amount = Integer.parseInt(parts[1].trim());
                map.put(item, amount);
            } catch (NumberFormatException ignored) {}
        }

        return map;
    }

    // -----------------------------------------------------------------------
    //  Outgoing
    // -----------------------------------------------------------------------

    public void sendChatMessage(String message) {
        if (!connected || myId == null || out == null) return;
        if (message == null || message.isEmpty()) return;

        String input = message.trim();

        // 🔐 LOGIN
        if (input.toLowerCase().startsWith("/login ")) {
            String username = input.substring(7).trim();
            if (!username.isEmpty()) {
                out.println("LOGIN " + username);
            }
            return;
        }

        // 🔐 REGISTER
        if (input.toLowerCase().startsWith("/register ")) {
            String username = input.substring(10).trim();
            if (!username.isEmpty()) {
                out.println("REGISTER " + username);
            }
            return;
        }

        // 💬 NORMAL CHAT
        out.println("CHAT " + input);
    }

    public void sendPosition(int x, int y) {
        if (!connected || myId == null || out == null) return;
        long now = System.currentTimeMillis();
        if (now - lastSentTime < SEND_INTERVAL_MS) return;
        if (x == lastSentX && y == lastSentY) return;
        out.println("UPDATE " + myId + " " + x + " " + y);
        lastSentX    = x;
        lastSentY    = y;
        lastSentTime = now;
    }

    public void sendChop(String treeId) {
        if (!connected || out == null) return;
        out.println("CHOP " + treeId);
    }

    public void sendMine(String rockId) {
        if (!connected || out == null) return;
        out.println("MINE " + rockId);
    }

    public void sendAttack(String enemyId, int damage) {
        if (!connected || out == null) return;
        out.println("ATTACK " + enemyId + " " + damage);
    }

    public void sendPickup(String lootId) {
        if (!connected || out == null) return;
        out.println("PICKUP " + lootId);
    }

    // -----------------------------------------------------------------------
    //  Getters
    // -----------------------------------------------------------------------

    public Map<String, int[]> getRemotePlayers() {
        return Collections.unmodifiableMap(remotePlayers);
    }

    public String  getMyId()             { return myId; }
    public boolean isConnected()         { return connected; }
    public double  getUpdatesPerSecond() { return updatesPerSecond; }

    // -----------------------------------------------------------------------
    //  Loot parsing
    // -----------------------------------------------------------------------

    /**
     * Parse a full {@code LOOT} payload.
     * Format per entry: {@code id x y name count} separated by {@code ;}.
     */
    private Map<String, LootData> parseLootMessage(String payload) {
        Map<String, LootData> result = new LinkedHashMap<>();
        for (String entry : payload.split(";")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            String[] parts = entry.split(" ");
            if (parts.length != 5) continue;
            try {
                String id    = parts[0];
                int    x     = Integer.parseInt(parts[1]);
                int    y     = Integer.parseInt(parts[2]);
                String name  = parts[3];
                int    count = Integer.parseInt(parts[4]);
                result.put(id, new LootData(id, x, y, name, count));
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    /**
     * Parse a {@code LOOT_ADD} payload (everything after "LOOT_ADD ").
     * Format: {@code id x y name count}
     */
    private LootData parseLootAdd(String payload) {
        String[] parts = payload.split(" ");
        if (parts.length != 5) return null;
        try {
            String id    = parts[0];
            int    x     = Integer.parseInt(parts[1]);
            int    y     = Integer.parseInt(parts[2]);
            String name  = parts[3];
            int    count = Integer.parseInt(parts[4]);
            return new LootData(id, x, y, name, count);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
