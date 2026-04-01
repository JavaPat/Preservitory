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
import com.classic.preservitory.ui.quests.QuestEntry;
import com.classic.preservitory.ui.quests.QuestState;
import com.classic.preservitory.ui.shops.Shop;
import com.classic.preservitory.ui.shops.ShopParser;
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

    private volatile boolean connected            = false;
    private volatile String  myId                 = null;
    private volatile boolean intentionalDisconnect = false;

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
        /** Called when the player voluntarily logs out (never shows "server offline"). */
        default void onLoggedOut() {}
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
    private Consumer<String>                 dialogueCloseListener;
    private Consumer<Shop>                   shopListener;
    private Consumer<String>                 authSuccessListener;
    private Consumer<String>                 authFailureListener;

    // Loot listeners
    private Consumer<Map<String, LootData>> lootUpdateListener;
    private Consumer<LootData>              lootAddListener;
    private Consumer<String>                lootRemoveListener;
    private Consumer<Map<Integer, Integer>>  inventoryListener;
    private Consumer<int[][]>               inventorySlotListener;
    private Consumer<Map<String, Integer>>   equipmentListener;
    private Runnable                         stopActionListener;

    // Quest event listeners
    private Consumer<String>           questStartListener;
    private Consumer<String>           questCompleteListener;
    private Consumer<int[]>            questRewardListener;
    private Consumer<String[]>         questXpListener;
    private Consumer<List<QuestEntry>> questLogListener;
    private Consumer<String[]>         questStageListener;
    private Consumer<String>           questObjectiveCompleteListener;

    // Dialogue options listener — fired when the server sends DIALOGUE_OPTIONS
    private Consumer<String[]> dialogueOptionsListener;

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

    public void setDialogueCloseListener(Consumer<String> l) {
        dialogueCloseListener = l;
    }

    public void setShopListener(Consumer<Shop> l) {
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
    public void setInventoryListener(Consumer<Map<Integer, Integer>> listener) {
        this.inventoryListener = listener;
    }

    public void setInventoryUpdateListener(Consumer<Map<Integer, Integer>> listener) {
        setInventoryListener(listener);
    }

    public void setInventorySlotListener(Consumer<int[][]> listener) {
        this.inventorySlotListener = listener;
    }

    public void setStopActionListener(Runnable listener) {
        this.stopActionListener = listener;
    }

    public void setEquipmentListener(Consumer<Map<String, Integer>> listener) {
        this.equipmentListener = listener;
    }

    public void setQuestStartListener              (Consumer<String>           l) { questStartListener              = l; }
    public void setQuestCompleteListener           (Consumer<String>           l) { questCompleteListener           = l; }
    public void setQuestRewardListener             (Consumer<int[]>            l) { questRewardListener             = l; }
    public void setQuestXpListener                 (Consumer<String[]>         l) { questXpListener                 = l; }
    public void setQuestLogListener                (Consumer<List<QuestEntry>> l) { questLogListener                = l; }
    public void setQuestStageListener              (Consumer<String[]>         l) { questStageListener              = l; }
    public void setQuestObjectiveCompleteListener  (Consumer<String>           l) { questObjectiveCompleteListener  = l; }
    public void setDialogueOptionsListener         (Consumer<String[]>         l) { dialogueOptionsListener         = l; }

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
            boolean wasLogout    = false;
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
                if (!intentionalDisconnect) {
                    System.out.println("[Client] Could not connect to server: " + e.getMessage());
                }
            } finally {
                // Snapshot and clear the flag atomically inside finally so we never
                // miss a logout that races with an IOException.
                wasLogout             = intentionalDisconnect;
                intentionalDisconnect = false;

                connected        = false;
                updatesPerSecond = 0.0;
                remotePlayers.clear();
                myId             = null;
                out              = null;

                try { if (socket != null) socket.close(); } catch (IOException ignored) {}
                socket = null;

                // Fire the appropriate callback only when we had a live session.
                if (wasConnected && listener != null) {
                    if (wasLogout) {
                        listener.onLoggedOut();
                    } else {
                        listener.onDisconnected();
                    }
                }
            }

            if (wasLogout) {
                // Intentional logout — reconnect immediately so the login screen
                // reappears as soon as the server accepts the new connection.
                continue;
            }

            // Unexpected disconnect — wait before retrying.
            try {
                Thread.sleep(RECONNECT_DELAY_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Cleanly disconnect from the server for logout.
     * Fires {@link Listener#onLoggedOut()} instead of {@link Listener#onDisconnected()},
     * then reconnects immediately so the login screen reappears without restarting.
     */
    public void disconnect() {
        intentionalDisconnect = true;
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
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

        } else if (line.startsWith("DIALOGUE_CLOSE\t")) {
            String npcId = line.substring(15).trim();
            if (dialogueCloseListener != null) {
                dialogueCloseListener.accept(npcId);
            }

        } else if (line.startsWith("DIALOGUE_OPTIONS\t")) {
            // Split on \t — each tab-separated token after the header is one option text
            String[] options = line.substring(17).split("\t", -1);
            if (dialogueOptionsListener != null) dialogueOptionsListener.accept(options);

        } else if (line.startsWith("SHOP\t")) {
            Shop shop = ShopParser.parse(line);
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

        } else if (line.startsWith("GROUND_ITEM_ADD ")) {
            LootData d = parseLootAdd(line.substring(16).trim());
            if (d != null && lootAddListener != null) lootAddListener.accept(d);

        } else if (line.startsWith("LOOT_REMOVE ")) {
            String id = line.substring(12).trim();
            if (lootRemoveListener != null) lootRemoveListener.accept(id);

        } else if (line.startsWith("GROUND_ITEM_REMOVE ")) {
            String id = line.substring(19).trim();
            if (lootRemoveListener != null) lootRemoveListener.accept(id);

        // ---- Quest feedback messages ----
        } else if (line.startsWith("QUEST_START\t")) {
            if (questStartListener != null) questStartListener.accept(line.substring(12));

        } else if (line.startsWith("QUEST_COMPLETE\t")) {
            if (questCompleteListener != null) questCompleteListener.accept(line.substring(15));

        } else if (line.startsWith("QUEST_REWARD\t")) {
            if (questRewardListener != null) {
                String[] parts = line.split("\t", 3);
                if (parts.length == 3) {
                    try {
                        int itemId = Integer.parseInt(parts[1]);
                        int amount = Integer.parseInt(parts[2]);
                        questRewardListener.accept(new int[]{itemId, amount});
                    } catch (NumberFormatException ignored) {}
                }
            }

        } else if (line.startsWith("QUEST_XP\t")) {
            if (questXpListener != null) {
                String[] parts = line.split("\t", 3);
                if (parts.length == 3) questXpListener.accept(new String[]{parts[1], parts[2]});
            }

        } else if (line.startsWith("QUEST_LOG\t")) {
            if (questLogListener != null) {
                questLogListener.accept(parseQuestLog(line.substring(10)));
            }

        } else if (line.startsWith("QUEST_STAGE\t")) {
            if (questStageListener != null) {
                String[] parts = line.split("\t", 3);
                if (parts.length == 3) questStageListener.accept(new String[]{parts[1], parts[2]});
            }

        } else if (line.startsWith("QUEST_OBJECTIVE_COMPLETE\t")) {
            if (questObjectiveCompleteListener != null) {
                questObjectiveCompleteListener.accept(line.substring(25));
            }

        // ---- Inventory messages ----
        } else if (line.startsWith("INVENTORY_UPDATE")) {
            String payload = line.length() > 16 ? line.substring(17) : "";
            int[][] slots = parseInventorySlots(payload);
            if (inventorySlotListener != null) inventorySlotListener.accept(slots);

        } else if (line.startsWith("STOP_ACTION")) {
            if (stopActionListener != null) stopActionListener.run();

        } else if (line.startsWith("INVENTORY")) {
            String payload = line.length() > 9 ? line.substring(10) : "";
            Map<Integer, Integer> inv = parseInventory(payload);
            if (inventoryListener != null) inventoryListener.accept(inv);

        // ---- Equipment messages ----
        } else if (line.startsWith("EQUIPMENT")) {
            String payload = line.length() > 9 ? line.substring(10) : "";
            Map<String, Integer> eq = parseEquipment(payload);
            if (equipmentListener != null) equipmentListener.accept(eq);
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

    /**
     * Parse an {@code INVENTORY_UPDATE} payload.
     * Format: {@code slot:itemId:amount ...} (28 space-separated entries).
     *
     * @return 28-element array of {@code [itemId, amount]}; empty slots have itemId=-1.
     */
    private int[][] parseInventorySlots(String payload) {
        int[][] result = new int[28][2];
        for (int[] entry : result) { entry[0] = -1; entry[1] = 0; }
        if (payload == null || payload.isEmpty()) return result;
        for (String entry : payload.split(" ")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            String[] parts = entry.split(":");
            if (parts.length != 3) continue;
            try {
                int idx    = Integer.parseInt(parts[0]);
                int itemId = Integer.parseInt(parts[1]);
                int amount = Integer.parseInt(parts[2]);
                if (idx >= 0 && idx < 28) {
                    result[idx][0] = itemId;
                    result[idx][1] = amount;
                }
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    private Map<Integer, Integer> parseInventory(String payload) {
        Map<Integer, Integer> map = new HashMap<>();
        if (payload == null || payload.isEmpty()) return map;

        for (String entry : payload.split(";")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            String[] parts = entry.split(":");
            if (parts.length != 2) continue;
            try {
                int itemId = Integer.parseInt(parts[0].trim());
                int amount = Integer.parseInt(parts[1].trim());
                map.put(itemId, amount);
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

    /**
     * Parse a QUEST_LOG payload.
     *
     * New format (7 fields): {@code questId:name:state:stageId:progressAmount:requiredAmount:stageDesc|...}
     * Old format (5 fields): {@code questId:name:state:stageId:stageDesc|...}
     * Legacy format (3 fields): {@code questId:name:state|...}
     *
     * Unknown quest IDs or states are skipped safely.
     * stageDesc is the last field and may contain ':'.
     */
    private List<QuestEntry> parseQuestLog(String payload) {
        List<QuestEntry> result = new ArrayList<>();
        if (payload == null || payload.isEmpty()) return result;
        for (String entry : payload.split("\\|")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            // Split with limit=7 so stageDesc (last field) can contain ':'
            String[] parts = entry.split(":", 7);
            if (parts.length < 3) continue;
            try {
                int questId = Integer.parseInt(parts[0].trim());
                String name = parts[1].trim();
                QuestState state;
                try {
                    state = QuestState.valueOf(parts[2].trim());
                } catch (IllegalArgumentException ignored) {
                    state = QuestState.NOT_STARTED;
                }
                int    stageId  = parts.length >= 4 ? parseInt(parts[3].trim(), 0) : 0;
                int    progress = 0;
                int    required = 0;
                String desc     = "";
                if (parts.length >= 7) {
                    // New 7-field format
                    progress = parseInt(parts[4].trim(), 0);
                    required = parseInt(parts[5].trim(), 0);
                    desc     = parts[6].trim();
                } else if (parts.length == 5) {
                    // Old 5-field format — no progress fields
                    desc = parts[4].trim();
                }
                result.add(new QuestEntry(questId, name, state, stageId, desc, progress, required));
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return fallback; }
    }

    private DialogueData parseDialogue(String line) {
        String[] parts = line.split("\t", 3);
        if (parts.length != 3) return null;

        String npcId = parts[1];
        String text  = parts[2];
        return new DialogueData(npcId, text);
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

    public void sendCombatStyle(String style) {
        if (!connected || out == null) return;
        out.println("COMBAT_STYLE " + style);
    }

    public void sendPickup(String lootId) {
        if (!connected || out == null) return;
        out.println("PICKUP_ITEM " + lootId);
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

    public void sendBuy(int itemId) {
        if (!connected || out == null) return;
        out.println("BUY " + itemId);
    }

    public void sendSell(int itemId) {
        if (!connected || out == null) return;
        out.println("SELL " + itemId);
    }

    public void sendShopClose() {
        if (!connected || out == null) return;
        out.println("SHOP_CLOSE");
    }

    public void sendEquip(int itemId) {
        if (!connected || out == null) return;
        out.println("EQUIP " + itemId);
    }

    public void sendUse(int itemId) {
        if (!connected || out == null) return;
        out.println("USE " + itemId);
    }

    public void sendDrop(int itemId) {
        if (!connected || out == null) return;
        out.println("DROP " + itemId);
    }

    public void sendUnequip(String slot) {
        if (!connected || out == null) return;
        out.println("UNEQUIP " + slot);
    }

    public void sendDialogueNext() {
        if (!connected || out == null) return;
        out.println("DIALOGUE_NEXT");
    }

    public void sendDialogueOption(int index) {
        if (!connected || out == null) return;
        out.println("DIALOGUE_OPTION\t" + index);
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
                String id     = parts[0];
                int    x      = Integer.parseInt(parts[1]);
                int    y      = Integer.parseInt(parts[2]);
                int    itemId = Integer.parseInt(parts[3]);
                int    count  = Integer.parseInt(parts[4]);
                result.put(id, new LootData(id, x, y, itemId, count));
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    /**
     * Parse a {@code LOOT_ADD} payload (everything after "LOOT_ADD ").
     * Format: {@code id x y itemId count}
     */
    private LootData parseLootAdd(String payload) {
        String[] parts = payload.split(" ");
        if (parts.length != 5) return null;
        try {
            String id     = parts[0];
            int    x      = Integer.parseInt(parts[1]);
            int    y      = Integer.parseInt(parts[2]);
            int    itemId = Integer.parseInt(parts[3]);
            int    count  = Integer.parseInt(parts[4]);
            return new LootData(id, x, y, itemId, count);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Parse an {@code EQUIPMENT} payload.
     * Format: {@code SLOT:itemId;SLOT:itemId;} — trailing semi is fine.
     */
    private Map<String, Integer> parseEquipment(String payload) {
        Map<String, Integer> map = new LinkedHashMap<>();
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

    public static final class DialogueData {
        public final String npcId;
        public final String line;

        public DialogueData(String npcId, String line) {
            this.npcId = npcId;
            this.line  = line;
        }
    }

}
