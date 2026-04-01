package com.classic.preservitory.ui.panels;

import com.classic.preservitory.client.definitions.ItemDefinitionManager;
import com.classic.preservitory.client.world.ClientWorld;
import com.classic.preservitory.entity.Animation;
import com.classic.preservitory.entity.Player;
import com.classic.preservitory.entity.RemotePlayer;
import com.classic.preservitory.game.GameLoop;
import com.classic.preservitory.input.MouseHandler;
import com.classic.preservitory.network.ClientConnection;
import com.classic.preservitory.system.CombatSystem;
import com.classic.preservitory.system.MiningSystem;
import com.classic.preservitory.system.MovementSystem;
import com.classic.preservitory.system.SoundSystem;
import com.classic.preservitory.system.WoodcuttingSystem;
import com.classic.preservitory.system.audio.MusicManager;
import com.classic.preservitory.ui.framework.assets.AssetManager;
import com.classic.preservitory.ui.overlays.ChatBox;
import com.classic.preservitory.ui.overlays.FloatingText;
import com.classic.preservitory.ui.quests.QuestCompleteWindow;
import com.classic.preservitory.ui.screens.LoginScreen;
import com.classic.preservitory.ui.shops.ShopWindow;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;
import com.classic.preservitory.world.World;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Main game surface — orchestrates the game loop, rendering, and input.
 *
 * === Layout ===
 *   Left 566 px  → scrollable game viewport (camera follows player)
 *   Right 234 px → fixed side panel: stats, skills, inventory, info
 *
 * === Responsibilities ===
 *   - Owns the game loop and all major system references
 *   - Delegates rendering to {@link GameRenderer}
 *   - Delegates input handling and interaction updates to {@link GameInputHandler}
 *   - Wires server callbacks via {@link ClientConnection} listeners
 */
public class GamePanel extends JPanel {

    // -----------------------------------------------------------------------
    //  Game state (package-private — accessed by GameInputHandler/GameRenderer)
    // -----------------------------------------------------------------------

    enum GameState { PLAYING, IN_DIALOGUE }
    GameState gameState = GameState.PLAYING;

    // -----------------------------------------------------------------------
    //  Layout constants (package-private — used by input handler and renderer)
    // -----------------------------------------------------------------------

    static final int    CHAT_H                = 88;
    static final int    LOGOUT_BTN_H          = 18;
    static final int    LOGOUT_BTN_Y          = Constants.SCREEN_HEIGHT - LOGOUT_BTN_H - 2;
    static final int    LOGOUT_BTN_X          = Constants.PANEL_X + 4;
    static final int    LOGOUT_BTN_W          = Constants.PANEL_W - 8;
    static final double LOGOUT_CONFIRM_TIMEOUT = 3.0;
    static final int    CONTEXT_MENU_OPTION_H = 18;
    static final int    XP_TRACKER_X          = Constants.VIEWPORT_W - 160;
    static final int    XP_TRACKER_Y          = 12;
    static final int    XP_DROP_START_X       = Constants.PANEL_X - 60;
    static final int    XP_DROP_START_Y       = 150;

    // -----------------------------------------------------------------------
    //  Core game objects (package-private — accessed by renderer/input handler)
    // -----------------------------------------------------------------------

    final World             world;
    final Player            player;
    final MouseHandler      mouseHandler;
    final MovementSystem    movementSystem;
    final WoodcuttingSystem woodcuttingSystem;
    final MiningSystem      miningSystem;
    final CombatSystem      combatSystem;
    final SoundSystem       soundSystem;

    private MusicManager musicManager;
    private final GameLoop gameLoop;

    // -----------------------------------------------------------------------
    //  Networking (package-private)
    // -----------------------------------------------------------------------

    final ClientConnection clientConnection = new ClientConnection();
    final ClientWorld      clientWorld      = new ClientWorld();
    volatile Map<String, RemotePlayer> remotePlayers = Collections.emptyMap();

    // -----------------------------------------------------------------------
    //  UI components (package-private)
    // -----------------------------------------------------------------------

