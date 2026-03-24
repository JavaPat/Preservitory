package com.classic.preservitory.ui;

import com.classic.preservitory.entity.Animation;
import com.classic.preservitory.entity.Enemy;
import com.classic.preservitory.entity.Entity;
import com.classic.preservitory.entity.NPC;
import com.classic.preservitory.entity.Player;
import com.classic.preservitory.entity.RemotePlayer;
import com.classic.preservitory.entity.Skill;
import com.classic.preservitory.network.ClientConnection;
import com.classic.preservitory.game.GameLoop;
import com.classic.preservitory.ui.ChatBox;
import com.classic.preservitory.ui.FloatingText;
import com.classic.preservitory.ui.RightPanel;
import com.classic.preservitory.input.MouseHandler;
import com.classic.preservitory.item.Item;
import com.classic.preservitory.quest.Quest;
import com.classic.preservitory.quest.QuestSystem;
import com.classic.preservitory.system.CombatSystem;
import com.classic.preservitory.system.DialogueSystem;
import com.classic.preservitory.system.MiningSystem;
import com.classic.preservitory.system.MovementSystem;
import com.classic.preservitory.system.Pathfinding;
import com.classic.preservitory.system.SaveSystem;
import com.classic.preservitory.system.ShopSystem;
import com.classic.preservitory.system.SkillSystem;
import com.classic.preservitory.system.SoundSystem;
import com.classic.preservitory.system.WoodcuttingSystem;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;
import com.classic.preservitory.world.World;
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
 *   IN_SHOP     — shop overlay visible; movement blocked
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
 *   S — save game        L — load game
 *   D — toggle debug     M — toggle sound
 *   Escape — close UI
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

    private enum GameState { PLAYING, IN_DIALOGUE, IN_SHOP }
    private GameState gameState = GameState.PLAYING;

    // -----------------------------------------------------------------------
    //  Chat box — height of the overlay at the bottom of the game viewport
    // -----------------------------------------------------------------------

    private static final int CHAT_H = 88;

    // -----------------------------------------------------------------------
    //  Shop overlay — centred inside the game viewport
    // -----------------------------------------------------------------------

    private static final int SHOP_W = 500;
    private static final int SHOP_H = 360;
    private static final int SHOP_X = (Constants.VIEWPORT_W - SHOP_W) / 2;
    private static final int SHOP_Y = (Constants.SCREEN_HEIGHT - SHOP_H) / 2;

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
    private final QuestSystem       questSystem;
    private final ShopSystem        shopSystem;
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

    /** Manages the TCP connection to the game server (no-op if offline). */
    private final ClientConnection clientConnection = new ClientConnection();

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

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public GamePanel() {
        setPreferredSize(new Dimension(Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);

        world  = new World();
        // Player spawns near col 12, row 9 (away from centre objects)
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
        questSystem       = new QuestSystem();
        shopSystem        = new ShopSystem();
        soundSystem       = new SoundSystem();
        gameLoop          = new GameLoop(this);

        // Attempt server connection.  If the server is offline this is a no-op
        // and the game runs in single-player mode.
        clientConnection.setListener(new ClientConnection.Listener() {
            @Override public void onConnected(String assignedId) {
                player.setId(assignedId);
                chatBox.post("Connected as " + assignedId + ".  Press ENTER to chat.",
                             ChatBox.COLOR_SYSTEM);
            }
            @Override public void onDisconnected() {
                chatBox.post("Disconnected from server.", ChatBox.COLOR_SYSTEM);
            }
            @Override public void onChat(String fromId, String message) {
                // Skip our own ID — we already posted it locally in sendChatInput()
                // to give instant feedback without waiting for the server round-trip.
                if (fromId.equals(player.getId())) return;
                chatBox.post("[" + fromId + "]: " + message, ChatBox.COLOR_CHAT);
            }
        });
        clientConnection.connect();

        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { handleClick(e.getX(), e.getY()); }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                hoverX = e.getX();
                hoverY = e.getY();
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
            @Override public void keyPressed(KeyEvent e) { handleKey(e.getKeyCode()); }
            /** Capture printable characters for chat input. */
            @Override public void keyTyped(KeyEvent e)   { handleCharTyped(e.getKeyChar()); }
        });
    }

    // -----------------------------------------------------------------------
    //  Input — keyboard
    // -----------------------------------------------------------------------

    private void handleKey(int keyCode) {

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

            case KeyEvent.VK_S:
                SaveSystem.save(player, questSystem);
                showMessage("Game saved!  (L to load)");
                break;

            case KeyEvent.VK_L:
                if (SaveSystem.load(player, questSystem)) {
                    stopAllActivities();
                    mouseHandler.clearTarget();
                    showMessage("Game loaded!");
                } else {
                    showMessage("No save file found.");
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
                if (gameState == GameState.IN_DIALOGUE) {
                    dialogueSystem.close();
                    gameState = GameState.PLAYING;
                } else if (gameState == GameState.IN_SHOP) {
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

        // Run the message through the filter before doing anything with it
        String msg = com.classic.preservitory.util.ChatFilter.filter(raw);
        if (msg == null) {
            // Nothing valid remained — give brief feedback and stop
            chatBox.post("Invalid message.", ChatBox.COLOR_SYSTEM);
            return;
        }

        // Show immediately in our own chat box (no round-trip wait)
        chatBox.post("[" + player.getId() + "]: " + msg, ChatBox.COLOR_CHAT);
        // Send cleaned text to server — server broadcasts to all other clients
        clientConnection.sendChatMessage(msg);
    }

    // -----------------------------------------------------------------------
    //  Input — mouse clicks
    // -----------------------------------------------------------------------

    private void handleClick(int cx, int cy) {
        // Ignore world clicks while the player is composing a chat message
        // (right-panel clicks are still intentional and allowed through)
        if (isTypingChat && cx < Constants.PANEL_X) return;

        // Dialogue: any click in the viewport advances or closes the conversation
        if (gameState == GameState.IN_DIALOGUE) {
            handleDialogueClick();
            return;
        }

        // Shop: route to hit-testing inside the overlay (screen-space coords)
        if (gameState == GameState.IN_SHOP) {
            handleShopClick(cx, cy);
            return;
        }

        // --- PLAYING state ---

        // Route right-panel clicks (tab switching, inventory hover, etc.)
        if (cx >= Constants.PANEL_X) {
            rightPanel.handleClick(cx, cy);
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
        NPC clickedNPC = world.getNPCAt(worldX, worldY);
        if (clickedNPC != null) {
            stopAllActivities();
            activeNPC = clickedNPC;
            setApproachTarget(clickedNPC);
            return;
        }

        // 1. Enemy?
        Enemy clickedEnemy = world.getEnemyAt(worldX, worldY);
        if (clickedEnemy != null) {
            stopAllActivities();
            activeEnemy = clickedEnemy;
            setApproachTarget(clickedEnemy);
            return;
        }

        // 2. Tree?
        Tree clickedTree = world.getTreeAt(worldX, worldY);
        if (clickedTree != null) {
            stopAllActivities();
            activeTree = clickedTree;
            setApproachTarget(clickedTree);
            return;
        }

        // 3. Rock?
        Rock clickedRock = world.getRockAt(worldX, worldY);
        if (clickedRock != null) {
            stopAllActivities();
            activeRock = clickedRock;
            setApproachTarget(clickedRock);
            return;
        }

        // 4. Ground — pathfind to the clicked tile
        stopAllActivities();
        // clickTileCol/Row already computed from iso click above
        int goalCol  = clickTileCol;
        int goalRow  = clickTileRow;
        int startCol = Pathfinding.pixelToTileCol(player.getCenterX());
        int startRow = Pathfinding.pixelToTileRow(player.getCenterY());

        List<Point> path = Pathfinding.findPath(startCol, startRow, goalCol, goalRow, world);
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

        List<Point> path = Pathfinding.findPath(startCol, startRow, goalCol, goalRow, world);
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
    }

    // -----------------------------------------------------------------------
    //  Dialogue & shop click handlers
    // -----------------------------------------------------------------------

    private void handleDialogueClick() {
        if (!dialogueSystem.isLastLine()) {
            dialogueSystem.advance();
            return;
        }

        // Final line acknowledged — resolve quest events
        Quest quest = questSystem.getGettingStarted();
        dialogueSystem.close();

        if (quest.getState() == Quest.State.NOT_STARTED) {
            quest.start();
            showMessage("Quest Started: Getting Started!");
            gameState = GameState.PLAYING;

        } else if (quest.getState() == Quest.State.IN_PROGRESS && quest.isLogsStepDone()) {
            quest.complete();
            giveQuestReward();
            gameState = GameState.PLAYING;

        } else if (quest.getState() == Quest.State.COMPLETE) {
            gameState = GameState.IN_SHOP;

        } else {
            gameState = GameState.PLAYING;
        }
    }

    private void giveQuestReward() {
        Item coins = new Item("Coins", true);
        coins.setCount(50);
        player.getInventory().addItem(coins);
        player.getSkillSystem().addXp("woodcutting", 100);
        player.getSkillSystem().addXp("mining", 50);
        soundSystem.play(SoundSystem.Sound.LEVEL_UP);
        showMessage("Quest Complete!  Reward: 50 Coins + Bonus XP!");
    }

    private void handleShopClick(int cx, int cy) {
        // Close button
        if (cx >= SHOP_X + SHOP_W - 28 && cx <= SHOP_X + SHOP_W - 6
         && cy >= SHOP_Y + 6           && cy <= SHOP_Y + 26) {
            gameState = GameState.PLAYING;
            return;
        }

        // Buy rows (left column)
        List<ShopSystem.ShopEntry> stock = shopSystem.getStock();
        int buyX = SHOP_X + 10;
        int buyY = SHOP_Y + 55;
        for (int i = 0; i < stock.size(); i++) {
            int rowY = buyY + i * 36;
            if (cx >= buyX && cx <= buyX + 225
             && cy >= rowY && cy <= rowY + 32) {
                String err = shopSystem.buyItem(stock.get(i).name, player.getInventory());
                if (err == null) {
                    soundSystem.play(SoundSystem.Sound.ITEM_PICKUP);
                    showMessage("Bought " + stock.get(i).name + "!");
                } else {
                    showMessage(err);
                }
                return;
            }
        }

        // Sell rows (right column)
        List<Item> sellable = getSellableItems();
        int sellX = SHOP_X + SHOP_W / 2 + 10;
        int sellY = SHOP_Y + 55;
        for (int i = 0; i < sellable.size(); i++) {
            int rowY = sellY + i * 36;
            if (cx >= sellX && cx <= sellX + 225
             && cy >= rowY  && cy <= rowY + 32) {
                String err = shopSystem.sellItem(sellable.get(i).getName(), player.getInventory());
                if (err == null) {
                    soundSystem.play(SoundSystem.Sound.ITEM_PICKUP);
                    showMessage("Sold " + sellable.get(i).getName() + "!");
                } else {
                    showMessage(err);
                }
                return;
            }
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
        // World object timers always tick (respawns, regrowth)
        world.updateTrees(deltaTime);
        world.updateRocks(deltaTime);
        world.updateEnemies(deltaTime);

        if (gameState == GameState.PLAYING) {
            movementSystem.update(player, mouseHandler, deltaTime);
            updateTreeInteraction(deltaTime);
            updateRockInteraction(deltaTime);
            updateCombat(deltaTime);
            updateNPCInteraction(deltaTime);

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
        clientConnection.sendPosition((int) player.getX(), (int) player.getY());
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

        if (distanceTo(player, activeTree) <= Constants.TILE_SIZE * 1.6) {
            if (!woodcuttingSystem.isChopping()) {
                woodcuttingSystem.startChopping(activeTree);
                showMessage("You swing your axe...");
            }
            if (woodcuttingSystem.update(deltaTime)) applyChopReward();
        }
    }

    private void applyChopReward() {
        Skill wc          = player.getSkillSystem().getSkill("woodcutting");
        int   levelBefore = wc.getLevel();

        boolean logsAdded = woodcuttingSystem.grantReward(
                player.getSkillSystem(), player.getInventory());

        questSystem.onLogChopped();
        activeTree = null;

        soundSystem.play(SoundSystem.Sound.CHOP);

        if (!logsAdded) {
            showMessage("Your inventory is full!");
            return;
        }

        spawnText(player.getCenterX(), player.getY() - 14,
                  "+" + WoodcuttingSystem.XP_PER_CHOP + " WC XP",
                  new Color(120, 230, 120));

        if (wc.getLevel() > levelBefore) {
            soundSystem.play(SoundSystem.Sound.LEVEL_UP);
            showMessage("Level up! Woodcutting is now level " + wc.getLevel() + "!");
        } else {
            Quest quest = questSystem.getGettingStarted();
            if (quest.getState() == Quest.State.IN_PROGRESS && !quest.isLogsStepDone()) {
                showMessage("You got some Logs.  [" + quest.getLogsChopped() + "/3 for quest]");
            } else if (quest.getState() == Quest.State.IN_PROGRESS && quest.isLogsStepDone()) {
                showMessage("You got some Logs.  [Quest step done — talk to Guide!]");
            } else {
                showMessage("You got some Logs.  (+" + WoodcuttingSystem.XP_PER_CHOP + " WC XP)");
            }
        }
    }

    private void updateRockInteraction(double deltaTime) {
        if (activeRock == null) return;

        if (!activeRock.isSolid()) {
            miningSystem.stopMining();
            activeRock = null;
            return;
        }

        if (distanceTo(player, activeRock) <= Constants.TILE_SIZE * 1.6) {
            if (!miningSystem.isMining()) {
                miningSystem.startMining(activeRock);
                showMessage("You swing your pickaxe...");
            }
            if (miningSystem.update(deltaTime)) applyMineReward();
        }
    }

    private void applyMineReward() {
        Skill mining      = player.getSkillSystem().getSkill("mining");
        int   levelBefore = mining.getLevel();

        boolean oreAdded = miningSystem.grantReward(
                player.getSkillSystem(), player.getInventory());

        activeRock = null;
        soundSystem.play(SoundSystem.Sound.MINE);

        if (!oreAdded) {
            showMessage("Your inventory is full!");
            return;
        }

        spawnText(player.getCenterX(), player.getY() - 14,
                  "+" + MiningSystem.XP_PER_MINE + " Mining XP",
                  new Color(140, 170, 230));

        if (mining.getLevel() > levelBefore) {
            soundSystem.play(SoundSystem.Sound.LEVEL_UP);
            showMessage("Level up! Mining is now level " + mining.getLevel() + "!");
        } else {
            showMessage("You got some Ore.  (+" + MiningSystem.XP_PER_MINE + " Mining XP)");
        }
    }

    private void updateCombat(double deltaTime) {
        if (activeEnemy == null) return;

        if (!activeEnemy.isAlive()) {
            combatSystem.stopCombat();
            activeEnemy = null;
            return;
        }

        if (distanceTo(player, activeEnemy) <= Constants.TILE_SIZE * 1.6) {
            if (!combatSystem.isInCombat()) {
                combatSystem.startCombat(activeEnemy);
                showMessage("You attack the " + activeEnemy.getName() + "!");
            }
            CombatSystem.CombatResult result = combatSystem.update(player, deltaTime);
            if (result != null) applyCombatResult(result);
        }
    }

    private void applyCombatResult(CombatSystem.CombatResult result) {
        activeEnemy.takeDamage(result.playerDmg);
        spawnDamage(activeEnemy.getCenterX(), activeEnemy.getY() - 4,
                    result.playerDmg, false);

        if (activeEnemy.isAlive()) {
            player.takeDamage(result.enemyDmg);
            spawnDamage(player.getCenterX(), player.getY() - 4,
                        result.enemyDmg, true);
        }

        soundSystem.play(SoundSystem.Sound.HIT);

        if (activeEnemy.isDead()) {
            collectLoot(activeEnemy);
            combatSystem.stopCombat();
            activeEnemy = null;
        }
    }

    private void collectLoot(Enemy enemy) {
        List<Item> drops = enemy.rollLoot();
        if (drops.isEmpty()) {
            showMessage("You killed the " + enemy.getName() + ".");
            return;
        }

        StringBuilder msg = new StringBuilder("Killed ").append(enemy.getName()).append("!");
        for (Item item : drops) {
            if (player.getInventory().addItem(item)) {
                msg.append("  +").append(item.getCount()).append(" ").append(item.getName());
                soundSystem.play(SoundSystem.Sound.ITEM_PICKUP);
            }
        }
        showMessage(msg.toString());
    }

    private void updateNPCInteraction(double deltaTime) {
        if (activeNPC == null) return;
        if (distanceTo(player, activeNPC) <= Constants.TILE_SIZE * 1.6) {
            String[] lines = questSystem.getGuideDialogue();
            dialogueSystem.open(activeNPC, lines);
            gameState = GameState.IN_DIALOGUE;
            activeNPC = null;
        }
    }

    private void handlePlayerDeath() {
        showMessage("You have died!  Respawning...");
        stopAllActivities();
        player.respawn();
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
        depthSorted.addAll(world.getRocks());
        depthSorted.addAll(world.getTrees());
        depthSorted.addAll(world.getEnemies());
        depthSorted.addAll(world.getNPCs());
        depthSorted.addAll(remotePlayers.values());   // other connected players
        depthSorted.add(player);
        depthSorted.sort(Comparator.comparingDouble(e -> e.getY() + e.getHeight()));
        for (Entity e : depthSorted) e.render(g2);

        // World-space overlays
        drawClickIndicator(g2);
        drawActivityBar(g2);
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
        String combatName = (combatSystem.isInCombat() && combatSystem.getTargetEnemy() != null)
                ? combatSystem.getTargetEnemy().getName() : null;
        rightPanel.render(g2, player,
                questSystem.getGettingStarted(),
                woodcuttingSystem.isChopping(),
                miningSystem.isMining(),
                combatSystem.isInCombat(),
                combatName);

        // Chat box — message log (+ typing bar when composing)
        chatBox.render(g2, 0, Constants.VIEWPORT_H - CHAT_H,
                Constants.VIEWPORT_W, CHAT_H,
                isTypingChat ? chatInput.toString() : null);

        // Full-screen overlays (screen space, rendered over the viewport only)
        if (gameState == GameState.IN_DIALOGUE) drawDialogueBox(g2);
        if (gameState == GameState.IN_SHOP)     drawShopOverlay(g2);

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
        Entity hovered = world.getNPCAt(worldX, worldY);
        if (hovered == null) hovered = world.getEnemyAt(worldX, worldY);
        if (hovered == null) hovered = world.getTreeAt(worldX, worldY);
        if (hovered == null) hovered = world.getRockAt(worldX, worldY);
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

        if (world.getEnemyAt(worldX, worldY) != null) {
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        } else if (world.getTreeAt(worldX, worldY) != null
                || world.getRockAt(worldX, worldY) != null
                || world.getNPCAt(worldX, worldY)  != null) {
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

    /**
     * Progress bar below the player showing the current timed activity.
     * Positioned in iso screen space below the player's tile diamond.
     */
    private void drawActivityBar(Graphics2D g) {
        double progress;
        Color  barColor;

        if (woodcuttingSystem.isChopping()) {
            progress = woodcuttingSystem.getChopProgress();
            barColor = new Color(255, 155, 0);
        } else if (miningSystem.isMining()) {
            progress = miningSystem.getMineProgress();
            barColor = new Color(90, 140, 220);
        } else if (combatSystem.isInCombat()) {
            progress = combatSystem.getTickProgress();
            barColor = new Color(215, 55, 55);
        } else {
            return;
        }

        // Position bar just below the player's iso foot point
        int isoX = IsoUtils.worldToIsoX(player.getX(), player.getY());
        int isoY = IsoUtils.worldToIsoY(player.getX(), player.getY());
        int bw = 32;
        int bh = 4;
        int bx = isoX + IsoUtils.ISO_TILE_W / 2 - bw / 2;
        int by = isoY + IsoUtils.ISO_TILE_H + 6;

        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(bx, by, bw, bh);
        g.setColor(barColor);
        g.fillRect(bx, by, (int)(bw * progress), bh);
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

    /** Centred shop overlay (inside viewport). */
    private void drawShopOverlay(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, Constants.VIEWPORT_W, Constants.SCREEN_HEIGHT);

        g.setColor(new Color(20, 16, 8, 245));
        g.fillRoundRect(SHOP_X, SHOP_Y, SHOP_W, SHOP_H, 10, 10);
        g.setColor(new Color(180, 150, 60));
        g.drawRoundRect(SHOP_X, SHOP_Y, SHOP_W, SHOP_H, 10, 10);

        g.setFont(new Font("Monospaced", Font.BOLD, 16));
        g.setColor(new Color(240, 200, 80));
        FontMetrics fm = g.getFontMetrics();
        String title = "GUIDE'S SHOP";
        g.drawString(title, SHOP_X + (SHOP_W - fm.stringWidth(title)) / 2, SHOP_Y + 22);

        // Close button
        g.setColor(new Color(180, 40, 40));
        g.fillRect(SHOP_X + SHOP_W - 28, SHOP_Y + 6, 22, 20);
        g.setFont(new Font("Monospaced", Font.BOLD, 12));
        g.setColor(Color.WHITE);
        g.drawString("X", SHOP_X + SHOP_W - 21, SHOP_Y + 20);

        g.setColor(new Color(180, 150, 60, 100));
        g.drawLine(SHOP_X + 10, SHOP_Y + 30, SHOP_X + SHOP_W - 10, SHOP_Y + 30);

        int midX = SHOP_X + SHOP_W / 2;
        g.setColor(new Color(100, 100, 100, 160));
        g.drawLine(midX, SHOP_Y + 32, midX, SHOP_Y + SHOP_H - 10);

        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        g.setColor(new Color(100, 200, 100));
        g.drawString("BUY",  SHOP_X + 20, SHOP_Y + 46);
        g.setColor(new Color(200, 160, 80));
        g.drawString("SELL", midX  + 20,  SHOP_Y + 46);

        // Buy rows
        List<ShopSystem.ShopEntry> stock = shopSystem.getStock();
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        for (int i = 0; i < stock.size(); i++) {
            ShopSystem.ShopEntry e = stock.get(i);
            int rowY = SHOP_Y + 55 + i * 36;
            g.setColor(new Color(50, 45, 20, 180));
            g.fillRect(SHOP_X + 10, rowY, 225, 32);
            g.setColor(new Color(100, 85, 35));
            g.drawRect(SHOP_X + 10, rowY, 225, 32);
            g.setColor(iconColorFor(e.name));
            g.fillRect(SHOP_X + 14, rowY + 4, 24, 24);
            g.setColor(Color.WHITE);
            g.drawString(e.name, SHOP_X + 44, rowY + 14);
            g.setColor(new Color(240, 200, 60));
            g.drawString(e.buyPrice + " Coins", SHOP_X + 44, rowY + 28);
        }

        // Sell rows
        List<Item> sellable = getSellableItems();
        if (sellable.isEmpty()) {
            g.setFont(new Font("Monospaced", Font.ITALIC, 11));
            g.setColor(new Color(140, 140, 140));
            g.drawString("Nothing to sell", midX + 20, SHOP_Y + 75);
        } else {
            g.setFont(new Font("Monospaced", Font.PLAIN, 12));
            for (int i = 0; i < sellable.size(); i++) {
                Item item  = sellable.get(i);
                int  rowY  = SHOP_Y + 55 + i * 36;
                int  price = shopSystem.getSellPrices().getOrDefault(item.getName(), 0);
                g.setColor(new Color(45, 38, 15, 180));
                g.fillRect(midX + 10, rowY, 225, 32);
                g.setColor(new Color(95, 80, 30));
                g.drawRect(midX + 10, rowY, 225, 32);
                g.setColor(iconColorFor(item.getName()));
                g.fillRect(midX + 14, rowY + 4, 24, 24);
                g.setColor(Color.WHITE);
                g.drawString(item.getName() + " x" + item.getCount(), midX + 44, rowY + 14);
                g.setColor(new Color(200, 200, 80));
                g.drawString(price + " Coins ea", midX + 44, rowY + 28);
            }
        }

        g.setFont(new Font("Monospaced", Font.BOLD, 12));
        g.setColor(new Color(240, 200, 60));
        g.drawString("Your Coins: " + player.getInventory().countOf("Coins"),
                     SHOP_X + 10, SHOP_Y + SHOP_H - 10);
        g.setFont(new Font("Monospaced", Font.ITALIC, 10));
        g.setColor(new Color(150, 150, 150));
        g.drawString("Click item to buy/sell  |  ESC to close",
                     SHOP_X + 125, SHOP_Y + SHOP_H - 10);
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
        g.drawString("FPS: " + displayedFps, 8, 14);
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
        for (FloatingText ft : floatingTexts) {
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

    /** Spawn a floating text label (XP gained, item pickups, etc.). */
    private void spawnText(double x, double y, String text, Color color) {
        double isoFtX = IsoUtils.worldToIsoX(x, y) + IsoUtils.ISO_TILE_W / 2.0 - 6;
        double isoFtY = IsoUtils.worldToIsoY(x, y) - 12;
        floatingTexts.add(new FloatingText(isoFtX, isoFtY, text, color));
    }

    private double distanceTo(Entity a, Entity b) {
        double dx = a.getCenterX() - b.getCenterX();
        double dy = a.getCenterY() - b.getCenterY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private List<Item> getSellableItems() {
        List<Item> result = new ArrayList<>();
        for (Item item : player.getInventory().getSlots()) {
            if (shopSystem.getSellPrices().containsKey(item.getName())) result.add(item);
        }
        return result;
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

    // -----------------------------------------------------------------------
    //  Inner class: floating text label
    // -----------------------------------------------------------------------

}
