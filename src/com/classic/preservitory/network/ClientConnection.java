package com.classic.preservitory.network;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import com.classic.preservitory.client.world.EnemyData;
import com.classic.preservitory.client.world.LootData;
import com.classic.preservitory.client.world.NPCData;
import com.classic.preservitory.client.world.ObjectStateData;
import com.classic.preservitory.util.Constants;

/**
 * Manages the TCP connection between a game client and the Preservitory server.
 *
 * Clean architecture:
 *   - This class handles ONLY networking
 *   - Game/world state is passed out via listeners
 */
public class ClientConnection {

    private static final String HOST = Constants.LOCALHOST;
    private static final int PORT = Constants.PORT;
    private static final long SEND_INTERVAL_MS = 50;

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
        default void onChat(String username, String role, String message) {}
    }

    private Listener listener;

    // Tree listeners
    private Consumer<Map<String, ObjectStateData>> treeUpdateListener;
    private Consumer<String>             treeRemoveListener;
    private Consumer<String[]>           treeAddListener;

    // Rock listeners
    private Consumer<Map<String, ObjectStateData>> rockUpdateListener;
    private Consumer<String>             rockRemoveListener;
    private Consumer<String[]>           rockAddListener;

    private Consumer<Map<String, NPCData>>   npcUpdateListener;
    private Consumer<Map<String, EnemyData>> enemyUpdateListener;
    private Consumer<int[]>                  playerHpListener;
    private Consumer<String[]>               skillXpListener;
    private Consumer<Map<String, int[]>>     skillSnapshotListener;
    private Consumer<int[]>                  damageListener;
    private Consumer<DialogueData>           dialogueListener;
    private Consumer<ShopData>               shopListener;
    private Consumer<String>                 authSuccessListener;
    private Consumer<String>                 authFailureListener;

    // Loot listeners
    private Consumer<Map<String, LootData>> lootUpdateListener;
    private Consumer<LootData>              lootAddListener;
    private Consumer<String>                lootRemoveListener;
    private Consumer<Map<String, Integer>>  inventoryListener;

    public void setListener(Listener l) { this.listener = l; }

    public void setTreeUpdateListener(Consumer<Map<String, ObjectStateData>> l) { treeUpdateListener = l; }
    public void setTreeRemoveListener(Consumer<String>             l) { treeRemoveListener = l; }
    public void setTreeAddListener   (Consumer<String[]>           l) { treeAddListener    = l; }

    public void setRockUpdateListener(Consumer<Map<String, ObjectStateData>>    l) { rockUpdateListener = l; }
    public void setRockRemoveListener(Consumer<String>                l) { rockRemoveListener = l; }
    public void setRockAddListener   (Consumer<String[]>              l) { rockAddListener    = l; }

    public void setNpcUpdateListener(Consumer<Map<String, NPCData>> l) {
        npcUpdateListener = l;
    }

    public void setEnemyUpdateListener(Consumer<Map<String, EnemyData>> l) {
        enemyUpdateListener = l;
    }

    public void setPlayerHpListener(Consumer<int[]> l) {
        playerHpListener = l;
    }

    public void setSkillXpListener(Consumer<String[]> l) {
        skillXpListener = l;
    }

    public void setSkillSnapshotListener(Consumer<Map<String, int[]>> l) {
        skillSnapshotListener = l;
    }

    public void setDamageListener(Consumer<int[]> l) {
        damageListener = l;
    }

    public void setDialogueListener(Consumer<DialogueData> l) {
        dialogueListener = l;
    }

    public void setShopListener(Consumer<ShopData> l) {
        shopListener = l;
    }

    public void setAuthSuccessListener(Consumer<String> l) {
        authSuccessListener = l;
    }

    public void setAuthFailureListener(Consumer<String> l) {
        authFailureListener = l;
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

    private static final long RECONNECT_DELAY_MS = 5_000;

    private void connectAndListen() {
        while (!Thread.currentThread().isInterrupted()) {
            boolean wasConnected = false;
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(HOST, PORT), 3000);

                out          = new PrintWriter(socket.getOutputStream(), true);
                connected    = true;
                wasConnected = true;

                System.out.println("[Client] Connected to " + HOST + ":" + PORT);

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));

                String line;
                while ((line = in.readLine()) != null) {
                    handleMessage(line.trim());
                }

            } catch (IOException e) {
                System.out.println("[Client] Could not connect to server: " + e.getMessage());
            } finally {
                connected        = false;
                updatesPerSecond = 0.0;
                remotePlayers.clear();
                myId             = null;
                out              = null;

                // Only notify if we had an established session (not failed initial attempts)
                if (wasConnected && listener != null) listener.onDisconnected();

                try { if (socket != null) socket.close(); } catch (IOException ignored) {}
                socket = null;
            }

            // Wait before the next reconnect attempt
            try {
                Thread.sleep(RECONNECT_DELAY_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
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
                l.onChat("SYSTEM","SYSTEM", msg);
            }

        } else if (line.startsWith("AUTH_OK ")) {
            if (authSuccessListener != null) {
                authSuccessListener.accept(line.substring(8).trim());
            }

        } else if (line.startsWith("AUTH_FAIL ")) {
            if (authFailureListener != null) {
                authFailureListener.accept(line.substring(10).trim());
            }

        } else if (line.startsWith("CHAT ")) {
            String payload = line.substring(5);
            String[] parts = payload.split(" ", 3);

            if (parts.length >= 3 && l != null) {
                String username = parts[0];
                String role = parts[1];
                String message = parts[2];

                l.onChat(username, role, message);
            }

        // ---- Tree messages ----
        } else if (line.equals("TREES") || line.startsWith("TREES ")) {
            String payload = line.length() > 5 ? line.substring(6) : "";
            Map<String, ObjectStateData> parsed = parseFullObjectState(payload);
            if (treeUpdateListener != null) treeUpdateListener.accept(parsed);

        } else if (line.startsWith("TREE_REMOVE ")) {
            String treeId = line.substring(12).trim();
            if (treeRemoveListener != null) treeRemoveListener.accept(treeId);

        } else if (line.startsWith("TREE_ADD ")) {
            String[] parts = line.substring(9).trim().split(" ");
            if (parts.length == 4 && treeAddListener != null) treeAddListener.accept(parts);

        // ---- Rock messages ----
        } else if (line.equals("ROCKS") || line.startsWith("ROCKS ")) {
            String payload = line.length() > 5 ? line.substring(6) : "";
            Map<String, ObjectStateData> parsed = parseFullObjectState(payload);
            if (rockUpdateListener != null) rockUpdateListener.accept(parsed);

        } else if (line.startsWith("ROCK_REMOVE ")) {
            String rockId = line.substring(12).trim();
            if (rockRemoveListener != null) rockRemoveListener.accept(rockId);

        } else if (line.startsWith("ROCK_ADD ")) {
            String[] parts = line.substring(9).trim().split(" ");
            if (parts.length == 4 && rockAddListener != null) rockAddListener.accept(parts);

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

        } else if (line.startsWith("PLAYER_HP ")) {
            int[] hp = parsePlayerHp(line.substring(10).trim());
            if (hp != null && playerHpListener != null) {
                playerHpListener.accept(hp);
            }

        } else if (line.startsWith("DAMAGE ")) {
            int[] damage = parseDamage(line.substring(7).trim());
            if (damage != null && damageListener != null) {
                damageListener.accept(damage);
            }

        } else if (line.startsWith("DIALOGUE\t")) {
            DialogueData dialogue = parseDialogue(line);
            if (dialogue != null && dialogueListener != null) {
                dialogueListener.accept(dialogue);
            }

        } else if (line.startsWith("SHOP\t")) {
            ShopData shop = parseShop(line);
            if (shop != null && shopListener != null) {
                shopListener.accept(shop);
            }

        // ---- Skill XP ----
        } else if (line.startsWith("SKILL_XP ")) {
            String[] parts = line.substring(9).trim().split(" ");
            if (parts.length == 2 && skillXpListener != null) {
                skillXpListener.accept(parts);
            }

        } else if (line.equals("SKILLS") || line.startsWith("SKILLS ")) {
            String payload = line.length() > 6 ? line.substring(7) : "";
            Map<String, int[]> parsed = parseSkillsMessage(payload);
            if (skillSnapshotListener != null) {
                skillSnapshotListener.accept(parsed);
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
                        id, x, y, name, shopkeeper));
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
    private Map<String, ObjectStateData> parseFullObjectState(String payload) {
        Map<String, ObjectStateData> result = new HashMap<>();
        for (String entry : payload.split(";")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            String[] parts = entry.split(" ");
            if (parts.length != 5) continue;
            try {
                String id    = parts[0];
                String typeId = parts[1];
                int    x     = Integer.parseInt(parts[2]);
                int    y     = Integer.parseInt(parts[3]);
                int    alive = Integer.parseInt(parts[4]);
                if (alive == 1) result.put(id, new ObjectStateData(typeId, x, y));
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

    private int[] parsePlayerHp(String payload) {
        if (payload.isEmpty()) return null;

        String[] parts = payload.split(" ");
        try {
            int currentHp = Integer.parseInt(parts[0]);
            int maxHp = parts.length >= 2 ? Integer.parseInt(parts[1]) : -1;
            return new int[]{currentHp, maxHp};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Map<String, int[]> parseSkillsMessage(String payload) {
        Map<String, int[]> result = new LinkedHashMap<>();
        for (String entry : payload.split(";")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;

            String[] parts = entry.split(":");
            if (parts.length != 3) continue;

            try {
                String skillName = parts[0].trim().toLowerCase();
                int level = Integer.parseInt(parts[1].trim());
                int xp = Integer.parseInt(parts[2].trim());
                result.put(skillName, new int[]{level, xp});
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    private DialogueData parseDialogue(String line) {
        String[] parts = line.split("\t", 4);
        if (parts.length != 4) return null;

        String npcId = parts[1];
        boolean openShop = "1".equals(parts[2]);
        String[] lines = parts[3].isEmpty() ? new String[0] : parts[3].split("\\|", -1);
        return new DialogueData(npcId, lines, openShop);
    }

    private ShopData parseShop(String line) {
        String[] parts = line.split("\t", 3);
        if (parts.length != 3) return null;
        return new ShopData(parsePriceMap(parts[1]), parsePriceMap(parts[2]));
    }

    private LinkedHashMap<String, Integer> parsePriceMap(String payload) {
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
        if (payload == null || payload.isEmpty()) return map;

        for (String entry : payload.split(";")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;

            String[] parts = entry.split(":");
            if (parts.length != 2) continue;

            try {
                map.put(parts[0].trim(), Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException ignored) {}
        }
        return map;
    }

    private int[] parseDamage(String payload) {
        String[] parts = payload.split(" ");
        if (parts.length != 3) return null;

        try {
            int x = (int) Math.round(Double.parseDouble(parts[0]));
            int y = (int) Math.round(Double.parseDouble(parts[1]));
            int amount = Integer.parseInt(parts[2]);
            return new int[]{x, y, amount};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // -----------------------------------------------------------------------
    //  Outgoing
    // -----------------------------------------------------------------------

    public void sendChatMessage(String message) {
        if (!connected || myId == null || out == null) return;
        if (message == null || message.isEmpty()) return;

        String input = message.trim();

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

    public void sendAttack(String enemyId) {
        if (!connected || out == null) return;
        out.println("ATTACK " + enemyId);
    }

    public void sendPickup(String lootId) {
        if (!connected || out == null) return;
        out.println("PICKUP " + lootId);
    }

    public void sendTalk(String npcId) {
        if (!connected || out == null) return;
        out.println("TALK " + npcId);
    }

    public void sendLogin(String username, String password) {
        if (!connected || out == null) return;
        out.println("LOGIN " + username + " " + password);
    }

    public void sendRegister(String username, String password) {
        if (!connected || out == null) return;
        out.println("REGISTER " + username + " " + password);
    }

    public void sendBuy(String itemName) {
        if (!connected || out == null) return;
        out.println("BUY " + itemName);
    }

    public void sendSell(String itemName) {
        if (!connected || out == null) return;
        out.println("SELL " + itemName);
    }

    public void sendShopClose() {
        if (!connected || out == null) return;
        out.println("SHOP_CLOSE");
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

    public static final class DialogueData {
        public final String npcId;
        public final String[] lines;
        public final boolean openShop;

        public DialogueData(String npcId, String[] lines, boolean openShop) {
            this.npcId = npcId;
            this.lines = lines;
            this.openShop = openShop;
        }
    }

    public static final class ShopData {
        public final LinkedHashMap<String, Integer> stockPrices;
        public final LinkedHashMap<String, Integer> sellPrices;

        public ShopData(LinkedHashMap<String, Integer> stockPrices,
                        LinkedHashMap<String, Integer> sellPrices) {
            this.stockPrices = stockPrices;
            this.sellPrices = sellPrices;
        }
    }
}