    final RightPanel         rightPanel         = new RightPanel();
    final ChatBox            chatBox            = new ChatBox();
    ShopWindow               shopWindow;
    QuestCompleteWindow      questCompleteWindow;
    LoginScreen              loginScreen;

    // -----------------------------------------------------------------------
    //  Camera (package-private — set by GameRenderer, read by GameInputHandler)
    // -----------------------------------------------------------------------

    int    cameraOffsetX    = 0;
    int    cameraOffsetY    = 0;
    /** Persistent zoom-pan offset accumulated by mouse-centred zoom and middle-mouse pan. */
    double cameraZoomOffsetX = 0.0;
    double cameraZoomOffsetY = 0.0;

    // -----------------------------------------------------------------------
    //  Middle-mouse pan state
    // -----------------------------------------------------------------------

    private boolean panDragging   = false;
    private int     panLastMouseX = 0;
    private int     panLastMouseY = 0;

    // -----------------------------------------------------------------------
    //  HUD / feedback state (package-private — read by GameRenderer)
    // -----------------------------------------------------------------------

    String  actionMessage = "";
    String  hoverText     = "";
    double  messageTimer  = 0;
    final List<FloatingText> floatingTexts = new ArrayList<>();
    final List<XpDrop> xpDrops = new ArrayList<>();
    final List<ContextMenuOption> contextMenuOptions = new ArrayList<>();
    boolean contextMenuOpen = false;
    int contextMenuX = 0;
    int contextMenuY = 0;
    long totalXp = 0L;
    int     displayedFps;
    String  currentAccountName = "";
    boolean debugMode          = false;
    boolean authRequired       = true;

    // -----------------------------------------------------------------------
    //  Zoom
    // -----------------------------------------------------------------------

    private double zoom       = 1.0;
    private double targetZoom = 1.0;

    public double getZoom() { return zoom; }

    // -----------------------------------------------------------------------
    //  Private state
    // -----------------------------------------------------------------------

    private boolean deathHandled;
    private int     fpsCounter;
    private long    fpsTimer;

    // -----------------------------------------------------------------------
    //  Helper collaborators (package-private — GameRenderer accesses inputHandler)
    // -----------------------------------------------------------------------

    GameInputHandler inputHandler;
    GameRenderer     renderer;

    // -----------------------------------------------------------------------
    //  External listeners (set by Game)
    // -----------------------------------------------------------------------

    private java.util.function.Consumer<String> loginSuccessListener;
    private Runnable disconnectListener;

    public void setLoginSuccessListener(java.util.function.Consumer<String> listener) {
        this.loginSuccessListener = listener;
    }

    public void setDisconnectListener(Runnable listener) {
        this.disconnectListener = listener;
    }

    void setHoverText(String text) {
        hoverText = text != null && !text.isBlank() ? text : "";
    }

    void openContextMenu(List<ContextMenuOption> options, int x, int y) {
        contextMenuOptions.clear();
        contextMenuOptions.addAll(options);
        contextMenuOpen = !contextMenuOptions.isEmpty();
        contextMenuX = x;
        contextMenuY = y;
    }

    void closeContextMenu() {
        contextMenuOpen = false;
        contextMenuOptions.clear();
    }

    void updateTotalXpFromSkills() {
        long total = 0L;
        for (com.classic.preservitory.entity.Skill skill : player.getSkillSystem().getAllSkills().values()) {
            total += skill.getXp();
        }
        totalXp = total;
    }

    void addXpDrop(String skillName, int xp) {
        totalXp += Math.max(0, xp);
        int lane = xpDrops.size() % 4;
        int inventoryPanelX = Constants.PANEL_X;
        double startX = inventoryPanelX - 60;
        double startY = Math.max(60, getHeight() / 2.0) + lane * 18;
        double targetX = inventoryPanelX - 60;
        double targetY = XP_TRACKER_Y + 16;
        xpDrops.add(new XpDrop(startX, startY, "+" + xp + " " + skillName, FloatingText.COLOR_SKILL, targetX, targetY));
    }

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public GamePanel(MusicManager musicManager) {
        this.musicManager = musicManager;

        AssetManager.load();

        loginScreen = new LoginScreen(
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
                },
                musicManager
        );

