package com.classic.preservitory.ui.panels;

import com.classic.preservitory.client.definitions.ItemDefinitionManager;
import com.classic.preservitory.client.editor.EditorActions;
import com.classic.preservitory.client.editor.EditorObject;
import com.classic.preservitory.client.editor.EditorState;
import com.classic.preservitory.client.settings.ClientSettings;
import com.classic.preservitory.client.world.ClientWorld;
import com.classic.preservitory.client.world.map.TileMap;
import com.classic.preservitory.entity.Animation;
import com.classic.preservitory.entity.Player;
import com.classic.preservitory.entity.RemotePlayer;
import com.classic.preservitory.game.GameLoop;
import com.classic.preservitory.input.MouseHandler;
import com.classic.preservitory.network.ClientConnection;
import com.classic.preservitory.system.AttackSystem;
import com.classic.preservitory.system.CombatSystem;
import com.classic.preservitory.system.MiningSystem;
import com.classic.preservitory.system.MovementSystem;
import com.classic.preservitory.system.SoundSystem;
import com.classic.preservitory.system.WoodcuttingSystem;
import com.classic.preservitory.system.audio.MusicManager;
import com.classic.preservitory.ui.framework.assets.AssetManager;
import com.classic.preservitory.ui.framework.assets.PlayerSpriteManager;
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

    enum ClientState {
        LOGIN, LOGGING_IN, LOADING, IN_GAME
    }
    ClientState clientState = ClientState.LOGIN;

    enum GameState {
        PLAYING, IN_DIALOGUE
    }
    GameState gameState = GameState.PLAYING;

    // -----------------------------------------------------------------------
    //  Layout constants (package-private — used by input handler and renderer)
    // -----------------------------------------------------------------------

    static final int CHAT_H = 88;
    static final int LOGOUT_BTN_H = 18;
    static final double LOGOUT_CONFIRM_TIMEOUT = 3.0;
    static final int CONTEXT_MENU_OPTION_H = 18;
    static final int XP_TRACKER_Y = 12;
    static final int XP_DROP_START_Y = 150;
    static final long PANEL_HOVER_SUPPRESS_MS = 120L;

    // Dynamic layout — depend on current window size; use methods not static fields
    int getPanelX()     { return getWidth() - Constants.PANEL_W; }
    int getViewportW()  { return getWidth() - Constants.PANEL_W; }
    int getLogoutBtnX() { return getPanelX() + 4; }
    int getLogoutBtnY() { return getHeight() - LOGOUT_BTN_H - 2; }
    int getLogoutBtnW() { return Constants.PANEL_W - 8; }
    int getXpTrackerX() { return getPanelX() - 140; }

    // -----------------------------------------------------------------------
    //  Core game objects (package-private — accessed by renderer/input handler)
    // -----------------------------------------------------------------------

    final World world;
    final Player player;
    final MouseHandler mouseHandler;
    final MovementSystem    movementSystem;
    final WoodcuttingSystem woodcuttingSystem;
    final MiningSystem      miningSystem;
    final CombatSystem      combatSystem;
    final AttackSystem      attackSystem;
    final SoundSystem       soundSystem;

    private MusicManager musicManager;
    private final GameLoop gameLoop;


    private TileMap tileMap = new TileMap(64, 64);

    private int hoveredTileX = -1;
    private int hoveredTileY = -1;

    final EditorState editorState = new EditorState();
    final EditorActions editorActions = new EditorActions();

    // -----------------------------------------------------------------------
    //  Networking (package-private)
    // -----------------------------------------------------------------------

    final ClientConnection clientConnection = new ClientConnection();
    final ClientWorld clientWorld = new ClientWorld();
    final ClientSettings settings;
    volatile Map<String, RemotePlayer> remotePlayers = Collections.emptyMap();

    // -----------------------------------------------------------------------
    //  UI components (package-private)
    // -----------------------------------------------------------------------

    final RightPanel rightPanel;
    final ChatBox chatBox = new ChatBox();
    ShopWindow shopWindow;
    QuestCompleteWindow questCompleteWindow;
    LoginScreen loginScreen;

    // -----------------------------------------------------------------------
    //  Camera (package-private — set by GameRenderer, read by GameInputHandler)
    // -----------------------------------------------------------------------

    int cameraOffsetX = 0;
    int cameraOffsetY = 0;
    /** Persistent zoom-pan offset accumulated by mouse-centred zoom and middle-mouse pan. */
    double cameraZoomOffsetX = 0.0;
    double cameraZoomOffsetY = 0.0;

    // -----------------------------------------------------------------------
    //  Middle-mouse pan state
    // -----------------------------------------------------------------------

    private boolean panDragging = false;
    private int panLastMouseX = 0;
    private int panLastMouseY = 0;

    // -----------------------------------------------------------------------
    //  HUD / feedback state (package-private — read by GameRenderer)
    // -----------------------------------------------------------------------

    String actionMessage = "";
    String hoverText = "";
    double messageTimer = 0;
    final List<FloatingText> floatingTexts = new ArrayList<>();
    final List<XpDrop> xpDrops = new ArrayList<>();
    final List<ContextMenuOption> contextMenuOptions = new ArrayList<>();
    boolean contextMenuOpen = false;
    int contextMenuX = 0;
    int contextMenuY = 0;
    final Rectangle settingsCogBounds = new Rectangle();
    long totalXp = 0L;
    int displayedFps;
    String  currentAccountName = "";
    boolean debugMode = false;
    long ignoreHoverUntil = 0L;
    float fadeAlpha = 0f;
    boolean fadingIn = false;
    boolean fadingOut = false;
    private static final float FADE_SPEED = 4.0f;
    private static final double MIN_LOADING_DISPLAY_SECONDS = 0.15;
    private boolean pendingEnterGame = false;
    private double loadingOverlayTimer = 0.0;
    // -----------------------------------------------------------------------
    //  Zoom
    // -----------------------------------------------------------------------

    private double zoom = 1.0;
    private double targetZoom = 1.0;
    private boolean recenterCameraOnZoomIn = false;

    public double getZoom() { return zoom; }

    // -----------------------------------------------------------------------
    //  Private state
    // -----------------------------------------------------------------------

    private boolean deathHandled;
    private int fpsCounter;
    private long fpsTimer;

    // -----------------------------------------------------------------------
    //  Helper collaborators (package-private — GameRenderer accesses inputHandler)
    // -----------------------------------------------------------------------

    GameInputHandler inputHandler;
    GameRenderer renderer;

    // -----------------------------------------------------------------------
    //  External listeners (set by Game)
    // -----------------------------------------------------------------------

    private java.util.function.Consumer<String> loginSuccessListener;
    private java.util.function.Consumer<Boolean> authStateListener;
    private Runnable disconnectListener;
    private boolean pendingRegistrationAuth;

    public void setLoginSuccessListener(java.util.function.Consumer<String> listener) {
        this.loginSuccessListener = listener;
    }

    public void setDisconnectListener(Runnable listener) {
        this.disconnectListener = listener;
    }

    public void setAuthStateListener(java.util.function.Consumer<Boolean> listener) {
        this.authStateListener = listener;
    }

    public void setFullscreenListener(Runnable listener) {
        rightPanel.setFullscreenListener(listener);
    }

    public void setResizableListener(Runnable listener) {
        rightPanel.setResizableListener(listener);
    }

    public void setFpsListener(Runnable listener) {
        rightPanel.setFpsListener(listener);
    }

    public void setPingListener(Runnable listener) {
        rightPanel.setPingListener(listener);
    }

    public void setFullscreenState(boolean fullscreen) {
        rightPanel.setFullscreen(fullscreen);
    }

    public void setResizableState(boolean resizable) {
        rightPanel.setResizable(resizable);
    }

    public void setShowFpsState(boolean showFps) {
        settings.setShowFps(showFps);
        rightPanel.setShowFps(showFps);
    }

    public void setShowPingState(boolean showPing) {
        settings.setShowPing(showPing);
        rightPanel.setShowPing(showPing);
    }

    public void syncSettingsUi() {
        rightPanel.setShowFps(settings.isShowFps());
        rightPanel.setShowPing(settings.isShowPing());
    }

    public boolean isShowTotalXpEnabled() {
        return settings.isShowTotalXp();
    }

    public void toggleShowFps() {
        settings.setShowFps(!settings.isShowFps());
        rightPanel.setShowFps(settings.isShowFps());
        settings.save();
    }

    public void toggleShowPing() {
        settings.setShowPing(!settings.isShowPing());
        rightPanel.setShowPing(settings.isShowPing());
        settings.save();
    }

    public void toggleShowTotalXp() {
        settings.setShowTotalXp(!settings.isShowTotalXp());
        settings.save();
    }

    public void setTotalXpListener(Runnable listener) {
        rightPanel.setTotalXpListener(listener);
    }

    public void toggleShiftClickDrop() {
        settings.setShiftClickDrop(!settings.isShiftClickDrop());
        settings.save();
    }

    public void setShiftDropListener(Runnable listener) {
        rightPanel.setShiftDropListener(listener);
    }

    public boolean isShowFpsEnabled() {
        return settings.isShowFps();
    }

    public boolean isShowPingEnabled() {
        return settings.isShowPing();
    }

    void setHoverText(String text) {
        hoverText = text != null && !text.isBlank() ? text : "";
    }

    boolean isInGame() {
        return clientState == ClientState.IN_GAME;
    }

    boolean isOnLoginScreen() {
        return clientState == ClientState.LOGIN;
    }

    boolean shouldRenderLoginScreen() {
        return clientState == ClientState.LOGIN || clientState == ClientState.LOGGING_IN;
    }

    boolean isPreGameState() {
        return clientState != ClientState.IN_GAME;
    }

    boolean isLoadingState() {
        return clientState == ClientState.LOADING;
    }

    void beginLoginTransition() {
        clientState = ClientState.LOGGING_IN;
        pendingEnterGame = false;
        loadingOverlayTimer = 0.0;
        fadingIn = false;
        fadingOut = true;
    }

    private void enterLoadingState() {
        clientState = ClientState.LOADING;
        loadingOverlayTimer = MIN_LOADING_DISPLAY_SECONDS;
    }

    private void beginFadeInToGame() {
        clientState = ClientState.IN_GAME;
        pendingEnterGame = false;
        loadingOverlayTimer = 0.0;
        fadingOut = false;
        fadingIn = true;
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

    void closeActiveInterfacePanel() {
        rightPanel.closeActiveTab();
    }

    void toggleActiveInterfacePanel(TabType targetTab) {
        if (rightPanel.getActiveTab() == targetTab) {
            rightPanel.closeActiveTab();
            return;
        }
        inputHandler.closeInterfaces();
        rightPanel.openTab(targetTab);
        suppressHoverBriefly();
    }

    void suppressHoverBriefly() {
        ignoreHoverUntil = System.currentTimeMillis() + PANEL_HOVER_SUPPRESS_MS;
        setHoverText(null);
        if (inputHandler != null) {
            inputHandler.clearUiHoverState();
        }
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
        double startX  = getXpTrackerX() + 60;
        double startY  = XP_DROP_START_Y + lane * 18;   // starts higher, uses the constant
        double targetX = getXpTrackerX() + 60;
        double targetY = XP_TRACKER_Y + 11;
        xpDrops.add(new XpDrop(startX, startY, "+" + xp + " " + skillName, FloatingText.COLOR_SKILL, targetX, targetY));
    }

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public GamePanel(MusicManager musicManager, ClientSettings settings) {
        this.musicManager = musicManager;
        this.settings = settings;
        this.rightPanel = new RightPanel(settings);

        AssetManager.load();
        PlayerSpriteManager.load();
        if (Constants.EDITOR_MODE) {
            editorState.setAvailableObjects(editorActions.loadAvailableObjects());
        }

        loginScreen = new LoginScreen(
                Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT,
                (u, p) -> {
                    if (!clientConnection.isConnected()) { loginScreen.setStatus("Server offline."); return; }
                    pendingRegistrationAuth = false;
                    loginScreen.setStatus("Logging in...");
                    beginLoginTransition();
                    clientConnection.sendLogin(u, p);
                },
                (u, p) -> {
                    if (!clientConnection.isConnected()) { loginScreen.setStatus("Server offline."); return; }
                    pendingRegistrationAuth = true;
                    loginScreen.setStatus("Registering...");
                    beginLoginTransition();
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

        setBackground(Color.BLACK);
        setFocusable(true);

        world = new World();
        player = new Player(12 * Constants.TILE_SIZE, 9 * Constants.TILE_SIZE);
        mouseHandler = new MouseHandler();
        movementSystem    = new MovementSystem();
        woodcuttingSystem = new WoodcuttingSystem();
        miningSystem      = new MiningSystem();
        combatSystem      = new CombatSystem();
        attackSystem      = new AttackSystem();
        soundSystem       = new SoundSystem();
        gameLoop = new GameLoop(this);

        inputHandler = new GameInputHandler(this);
        renderer = new GameRenderer(this);
        setFullscreenState(false);
        setResizableState(false);

        setupInputListeners();
        setupNetworkListeners();

        rightPanel.setCombatStyleListener(style -> clientConnection.sendCombatStyle(style));
        rightPanel.setUnequipListener(slot -> clientConnection.sendUnequip(slot));
        rightPanel.setKeybindingsListener(() -> toggleActiveInterfacePanel(TabType.KEYBINDINGS));
        rightPanel.setKeybindingRebindListener(inputHandler::startKeybindingRebind);
        syncSettingsUi();

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
                if (Constants.EDITOR_MODE && SwingUtilities.isLeftMouseButton(e)) {
                    setPainting(false);
                    return;
                }
            }
        });

        addMouseWheelListener(e -> {
            if (Constants.EDITOR_MODE) {
                targetZoom = Math.max(0.5, Math.min(2.0,
                        targetZoom - e.getPreciseWheelRotation() * 0.1));
                repaint();
                return;
            }
            if (shopWindow.isOpen()) {
                shopWindow.handleScroll(e.getWheelRotation() > 0 ? 1 : -1);
            } else if (e.getX() >= getPanelX()) {
                rightPanel.handleMouseWheel(e.getWheelRotation() > 0 ? 1 : -1);
            } else {
                double nextTargetZoom = Math.max(0.5, Math.min(2.0,
                        targetZoom - e.getPreciseWheelRotation() * 0.1));
                recenterCameraOnZoomIn = nextTargetZoom > targetZoom;
                targetZoom = nextTargetZoom;
                repaint();
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                inputHandler.hoverX = e.getX();
                inputHandler.hoverY = e.getY();
                if (shouldRenderLoginScreen()) { loginScreen.handleMouseMove(e.getX(), e.getY()); return; }
                if (isPreGameState()) { return; }
                questCompleteWindow.handleMouseMove(e.getX(), e.getY());
                shopWindow.handleMouseMove(e.getX(), e.getY());
                rightPanel.handleMouseMove(e.getX(), e.getY(), getPanelX());
                inputHandler.updateCursorForHover(e.getX(), e.getY());
            }

            @Override public void mouseDragged(MouseEvent e) {
                inputHandler.hoverX = e.getX();
                inputHandler.hoverY = e.getY();
                rightPanel.handleMouseMove(e.getX(), e.getY(), getPanelX());
                if (Constants.EDITOR_MODE && isPainting()) {
                    inputHandler.paintTile();
                    return;
                }
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
            @Override public void keyPressed(KeyEvent e)  { inputHandler.handleKey(e); }
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
                clientState        = ClientState.LOGIN;
                fadeAlpha          = 0f;
                fadingIn           = false;
                fadingOut          = false;
                pendingEnterGame   = false;
                loadingOverlayTimer = 0.0;
                if (authStateListener != null) authStateListener.accept(false);
                pendingRegistrationAuth = false;
                currentAccountName = "";
                totalXp            = 0L;
                xpDrops.clear();
                closeContextMenu();
                loginScreen.reset();
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

                clientState        = ClientState.LOGIN;
                fadeAlpha          = 0f;
                fadingIn           = false;
                fadingOut          = false;
                pendingEnterGame   = false;
                loadingOverlayTimer = 0.0;
                if (authStateListener != null) authStateListener.accept(false);
                pendingRegistrationAuth = false;
                currentAccountName = "";
                totalXp            = 0L;
                xpDrops.clear();
                closeContextMenu();
                loginScreen.setStatus("Server offline. Reconnecting...");
                if (disconnectListener != null) disconnectListener.run();
            }

            @Override
            public void onLoggedOut() {
                boolean shouldShowLogout = !currentAccountName.isEmpty()
                        && (clientState == ClientState.IN_GAME
                        || clientState == ClientState.LOADING
                        || pendingEnterGame);
                inputHandler.stopAllActivities();
                gameState = GameState.PLAYING;
                chatBox.clearDialogue();
                shopWindow.hide();
                questCompleteWindow.close();
                rightPanel.setQuestEntries(java.util.Collections.emptyList());
                player.applyEquipmentUpdate(null);

                clientState                  = ClientState.LOGIN;
                fadeAlpha                    = 0f;
                fadingIn                     = false;
                fadingOut                    = false;
                pendingEnterGame             = false;
                loadingOverlayTimer          = 0.0;
                if (authStateListener != null) authStateListener.accept(false);
                pendingRegistrationAuth      = false;
                currentAccountName           = "";
                totalXp                      = 0L;
                xpDrops.clear();
                closeContextMenu();
                inputHandler.logoutConfirmTimer = 0;
                inputHandler.isTypingChat    = false;
                inputHandler.chatInput.setLength(0);

                loginScreen.reset();
                if (shouldShowLogout) {
                    chatBox.post("Logged out.", ChatBox.COLOR_SYSTEM);
                }
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
        clientConnection.setStartGatheringListener(parts ->
                inputHandler.startServerApprovedGathering(parts[0], parts[1]));
        clientConnection.setGatherFailListener(message -> {
            inputHandler.stopAllActivities();
            showMessage(message);
        });
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
            inputHandler.closeInterfaces();
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
            inputHandler.closeDialogue();
            questCompleteWindow.softClose();
            rightPanel.openTab(TabType.INVENTORY);
            shopWindow.open(shop);
            showMessage("Shop opened.");
        });

        clientConnection.setAuthSuccessListener(username -> {
            boolean firstLogin = pendingRegistrationAuth;
            if (authStateListener != null) authStateListener.accept(true);
            pendingRegistrationAuth = false;
            currentAccountName = username;
            inputHandler.isTypingChat = false;
            inputHandler.chatInput.setLength(0);
            loginScreen.reset();
            musicManager.stop();
            chatBox.post(firstLogin
                    ? "Welcome, " + username + "!"
                    : "Welcome back, " + username + "!", ChatBox.COLOR_SYSTEM);
            if (loginSuccessListener != null) loginSuccessListener.accept(username);
            pendingEnterGame = true;
            if (!fadingOut && fadeAlpha >= 1f) {
                enterLoadingState();
            } else {
                clientState = ClientState.LOADING;
            }
        });

        clientConnection.setAuthFailureListener(message -> {
            clientState = ClientState.LOGIN;
            fadeAlpha = 0f;
            fadingIn = false;
            fadingOut = false;
            pendingEnterGame = false;
            loadingOverlayTimer = 0.0;
            if (authStateListener != null) authStateListener.accept(false);
            pendingRegistrationAuth = false;
            loginScreen.setStatus(message);
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
        if (fadingOut) {
            fadeAlpha = Math.min(1f, fadeAlpha + (float) (deltaTime * FADE_SPEED));
            if (fadeAlpha >= 1f) {
                fadeAlpha = 1f;
                fadingOut = false;
                enterLoadingState();
            }
        } else if (fadingIn) {
            fadeAlpha = Math.max(0f, fadeAlpha - (float) (deltaTime * FADE_SPEED));
            if (fadeAlpha <= 0f) {
                fadeAlpha = 0f;
                fadingIn = false;
            }
        }

        if (clientState == ClientState.LOADING) {
            loadingOverlayTimer = Math.max(0.0, loadingOverlayTimer - deltaTime);
            if (pendingEnterGame && !fadingOut && loadingOverlayTimer <= 0.0) {
                beginFadeInToGame();
            }
        }

        if (Constants.EDITOR_MODE) {
            soundSystem.setEnabled(false);
            if (musicManager != null) musicManager.stop();
            inputHandler.stopAllActivities();
        } else {
            soundSystem.setEnabled(true);
            if (!clientConnection.isConnected() || !isInGame()) {
                inputHandler.stopAllActivities();
                player.getAnimation().setState(Animation.State.IDLE);
            } else if (gameState == GameState.PLAYING) {
                attackSystem.update(deltaTime);
                // Movement is locked while an attack animation is playing.
                if (!attackSystem.isAttacking()) {
                    movementSystem.update(player, mouseHandler, deltaTime);
                }
                inputHandler.updateInteractions(deltaTime);

                if (player.isDead()) handlePlayerDeath();
                updateAnimation(deltaTime);
            }
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

        clientConnection.sendPingIfDue();
        if (isInGame()) clientConnection.sendPosition((int) player.getX(), (int) player.getY());
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
            if (mx >= 0 && mx < getViewportW() && my >= 0 && my < getHeight()) {
                double vpCx = getViewportW() / 2.0;
                double vpCy = getHeight() / 2.0;
                cameraZoomOffsetX += (mx - vpCx) * (1.0 / oldZoom - 1.0 / zoom);
                cameraZoomOffsetY += (my - vpCy) * (1.0 / oldZoom - 1.0 / zoom);
            }
        }

        if (recenterCameraOnZoomIn) {
            double factor = Math.min(1.0, deltaTime * 10.0);
            cameraZoomOffsetX += (0.0 - cameraZoomOffsetX) * factor;
            cameraZoomOffsetY += (0.0 - cameraZoomOffsetY) * factor;
            if (Math.abs(cameraZoomOffsetX) < 0.5 && Math.abs(cameraZoomOffsetY) < 0.5) {
                cameraZoomOffsetX = 0.0;
                cameraZoomOffsetY = 0.0;
                recenterCameraOnZoomIn = false;
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
        // ATTACKING overrides everything else — animation data comes from AttackSystem.
        if (attackSystem.isAttacking()) {
            player.getAnimation().setAttackData(
                    attackSystem.getAnimName(), attackSystem.getCurrentFrame());
            // AttackSystem manages its own timer; no tick() call needed here.
            return;
        }

        Animation.State anim;
        if      (combatSystem.isInCombat())       anim = Animation.State.FIGHTING;
        else if (woodcuttingSystem.isChopping())  anim = Animation.State.CHOPPING;
        else if (miningSystem.isMining())         anim = Animation.State.MINING;
        else if (movementSystem.isMoving())       anim = Animation.State.WALKING;
        else                                      anim = Animation.State.IDLE;

        player.getAnimation().setState(anim);
        // Only advance the timer when an action/movement is active.
        if (anim != Animation.State.IDLE) {
            player.getAnimation().tick(deltaTime);
        }
    }

    // -----------------------------------------------------------------------
    //  Player death
    // -----------------------------------------------------------------------

    private void handlePlayerDeath() {
        if (deathHandled) return;
        deathHandled = true;
        attackSystem.stopAttack();
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
            // Stop just below the XP tracker box (tracker top + height + small gap).
            int stopY = XP_TRACKER_Y + 22 + 6;
            return alpha <= 0f || y < stopY || timer <= 0;
        }
    }

    public TileMap getTileMap() {
        return tileMap;
    }

    public void setHoveredTile(int x, int y) {
        this.hoveredTileX = x;
        this.hoveredTileY = y;
    }

    public int getHoveredTileX() { return hoveredTileX; }
    public int getHoveredTileY() { return hoveredTileY; }

    public int getSelectedTileId() { return editorState.getSelectedTileId(); }
    public void setSelectedTileId(int id) { editorState.setSelectedTileId(id); }

    public boolean isPainting() { return editorState.isPainting(); }
    public void setPainting(boolean painting) { editorState.setPainting(painting); }

    // -----------------------------------------------------------------------
    //  Editor — map operations (delegate to EditorActions)
    // -----------------------------------------------------------------------

    public void newMap(int width, int height) {
        editorActions.newMap(tileMap, editorState);
        repaint();
    }

    public void saveMap(String filePath) {
        showMessage(editorActions.saveMap(tileMap, editorState.getObjects(), filePath));
    }

    public void loadMap(String filePath) {
        if (editorActions.loadMap(tileMap, editorState, filePath)) {
            repaint();
            showMessage("Map loaded from " + filePath);
        } else {
            showMessage("Load failed: " + filePath);
        }
    }

}
