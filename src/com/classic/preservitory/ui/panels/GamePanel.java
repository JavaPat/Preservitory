package com.classic.preservitory.ui.panels;

import com.classic.preservitory.client.world.ClientWorld;
import com.classic.preservitory.entity.Animation;
import com.classic.preservitory.entity.Enemy;
import com.classic.preservitory.entity.Entity;
import com.classic.preservitory.entity.NPC;
import com.classic.preservitory.entity.Player;
import com.classic.preservitory.entity.RemotePlayer;
import com.classic.preservitory.network.ClientConnection;
import com.classic.preservitory.game.GameLoop;
import com.classic.preservitory.input.MouseHandler;
import com.classic.preservitory.system.CombatSystem;
import com.classic.preservitory.system.DialogueSystem;
import com.classic.preservitory.system.MiningSystem;
import com.classic.preservitory.system.MovementSystem;
import com.classic.preservitory.system.Pathfinding;
import com.classic.preservitory.system.SoundSystem;
import com.classic.preservitory.system.WoodcuttingSystem;
import com.classic.preservitory.ui.framework.assets.AssetManager;
import com.classic.preservitory.ui.overlays.ChatBox;
import com.classic.preservitory.ui.overlays.FloatingText;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;
import com.classic.preservitory.world.World;
import com.classic.preservitory.world.objects.Loot;
import com.classic.preservitory.world.objects.Rock;
import com.classic.preservitory.world.objects.Tree;

import javax.swing.JPanel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Main game surface — central orchestrator for all systems.
 *
 * === Layout ===
 *   Left 566 px  → scrollable game viewport (camera follows player)
 *   Right 234 px → fixed side panel: stats, skills, inventory, info
 *
 * === Game states ===
 *   PLAYING     — normal gameplay
 *   IN_DIALOGUE — NPC dialogue visible; movement blocked; click advances text
 *
 * === Click priority (PLAYING, viewport only) ===
 *   0. Panel click (cx ≥ PANEL_X)  → ignored
 *   1. NPC    → walk to it (A*), open dialogue
 *   2. Enemy  → walk to it (A*), start combat
 *   3. Tree   → walk to it (A*), start woodcutting
 *   4. Rock   → walk to it (A*), start mining
 *   5. Ground → pathfind there; direct movement fallback if no path
 *
 * === Keyboard shortcuts ===
 *   Enter — chat         D — toggle debug
 *   M — toggle sound     Escape — close UI
 *
 * === Camera ===
 *   {@code cameraOffsetX/Y} track the top-left world-space pixel that maps to
 *   screen (0,0).  They are clamped so the camera never shows outside the world.
 *   All world-space rendering is done inside a {@code g2.translate(-offset)}
 *   block bounded by a clip rect covering only the viewport.
 *   Screen-space UI (side panel, overlays) is drawn after the transform is
 *   restored, so it is never affected by the camera.
 */
public class GamePanel extends JPanel {

    // -----------------------------------------------------------------------
    //  Game state
    // -----------------------------------------------------------------------

    private enum GameState { PLAYING, IN_DIALOGUE }
    private GameState gameState = GameState.PLAYING;

    // -----------------------------------------------------------------------
    //  Chat box — height of the overlay at the bottom of the game viewport
    // -----------------------------------------------------------------------

    private static final int CHAT_H = 88;

    // -----------------------------------------------------------------------
    //  Game objects
    // -----------------------------------------------------------------------

    private final World             world;
    private final Player            player;
    private final MouseHandler      mouseHandler;
    private final MovementSystem    movementSystem;
    private final WoodcuttingSystem woodcuttingSystem;
    private final MiningSystem      miningSystem;
    private final CombatSystem      combatSystem;
    private final DialogueSystem    dialogueSystem;
    private final SoundSystem       soundSystem;
    private final GameLoop          gameLoop;

    // -----------------------------------------------------------------------
    //  UI components
    // -----------------------------------------------------------------------

    /** Right panel — tab system (Inventory / Skills). */
    private final RightPanel rightPanel = new RightPanel();

    /** Chat box — persistent message log at the bottom of the viewport. */
    private final ChatBox chatBox = new ChatBox();

    // -----------------------------------------------------------------------
    //  Multiplayer networking
    // -----------------------------------------------------------------------

    /** Manages the TCP connection to the game server. */
    private final ClientConnection clientConnection = new ClientConnection();

    /** ✅ NEW: holds server-side synced world data */
    private final ClientWorld clientWorld = new ClientWorld();

    /**
     * Live remote players keyed by their server-assigned ID.
     * Updated each frame from {@link ClientConnection#getRemotePlayers()}.
     * Only accessed on the Swing EDT / game-loop thread.
     */
    private final Map<String, RemotePlayer> remotePlayers = new HashMap<>();

    // Active interaction targets (at most one per category at a time)
    private Tree  activeTree;
    private Rock  activeRock;
    private Enemy activeEnemy;
    private NPC   activeNPC;
    private Loot  activeLoot;

    // -----------------------------------------------------------------------
    //  Camera
    // -----------------------------------------------------------------------

    /** World-space x of the top-left corner currently shown in the viewport. */
    private int cameraOffsetX = 0;

    /** World-space y of the top-left corner currently shown in the viewport. */
    private int cameraOffsetY = 0;

    // -----------------------------------------------------------------------
    //  Feedback / HUD state
    // -----------------------------------------------------------------------

    private String actionMessage = "";
    private double messageTimer  = 0;

    /** All live floating-text labels (damage, XP, loot). */
    private final List<FloatingText> floatingTexts = new ArrayList<>();
    private boolean deathHandled;
    private boolean shopOpen;
    private boolean shopOpenAfterDialogue;
    private final LinkedHashMap<String, Integer> shopStockPrices = new LinkedHashMap<>();
    private final LinkedHashMap<String, Integer> shopSellPrices = new LinkedHashMap<>();

    // FPS tracking
    private int  fpsCounter;
    private int  displayedFps;
    private long fpsTimer;

    // Hover highlight (screen-space coordinates updated by mouse motion)
    private int hoverX = -1;
    private int hoverY = -1;

    // Debug overlay
    private boolean debugMode = false;

    // -----------------------------------------------------------------------
    //  Chat input state
    // -----------------------------------------------------------------------

    /** True while the player is composing a chat message (ENTER to toggle). */
    private boolean isTypingChat = false;