        questCompleteWindow = new QuestCompleteWindow();
        shopWindow = new ShopWindow(
                itemId -> {
                    clientConnection.sendBuy(itemId);
                    String name = ItemDefinitionManager.get(itemId).name;
                    showMessage("Buying: " + name);
                },
                () -> {
                    clientConnection.sendShopClose();
                    showMessage("Shop closed.");
                }
        );

        setPreferredSize(new Dimension(Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);

        world             = new World();
        player            = new Player(12 * Constants.TILE_SIZE, 9 * Constants.TILE_SIZE);
        mouseHandler      = new MouseHandler();
        movementSystem    = new MovementSystem();
        woodcuttingSystem = new WoodcuttingSystem();
        miningSystem      = new MiningSystem();
        combatSystem      = new CombatSystem();
        soundSystem       = new SoundSystem();
        gameLoop          = new GameLoop(this);

        inputHandler = new GameInputHandler(this);
        renderer     = new GameRenderer(this);

        setupInputListeners();
        setupNetworkListeners();

        rightPanel.setCombatStyleListener(style -> clientConnection.sendCombatStyle(style));
        rightPanel.setUnequipListener(slot -> clientConnection.sendUnequip(slot));

        clientWorld.setDamageListener(event ->
                spawnDamage(event.x, event.y, event.amount, false));

        clientConnection.connect();
    }

    // -----------------------------------------------------------------------
    //  Input listener wiring
    // -----------------------------------------------------------------------

