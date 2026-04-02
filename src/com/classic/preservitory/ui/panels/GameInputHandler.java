package com.classic.preservitory.ui.panels;

import com.classic.preservitory.client.definitions.ItemDefinitionManager;
import com.classic.preservitory.entity.Enemy;
import com.classic.preservitory.entity.Entity;
import com.classic.preservitory.entity.NPC;
import com.classic.preservitory.client.settings.ClientSettings;
import com.classic.preservitory.system.CombatSystem;
import com.classic.preservitory.system.Pathfinding;
import com.classic.preservitory.system.SoundSystem;
import com.classic.preservitory.ui.overlays.ChatBox;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;
import com.classic.preservitory.world.objects.Loot;
import com.classic.preservitory.world.objects.Rock;
import com.classic.preservitory.world.objects.Tree;

import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * Handles all player input (keyboard, mouse) and manages active interaction
 * targets and their per-frame update logic.
 *
 * This class is an internal collaborator of GamePanel; it accesses GamePanel
 * fields directly via package-private visibility.
 */
class GameInputHandler {

    private final GamePanel panel;

    // -------------------------------------------------------------------------
    //  Active interaction targets (at most one per category at a time)
    // -------------------------------------------------------------------------

    Tree  activeTree;
    Rock  activeRock;
    Enemy activeEnemy;
    NPC   activeNPC;
    Loot  activeLoot;

    // -------------------------------------------------------------------------
    //  Chat input state
    // -------------------------------------------------------------------------

    boolean isTypingChat = false;
    final StringBuilder chatInput = new StringBuilder();

    // -------------------------------------------------------------------------
    //  Logout confirm state
    // -------------------------------------------------------------------------

    double logoutConfirmTimer = 0;
    private boolean shiftDown = false;

    // -------------------------------------------------------------------------
    //  Hover position (screen-space, updated by mouse motion)
    // -------------------------------------------------------------------------

    int hoverX = -1;
    int hoverY = -1;
    int hoveredTabIndex = -1;
    private boolean listeningForKeybind = false;
    private ClientSettings.Action selectedAction;
    // -------------------------------------------------------------------------

    GameInputHandler(GamePanel panel) {
        this.panel = panel;
    }

    // -------------------------------------------------------------------------
    //  Keyboard
    // -------------------------------------------------------------------------

    void handleKey(java.awt.event.KeyEvent e) {
        int keyCode = e.getKeyCode();
        if (listeningForKeybind) {
            if (keyCode == KeyEvent.VK_ESCAPE) {
                listeningForKeybind = false;
                selectedAction = null;
                panel.rightPanel.setListeningKeybindingAction(null);
                return;
            }
            if (selectedAction != null) {
                panel.settings.setKeyBinding(selectedAction, keyCode);
                panel.settings.save();
            }
            listeningForKeybind = false;
            selectedAction = null;
            panel.rightPanel.setListeningKeybindingAction(null);
            return;
        }
        if (handlePanelKeybind(keyCode)) {
            return;
        }
        if (keyCode == KeyEvent.VK_ESCAPE && panel.rightPanel.hasActiveTab()) {
            panel.closeActiveInterfacePanel();
            return;
        }
        if (panel.shouldRenderLoginScreen() && !Constants.EDITOR_MODE) {
            panel.loginScreen.handleKey(keyCode);
            return;
        }
        if (panel.isPreGameState() && !Constants.EDITOR_MODE) {
            return;
        }

        // Chat-input mode: intercept all keys so game shortcuts don't fire
        if (isTypingChat) {
            switch (keyCode) {
                case KeyEvent.VK_ENTER:
                    sendChatInput();
                    break;
                case KeyEvent.VK_ESCAPE:
                    isTypingChat = false;
                    chatInput.setLength(0);
                    break;
                case KeyEvent.VK_BACK_SPACE:
                    if (chatInput.length() > 0)
                        chatInput.deleteCharAt(chatInput.length() - 1);
                    break;
                default:
                    break;
            }
            return;
        }

        // Dialogue mode: SPACE advances; 1–4 selects options
        if (panel.gameState == GamePanel.GameState.IN_DIALOGUE) {
            if (keyCode == KeyEvent.VK_SPACE) {
                if (!panel.chatBox.hasOptions()) panel.clientConnection.sendDialogueNext();
                return;
            }
            if (keyCode >= KeyEvent.VK_1 && keyCode <= KeyEvent.VK_4) {
                int idx = keyCode - KeyEvent.VK_1;
                if (panel.chatBox.hasOptions() && idx < panel.chatBox.getOptionCount()) {
                    panel.clientConnection.sendDialogueOption(idx);
                }
                return;
            }
        }

        // Normal mode
        if (Constants.EDITOR_MODE) {
            boolean ctrl = (e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0;
            if (ctrl && keyCode == KeyEvent.VK_Z) { System.out.println("UNDO PRESSED"); panel.editorActions.undo(panel.getTileMap(), panel.editorState); panel.repaint(); return; }
            if (ctrl && keyCode == KeyEvent.VK_R) { System.out.println("REDO PRESSED"); panel.editorActions.redo(panel.getTileMap(), panel.editorState); panel.repaint(); return; }
            if (keyCode == KeyEvent.VK_1) panel.setSelectedTileId(0); // grass
            if (keyCode == KeyEvent.VK_2) panel.setSelectedTileId(1); // path
            if (keyCode == KeyEvent.VK_3) panel.setSelectedTileId(2); // water
            if (keyCode == KeyEvent.VK_4) panel.setSelectedTileId(3); // stone
            if (!ctrl && keyCode == KeyEvent.VK_R) {
                int rot = (panel.editorState.getSelectedRotation() + 90) % 360;
                panel.editorState.setSelectedRotation(rot);
                panel.repaint();
                return;
            }
            if (keyCode == KeyEvent.VK_N) panel.newMap(64, 64);
            if (keyCode == KeyEvent.VK_S) panel.saveMap("map.json");
            if (keyCode == KeyEvent.VK_L) panel.loadMap("map.json");
        }

        switch (keyCode) {
            case KeyEvent.VK_ENTER:
                if (panel.gameState == GamePanel.GameState.PLAYING) {
                    isTypingChat = true;
                    chatInput.setLength(0);
                    stopAllActivities();
                }
                break;

            case KeyEvent.VK_D:
                panel.debugMode = !panel.debugMode;
                break;

            case KeyEvent.VK_M:
                panel.soundSystem.setEnabled(!panel.soundSystem.isEnabled());
                panel.showMessage("Sound " + (panel.soundSystem.isEnabled() ? "ON" : "OFF"));
                break;

            case KeyEvent.VK_ESCAPE:
                if (panel.questCompleteWindow.isOpen()) {
                    panel.questCompleteWindow.close();
                    break;
                }
                if (panel.shopWindow.isOpen()) {
                    panel.shopWindow.close();
                    break;
                }
                if (panel.gameState == GamePanel.GameState.IN_DIALOGUE) {
                    panel.gameState = GamePanel.GameState.PLAYING;
                    panel.chatBox.clearDialogue();
                }
                break;
        }
    }

    void handleCharTyped(char c) {
        if (panel.shouldRenderLoginScreen()) {
            panel.loginScreen.handleChar(c);
            return;
        }
        if (panel.isPreGameState() && !Constants.EDITOR_MODE) return;
        if (!isTypingChat) return;
        if (c < 32 || c > 126) return;
        if (chatInput.length() < 80) chatInput.append(c);
    }

    void sendChatInput() {
        String raw = chatInput.toString();
        isTypingChat = false;
        chatInput.setLength(0);

        if (raw.startsWith("/")) {
            panel.clientConnection.sendChatMessage(raw);
            return;
        }

        String msg = com.classic.preservitory.util.ChatFilter.filter(raw);
        if (msg == null) {
            panel.chatBox.post("Invalid message.", ChatBox.COLOR_SYSTEM);
            return;
        }

        if (!panel.clientConnection.isConnected()) {
            panel.loginScreen.setStatus("Server offline.");
            return;
        }

        panel.clientConnection.sendChatMessage(msg);
    }

    // -------------------------------------------------------------------------
    //  Mouse click routing
    // -------------------------------------------------------------------------

    void handleMousePress(MouseEvent e) {
        int cx = e.getX();
        int cy = e.getY();

        if (Constants.EDITOR_MODE) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                if (clickInsideEditorPanel(cx, cy)) {
                    handleEditorClick(cx, cy);
                    return;
                }
                if (panel.editorState.getSelectedObjectKey() != null) {
                    placeObject();
                } else {
                    panel.setPainting(true);
                    paintTile();
                }
            }
            return;
        }