    /** Accumulates characters typed by the player until ENTER is pressed. */
    private final StringBuilder chatInput = new StringBuilder();
    private boolean authRequired = true;
    private String currentAccountName = "";
    private com.classic.preservitory.ui.screens.LoginScreen loginScreen;
    private java.util.function.Consumer<String> loginSuccessListener;
    private Runnable disconnectListener;

    public void setLoginSuccessListener(java.util.function.Consumer<String> listener) {
        this.loginSuccessListener = listener;
    }

    public void setDisconnectListener(Runnable listener) {
        this.disconnectListener = listener;
    }

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public GamePanel() {

        AssetManager.load();

        loginScreen = new com.classic.preservitory.ui.screens.LoginScreen(
                Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT,
                (u, p) -> {
                    if (!clientConnection.isConnected()) { loginScreen.setStatus("Server offline."); return; }
                    loginScreen.setStatus("Logging in...");
                    clientConnection.sendLogin(u, p);
                },
                (u, p) -> {
                    if (!clientConnection.isConnected()) { loginScreen.setStatus("Server offline."); return; }
                    loginScreen.setStatus("Registering...");
                    clientConnection.sendRegister(u, p);
                }
        );

        setPreferredSize(new Dimension(Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);

        world  = new World();
        player = new Player(
                12 * Constants.TILE_SIZE,
                9  * Constants.TILE_SIZE
        );

        mouseHandler      = new MouseHandler();
        movementSystem    = new MovementSystem();
        woodcuttingSystem = new WoodcuttingSystem();
        miningSystem      = new MiningSystem();
        combatSystem      = new CombatSystem();
        dialogueSystem    = new DialogueSystem();
        soundSystem       = new SoundSystem();
        gameLoop          = new GameLoop(this);

        clientConnection.setListener(new ClientConnection.Listener() {

            @Override
            public void onConnected(String assignedId) {
                player.setId(assignedId);
                deathHandled  = false;
                authRequired  = true;
                currentAccountName = "";
                loginScreen.reset();
                chatBox.post("Connected. Please log in.", ChatBox.COLOR_SYSTEM);
            }

            @Override
            public void onDisconnected() {
                stopAllActivities();
                dialogueSystem.close();
                gameState = GameState.PLAYING;

                shopOpen = false;
                shopOpenAfterDialogue = false;
                shopStockPrices.clear();
                shopSellPrices.clear();

                authRequired = true;
                currentAccountName = "";
                loginScreen.setStatus("Server offline. Reconnecting...");

                chatBox.post("Disconnected from server. Reconnecting...", ChatBox.COLOR_SYSTEM);
                showMessage("Server offline.");
                if (disconnectListener != null) disconnectListener.run();
            }

            @Override
            public void onChat(String username, String role, String message) {

                String prefix = "";

                switch (role) {
                    case "MODERATOR": prefix = "[MOD] "; break;
                    case "ADMIN": prefix = "[ADMIN] "; break;
                    case "OWNER": prefix = "[OWNER] "; break;
                    case "DEVELOPER": prefix = "[DEV] "; break;
                }

                chatBox.post(prefix + username + ": " + message, ChatBox.COLOR_CHAT);
            }
        });

        clientConnection.connect();

        clientConnection.setTreeUpdateListener(clientWorld::updateTrees);
        clientConnection.setTreeRemoveListener(clientWorld::chopTree);
        clientConnection.setTreeAddListener(clientWorld::addTree);

        clientConnection.setRockUpdateListener(clientWorld::updateRocks);
        clientConnection.setRockRemoveListener(clientWorld::mineRock);
        clientConnection.setRockAddListener(clientWorld::addRock);

        clientConnection.setNpcUpdateListener(clientWorld::updateNpcs);
        clientConnection.setEnemyUpdateListener(clientWorld::updateEnemies);
        clientWorld.setDamageListener(event ->
                spawnDamage(event.x, event.y, event.amount, false));
        clientConnection.setDamageListener(damage ->
                clientWorld.handleDamage(damage[0], damage[1], damage[2]));

        clientConnection.setPlayerHpListener(hpState -> {
            int oldHp = player.getHp();
            int newHp = hpState[0];
            if (hpState.length >= 2 && hpState[1] > 0) {
                player.setMaxHp(hpState[1]);
            }
            player.setHp(newHp);
            if (newHp > 0) deathHandled = false;
            int delta = oldHp - newHp;
            if (delta > 0) spawnDamage(player.getCenterX(), player.getY() - 4, delta, true);
            if (player.isDead()) handlePlayerDeath();
        });

        clientConnection.setLootUpdateListener(clientWorld::updateLoot);
        clientConnection.setLootAddListener(clientWorld::addLoot);
        clientConnection.setLootRemoveListener(clientWorld::removeLoot);
        clientConnection.setInventoryListener(inv -> player.applyInventoryUpdate(inv));
        clientConnection.setSkillSnapshotListener(player::applySkillSnapshot);

        clientConnection.setSkillXpListener(parts -> {
            try {
                String skillName = parts[0].toLowerCase();
                int xp = Integer.parseInt(parts[1]);
                player.getSkillSystem().addXp(skillName, xp);
            } catch (NumberFormatException ignored) {}
        });
        clientConnection.setDialogueListener(dialogue -> {
            NPC npc = clientWorld.getNpc(dialogue.npcId);
            if (npc == null) npc = activeNPC;
            if (npc == null) return;

            dialogueSystem.open(npc, dialogue.lines);
            gameState = GameState.IN_DIALOGUE;
            shopOpenAfterDialogue = dialogue.openShop;
        });
        clientConnection.setShopListener(shop -> {
            shopStockPrices.clear();
            shopStockPrices.putAll(shop.stockPrices);
            shopSellPrices.clear();
            shopSellPrices.putAll(shop.sellPrices);
        });
        clientConnection.setAuthSuccessListener(username -> {
            authRequired  = false;
            currentAccountName = username;
            isTypingChat  = false;
            chatInput.setLength(0);
            loginScreen.reset();
            chatBox.post("Logged in as " + username + ".", ChatBox.COLOR_SYSTEM);
            showMessage("Authenticated as " + username + ".");
            if (loginSuccessListener != null) loginSuccessListener.accept(username);
        });
        clientConnection.setAuthFailureListener(message -> {
            authRequired = true;
            loginScreen.setStatus(message);
            chatBox.post(message, ChatBox.COLOR_SYSTEM);
            showMessage(message);
        });

        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                handleClick(e.getX(), e.getY());
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                hoverX = e.getX();
                hoverY = e.getY();
                if (authRequired) { loginScreen.handleMouseMove(e.getX(), e.getY()); return; }
                rightPanel.handleMouseMove(e.getX(), e.getY());
                updateCursorForHover(e.getX(), e.getY());
            }

            @Override public void mouseDragged(MouseEvent e) {
                hoverX = e.getX();
                hoverY = e.getY();
                rightPanel.handleMouseMove(e.getX(), e.getY());
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                handleKey(e.getKeyCode());
            }

            @Override public void keyTyped(KeyEvent e) {
                handleCharTyped(e.getKeyChar());
            }
        });
    }
    // -----------------------------------------------------------------------
    //  Input — keyboard
    // -----------------------------------------------------------------------

    private void handleKey(int keyCode) {
        if (authRequired) {
            loginScreen.handleKey(keyCode);
            return;
        }

        // ----------------------------------------------------------------
        //  Chat-input mode — intercept all keys so game shortcuts don't
        //  fire while the player is mid-sentence.
        // ----------------------------------------------------------------
        if (isTypingChat) {
            switch (keyCode) {
                case KeyEvent.VK_ENTER:
                    sendChatInput();      // send and exit typing mode
                    break;
                case KeyEvent.VK_ESCAPE:
                    isTypingChat = false; // cancel without sending
                    chatInput.setLength(0);
                    break;
                case KeyEvent.VK_BACK_SPACE:
                    if (chatInput.length() > 0)
                        chatInput.deleteCharAt(chatInput.length() - 1);
                    break;
                default:
                    break;               // printable chars handled by keyTyped
            }
            return;   // block all other game shortcuts while typing
        }

        // ----------------------------------------------------------------
        //  Normal mode
        // ----------------------------------------------------------------
        switch (keyCode) {
            case KeyEvent.VK_ENTER:
                // ENTER outside chat mode → start composing a message
                if (gameState == GameState.PLAYING) {
                    isTypingChat = true;
                    chatInput.setLength(0);
                    stopAllActivities();   // stop movement so the player doesn't
                                           // wander off while typing
                }
                break;

            case KeyEvent.VK_D:
                debugMode = !debugMode;
                break;

            case KeyEvent.VK_M:
                soundSystem.setEnabled(!soundSystem.isEnabled());
                showMessage("Sound " + (soundSystem.isEnabled() ? "ON" : "OFF"));
                break;

            case KeyEvent.VK_ESCAPE:
                if (shopOpen) {
                    shopOpen = false;
                    shopOpenAfterDialogue = false;
                    clientConnection.sendShopClose();
                    showMessage("Shop closed.");
                    break;
                }
                if (gameState == GameState.IN_DIALOGUE) {
                    dialogueSystem.close();
                    gameState = GameState.PLAYING;
                }
                break;
        }
    }

    /**
     * Append a printable ASCII character to the chat input buffer.
     * Called by the {@code keyTyped} listener; ENTER and BACKSPACE are
     * handled separately in {@link #handleKey(int)}.
     */
    private void handleCharTyped(char c) {
        if (authRequired) {
            loginScreen.handleChar(c);
            return;
        }
        if (!isTypingChat) return;
        // Accept printable ASCII only; ignore control chars (Enter=\n, BS=\b…)
        if (c < 32 || c > 126) return;
        if (chatInput.length() < 80) chatInput.append(c);   // 80-char cap
    }

    /**
     * Send the current chat input to the server and post it locally.
     * Clears the input buffer and exits typing mode regardless of result.
     */
    private void sendChatInput() {
        String raw = chatInput.toString();
        isTypingChat = false;
        chatInput.setLength(0);

        if (raw.startsWith("/")) {
            clientConnection.sendChatMessage(raw);
            return;
        }

        // Run the message through the filter before doing anything with it
        String msg = com.classic.preservitory.util.ChatFilter.filter(raw);
        if (msg == null) {
            // Nothing valid remained — give brief feedback and stop
            chatBox.post("Invalid message.", ChatBox.COLOR_SYSTEM);
            return;
        }

        if (!clientConnection.isConnected()) {
            chatBox.post("Server offline. Message not sent.", ChatBox.COLOR_SYSTEM);
            return;
        }

        // Send cleaned text to server — server broadcasts to all other clients
        clientConnection.sendChatMessage(msg);
    }

    // -----------------------------------------------------------------------
    //  Input — mouse clicks
    // -----------------------------------------------------------------------

    private void handleClick(int cx, int cy) {
        if (authRequired) {
            loginScreen.handleClick(cx, cy);
            return;
        }
        // Ignore world clicks while the player is composing a chat message
        // (right-panel clicks are still intentional and allowed through)
        if (isTypingChat && cx < Constants.PANEL_X) return;

        // Dialogue: any click in the viewport advances or closes the conversation
        if (gameState == GameState.IN_DIALOGUE) {
            handleDialogueClick();
            return;
        }

        // --- PLAYING state ---

        // Route right-panel clicks (tab switching, inventory hover, etc.)
        if (cx >= Constants.PANEL_X) {
            if (shopOpen) {
                String buyItem = rightPanel.getClickedShopItem(cx, cy, shopStockPrices);
                if (buyItem != null) {
                    clientConnection.sendBuy(buyItem);
                    showMessage("Buy request sent: " + buyItem);
                    return;
                }

                String sellItem = rightPanel.getClickedInventoryItem(cx, cy, player);
                if (sellItem != null && shopSellPrices.containsKey(sellItem)) {
                    clientConnection.sendSell(sellItem);
                    showMessage("Sell request sent: " + sellItem);
                    return;
                }
            }
            rightPanel.handleClick(cx, cy);
            return;
        }

        if (!clientConnection.isConnected()) {
            showMessage("Server offline. Start the server to play.");
            return;
        }

        // Convert screen-space viewport click → iso space → tile → world pixel
        int isoClickX   = cx + cameraOffsetX;
        int isoClickY   = cy + cameraOffsetY;
        int clickTileCol = Math.max(0, Math.min(world.getCols() - 1,
                IsoUtils.isoToTileCol(isoClickX, isoClickY)));
        int clickTileRow = Math.max(0, Math.min(world.getRows() - 1,
                IsoUtils.isoToTileRow(isoClickX, isoClickY)));
        // Centre of the clicked tile in world pixel space (used by containsPoint checks)
        int worldX = clickTileCol * Constants.TILE_SIZE + Constants.TILE_SIZE / 2;
        int worldY = clickTileRow * Constants.TILE_SIZE + Constants.TILE_SIZE / 2;

        // 0. NPC? (highest priority — before enemies so clicking the guide doesn't fight)
        NPC clickedNPC = clientWorld.getNpcAt(worldX, worldY);
        if (clickedNPC != null) {
            stopAllActivities();
            activeNPC = clickedNPC;
            setApproachTarget(clickedNPC);
            return;
        }

        // 1. Enemy?
        Enemy clickedEnemy = clientWorld.getEnemyAt(worldX, worldY);
        if (clickedEnemy != null) {
            stopAllActivities();
            activeEnemy = clickedEnemy;
            setApproachTarget(clickedEnemy);
            return;
        }

        // 2. Tree?
        Tree clickedTree = clientWorld.getTreeAt(worldX, worldY);
        if (clickedTree != null) {
            stopAllActivities();
            activeTree = clickedTree;
            setApproachTarget(clickedTree);
            return;
        }

        // 3. Rock?
        Rock clickedRock = clientWorld.getRockAt(worldX, worldY);
        if (clickedRock != null) {
            stopAllActivities();
            activeRock = clickedRock;
            setApproachTarget(clickedRock);
            return;
        }

        // 4. Loot?
        Loot clickedLoot = clientWorld.getLootAt(worldX, worldY);
        if (clickedLoot != null) {
            stopAllActivities();
            activeLoot = clickedLoot;
            setApproachTarget(clickedLoot);
            return;
        }

        // 5. Ground — pathfind to the clicked tile
        stopAllActivities();
        // clickTileCol/Row already computed from iso click above
        int goalCol  = clickTileCol;
        int goalRow  = clickTileRow;
        int startCol = Pathfinding.pixelToTileCol(player.getCenterX());
        int startRow = Pathfinding.pixelToTileRow(player.getCenterY());

        List<Point> path = Pathfinding.findPath(startCol, startRow, goalCol, goalRow, world,
                clientWorld::isBlocked);
        if (!path.isEmpty()) {
            movementSystem.setPath(path);
        } else {
            mouseHandler.setTarget(worldX, worldY);   // fallback: straight line (world coords)
        }
    }

    /**
     * Use A* to find a path to the tile adjacent to {@code target} (in the
     * player's current direction).  Falls back to direct movement if no path
     * is found.
     */
    private void setApproachTarget(Entity target) {
        double dx   = player.getCenterX() - target.getCenterX();
        double dy   = player.getCenterY() - target.getCenterY();
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 1) dist = 1;

        // Tile of the target
        int targetCol = Pathfinding.pixelToTileCol(target.getCenterX());
        int targetRow = Pathfinding.pixelToTileRow(target.getCenterY());

        // Adjacent tile in the player's direction (rounded to nearest axis/diagonal)
        int offCol = (int) Math.round(dx / dist);
        int offRow = (int) Math.round(dy / dist);
        int goalCol = Math.max(0, Math.min(world.getCols() - 1, targetCol + offCol));
        int goalRow = Math.max(0, Math.min(world.getRows() - 1, targetRow + offRow));

        int startCol = Pathfinding.pixelToTileCol(player.getCenterX());
        int startRow = Pathfinding.pixelToTileRow(player.getCenterY());

        List<Point> path = Pathfinding.findPath(startCol, startRow, goalCol, goalRow, world,
                clientWorld::isBlocked);
        if (!path.isEmpty()) {
            movementSystem.setPath(path);
            mouseHandler.clearTarget();
        } else {
            // Fallback: straight-line approach using world-space pixel coordinates
            int approachX = (int)(target.getCenterX() + (dx / dist) * Constants.TILE_SIZE * 1.5);
            int approachY = (int)(target.getCenterY() + (dy / dist) * Constants.TILE_SIZE * 1.5);
            mouseHandler.setTarget(approachX, approachY);
        }
    }

    /** Cancel every ongoing activity and clear all movement. */
    private void stopAllActivities() {
        woodcuttingSystem.stopChopping();
        miningSystem.stopMining();
        combatSystem.stopCombat();
        movementSystem.clearPath();
        mouseHandler.clearTarget();
        activeTree  = null;
        activeRock  = null;
        activeEnemy = null;
        activeNPC   = null;
        activeLoot  = null;
    }

    // -----------------------------------------------------------------------
    //  Dialogue & shop click handlers
    // -----------------------------------------------------------------------

    private void handleDialogueClick() {
        if (!dialogueSystem.isLastLine()) {
            dialogueSystem.advance();
            return;
        }

        dialogueSystem.close();
        gameState = GameState.PLAYING;
        if (shopOpenAfterDialogue) {
            shopOpen = true;
            shopOpenAfterDialogue = false;
            showMessage("Shop opened. Use SKILLS tab to buy, INVENTORY to sell.");
        }
    }

    // -----------------------------------------------------------------------
    //  Game loop
    // -----------------------------------------------------------------------

    public void startGameLoop() {
        fpsTimer = System.nanoTime();
        gameLoop.start();
    }

    /** Called once per frame by GameLoop. */
    public void update(double deltaTime) {
        if (!clientConnection.isConnected() || authRequired) {
            stopAllActivities();
            player.getAnimation().setState(Animation.State.IDLE);
        } else if (gameState == GameState.PLAYING) {
            movementSystem.update(player, mouseHandler, deltaTime);
            updateTreeInteraction(deltaTime);
            updateRockInteraction(deltaTime);
            updateCombat(deltaTime);
            updateLootInteraction();
            updateNPCInteraction();

            if (player.isDead()) handlePlayerDeath();

            // Sync animation state with active systems
            updateAnimation(deltaTime);
        }

        // Floating texts always animate (visible even in dialogue)
        Iterator<FloatingText> it = floatingTexts.iterator();
        while (it.hasNext()) {
            FloatingText ft = it.next();
            ft.tick(deltaTime);
            if (ft.isDone()) it.remove();
        }

        if (messageTimer > 0) messageTimer = Math.max(0, messageTimer - deltaTime);

        // ---- Multiplayer: send our position + sync remote players ----
        if (!authRequired) {
            clientConnection.sendPosition((int) player.getX(), (int) player.getY());
        }
        syncRemotePlayers(deltaTime);

        trackFps();
    }

    /**
     * Reconcile the {@link #remotePlayers} map with the latest snapshot from
     * {@link ClientConnection#getRemotePlayers()}.
     *
     *   - New IDs:     create a RemotePlayer at the reported position.
     *   - Known IDs:   update the lerp target; advance the lerp by dt.
     *   - Removed IDs: drop from the map (DISCONNECT already removed them from
     *                  the ClientConnection's ConcurrentHashMap).
     */
    private void syncRemotePlayers(double deltaTime) {
        Map<String, int[]> netSnapshot = clientConnection.getRemotePlayers();

        // Add or update
        for (Map.Entry<String, int[]> entry : netSnapshot.entrySet()) {
            String id  = entry.getKey();
            int[]  pos = entry.getValue();

            if (id.equals(player.getId())) {
                double dx = Math.abs(player.getX() - pos[0]);
                double dy = Math.abs(player.getY() - pos[1]);

                // Only snap on server-forced teleport/respawn (> 4 tiles).
                // Normal movement lag is < 1 tile — ignore it so the path isn't cleared.
                if (dx > Constants.TILE_SIZE * 4 || dy > Constants.TILE_SIZE * 4) {
                    player.setX(pos[0]);
                    player.setY(pos[1]);
                    movementSystem.clearPath();
                    mouseHandler.clearTarget();
                }

                continue;
            }

            RemotePlayer rp = remotePlayers.get(id);
            if (rp == null) {
                // First time we see this player: spawn at reported position
                rp = new RemotePlayer(id, pos[0], pos[1]);
                remotePlayers.put(id, rp);
            } else {
                rp.setTargetPosition(pos[0], pos[1]);
            }
            rp.update(deltaTime);
        }

        // Remove players that are no longer in the server snapshot
        remotePlayers.entrySet().removeIf(e -> !netSnapshot.containsKey(e.getKey()));
    }

    // -----------------------------------------------------------------------
    //  Camera
    // -----------------------------------------------------------------------

    /**
     * Recompute camera offsets so the viewport is centred on the player in
     * isometric screen space.
     *
     * {@code cameraOffsetX/Y} now represent the top-left corner of the
     * isometric-projected world that maps to viewport pixel (0, 0).
     * All world-space rendering is converted to iso screen coords via
     * {@link IsoUtils} before being drawn within the translate block.
     */
    private void updateCamera() {
        // Player centre in iso screen coords
        int playerIsoX = IsoUtils.worldToIsoX(player.getCenterX(), player.getCenterY());
        int playerIsoY = IsoUtils.worldToIsoY(player.getCenterX(), player.getCenterY());

        // Centre the viewport on the player's iso position
        cameraOffsetX = playerIsoX - Constants.VIEWPORT_W / 2;
        cameraOffsetY = playerIsoY - Constants.VIEWPORT_H / 2;
    }

    // -----------------------------------------------------------------------
    //  Animation state sync
    // -----------------------------------------------------------------------

    private void updateAnimation(double deltaTime) {
        Animation.State anim;
        if      (combatSystem.isInCombat())        anim = Animation.State.FIGHTING;
        else if (woodcuttingSystem.isChopping())   anim = Animation.State.CHOPPING;
        else if (miningSystem.isMining())          anim = Animation.State.MINING;
        else if (movementSystem.isMoving())        anim = Animation.State.WALKING;
        else                                       anim = Animation.State.IDLE;

        player.getAnimation().setState(anim);
        player.getAnimation().tick(deltaTime);
    }

    // -----------------------------------------------------------------------
    //  Interaction updates
    // -----------------------------------------------------------------------

    private void updateTreeInteraction(double deltaTime) {
        if (activeTree == null) return;

        if (!activeTree.isAlive()) {
            woodcuttingSystem.stopChopping();
            activeTree = null;
            return;
        }

        if (isWithinInteractionRange(player, activeTree)) {
            if (!woodcuttingSystem.isChopping()) {
                woodcuttingSystem.startChopping(activeTree);
                showMessage("You swing your axe...");
            }
            if (woodcuttingSystem.update(deltaTime)) sendChopRequest();
        }
    }

    private void sendChopRequest() {
        if (activeTree != null) {
            clientConnection.sendChop(activeTree.getId());
        }
        woodcuttingSystem.stopChopping();
        activeTree = null;
        soundSystem.play(SoundSystem.Sound.CHOP);
        showMessage("Chop request sent to server.");
    }

    private void updateRockInteraction(double deltaTime) {
        if (activeRock == null) return;

        if (!activeRock.isSolid()) {
            miningSystem.stopMining();
            activeRock = null;
            return;
        }

        if (isWithinInteractionRange(player, activeRock)) {
            if (!miningSystem.isMining()) {
                miningSystem.startMining(activeRock);
                showMessage("You swing your pickaxe...");
            }
            if (miningSystem.update(deltaTime)) sendMineRequest();
        }
    }

    private void sendMineRequest() {
        String rockId = (activeRock != null) ? activeRock.getId() : null;

        if (rockId != null) clientConnection.sendMine(rockId);
        miningSystem.stopMining();
        activeRock = null;
        soundSystem.play(SoundSystem.Sound.MINE);
        showMessage("Mine request sent to server.");
    }

    private void updateCombat(double deltaTime) {
        if (activeEnemy == null) return;

        if (!activeEnemy.isAlive()) {
            combatSystem.stopCombat();
            activeEnemy = null;
            return;
        }

        if (isWithinInteractionRange(player, activeEnemy)) {
            if (!combatSystem.isInCombat()) {
                combatSystem.startCombat(activeEnemy);
                showMessage("You attack the " + activeEnemy.getName() + "!");
            }
            CombatSystem.CombatResult result = combatSystem.update(player, deltaTime);
            if (result != null) applyCombatResult(result);
        }
    }

    private void applyCombatResult(CombatSystem.CombatResult result) {
        if (activeEnemy == null) return;

        clientConnection.sendAttack(activeEnemy.getId());

        soundSystem.play(SoundSystem.Sound.HIT);
        if (activeEnemy.isDead()) {
            combatSystem.stopCombat();
            activeEnemy = null;
        }
    }

    private void updateLootInteraction() {
        if (activeLoot == null) return;
        if (clientWorld.getLootAt((int) activeLoot.getCenterX(), (int) activeLoot.getCenterY()) == null) {
            activeLoot = null;
            return;
        }
        if (isWithinInteractionRange(player, activeLoot)) {
            clientConnection.sendPickup(activeLoot.getId());
            soundSystem.play(SoundSystem.Sound.ITEM_PICKUP);
            showMessage("Pickup request sent to server.");
            activeLoot = null;
        }
    }

    private void updateNPCInteraction() {
        if (activeNPC == null) return;
        if (distanceTo(player, activeNPC) <= Constants.TILE_SIZE * 1.6) {
            String npcId = activeNPC.getId();
            clientConnection.sendTalk(npcId);
            activeNPC = null;
        }
    }

    private void handlePlayerDeath() {
        if (deathHandled) return;
        deathHandled = true;
        stopAllActivities();
        showMessage("You have died. Waiting for server respawn/state update.");
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        updateCamera();

        // ---- World-space viewport (clipped + camera-translated) ----
        Shape         savedClip      = g2.getClip();
        AffineTransform savedTransform = g2.getTransform();

        g2.setClip(0, 0, Constants.VIEWPORT_W, Constants.VIEWPORT_H);
        g2.translate(-cameraOffsetX, -cameraOffsetY);

        // --- Tile layer (row-major for correct iso depth) ---
        world.render(g2);

        // --- Hover highlight under entities ---
        drawHoverHighlightWorld(g2);

        // --- Depth-sorted entities: rocks, trees, enemies, NPCs, remote players, player ---
        // Sort by world-space Y (bottom of bounding box) so entities closer to
        // the camera (higher Y) are rendered on top of those further away.
        List<Entity> depthSorted = new ArrayList<>();
        depthSorted.addAll(clientWorld.getRocks());
        depthSorted.addAll(clientWorld.getTrees());
        depthSorted.addAll(clientWorld.getEnemies());
        depthSorted.addAll(clientWorld.getNpcs());
        depthSorted.addAll(remotePlayers.values());   // other connected players
        depthSorted.add(player);
        depthSorted.sort(Comparator.comparingDouble(e -> e.getY() + e.getHeight()));
        for (Entity e : depthSorted) e.render(g2);

        // World-space overlays
        drawClickIndicator(g2);
        renderFloatingTexts(g2);

        // ---- Restore to screen space ----
        g2.setTransform(savedTransform);
        g2.setClip(savedClip);

        // Panel separator line (two-tone for depth)
        g2.setColor(new Color(55, 44, 22));
        g2.fillRect(Constants.PANEL_X - 2, 0, 2, Constants.SCREEN_HEIGHT);
        g2.setColor(new Color(25, 20, 10));
        g2.fillRect(Constants.PANEL_X - 1, 0, 1, Constants.SCREEN_HEIGHT);

        // Right panel — tab system with inventory / skills
        rightPanel.render(g2, player,
                shopOpen,
                shopStockPrices,
                shopSellPrices,
                activeTree != null,
                activeRock != null,
                activeEnemy != null,
                activeEnemy != null ? activeEnemy.getName() : null);

        // Chat box — message log (+ typing bar when composing)
        chatBox.render(g2, 0, Constants.VIEWPORT_H - CHAT_H,
                Constants.VIEWPORT_W, CHAT_H,
                isTypingChat ? chatInput.toString() : null);

        // Full-screen overlays (screen space, rendered over the viewport only)
        if (gameState == GameState.IN_DIALOGUE) drawDialogueBox(g2);
        if (authRequired) loginScreen.render(g2);
        // Messages, debug, FPS (screen space)
        drawActionMessage(g2);
        if (debugMode) drawDebugOverlay(g2);
        drawFps(g2);
    }

    // -----------------------------------------------------------------------
    //  Hover highlight (called in world-space transform)
    // -----------------------------------------------------------------------

    /**
     * Draws two overlapping highlights in isometric space:
     *   1. Subtle diamond cursor on the tile under the mouse.
     *   2. Yellow diamond glow around the interactable entity under the cursor.
     */
    private void drawHoverHighlightWorld(Graphics2D g) {
        if (hoverX < 0 || hoverX >= Constants.VIEWPORT_W
         || hoverY < 0 || hoverY >= Constants.VIEWPORT_H
         || gameState != GameState.PLAYING) return;

        // Convert screen hover → iso space → tile
        int isoHX    = hoverX + cameraOffsetX;
        int isoHY    = hoverY + cameraOffsetY;
        int tileCol  = IsoUtils.isoToTileCol(isoHX, isoHY);
        int tileRow  = IsoUtils.isoToTileRow(isoHX, isoHY);

        if (tileCol < 0 || tileCol >= world.getCols() ||
            tileRow < 0 || tileRow >= world.getRows()) return;

        // Draw diamond highlight for the hovered tile
        int hw = IsoUtils.ISO_TILE_W / 2;
        int hh = IsoUtils.ISO_TILE_H / 2;
        int tx = IsoUtils.tileToIsoX(tileCol, tileRow);
        int ty = IsoUtils.tileToIsoY(tileCol, tileRow);
        int[] txPts = { tx + hw, tx + IsoUtils.ISO_TILE_W, tx + hw, tx };
        int[] tyPts = { ty,      ty + hh,                   ty + IsoUtils.ISO_TILE_H, ty + hh };
        g.setColor(new Color(255, 255, 255, 50));
        g.fillPolygon(txPts, tyPts, 4);

        // Entity highlight — world centre of the hovered tile
        int worldX = tileCol * Constants.TILE_SIZE + Constants.TILE_SIZE / 2;
        int worldY = tileRow * Constants.TILE_SIZE + Constants.TILE_SIZE / 2;
        Entity hovered = clientWorld.getNpcAt(worldX, worldY);
        if (hovered == null) hovered = clientWorld.getEnemyAt(worldX, worldY);
        if (hovered == null) hovered = clientWorld.getTreeAt(worldX, worldY);
        if (hovered == null) hovered = clientWorld.getRockAt(worldX, worldY);
        if (hovered == null) return;

        // Yellow diamond glow around the entity's tile
        int ex = IsoUtils.worldToIsoX(hovered.getX(), hovered.getY());
        int ey = IsoUtils.worldToIsoY(hovered.getX(), hovered.getY());
        int[] exPts = { ex + hw,     ex + IsoUtils.ISO_TILE_W, ex + hw,     ex     };
        int[] eyPts = { ey - 2,      ey + hh,                   ey + IsoUtils.ISO_TILE_H + 2, ey + hh };
        g.setColor(new Color(255, 255, 100, 55));
        g.fillPolygon(exPts, eyPts, 4);
        g.setColor(new Color(255, 255, 100, 200));
        g.drawPolygon(exPts, eyPts, 4);
    }

    // -----------------------------------------------------------------------
    //  Cursor states — called from the mouse-motion listener
    // -----------------------------------------------------------------------

    /**
     * Set the Java cursor based on what the mouse is currently hovering over.
     *
     *   CROSSHAIR  — hovering an alive enemy  (attack intent)
     *   HAND       — hovering a tree, rock, or NPC  (skill/dialogue intent)
     *   DEFAULT    — empty ground or right-side panel
     */
    private void updateCursorForHover(int mx, int my) {
        if (mx >= Constants.PANEL_X || gameState != GameState.PLAYING) {
            setCursor(Cursor.getDefaultCursor());
            return;
        }
        // Convert screen → iso → world tile
        int isoX    = mx + cameraOffsetX;
        int isoY    = my + cameraOffsetY;
        int col     = Math.max(0, Math.min(world.getCols() - 1, IsoUtils.isoToTileCol(isoX, isoY)));
        int row     = Math.max(0, Math.min(world.getRows() - 1, IsoUtils.isoToTileRow(isoX, isoY)));
        int worldX  = col * Constants.TILE_SIZE + Constants.TILE_SIZE / 2;
        int worldY  = row * Constants.TILE_SIZE + Constants.TILE_SIZE / 2;

        if (clientWorld.getEnemyAt(worldX, worldY) != null) {
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        } else if (clientWorld.getTreeAt(worldX, worldY) != null
                || clientWorld.getRockAt(worldX, worldY) != null
                || clientWorld.getNpcAt(worldX, worldY)  != null) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    // -----------------------------------------------------------------------
    //  World-space overlays (called inside camera-translated block)
    // -----------------------------------------------------------------------

    /**
     * Cross at the movement destination.
     * The target is stored in world-space pixel coords; convert to iso for drawing.
     */
    private void drawClickIndicator(Graphics2D g) {
        if (!mouseHandler.hasTarget()) return;
        // Convert world target → iso screen position (centre of that tile)
        int cx = IsoUtils.worldToIsoX(mouseHandler.getTargetX(), mouseHandler.getTargetY())
                 + IsoUtils.ISO_TILE_W / 2;
        int cy = IsoUtils.worldToIsoY(mouseHandler.getTargetX(), mouseHandler.getTargetY())
                 + IsoUtils.ISO_TILE_H / 2;
        int r  = 6;
        g.setColor(new Color(255, 220, 0, 200));
        g.drawLine(cx - r, cy - r, cx + r, cy + r);
        g.drawLine(cx + r, cy - r, cx - r, cy + r);
    }

    private Color iconColorFor(String name) {
        if ("Logs".equals(name))   return new Color(139,  90,  43);
        if ("Ore".equals(name))    return new Color(160,  88,  65);
        if ("Coins".equals(name))  return new Color(240, 200,  40);
        if ("Stone".equals(name))  return new Color(130, 130, 130);
        if ("Candle".equals(name)) return new Color(240, 230, 120);
        if ("Rope".equals(name))   return new Color(160, 130,  80);
        if ("Gem".equals(name))    return new Color( 80, 180, 220);
        return new Color(180, 180, 60);
    }

    /** Dialogue box anchored to the bottom of the viewport. */
    private void drawDialogueBox(Graphics2D g) {
        NPC npc = dialogueSystem.getNPC();
        if (npc == null) return;

        // Dim the game viewport
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRect(0, 0, Constants.VIEWPORT_W, Constants.SCREEN_HEIGHT);

        int bx = 30;
        int by = Constants.VIEWPORT_H - 155;
        int bw = Constants.VIEWPORT_W - 60;
        int bh = 135;

        g.setColor(new Color(10, 20, 30, 220));
        g.fillRoundRect(bx, by, bw, bh, 10, 10);
        g.setColor(new Color(100, 180, 220, 200));
        g.drawRoundRect(bx, by, bw, bh, 10, 10);

        g.setFont(new Font("Monospaced", Font.BOLD, 14));
        g.setColor(new Color(100, 220, 255));
        g.drawString(npc.getName(), bx + 12, by + 22);

        g.setColor(new Color(100, 180, 220, 100));
        g.drawLine(bx + 12, by + 28, bx + bw - 12, by + 28);

        g.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g.setColor(Color.WHITE);
        g.drawString(dialogueSystem.getCurrentLine(), bx + 12, by + 52);

        String prompt = dialogueSystem.isLastLine() ? "[Click to close]" : "[Click to continue]";
        g.setFont(new Font("Monospaced", Font.ITALIC, 11));
        g.setColor(new Color(170, 170, 170));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(prompt, bx + bw - fm.stringWidth(prompt) - 12, by + bh - 10);
    }

    /** Fading centred message near the top of the viewport. */
    private void drawActionMessage(Graphics2D g) {
        if (messageTimer <= 0 || actionMessage.isEmpty()) return;
        float alpha = (float) Math.min(1.0, messageTimer / 0.8);
        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(actionMessage);
        int tx = (Constants.VIEWPORT_W - tw) / 2;
        int ty = 38;
        g.setColor(new Color(0, 0, 0, (int)(alpha * 200)));
        g.drawString(actionMessage, tx + 1, ty + 1);
        g.setColor(new Color(1f, 1f, 0.75f, alpha));
        g.drawString(actionMessage, tx, ty);
    }

    private void drawFps(Graphics2D g) {
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g.setColor(new Color(180, 180, 180, 160));
        String label = currentAccountName.isEmpty()
                ? "FPS: " + displayedFps
                : "FPS: " + displayedFps + "  Account: " + currentAccountName;
        g.drawString(label, 8, 14);
    }


    // -----------------------------------------------------------------------
    //  Debug overlay
    // -----------------------------------------------------------------------

    /**
     * Developer overlay (toggle with D).
     * Shows player position, camera offset, tile, game state, animation state,
     * and draws A* path waypoints as yellow dots in screen space.
     */
    private void drawDebugOverlay(Graphics2D g) {
        int panelX = 8;
        int panelY = 20;
        int panelW = 310;
        int panelH = 148;

        g.setColor(new Color(0, 0, 0, 185));
        g.fillRoundRect(panelX, panelY, panelW, panelH, 6, 6);
        g.setColor(new Color(80, 200, 80, 200));
        g.drawRoundRect(panelX, panelY, panelW, panelH, 6, 6);

        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        g.setColor(new Color(100, 255, 100));
        g.drawString("DEBUG  (D=toggle)", panelX + 6, panelY + 13);

        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(Color.WHITE);

        int tx = panelX + 6;
        int ty = panelY + 26;
        int lineH = 13;

        int tileCol = Pathfinding.pixelToTileCol(player.getCenterX());
        int tileRow = Pathfinding.pixelToTileRow(player.getCenterY());

        g.drawString(String.format("Pixel: (%.0f, %.0f)", player.getX(), player.getY()), tx, ty); ty += lineH;
        g.drawString("Tile:  (" + tileCol + ", " + tileRow + ")",                         tx, ty); ty += lineH;
        g.drawString("Cam:   (" + cameraOffsetX + ", " + cameraOffsetY + ")",              tx, ty); ty += lineH;
        g.drawString("State: " + gameState.name(),                                         tx, ty); ty += lineH;
        g.drawString("Anim:  " + player.getAnimation().getState().name(),                  tx, ty); ty += lineH;
        g.drawString("FPS:   " + displayedFps,                                             tx, ty); ty += lineH;
        g.drawString("Sound: " + (soundSystem.isEnabled() ? "ON (M=mute)" : "OFF (M=on)"), tx, ty); ty += lineH;
        if (clientConnection.isConnected()) {
            double hz   = clientConnection.getUpdatesPerSecond();
            // Max interpolation lag across all remote players (px behind target)
            double maxLag = 0;
            for (com.classic.preservitory.entity.RemotePlayer rp : remotePlayers.values()) {
                maxLag = Math.max(maxLag, rp.getInterpolationDistance());
            }
            String netLine = String.format("Net:   Online  id=%s  peers=%d  %.0f Hz  lag=%.0fpx",
                    player.getId(), remotePlayers.size(), hz, maxLag);
            g.setColor(new Color(80, 220, 80));
            g.drawString(netLine, tx, ty);
        } else {
            g.setColor(new Color(200, 100, 100));
            g.drawString("Net:   Offline (no server)", tx, ty);
        }
        g.setColor(Color.WHITE);

        // Draw A* path waypoints in screen space (world coords → iso → screen)
        List<Point> path = movementSystem.getPath();
        if (!path.isEmpty()) {
            g.setColor(new Color(255, 220, 0, 160));
            int prevSx = 0, prevSy = 0;
            boolean hasPrev = false;
            int wi = movementSystem.getWaypointIndex();
            for (int i = wi; i < path.size(); i++) {
                Point p  = path.get(i);
                int   sx = IsoUtils.worldToIsoX(p.x, p.y) + IsoUtils.ISO_TILE_W / 2 - cameraOffsetX;
                int   sy = IsoUtils.worldToIsoY(p.x, p.y) + IsoUtils.ISO_TILE_H / 2 - cameraOffsetY;
                if (hasPrev) g.drawLine(prevSx, prevSy, sx, sy);
                g.fillOval(sx - 3, sy - 3, 7, 7);
                prevSx = sx; prevSy = sy; hasPrev = true;
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Floating texts (damage numbers + XP) — world space
    // -----------------------------------------------------------------------

    private void renderFloatingTexts(Graphics2D g) {
        g.setFont(new Font("Monospaced", Font.BOLD, 13));

        List<FloatingText> snapshot = new ArrayList<>(floatingTexts); // ✅ COPY

        for (FloatingText ft : snapshot) {
            int a = (int)(ft.alpha * 255);
            if (a <= 0) continue;

            g.setColor(new Color(0, 0, 0, Math.min(a, 180)));
            g.drawString(ft.text, (int)ft.x + 1, (int)ft.y + 1);

            g.setColor(new Color(ft.color.getRed(), ft.color.getGreen(),
                    ft.color.getBlue(), a));
            g.drawString(ft.text, (int)ft.x, (int)ft.y);
        }
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    /**
     * Draw text with a dark outline for readability over any background.
     * Four 1-pixel offsets are drawn in {@code outlineColor} then the main
     * text is drawn in {@code textColor} on top.
     */
    private void drawOutlinedString(Graphics2D g, String text, int x, int y,
                                     Color textColor, Color outlineColor) {
        g.setColor(outlineColor);
        g.drawString(text, x + 1, y + 1);
        g.drawString(text, x - 1, y + 1);
        g.drawString(text, x + 1, y - 1);
        g.drawString(text, x - 1, y - 1);
        g.setColor(textColor);
        g.drawString(text, x, y);
    }

    private void showMessage(String msg) {
        actionMessage = msg;
        messageTimer  = 3.0;
        chatBox.post(msg, categorizeMsgColor(msg));
    }

    /**
     * Assign a chat-box colour based on message content.
     * Checks for keywords in the lower-cased message.
     */
    private Color categorizeMsgColor(String msg) {
        String lc = msg.toLowerCase(Locale.ROOT);
        if (lc.contains("level up"))                                         return ChatBox.COLOR_LEVEL;
        if (lc.contains("quest"))                                            return ChatBox.COLOR_QUEST;
        if (lc.contains(" xp") || lc.contains("logs") || lc.contains("ore")
         || lc.contains("chop") || lc.contains("mine"))                     return ChatBox.COLOR_SKILL;
        if (lc.contains("attack") || lc.contains("kill") || lc.contains("hit")
         || lc.contains("died") || lc.contains("fight") || lc.contains("damage")) return ChatBox.COLOR_COMBAT;
        if (lc.contains("save") || lc.contains("load") || lc.contains("sound")) return ChatBox.COLOR_SYSTEM;
        return ChatBox.COLOR_DEFAULT;
    }

    /**
     * Spawn a floating damage number.
     * {@code x, y} are world-space pixel coordinates; they are converted to
     * iso screen coords before being stored so FloatingText renders correctly.
     */
    private void spawnDamage(double x, double y, int damage, boolean isPlayerReceiving) {
        Color c;
        String text;
        if (damage == 0) {
            c = new Color(160, 160, 160); text = "0";
        } else if (isPlayerReceiving) {
            c = new Color(220,  55,  55); text = "-" + damage;
        } else {
            c = new Color(255, 220,  50); text = String.valueOf(damage);
        }
        // Convert world position to iso screen coords; offset upward from the ground
        double isoFtX = IsoUtils.worldToIsoX(x, y) + IsoUtils.ISO_TILE_W / 2.0 - 6;
        double isoFtY = IsoUtils.worldToIsoY(x, y) - 12;
        floatingTexts.add(new FloatingText(isoFtX, isoFtY, text, c));
    }

    private double distanceTo(Entity a, Entity b) {
        double dx = a.getCenterX() - b.getCenterX();
        double dy = a.getCenterY() - b.getCenterY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private boolean isWithinInteractionRange(Entity a, Entity b) {
        return distanceTo(a, b) <= Constants.TILE_SIZE * 1.6;
    }

    private void trackFps() {
        fpsCounter++;
        long now = System.nanoTime();
        if (now - fpsTimer >= 1_000_000_000L) {
            displayedFps = fpsCounter;
            fpsCounter   = 0;
            fpsTimer     = now;
        }
    }
}