    private void setupInputListeners() {
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    panDragging   = true;
                    panLastMouseX = e.getX();
                    panLastMouseY = e.getY();
                    return;
                }
                inputHandler.handleMousePress(e);
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    panDragging = false;
                }
            }
        });

        addMouseWheelListener(e -> {
            if (shopWindow.isOpen()) {
                shopWindow.handleScroll(e.getWheelRotation() > 0 ? 1 : -1);
            } else if (e.getX() >= Constants.PANEL_X) {
                rightPanel.handleMouseWheel(e.getWheelRotation() > 0 ? 1 : -1);
            } else {
                targetZoom = Math.max(0.5, Math.min(2.0,
                        targetZoom - e.getPreciseWheelRotation() * 0.1));
                repaint();
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                inputHandler.hoverX = e.getX();
                inputHandler.hoverY = e.getY();
                if (authRequired) { loginScreen.handleMouseMove(e.getX(), e.getY()); return; }
                questCompleteWindow.handleMouseMove(e.getX(), e.getY());
                shopWindow.handleMouseMove(e.getX(), e.getY());
                rightPanel.handleMouseMove(e.getX(), e.getY());
                inputHandler.updateCursorForHover(e.getX(), e.getY());
            }

            @Override public void mouseDragged(MouseEvent e) {
                inputHandler.hoverX = e.getX();
                inputHandler.hoverY = e.getY();
                rightPanel.handleMouseMove(e.getX(), e.getY());
                if (panDragging) {
                    double z = getZoom();
                    cameraZoomOffsetX -= (e.getX() - panLastMouseX) / z;
                    cameraZoomOffsetY -= (e.getY() - panLastMouseY) / z;
                    panLastMouseX = e.getX();
                    panLastMouseY = e.getY();
                    repaint();
                }
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e)  { inputHandler.handleKey(e.getKeyCode()); }
            @Override public void keyTyped(KeyEvent e)    { inputHandler.handleCharTyped(e.getKeyChar()); }
        });
    }

    // -----------------------------------------------------------------------
    //  Network listener wiring
    // -----------------------------------------------------------------------

    private void setupNetworkListeners() {
        clientConnection.setListener(new ClientConnection.Listener() {

            @Override
            public void onConnected(String assignedId) {
                player.setId(assignedId);
                deathHandled       = false;
                authRequired       = true;
                currentAccountName = "";
                totalXp            = 0L;
                xpDrops.clear();
                closeContextMenu();
                loginScreen.reset();
                chatBox.post("Connected. Please log in.", ChatBox.COLOR_SYSTEM);
            }

            @Override
            public void onDisconnected() {
                inputHandler.stopAllActivities();
                gameState = GameState.PLAYING;
                chatBox.clearDialogue();
                shopWindow.hide();
                questCompleteWindow.close();
                rightPanel.setQuestEntries(java.util.Collections.emptyList());
                player.applyEquipmentUpdate(null);

                authRequired       = true;
                currentAccountName = "";
                totalXp            = 0L;
                xpDrops.clear();
                closeContextMenu();
                loginScreen.setStatus("Server offline. Reconnecting...");
                chatBox.post("Disconnected from server. Reconnecting...", ChatBox.COLOR_SYSTEM);
                showMessage("Server offline.");
                if (disconnectListener != null) disconnectListener.run();
            }

            @Override
            public void onLoggedOut() {
                inputHandler.stopAllActivities();
                gameState = GameState.PLAYING;
                chatBox.clearDialogue();
                shopWindow.hide();
                questCompleteWindow.close();
                rightPanel.setQuestEntries(java.util.Collections.emptyList());
                player.applyEquipmentUpdate(null);

                authRequired                 = true;
                currentAccountName           = "";
                totalXp                      = 0L;
                xpDrops.clear();
                closeContextMenu();
                inputHandler.logoutConfirmTimer = 0;
                inputHandler.isTypingChat    = false;
                inputHandler.chatInput.setLength(0);

                loginScreen.reset();
                chatBox.post("Logged out.", ChatBox.COLOR_SYSTEM);
                musicManager.play();
            }

            @Override
            public void onChat(String username, String role, String message) {
                String prefix = "";
                switch (role) {
                    case "MODERATOR":  prefix = "[MOD] ";   break;
                    case "ADMIN":      prefix = "[ADMIN] "; break;
                    case "OWNER":      prefix = "[OWNER] "; break;
                    case "DEVELOPER":  prefix = "[DEV] ";   break;
                }
                chatBox.post(prefix + username + ": " + message, ChatBox.COLOR_CHAT);
            }
        });

        clientConnection.setTreeUpdateListener(clientWorld::updateTrees);
        clientConnection.setTreeRemoveListener(clientWorld::chopTree);
        clientConnection.setTreeAddListener(clientWorld::addTree);

        clientConnection.setRockUpdateListener(clientWorld::updateRocks);
        clientConnection.setRockRemoveListener(clientWorld::mineRock);
        clientConnection.setRockAddListener(clientWorld::addRock);

        clientConnection.setNpcUpdateListener(clientWorld::updateNpcs);
        clientConnection.setEnemyUpdateListener(clientWorld::updateEnemies);

        clientConnection.setDamageListener(damage ->
                clientWorld.handleDamage(damage[0], damage[1], damage[2]));

        clientConnection.setPlayerHpListener(hpState -> {
            int oldHp = player.getHp();
            int newHp = hpState[0];
            if (hpState.length >= 2 && hpState[1] > 0) player.setMaxHp(hpState[1]);
            player.setHp(newHp);
            if (newHp > 0) deathHandled = false;
            int delta = oldHp - newHp;
            if (delta > 0) spawnDamage(player.getCenterX(), player.getY() - 4, delta, true);
            if (player.isDead()) handlePlayerDeath();
        });

        clientConnection.setLootUpdateListener(clientWorld::updateLoot);
        clientConnection.setLootAddListener(clientWorld::addLoot);
        clientConnection.setLootRemoveListener(clientWorld::removeLoot);
        clientConnection.setInventorySlotListener(slots -> player.applyInventorySlots(slots));
        clientConnection.setStopActionListener(() -> inputHandler.stopAllActivities());
        clientConnection.setEquipmentListener(eq  -> player.applyEquipmentUpdate(eq));
        clientConnection.setSkillSnapshotListener(snapshot -> {
            player.applySkillSnapshot(snapshot);
            updateTotalXpFromSkills();
        });

        clientConnection.setSkillXpListener(parts -> {
            try {
                int xp = Integer.parseInt(parts[1]);
                String skill = parts[0].substring(0, 1).toUpperCase()
                             + parts[0].substring(1).toLowerCase();
                addXpDrop(skill, xp);
            } catch (NumberFormatException | StringIndexOutOfBoundsException ignored) {}
        });

        boolean[] pendingRewardHeader = {false};

        clientConnection.setQuestStartListener(name ->
                chatBox.post("You have started: " + name, ChatBox.COLOR_QUEST));

        clientConnection.setQuestCompleteListener(name -> {
            chatBox.post("Quest completed: " + name, ChatBox.COLOR_LEVEL);
            chatBox.post("", ChatBox.COLOR_DEFAULT);
            pendingRewardHeader[0] = true;
            questCompleteWindow.open(name);
        });

        clientConnection.setQuestRewardListener(reward -> {
            if (pendingRewardHeader[0]) {
                chatBox.post("Rewards:", ChatBox.COLOR_QUEST);
                pendingRewardHeader[0] = false;
            }
            String rewardLine = ItemDefinitionManager.exists(reward[0])
                    ? ItemDefinitionManager.get(reward[0]).name + " x" + reward[1]
                    : "Unknown item (id: " + reward[0] + ") x" + reward[1];
            chatBox.post("  " + rewardLine, ChatBox.COLOR_SKILL);
            questCompleteWindow.addReward(reward[0], reward[1]);
        });

        clientConnection.setQuestXpListener(parts -> {
            try {
                int xp = Integer.parseInt(parts[1].trim());
                String skill = parts[0].substring(0, 1).toUpperCase()
                             + parts[0].substring(1).toLowerCase();
                chatBox.post("You gained " + xp + " " + skill + " XP", ChatBox.COLOR_SKILL);
                questCompleteWindow.addXpReward(parts[0], xp);
            } catch (NumberFormatException | StringIndexOutOfBoundsException ignored) {}
        });

        clientConnection.setQuestLogListener(entries -> rightPanel.setQuestEntries(entries));

        clientConnection.setQuestStageListener(parts -> {
            if (parts.length >= 2 && !parts[1].isEmpty())
                chatBox.post("Objective updated: " + parts[1], ChatBox.COLOR_QUEST);
        });

        clientConnection.setQuestObjectiveCompleteListener(name ->
                chatBox.post("Objective complete!", ChatBox.COLOR_QUEST));

        clientConnection.setDialogueListener(dialogue -> {
            com.classic.preservitory.entity.NPC npc = clientWorld.getNpc(dialogue.npcId);
            String npcName = (npc != null) ? npc.getName() : "NPC";
            gameState = GameState.IN_DIALOGUE;
            chatBox.setDialogue(npcName, dialogue.line);
        });

        clientConnection.setDialogueOptionsListener(opts -> {
            java.util.List<String> list = new java.util.ArrayList<>(opts.length);
            for (String o : opts) if (o != null && !o.isEmpty()) list.add(o);
            chatBox.setDialogueOptions(list);
        });

        clientConnection.setDialogueCloseListener(npcId -> {
            gameState = GameState.PLAYING;
            chatBox.clearDialogue();
        });

        clientConnection.setShopListener(shop -> {
            shopWindow.open(shop);
            showMessage("Shop opened.");
        });

        clientConnection.setAuthSuccessListener(username -> {
            authRequired       = false;
            currentAccountName = username;
            inputHandler.isTypingChat = false;
            inputHandler.chatInput.setLength(0);
            loginScreen.reset();
            musicManager.stop();
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
    }

    // -----------------------------------------------------------------------
    //  Game loop
    // -----------------------------------------------------------------------

    public void startGameLoop() {
        fpsTimer = System.nanoTime();
        gameLoop.start();
    }

    /** Called once per frame by {@link GameLoop}. */
    public void update(double deltaTime) {
        if (!clientConnection.isConnected() || authRequired) {
            inputHandler.stopAllActivities();
            player.getAnimation().setState(Animation.State.IDLE);
        } else if (gameState == GameState.PLAYING) {
            movementSystem.update(player, mouseHandler, deltaTime);
            inputHandler.updateInteractions(deltaTime);

            if (player.isDead()) handlePlayerDeath();
            updateAnimation(deltaTime);
        }

        // Floating texts animate in all states
        Iterator<FloatingText> it = floatingTexts.iterator();
        while (it.hasNext()) {
            FloatingText ft = it.next();
            ft.tick(deltaTime);
            if (ft.isDone()) it.remove();
        }
        Iterator<XpDrop> xpIt = xpDrops.iterator();
        while (xpIt.hasNext()) {
            XpDrop drop = xpIt.next();
            drop.tick(deltaTime);
            if (drop.isDone()) xpIt.remove();
        }

        if (messageTimer > 0) messageTimer = Math.max(0, messageTimer - deltaTime);
        inputHandler.tickLogoutTimer(deltaTime);

        if (!authRequired) clientConnection.sendPosition((int) player.getX(), (int) player.getY());
        syncRemotePlayers(deltaTime);

        // Smooth zoom interpolation with mouse-centred camera adjustment
        if (Math.abs(zoom - targetZoom) > 0.001) {
            double oldZoom = zoom;
            zoom += (targetZoom - zoom) * Math.min(1.0, deltaTime * 12.0);
            if (Math.abs(zoom - targetZoom) < 0.001) zoom = targetZoom;

            // Keep the world point under the mouse fixed as zoom changes.
            // Formula: worldX = (screenX - vpCx) / zoom + vpCx + camX
            // The adjustment needed per frame is (screenX - vpCx) * (1/oldZoom - 1/newZoom).
            int mx = inputHandler.hoverX;
            int my = inputHandler.hoverY;
            if (mx >= 0 && mx < Constants.VIEWPORT_W && my >= 0 && my < Constants.VIEWPORT_H) {
                double vpCx = Constants.VIEWPORT_W / 2.0;
                double vpCy = Constants.VIEWPORT_H / 2.0;
                cameraZoomOffsetX += (mx - vpCx) * (1.0 / oldZoom - 1.0 / zoom);
                cameraZoomOffsetY += (my - vpCy) * (1.0 / oldZoom - 1.0 / zoom);
            }
        }

        trackFps();
    }

    // -----------------------------------------------------------------------
    //  Rendering — delegates to GameRenderer
    // -----------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        renderer.render((Graphics2D) g);
    }

    // -----------------------------------------------------------------------
    //  Remote player sync
    // -----------------------------------------------------------------------

    private void syncRemotePlayers(double deltaTime) {
        Map<String, int[]> netSnapshot = clientConnection.getRemotePlayers();
        Map<String, RemotePlayer> next = new HashMap<>(remotePlayers);

        for (Map.Entry<String, int[]> entry : netSnapshot.entrySet()) {
            String id  = entry.getKey();
            int[]  pos = entry.getValue();

            if (id.equals(player.getId())) {
                double dx = Math.abs(player.getX() - pos[0]);
                double dy = Math.abs(player.getY() - pos[1]);
                if (dx > Constants.TILE_SIZE * 4 || dy > Constants.TILE_SIZE * 4) {
                    player.setX(pos[0]);
                    player.setY(pos[1]);
                    movementSystem.clearPath();
                    mouseHandler.clearTarget();
                }
                continue;
            }

            RemotePlayer rp = next.get(id);
            if (rp == null) {
                rp = new RemotePlayer(id, pos[0], pos[1]);
                next.put(id, rp);
            } else {
                rp.setTargetPosition(pos[0], pos[1]);
            }
            rp.update(deltaTime);
        }

        next.entrySet().removeIf(e -> !netSnapshot.containsKey(e.getKey()));
        remotePlayers = next;
    }

    // -----------------------------------------------------------------------
    //  Animation state sync
    // -----------------------------------------------------------------------

    private void updateAnimation(double deltaTime) {
        Animation.State anim;
        if      (combatSystem.isInCombat())       anim = Animation.State.FIGHTING;
        else if (woodcuttingSystem.isChopping())  anim = Animation.State.CHOPPING;
        else if (miningSystem.isMining())         anim = Animation.State.MINING;
        else if (movementSystem.isMoving())       anim = Animation.State.WALKING;
        else                                      anim = Animation.State.IDLE;

        player.getAnimation().setState(anim);
        player.getAnimation().tick(deltaTime);
    }

    // -----------------------------------------------------------------------
    //  Player death
    // -----------------------------------------------------------------------

    private void handlePlayerDeath() {
        if (deathHandled) return;
        deathHandled = true;
        inputHandler.stopAllActivities();
        showMessage("You have died. Waiting for server respawn/state update.");
    }

    // -----------------------------------------------------------------------
    //  Floating text
    // -----------------------------------------------------------------------

    void spawnDamage(double x, double y, int damage, boolean isPlayerReceiving) {
        Color c;
        String text;
        if (damage == 0) {
            c = new Color(160, 160, 160); text = "0";
        } else if (isPlayerReceiving) {
            c = new Color(220, 55, 55);   text = "-" + damage;
        } else {
            c = new Color(255, 220, 50);  text = String.valueOf(damage);
        }
        double isoFtX = IsoUtils.worldToIsoX(x, y) + IsoUtils.ISO_TILE_W / 2.0 - 6;
        double isoFtY = IsoUtils.worldToIsoY(x, y) - 12;
        floatingTexts.add(new FloatingText(isoFtX, isoFtY, text, c));
    }

    // -----------------------------------------------------------------------
    //  Message display
    // -----------------------------------------------------------------------

    void showMessage(String msg) {
        actionMessage = msg;
        messageTimer  = 3.0;
        chatBox.post(msg, categorizeMsgColor(msg));
    }

    private Color categorizeMsgColor(String msg) {
        String lc = msg.toLowerCase(Locale.ROOT);
        if (lc.contains("level up"))                                              return ChatBox.COLOR_LEVEL;
        if (lc.contains("quest"))                                                 return ChatBox.COLOR_QUEST;
        if (lc.contains(" xp") || lc.contains("logs") || lc.contains("ore")
         || lc.contains("chop") || lc.contains("mine"))                          return ChatBox.COLOR_SKILL;
        if (lc.contains("attack") || lc.contains("kill") || lc.contains("hit")
         || lc.contains("died")   || lc.contains("fight") || lc.contains("damage")) return ChatBox.COLOR_COMBAT;
        if (lc.contains("save") || lc.contains("load") || lc.contains("sound")) return ChatBox.COLOR_SYSTEM;
        return ChatBox.COLOR_DEFAULT;
    }

    // -----------------------------------------------------------------------
    //  FPS tracking
    // -----------------------------------------------------------------------

    private void trackFps() {
        fpsCounter++;
        long now = System.nanoTime();
        if (now - fpsTimer >= 1_000_000_000L) {
            displayedFps = fpsCounter;
            fpsCounter   = 0;
            fpsTimer     = now;
        }
    }

    List<RemotePlayer> getRemotePlayersSnapshot() {
        return new ArrayList<>(remotePlayers.values());
    }

    static final class ContextMenuOption {
        final String label;
        final Runnable action;

        ContextMenuOption(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }
    }

    static final class XpDrop extends FloatingText {
        private final double targetX;
        private final double targetY;

        XpDrop(double x, double y, String text, Color color, double targetX, double targetY) {
            super(x, y, text, color);
            this.targetX = targetX;
            this.targetY = targetY;
        }

        @Override
        public void tick(double dt) {
            timer -= dt;
            y -= 60.0 * dt;
            x = targetX;
            alpha = Math.max(0f, alpha - (float) (1.2 * dt));
        }

        @Override
        public boolean isDone() {
            return alpha <= 0f || y < 40 || timer <= 0;
        }
    }
}