        shiftDown = e.isShiftDown();

        if (SwingUtilities.isRightMouseButton(e)) {
            //System.out.println("RIGHT CLICK");
            handleRightClick(cx, cy);
            return;
        }

        if (SwingUtilities.isLeftMouseButton(e)) {
            handleLeftClick(cx, cy);
        }
    }

    private void handleLeftClick(int cx, int cy) {
        if (panel.shouldRenderLoginScreen()) {
            panel.loginScreen.handleClick(cx, cy);
            return;
        }
        if (panel.isPreGameState() && !Constants.EDITOR_MODE) {
            return;
        }

        if (panel.rightPanel.getActiveTab() == TabType.SETTINGS
                && !panel.rightPanel.isInsideSettingsPanel(cx, cy)
                && !panel.settingsCogBounds.contains(cx, cy)) {
            panel.closeActiveInterfacePanel();
        }

        if (panel.settingsCogBounds.contains(cx, cy)) {
            handleSettingsCogClick();
            return;
        }

        if (panel.contextMenuOpen) {
            int menuOption = getContextMenuOptionIndex(cx, cy);
            if (menuOption >= 0) {
                Runnable action = panel.contextMenuOptions.get(menuOption).action;
                panel.closeContextMenu();
                action.run();
            } else {
                panel.closeContextMenu();
            }
            return;
        }

        if (isTypingChat && cx < panel.getPanelX()) return;

        // ---- Right panel ----
        if (cx >= panel.getPanelX()) {
            if (cx >= panel.getLogoutBtnX() && cx < panel.getLogoutBtnX() + panel.getLogoutBtnW()
                    && cy >= panel.getLogoutBtnY() && cy < panel.getLogoutBtnY() + GamePanel.LOGOUT_BTN_H) {
                handleLogoutClick();
                return;
            }

            if (panel.gameState == GamePanel.GameState.IN_DIALOGUE) {
                closeDialogue();
            }

            // Tab bar click → switch active tab via TabManager
            if (cy >= RightPanel.TAB_Y && cy < RightPanel.CONTENT_Y) {
                int tabIndex = getHoveredTabIndex(cx, cy);
                if (tabIndex >= 0) {
                    TabConfig clickedTab = TabConfig.TABS.get(tabIndex);
                    panel.toggleActiveInterfacePanel(clickedTab.type);
                    if (panel.rightPanel.getActiveTab() == clickedTab.type) {
                        panel.rightPanel.onTabSelected(clickedTab.type);
                    }
                }
                return;
            }

            // Shift-click drop
            if (panel.settings.isShiftClickDrop() && shiftDown
                    && !panel.shopWindow.isOpen()
                    && panel.rightPanel.getActiveTab() == TabType.INVENTORY) {
                int dropItemId = panel.rightPanel.getClickedInventoryItemId(cx, cy, panel.player, panel.getPanelX());
                if (dropItemId >= 0) {
                    String name = com.classic.preservitory.client.definitions.ItemDefinitionManager.get(dropItemId).name;
                    handleDropItem(dropItemId, name);
                    return;
                }
            }

            // Shop sell via inventory click
            if (panel.shopWindow.isOpen()) {
                int sellItemId = panel.rightPanel.getClickedInventoryItemId(cx, cy, panel.player, panel.getPanelX());
                if (sellItemId >= 0 && panel.shopWindow.canSell(sellItemId)) {
                    panel.clientConnection.sendSell(sellItemId);
                    String name = com.classic.preservitory.client.definitions.ItemDefinitionManager.get(sellItemId).name;
                    panel.showMessage("Sell request sent: " + name);
                    return;
                }
            }

            // Inventory click → equip (only when shop is closed)
            if (!panel.shopWindow.isOpen()) {
                int equipItemId = panel.rightPanel.getClickedInventoryItemId(cx, cy, panel.player, panel.getPanelX());
                if (equipItemId >= 0) {
                    com.classic.preservitory.client.definitions.ItemDefinition def =
                            com.classic.preservitory.client.definitions.ItemDefinitionManager.get(equipItemId);
                    if (def.equipSlot != null) {
                        panel.clientConnection.sendEquip(equipItemId);
                        panel.showMessage("Equipping " + def.name);
                        return;
                    }
                }
            }

            panel.rightPanel.handleClick(cx, cy, panel.getPanelX());
            return;
        }

        // ---- Dialogue mode: chatbox area advances / selects options ----
        if (panel.gameState == GamePanel.GameState.IN_DIALOGUE) {
            int chatBoxTop = panel.getHeight() - GamePanel.CHAT_H;
            if (cy >= chatBoxTop) {
                if (panel.chatBox.hasOptions()) {
                    int optIndex = panel.chatBox.getOptionIndexAtY(cy - chatBoxTop);
                    if (optIndex >= 0) panel.clientConnection.sendDialogueOption(optIndex);
                } else {
                    panel.clientConnection.sendDialogueNext();
                }
                return;
            }
            closeDialogue();
        }

        // ---- PLAYING state ----

        if (panel.questCompleteWindow.isOpen()) {
            panel.questCompleteWindow.handleClick(cx, cy);
            return;
        }

        if (panel.shopWindow.isOpen() && panel.shopWindow.containsPoint(cx, cy)) {
            panel.shopWindow.handleClick(cx, cy);
            return;
        }

        if (!panel.clientConnection.isConnected()) {
            panel.loginScreen.setStatus("Server offline.");
            return;
        }

        // Convert screen click → world coordinates (account for zoom)
        int isoClickX    = adjustedX(cx) + panel.cameraOffsetX;
        int isoClickY    = adjustedY(cy) + panel.cameraOffsetY;
        int clickTileCol = Math.max(0, Math.min(panel.world.getCols() - 1,
                IsoUtils.isoToTileCol(isoClickX, isoClickY)));
        int clickTileRow = Math.max(0, Math.min(panel.world.getRows() - 1,
                IsoUtils.isoToTileRow(isoClickX, isoClickY)));
        int worldX = clickTileCol * Constants.TILE_SIZE + Constants.TILE_SIZE / 2;
        int worldY = clickTileRow * Constants.TILE_SIZE + Constants.TILE_SIZE / 2;

        // Click priority: NPC > Enemy > Tree > Rock > Loot > Ground
        NPC clickedNPC = panel.clientWorld.getNpcAt(worldX, worldY);
        if (clickedNPC != null) {
            activateNpc(clickedNPC);
            return;
        }

        Enemy clickedEnemy = panel.clientWorld.getEnemyAt(worldX, worldY);
        if (clickedEnemy != null) {
            activateEnemy(clickedEnemy);
            return;
        }

        Tree clickedTree = panel.clientWorld.getTreeAt(worldX, worldY);
        if (clickedTree != null) {
            activateTree(clickedTree);
            return;
        }

        Rock clickedRock = panel.clientWorld.getRockAt(worldX, worldY);
        if (clickedRock != null) {
            activateRock(clickedRock);
            return;
        }

        Loot clickedLoot = panel.clientWorld.getLootAt(worldX, worldY);
        if (clickedLoot != null) {
            activateLoot(clickedLoot);
            return;
        }

        // Ground: pathfind to the clicked tile
        closeTransientInterfacesForMovement();
        stopAllActivities();
        panel.clientConnection.sendClearPendingInteraction();
        int startCol = Pathfinding.pixelToTileCol(panel.player.getCenterX());
        int startRow = Pathfinding.pixelToTileRow(panel.player.getCenterY());
        List<Point> path = Pathfinding.findPath(startCol, startRow, clickTileCol, clickTileRow,
                panel.world, panel.clientWorld::isBlocked);
        if (!path.isEmpty()) {
            panel.movementSystem.setPath(path);
        } else {
            panel.mouseHandler.setTarget(worldX, worldY);
        }
    }

    private void handleSettingsCogClick() {
        if (panel.combatSystem.isInCombat()) {
            panel.showMessage("You are in combat.");
            return;
        }
        if (panel.movementSystem.isMoving()) {
            panel.movementSystem.clearPath();
            panel.mouseHandler.clearTarget();
        }
        panel.toggleActiveInterfacePanel(TabType.SETTINGS);
    }

    void startKeybindingRebind(ClientSettings.Action action) {
        listeningForKeybind = true;
        selectedAction = action;
        panel.rightPanel.setListeningKeybindingAction(action);
    }

    private void handleRightClick(int cx, int cy) {
        panel.closeContextMenu();

        if (panel.isPreGameState() || panel.gameState != GamePanel.GameState.PLAYING) {
            return;
        }

        if (cx >= panel.getPanelX()) {
            openInventoryContextMenu(cx, cy);
            return;
        }

        if (!panel.clientConnection.isConnected()) {
            return;
        }

        int isoClickX = adjustedX(cx) + panel.cameraOffsetX;
        int isoClickY = adjustedY(cy) + panel.cameraOffsetY;
        int clickTileCol = Math.max(0, Math.min(panel.world.getCols() - 1,
                IsoUtils.isoToTileCol(isoClickX, isoClickY)));
        int clickTileRow = Math.max(0, Math.min(panel.world.getRows() - 1,
                IsoUtils.isoToTileRow(isoClickX, isoClickY)));
        int worldX = clickTileCol * Constants.TILE_SIZE + Constants.TILE_SIZE / 2;
        int worldY = clickTileRow * Constants.TILE_SIZE + Constants.TILE_SIZE / 2;

        Loot loot = panel.clientWorld.getLootAt(worldX, worldY);
        if (loot != null) {
            List<GamePanel.ContextMenuOption> options = new ArrayList<>();
            options.add(new GamePanel.ContextMenuOption("Pick-up " + ItemDefinitionManager.get(loot.getItemId()).name,
                    () -> activateLoot(loot)));
            options.add(new GamePanel.ContextMenuOption("Examine " + ItemDefinitionManager.get(loot.getItemId()).name,
                    () -> panel.showMessage(buildLootExamineText(loot))));
            panel.openContextMenu(options, cx, cy);
            return;
        }

        NPC npc = panel.clientWorld.getNpcAt(worldX, worldY);
        if (npc != null) {
            panel.openContextMenu(List.of(
                    new GamePanel.ContextMenuOption("Talk-to " + npc.getName(), () -> activateNpc(npc))
            ), cx, cy);
            return;
        }

        Enemy enemy = panel.clientWorld.getEnemyAt(worldX, worldY);
        if (enemy != null) {
            panel.openContextMenu(List.of(
                    new GamePanel.ContextMenuOption("Attack " + enemy.getName(), () -> activateEnemy(enemy))
            ), cx, cy);
        }
    }

    // -------------------------------------------------------------------------
    //  Cursor
    // -------------------------------------------------------------------------

    void clearUiHoverState() {
        hoveredTabIndex = -1;
        panel.rightPanel.setHoveredTabIndex(-1);
    }

    void updateCursorForHover(int mx, int my) {
        if (Constants.EDITOR_MODE) { updateEditorTooltip(mx, my); return; }
        if (panel.isPreGameState()) {
            clearUiHoverState();
            panel.setHoverText(null);
            panel.setCursor(Cursor.getDefaultCursor());
            return;
        }
        panel.setHoverText(null);
        if (System.currentTimeMillis() < panel.ignoreHoverUntil) {
            clearUiHoverState();
            panel.setCursor(Cursor.getDefaultCursor());
            return;
        }
        if (panel.contextMenuOpen) {
            if (isFarFromContextMenu(mx, my)) {
                panel.closeContextMenu();
                panel.setCursor(Cursor.getDefaultCursor());
                return;
            }
            panel.setCursor(Cursor.getDefaultCursor());
        }
        hoveredTabIndex = getHoveredTabIndex(mx, my);
        panel.rightPanel.setHoveredTabIndex(hoveredTabIndex);
        if (hoveredTabIndex >= 0) {
            panel.setHoverText(getTabHoverText(hoveredTabIndex));
            panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return;
        }

        if (panel.settingsCogBounds.contains(mx, my)) {
            panel.setHoverText("Settings");
            panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return;
        }

        if (panel.shopWindow.isOpen() && panel.shopWindow.containsPoint(mx, my)) {
            String shopItemName = panel.shopWindow.getHoveredItemName();
            if (shopItemName != null) {
                panel.setHoverText("Buy " + shopItemName);
                panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                return;
            }
            panel.setCursor(Cursor.getDefaultCursor());
            return;
        }

        if (mx >= panel.getPanelX()) {
            if (mx >= panel.getLogoutBtnX() && mx < panel.getLogoutBtnX() + panel.getLogoutBtnW()
                    && my >= panel.getLogoutBtnY() && my < panel.getLogoutBtnY() + GamePanel.LOGOUT_BTN_H) {
                panel.setHoverText("Logout");
                panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                return;
            }

            String buttonLabel = panel.rightPanel.getHoveredButtonLabel(mx, my, panel.getPanelX());
            if (buttonLabel != null) {
                panel.setHoverText(buttonLabel);
                panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                return;
            }

            String inventoryItemName = panel.rightPanel.getHoveredInventoryItemName(panel.player);
            if (inventoryItemName != null) {
                panel.setHoverText(panel.shopWindow.isOpen()
                        ? "Sell " + inventoryItemName
                        : inventoryItemName);
                panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                return;
            }
            panel.setCursor(Cursor.getDefaultCursor());
            return;
        }

        if (mx >= panel.getPanelX() || panel.gameState != GamePanel.GameState.PLAYING) {
            panel.setCursor(Cursor.getDefaultCursor());
            return;
        }

        int isoX   = adjustedX(mx) + panel.cameraOffsetX;
        int isoY   = adjustedY(my) + panel.cameraOffsetY;
        int col    = Math.max(0, Math.min(panel.world.getCols() - 1, IsoUtils.isoToTileCol(isoX, isoY)));
        int row    = Math.max(0, Math.min(panel.world.getRows() - 1, IsoUtils.isoToTileRow(isoX, isoY)));
        int worldX = col * Constants.TILE_SIZE + Constants.TILE_SIZE / 2;
        int worldY = row * Constants.TILE_SIZE + Constants.TILE_SIZE / 2;

        NPC hoveredNpc = panel.clientWorld.getNpcAt(worldX, worldY);
        if (hoveredNpc != null) {
            panel.setHoverText("Talk-to " + hoveredNpc.getName());
            panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return;
        }

        Enemy hoveredEnemy = panel.clientWorld.getEnemyAt(worldX, worldY);
        if (hoveredEnemy != null) {
            panel.setHoverText("Attack " + hoveredEnemy.getName());
            panel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            return;
        }

        Tree hoveredTree = panel.clientWorld.getTreeAt(worldX, worldY);
        if (hoveredTree != null) {
            panel.setHoverText("Chop " + getTreeDisplayName(hoveredTree));
            panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return;
        }

        Rock hoveredRock = panel.clientWorld.getRockAt(worldX, worldY);
        if (hoveredRock != null) {
            panel.setHoverText("Mine " + getRockDisplayName(hoveredRock));
            panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return;
        }

        Loot hoveredLoot = panel.clientWorld.getLootAt(worldX, worldY);
        if (hoveredLoot != null) {
            panel.setHoverText("Pick-up " + ItemDefinitionManager.get(hoveredLoot.getItemId()).name);
            panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return;
        }

        panel.setCursor(Cursor.getDefaultCursor());
    }

    private String getTabHoverText(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= TabConfig.TABS.size()) {
            return null;
        }
        return switch (TabConfig.TABS.get(tabIndex).type) {
            case COMBAT -> "Combat";
            case INVENTORY -> "Inventory";
            case SKILLS -> "Skills";
            case EQUIPMENT -> "Equipment";
            case QUESTS -> "Quests";
            case SETTINGS -> "Settings";
            case KEYBINDINGS, NONE -> null;
        };
    }

    private boolean handlePanelKeybind(int keyCode) {
        for (ClientSettings.Action action : ClientSettings.Action.values()) {
            if (keyCode == panel.settings.getKeyBinding(action)) {
                panel.toggleActiveInterfacePanel(mapActionToTab(action));
                return true;
            }
        }
        return false;
    }

    private TabType mapActionToTab(ClientSettings.Action action) {
        return switch (action) {
            case COMBAT -> TabType.COMBAT;
            case INVENTORY -> TabType.INVENTORY;
            case SKILLS -> TabType.SKILLS;
            case EQUIPMENT -> TabType.EQUIPMENT;
            case QUESTS -> TabType.QUESTS;
            case SETTINGS -> TabType.SETTINGS;
        };
    }

    private String getTreeDisplayName(Tree tree) {
        String typeId = tree.getTypeId();
        if (typeId == null || typeId.isBlank()) {
            return "Tree";
        }

        String[] words = typeId.split("_");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                name.append(word.substring(1));
            }
        }
        return name.toString();
    }

    private String getRockDisplayName(Rock rock) {
        String typeId = rock.getTypeId();
        if (typeId == null || typeId.isBlank()) {
            return "Rock";
        }
        String[] words = typeId.split("_");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                name.append(word.substring(1));
            }
        }
        return name.toString();
    }

    // -------------------------------------------------------------------------
    //  Interaction helpers
    // -------------------------------------------------------------------------

    private void activateNpc(NPC npc) {
        beginInteractionPath(npc);
        activeNPC = npc;
    }

    private void activateEnemy(Enemy enemy) {
        beginInteractionPath(enemy);
        activeEnemy = enemy;
        panel.clientConnection.sendAttack(enemy.getId());
    }

    private void activateTree(Tree tree) {
        beginInteractionPath(tree);
        activeTree = tree;
    }

    private void activateRock(Rock rock) {
        beginInteractionPath(rock);
        activeRock = rock;
    }

    private void activateLoot(Loot loot) {
        beginInteractionPath(loot);
        activeLoot = loot;
    }

    private String buildLootExamineText(Loot loot) {
        String itemName = ItemDefinitionManager.get(loot.getItemId()).name;
        return loot.getCount() > 1
                ? itemName + " x" + loot.getCount() + "."
                : itemName + ".";
    }

    private void openInventoryContextMenu(int cx, int cy) {
        if (panel.rightPanel.getActiveTab() != TabType.INVENTORY) return;
        int itemId = panel.rightPanel.getClickedInventoryItemId(cx, cy, panel.player, panel.getPanelX());
        if (itemId < 0) return;
        String itemName = com.classic.preservitory.client.definitions.ItemDefinitionManager.get(itemId).name;

        List<GamePanel.ContextMenuOption> options = new ArrayList<>();
        options.add(new GamePanel.ContextMenuOption("Use " + itemName, () -> handleUseItem(itemId, itemName)));
        options.add(new GamePanel.ContextMenuOption("Drop " + itemName, () -> handleDropItem(itemId, itemName)));
        options.add(new GamePanel.ContextMenuOption("Examine " + itemName, () -> panel.showMessage(itemName)));
        panel.openContextMenu(options, cx, cy);
    }

    private void handleUseItem(int itemId, String itemName) {
        panel.clientConnection.sendUse(itemId);
        panel.showMessage("Use " + itemName);
    }

    private void handleDropItem(int itemId, String itemName) {
        // Optimistic client removal — full stack for stackable, 1 for non-stackable.
        int amount = 1;
        for (com.classic.preservitory.item.Item slot : panel.player.getInventory().getSlots()) {
            if (slot != null && slot.getItemId() == itemId) {
                if (slot.isStackable()) amount = slot.getCount();
                break;
            }
        }
        panel.player.getInventory().removeItem(itemId, amount);
        panel.clientConnection.sendDrop(itemId);
        panel.showMessage("You drop the " + itemName + ".");
    }

    int getContextMenuOptionIndex(int mx, int my) {
        if (!panel.contextMenuOpen || panel.contextMenuOptions.isEmpty()) {
            return -1;
        }

        int menuWidth = getContextMenuWidth();
        int menuHeight = getContextMenuHeight();
        int menuX = getContextMenuX(menuWidth);
        int menuY = getContextMenuY(menuHeight);
        if (mx < menuX || mx >= menuX + menuWidth || my < menuY || my >= menuY + menuHeight) {
            return -1;
        }
        return (my - menuY) / GamePanel.CONTEXT_MENU_OPTION_H;
    }

    int getContextMenuWidth() {
        Font font = new Font("Arial", Font.PLAIN, 11);
        int width = 0;
        FontMetrics metrics = panel.getFontMetrics(font);
        for (GamePanel.ContextMenuOption option : panel.contextMenuOptions) {
            width = Math.max(width, metrics.stringWidth(option.label));
        }
        return Math.max(90, width + 10);
    }

    int getContextMenuHeight() {
        return panel.contextMenuOptions.size() * GamePanel.CONTEXT_MENU_OPTION_H;
    }

    int getContextMenuX(int menuWidth) {
        int maxX = Math.max(4, panel.getWidth() - menuWidth - 4);
        return Math.max(4, Math.min(panel.contextMenuX, maxX));
    }

    int getContextMenuY(int menuHeight) {
        int maxY = Math.max(4, panel.getHeight() - menuHeight - 4);
        return Math.max(4, Math.min(panel.contextMenuY, maxY));
    }

    private boolean isFarFromContextMenu(int mx, int my) {
        int menuWidth = getContextMenuWidth();
        int menuHeight = getContextMenuHeight();
        int menuX = getContextMenuX(menuWidth);
        int menuY = getContextMenuY(menuHeight);
        int margin = 28;
        return mx < menuX - margin || mx > menuX + menuWidth + margin
                || my < menuY - margin || my > menuY + menuHeight + margin;
    }

    private void beginInteractionPath(Entity target) {
        closeTransientInterfacesForMovement();
        stopAllActivities();
        panel.player.faceTarget(target);
        setApproachTarget(target);
    }

    private boolean isEnemyStillPresent(String enemyId) {
        for (Enemy enemy : panel.clientWorld.getEnemies()) {
            if (enemyId.equals(enemy.getId()) && enemy.isAlive()) {
                return true;
            }
        }
        return false;
    }

    private boolean isLootStillPresent(String lootId) {
        for (Loot loot : panel.clientWorld.getLoot()) {
            if (lootId.equals(loot.getId())) {
                return true;
            }
        }
        return false;
    }

    private void setApproachTarget(Entity target) {
        double dx   = panel.player.getCenterX() - target.getCenterX();
        double dy   = panel.player.getCenterY() - target.getCenterY();
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 1) dist = 1;

        int targetCol = Pathfinding.pixelToTileCol(target.getCenterX());
        int targetRow = Pathfinding.pixelToTileRow(target.getCenterY());
        int offCol    = (int) Math.round(dx / dist);
        int offRow    = (int) Math.round(dy / dist);
        int goalCol   = Math.max(0, Math.min(panel.world.getCols() - 1, targetCol + offCol));
        int goalRow   = Math.max(0, Math.min(panel.world.getRows() - 1, targetRow + offRow));

        int startCol = Pathfinding.pixelToTileCol(panel.player.getCenterX());
        int startRow = Pathfinding.pixelToTileRow(panel.player.getCenterY());

        List<Point> path = Pathfinding.findPath(startCol, startRow, goalCol, goalRow,
                panel.world, panel.clientWorld::isBlocked);
        if (!path.isEmpty()) {
            panel.movementSystem.setPath(path);
            panel.mouseHandler.clearTarget();
        } else {
            int approachX = (int)(target.getCenterX() + (dx / dist) * Constants.TILE_SIZE * 1.5);
            int approachY = (int)(target.getCenterY() + (dy / dist) * Constants.TILE_SIZE * 1.5);
            panel.mouseHandler.setTarget(approachX, approachY);
        }
    }

    void stopAllActivities() {
        panel.closeContextMenu();
        panel.woodcuttingSystem.stopChopping();
        panel.miningSystem.stopMining();
        panel.combatSystem.stopCombat();
        panel.movementSystem.clearPath();
        panel.mouseHandler.clearTarget();
        activeTree  = null;
        activeRock  = null;
        activeEnemy = null;
        activeNPC   = null;
        activeLoot  = null;
    }

    void closeDialogue() {
        if (panel.gameState == GamePanel.GameState.IN_DIALOGUE) {
            panel.gameState = GamePanel.GameState.PLAYING;
            panel.chatBox.clearDialogue();
        }
    }

    void closeInterfaces() {
        closeDialogue();
        panel.closeActiveInterfacePanel();
        if (panel.shopWindow.isOpen()) panel.shopWindow.close();
        panel.questCompleteWindow.softClose();
    }

    void closeTransientInterfacesForMovement() {
        closeDialogue();
        panel.questCompleteWindow.softClose();
    }

    private void handleLogoutClick() {
        if (logoutConfirmTimer > 0) {
            performLogout();
        } else {
            logoutConfirmTimer = GamePanel.LOGOUT_CONFIRM_TIMEOUT;
        }
    }

    private void performLogout() {
        logoutConfirmTimer = 0;
        closeInterfaces();
        stopAllActivities();
        panel.clientConnection.disconnect();
    }

    // -------------------------------------------------------------------------
    //  Per-frame interaction updates (called from GamePanel.update)
    // -------------------------------------------------------------------------

    void updateInteractions(double deltaTime) {
        updateTreeInteraction(deltaTime);
        updateRockInteraction(deltaTime);
        updateCombat(deltaTime);
        updateLootInteraction();
        updateNPCInteraction();
    }

    void tickLogoutTimer(double deltaTime) {
        if (logoutConfirmTimer > 0)
            logoutConfirmTimer = Math.max(0, logoutConfirmTimer - deltaTime);
    }

    private void updateTreeInteraction(double deltaTime) {
        if (activeTree == null) return;
        if (!activeTree.isAlive()) {
            panel.woodcuttingSystem.stopChopping();
            activeTree = null;
            return;
        }
        if (isWithinInteractionRange(panel.player, activeTree)) {
            if (panel.woodcuttingSystem.isChopping()) {
                return;
            }
            sendChopRequest();
        }
    }

    private void sendChopRequest() {
        if (activeTree != null) panel.clientConnection.sendChop(activeTree.getId());
        activeTree = null;
    }

    private void updateRockInteraction(double deltaTime) {
        if (activeRock == null) return;
        if (!activeRock.isSolid()) {
            panel.miningSystem.stopMining();
            activeRock = null;
            return;
        }
        if (isWithinInteractionRange(panel.player, activeRock)) {
            sendMineRequest();
        }
    }

    private void sendMineRequest() {
        String rockId = (activeRock != null) ? activeRock.getId() : null;
        if (rockId != null) panel.clientConnection.sendMine(rockId);
        activeRock = null;
    }

    void startServerApprovedGathering(String skillType, String objectId) {
        if ("woodcutting".equalsIgnoreCase(skillType)) {
            Tree tree = panel.clientWorld.getTree(objectId);
            if (tree == null || !tree.isAlive()) {
                return;
            }
            activeTree = tree;
            panel.woodcuttingSystem.startChopping(tree);
            panel.soundSystem.play(SoundSystem.Sound.CHOP);
            panel.showMessage("You swing your axe...");
        }
    }

    private void updateCombat(double deltaTime) {
        if (activeEnemy == null) return;
        if (!activeEnemy.isAlive()) {
            panel.combatSystem.stopCombat();
            activeEnemy = null;
            return;
        }
        if (isWithinInteractionRange(panel.player, activeEnemy)) {
            if (!panel.combatSystem.isInCombat()) {
                closeInterfaces();
                panel.combatSystem.startCombat(activeEnemy);
                panel.showMessage("You attack the " + activeEnemy.getName() + "!");
            }
            CombatSystem.CombatResult result = panel.combatSystem.update(panel.player, deltaTime);
            if (result != null) applyCombatResult();
        }
    }

    private void applyCombatResult() {
        if (activeEnemy == null) return;
        panel.clientConnection.sendAttack(activeEnemy.getId());
        panel.soundSystem.play(SoundSystem.Sound.HIT);
        // Trigger the attack animation in sync with the combat tick.
        panel.attackSystem.notifyCombatTick(panel.player);
        if (activeEnemy.isDead()) {
            panel.combatSystem.stopCombat();
            activeEnemy = null;
        }
    }

    private void updateLootInteraction() {
        if (activeLoot == null) return;
        if (panel.clientWorld.getLootAt((int) activeLoot.getCenterX(), (int) activeLoot.getCenterY()) == null) {
            activeLoot = null;
            return;
        }
        if (isWithinInteractionRange(panel.player, activeLoot)) {
            panel.clientConnection.sendPickup(activeLoot.getId());
            panel.soundSystem.play(SoundSystem.Sound.ITEM_PICKUP);
            panel.showMessage("Pickup request sent to server.");
            activeLoot = null;
        }
    }

    private void updateNPCInteraction() {
        if (activeNPC == null) return;
        if (distanceTo(panel.player, activeNPC) <= Constants.TILE_SIZE * 1.6) {
            panel.clientConnection.sendTalk(activeNPC.getId());
            activeNPC = null;
        }
    }

    // -------------------------------------------------------------------------
    //  Zoom-aware coordinate unprojection
    // -------------------------------------------------------------------------

    /** Convert a raw screen X inside the viewport to the equivalent pre-zoom X. */
    private int adjustedX(int screenX) {
        double zoom = panel.getZoom();
        if (zoom == 1.0) return screenX;
        return (int)((screenX - panel.getViewportW() / 2.0) / zoom + panel.getViewportW() / 2.0);
    }

    /** Convert a raw screen Y inside the viewport to the equivalent pre-zoom Y. */
    private int adjustedY(int screenY) {
        double zoom = panel.getZoom();
        if (zoom == 1.0) return screenY;
        return (int)((screenY - panel.getHeight() / 2.0) / zoom + panel.getHeight() / 2.0);
    }

    // -------------------------------------------------------------------------
    //  Utilities
    // -------------------------------------------------------------------------

    private double distanceTo(Entity a, Entity b) {
        double dx = a.getCenterX() - b.getCenterX();
        double dy = a.getCenterY() - b.getCenterY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private boolean isWithinInteractionRange(Entity a, Entity b) {
        return distanceTo(a, b) <= Constants.TILE_SIZE * 1.6;
    }

    private int getHoveredTabIndex(int mx, int my) {
        if (mx < panel.getPanelX() || my < RightPanel.TAB_Y || my >= RightPanel.CONTENT_Y) {
            return -1;
        }

        for (int i = 0; i < TabConfig.TABS.size(); i++) {
            if (panel.rightPanel.getTabBounds(i, panel.getPanelX()).contains(mx, my)) {
                return i;
            }
        }
        return -1;
    }

    // -------------------------------------------------------------------------
    //  Editor — UI button click + tooltip
    // -------------------------------------------------------------------------

    private boolean clickInsideEditorPanel(int mx, int my) {
        return mx >= panel.getWidth() - GameRenderer.EDITOR_PANEL_W;
    }

    private void handleEditorClick(int mx, int my) {
        int btnX = panel.getWidth() - GameRenderer.EDITOR_PANEL_W + 10;
        int btnW = GameRenderer.EDITOR_PANEL_W - 20;

        for (int i = 0; i < 4; i++) {
            int by = GameRenderer.EDITOR_TILE_Y + i * GameRenderer.EDITOR_BTN_GAP;
            if (mx >= btnX && mx <= btnX + btnW && my >= by && my <= by + GameRenderer.EDITOR_BTN_H) {
                panel.setSelectedTileId(i);
                panel.editorState.setSelectedObjectKey(null);
                return;
            }
        }

        for (int i = 0; i < 3; i++) {
            int by = GameRenderer.EDITOR_ACTION_Y + i * GameRenderer.EDITOR_BTN_GAP;
            if (mx >= btnX && mx <= btnX + btnW && my >= by && my <= by + GameRenderer.EDITOR_BTN_H) {
                switch (i) {
                    case 0: panel.newMap(64, 64);    break;
                    case 1: panel.saveMap("map.json"); break;
                    case 2: panel.loadMap("map.json"); break;
                }
                return;
            }
        }

        // Object icon grid
        java.util.List<String> available = panel.editorState.getAvailableObjects();
        int halfW = btnW / 2;
        for (int i = 0; i < available.size(); i++) {
            int col = i % GameRenderer.EDITOR_ICON_COLS;
            int row = i / GameRenderer.EDITOR_ICON_COLS;
            int ix  = btnX + col * halfW + (halfW - GameRenderer.EDITOR_ICON_SIZE) / 2;
            int iy  = GameRenderer.EDITOR_OBJECT_Y + row * GameRenderer.EDITOR_ICON_ROW_H;
            if (mx >= ix && mx <= ix + GameRenderer.EDITOR_ICON_SIZE
                    && my >= iy && my <= iy + GameRenderer.EDITOR_ICON_SIZE) {
                String key = available.get(i);
                if (key.equals(panel.editorState.getSelectedObjectKey())) {
                    panel.editorState.setSelectedObjectKey(null); // deselect on re-click
                } else {
                    panel.editorState.setSelectedObjectKey(key);
                }
                return;
            }
        }

        // "Clear Object" deselect button (shown only when an object is selected)
        if (panel.editorState.getSelectedObjectKey() != null) {
            int rows = (available.size() + GameRenderer.EDITOR_ICON_COLS - 1) / GameRenderer.EDITOR_ICON_COLS;
            int dby  = GameRenderer.EDITOR_OBJECT_Y + rows * GameRenderer.EDITOR_ICON_ROW_H + 4;
            if (mx >= btnX && mx <= btnX + btnW && my >= dby && my <= dby + 24) {
                panel.editorState.setSelectedObjectKey(null);
                panel.repaint();
                return;
            }
        }
    }

    void updateEditorTooltip(int mx, int my) {
        int panelX = panel.getWidth() - GameRenderer.EDITOR_PANEL_W;
        int btnX   = panelX + 10;
        int btnW   = GameRenderer.EDITOR_PANEL_W - 20;

        // Outside panel — show global help
        if (mx < panelX) {
            String help = "1-4: Tiles | Click: Paint | R: Rotate | Ctrl+Z: Undo | Ctrl+R: Redo";
            String selectedKey = panel.editorState.getSelectedObjectKey();
            if (selectedKey != null) {
                help += "  |  Rotation: " + panel.editorState.getSelectedRotation() + "\u00b0";
            }
            panel.editorState.setHoverTooltip(help);
            return;
        }

        // Tile buttons
        String[] tileTips = {
            "Paint grass terrain",
            "Paint sandy terrain",
            "Paint water (non-walkable)",
            "Paint rocky pavement"
        };
        for (int i = 0; i < tileTips.length; i++) {
            int by = GameRenderer.EDITOR_TILE_Y + i * GameRenderer.EDITOR_BTN_GAP;
            if (mx >= btnX && mx <= btnX + btnW && my >= by && my <= by + GameRenderer.EDITOR_BTN_H) {
                panel.editorState.setHoverTooltip(tileTips[i]); return;
            }
        }

        // Action buttons
        String[] actionTips = {
            "Create a new blank map",
            "Save current map to JSON",
            "Load map from JSON"
        };
        for (int i = 0; i < actionTips.length; i++) {
            int by = GameRenderer.EDITOR_ACTION_Y + i * GameRenderer.EDITOR_BTN_GAP;
            if (mx >= btnX && mx <= btnX + btnW && my >= by && my <= by + GameRenderer.EDITOR_BTN_H) {
                panel.editorState.setHoverTooltip(actionTips[i]); return;
            }
        }

        // Object icon tooltips
        java.util.List<String> available = panel.editorState.getAvailableObjects();
        int halfW = btnW / 2;
        for (int i = 0; i < available.size(); i++) {
            int col = i % GameRenderer.EDITOR_ICON_COLS;
            int row = i / GameRenderer.EDITOR_ICON_COLS;
            int ix  = btnX + col * halfW + (halfW - GameRenderer.EDITOR_ICON_SIZE) / 2;
            int iy  = GameRenderer.EDITOR_OBJECT_Y + row * GameRenderer.EDITOR_ICON_ROW_H;
            if (mx >= ix && mx <= ix + GameRenderer.EDITOR_ICON_SIZE
                    && my >= iy && my <= iy + GameRenderer.EDITOR_ICON_SIZE) {
                panel.editorState.setHoverTooltip("Place object: " + available.get(i)); return;
            }
        }

        // Inside panel, no element hovered — show global help
        panel.editorState.setHoverTooltip("1-4: Tiles | Click: Paint | R: Rotate | Ctrl+Z: Undo | Ctrl+R: Redo");
    }

    // -------------------------------------------------------------------------
    //  Editor — tile painting (delegates to EditorActions)
    // -------------------------------------------------------------------------

    void paintTile() {
        panel.editorActions.paintTile(
                panel.editorState,
                panel.getTileMap(),
                panel.getHoveredTileX(),
                panel.getHoveredTileY());
        panel.repaint();
    }

    private void placeObject() {
        panel.editorActions.placeObject(
                panel.editorState,
                panel.getHoveredTileX(),
                panel.getHoveredTileY(),
                panel.getTileMap());
        panel.repaint();
    }
}
