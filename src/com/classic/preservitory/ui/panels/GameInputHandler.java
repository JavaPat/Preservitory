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
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
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

    private String getMapPath(String fileName) {
        return System.getProperty("user.dir") + File.separator + fileName;
    }

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

    private boolean shiftDown = false;

    // -------------------------------------------------------------------------
    //  Hover position (screen-space, updated by mouse motion)
    // -------------------------------------------------------------------------

    int hoverX = -1;
    int hoverY = -1;
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
            if (keyCode == KeyEvent.VK_F11) {
                if (panel.editorFullscreenListener != null) panel.editorFullscreenListener.run();
                return;
            }
            if (keyCode == KeyEvent.VK_1) panel.setSelectedTileId(0); // grass
            if (keyCode == KeyEvent.VK_2) panel.setSelectedTileId(1); // path
            if (keyCode == KeyEvent.VK_3) panel.setSelectedTileId(2); // water
            if (keyCode == KeyEvent.VK_4) panel.setSelectedTileId(3); // stone
            if (keyCode == KeyEvent.VK_N) panel.newMap(64, 64);
            if (keyCode == KeyEvent.VK_S) panel.saveMap(getMapPath("map.json"));
            if (keyCode == KeyEvent.VK_L) panel.loadMap(getMapPath("map.json"));
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

        // ---- Fullscreen modal overlays — highest UI priority ----
        // These render on top of everything; clicks must be consumed before any other UI.
        if (panel.questCompleteWindow.isOpen()) {
            panel.questCompleteWindow.handleClick(cx, cy);
            return;
        }

        if (panel.shopWindow.isOpen()) {
            if (panel.shopWindow.containsPoint(cx, cy)) {
                panel.shopWindow.handleClick(cx, cy);
            }
            // Sell via inventory slot even when shop is open (right-panel area)
            if (cx >= panel.getPanelX()) {
                int sellItemId = getClickedInventoryItemId(cx, cy);
                if (sellItemId >= 0 && panel.shopWindow.canSell(sellItemId)) {
                    panel.clientConnection.sendSell(sellItemId);
                    String name = com.classic.preservitory.client.definitions.ItemDefinitionManager.get(sellItemId).name;
                    panel.showMessage("Sell request sent: " + name);
                }
            }
            return;
        }

        if (isTypingChat && cx < panel.getPanelX()) return;

        // ---- Right panel ----
        if (cx >= panel.getPanelX()) {
            if (panel.gameState == GamePanel.GameState.IN_DIALOGUE) {
                closeDialogue();
            }

            TabType clickedPanelTab = panel.getRightPanelTabAt(cx, cy);
            if (clickedPanelTab != TabType.NONE) {
                panel.toggleActiveInterfacePanel(clickedPanelTab);
                return;
            }

            // Shift-click drop
            if (panel.settings.isShiftClickDrop() && shiftDown
                    && panel.rightPanel.getActiveTab() == TabType.INVENTORY) {
                int dropItemId = getClickedInventoryItemId(cx, cy);
                if (dropItemId >= 0) {
                    String name = com.classic.preservitory.client.definitions.ItemDefinitionManager.get(dropItemId).name;
                    handleDropItem(dropItemId, name);
                    return;
                }
            }

            // Inventory click → equip
            int equipItemId = getClickedInventoryItemId(cx, cy);
            if (equipItemId >= 0) {
                com.classic.preservitory.client.definitions.ItemDefinition def =
                        com.classic.preservitory.client.definitions.ItemDefinitionManager.get(equipItemId);
                if (def.equipSlot != null) {
                    panel.clientConnection.sendEquip(equipItemId);
                    panel.showMessage("Equipping " + def.name);
                    return;
                }
            }

            panel.rightPanel.syncPanelX(panel.getPanelX());
            panel.rightPanel.handleClick(cx, cy);
            return;
        }

        // ---- Chatbox area — intercept clicks so they don't reach the game world ----
        int chatBoxTop = panel.getHeight() - GamePanel.CHAT_H;
        if (cy >= chatBoxTop) {
            // ---- Dialogue mode: chatbox area advances / selects options ----
            if (panel.gameState == GamePanel.GameState.IN_DIALOGUE) {
                if (panel.chatBox.hasOptions()) {
                    int optIndex = panel.chatBox.getOptionIndexAtY(cy - chatBoxTop);
                    if (optIndex >= 0) panel.clientConnection.sendDialogueOption(optIndex);
                } else {
                    panel.clientConnection.sendDialogueNext();
                }
            }
            return;
        }

        // ---- Minimap area — intercept so clicks don't reach the game world ----
        if (panel.shouldRenderGameMinimap()) {
            int mmSize = panel.getMinimapSize();
            int mmX = panel.getViewportW() - mmSize - 10;
            if (cx >= mmX && cx <= mmX + mmSize && cy >= 10 && cy <= 10 + mmSize) {
                return;
            }
        }

        // ---- Close dialogue on game-world click ----
        if (panel.gameState == GamePanel.GameState.IN_DIALOGUE) {
            closeDialogue();
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

        // Ground: send destination to server — server runs A* and queues the path
        closeTransientInterfacesForMovement();
        stopAllActivities();
        panel.clientConnection.sendClearPendingInteraction();
        panel.clientConnection.sendMoveTo(clickTileCol, clickTileRow);
        panel.mouseHandler.setTarget(clickTileCol * Constants.TILE_SIZE, clickTileRow * Constants.TILE_SIZE);
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
        panel.rightPanel.setHoveredTabIndex(-1);

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
            int tabIndex = panel.getRightPanelTabIndexAt(mx, my);
            panel.rightPanel.setHoveredTabIndex(tabIndex);
            if (tabIndex >= 0) {
                panel.setHoverText(getTabHoverText(TabConfig.TABS.get(tabIndex).type));
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

    private String getTabHoverText(TabType tab) {
        return switch (tab) {
            case COMBAT -> "Combat";
            case INVENTORY -> "Inventory";
            case SKILLS -> "Skills";
            case EQUIPMENT -> "Equipment";
            case QUESTS -> "Quests";
            case PRAYER -> "Prayer";
            case SETTINGS -> "Settings";
            case LOGOUT -> "Logout";
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
        int itemId = getClickedInventoryItemId(cx, cy);
        if (itemId < 0) return;
        String itemName = com.classic.preservitory.client.definitions.ItemDefinitionManager.get(itemId).name;

        com.classic.preservitory.client.definitions.ItemDefinition def =
                com.classic.preservitory.client.definitions.ItemDefinitionManager.get(itemId);

        List<GamePanel.ContextMenuOption> options = new ArrayList<>();
        if (def.buryable) {
            options.add(new GamePanel.ContextMenuOption("Bury " + itemName, () -> handleBuryBone(itemId, itemName)));
        }
        options.add(new GamePanel.ContextMenuOption("Use " + itemName, () -> handleUseItem(itemId, itemName)));
        options.add(new GamePanel.ContextMenuOption("Drop " + itemName, () -> handleDropItem(itemId, itemName)));
        options.add(new GamePanel.ContextMenuOption("Examine " + itemName, () -> panel.showMessage(itemName)));
        panel.openContextMenu(options, cx, cy);
    }

    private int getClickedInventoryItemId(int sx, int sy) {
        return panel.rightPanel.getClickedInventoryItemId(sx, sy, panel.player, panel.getPanelX());
    }

    private void handleUseItem(int itemId, String itemName) {
        panel.clientConnection.sendUse(itemId);
        panel.showMessage("Use " + itemName);
    }

    private void handleBuryBone(int itemId, String itemName) {
        panel.clientConnection.sendBuryBone(itemId);
        panel.showMessage("You bury the " + itemName + ".");
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
        sendApproachMoveTo(target);
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

    /**
     * Send a MOVE_TO request toward the tile adjacent to {@code target}.
     * The server will path-find and move the player tile-by-tile.
     */
    private void sendApproachMoveTo(Entity target) {
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

        panel.clientConnection.sendMoveTo(goalCol, goalRow);
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

    void performLogout() {
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

    // -------------------------------------------------------------------------
    //  Editor — UI button click + tooltip
    // -------------------------------------------------------------------------

    private boolean clickInsideEditorPanel(int mx, int my) {
        return mx >= panel.getWidth() - GameRenderer.EDITOR_PANEL_W;
    }

    private void handleEditorClick(int mx, int my) {
        int btnX = panel.getWidth() - GameRenderer.EDITOR_PANEL_W + 10;
        int btnW = GameRenderer.EDITOR_PANEL_W - 20;

        // Tile color squares
        int totalSqW = GameRenderer.EDITOR_TILE_COUNT * GameRenderer.EDITOR_TILE_SQ_SIZE
                     + (GameRenderer.EDITOR_TILE_COUNT - 1) * GameRenderer.EDITOR_TILE_SQ_GAP;
        int sqStartX = btnX + (btnW - totalSqW) / 2;
        for (int i = 0; i < GameRenderer.EDITOR_TILE_COUNT; i++) {
            int sx = sqStartX + i * (GameRenderer.EDITOR_TILE_SQ_SIZE + GameRenderer.EDITOR_TILE_SQ_GAP);
            if (mx >= sx && mx < sx + GameRenderer.EDITOR_TILE_SQ_SIZE
             && my >= GameRenderer.EDITOR_TILE_SQ_Y && my < GameRenderer.EDITOR_TILE_SQ_Y + GameRenderer.EDITOR_TILE_SQ_SIZE) {
                panel.setSelectedTileId(i);
                panel.editorState.setSelectedObjectKey(null);
                return;
            }
        }

        // Objects toggle button
        if (mx >= btnX && mx < btnX + btnW
         && my >= GameRenderer.EDITOR_OBJ_TOGGLE_Y && my < GameRenderer.EDITOR_OBJ_TOGGLE_Y + GameRenderer.EDITOR_OBJ_TOGGLE_H) {
            panel.editorState.setObjectsExpanded(!panel.editorState.isObjectsExpanded());
            panel.editorState.setObjectPanelScrollY(0);
            panel.repaint();
            return;
        }

        if (!panel.editorState.isObjectsExpanded()) return;

        // Category buttons and object icons
        java.util.Map<String, java.util.List<String>> categories = panel.editorState.getObjectCategories();
        String expandedCat = panel.editorState.getExpandedCategory();
        int cy = GameRenderer.EDITOR_OBJ_BODY_Y;

        for (java.util.Map.Entry<String, java.util.List<String>> entry : categories.entrySet()) {
            String cat = entry.getKey();

            if (mx >= btnX && mx < btnX + btnW && my >= cy && my < cy + GameRenderer.EDITOR_CAT_BTN_H) {
                if (cat.equals(expandedCat)) {
                    panel.editorState.setExpandedCategory(null);
                } else {
                    panel.editorState.setExpandedCategory(cat);
                    panel.editorState.setObjectPanelScrollY(0);
                    panel.editorState.setSelectedObjectKey(null);
                }
                panel.repaint();
                return;
            }

            cy += GameRenderer.EDITOR_CAT_BTN_H + GameRenderer.EDITOR_CAT_BTN_GAP;

            if (cat.equals(expandedCat)) {
                java.util.List<String> catKeys = entry.getValue();
                int scrollY = panel.editorState.getObjectPanelScrollY();
                int halfW   = btnW / 2;

                for (int i = 0; i < catKeys.size(); i++) {
                    int col = i % GameRenderer.EDITOR_ICON_COLS;
                    int row = i / GameRenderer.EDITOR_ICON_COLS;
                    int ix  = btnX + col * halfW + (halfW - GameRenderer.EDITOR_ICON_SIZE) / 2;
                    int iy  = cy + row * GameRenderer.EDITOR_ICON_ROW_H - scrollY;
                    if (mx >= ix && mx < ix + GameRenderer.EDITOR_ICON_SIZE
                     && my >= iy && my < iy + GameRenderer.EDITOR_ICON_SIZE) {
                        String key = catKeys.get(i);
                        panel.editorState.setSelectedObjectKey(
                            key.equals(panel.editorState.getSelectedObjectKey()) ? null : key);
                        panel.repaint();
                        return;
                    }
                }

                // "Clear Object" button pinned to bottom
                if (panel.editorState.getSelectedObjectKey() != null) {
                    int dby = panel.getHeight() - 30;
                    if (mx >= btnX && mx < btnX + btnW && my >= dby && my < dby + 24) {
                        panel.editorState.setSelectedObjectKey(null);
                        panel.repaint();
                        return;
                    }
                }

                break;
            }
        }
    }

    void updateEditorTooltip(int mx, int my) {
        int panelX = panel.getWidth() - GameRenderer.EDITOR_PANEL_W;
        int btnX   = panelX + 10;
        int btnW   = GameRenderer.EDITOR_PANEL_W - 20;

        if (mx < panelX) {
            panel.editorState.setHoverTooltip("1-4: Tiles | F11: Fullscreen | Ctrl+Z: Undo | Ctrl+R: Redo");
            return;
        }

        // Tile color squares
        String[] tileNames = {"Grass", "Path", "Water", "Pavement"};
        int totalSqW = GameRenderer.EDITOR_TILE_COUNT * GameRenderer.EDITOR_TILE_SQ_SIZE
                     + (GameRenderer.EDITOR_TILE_COUNT - 1) * GameRenderer.EDITOR_TILE_SQ_GAP;
        int sqStartX = btnX + (btnW - totalSqW) / 2;
        for (int i = 0; i < GameRenderer.EDITOR_TILE_COUNT; i++) {
            int sx = sqStartX + i * (GameRenderer.EDITOR_TILE_SQ_SIZE + GameRenderer.EDITOR_TILE_SQ_GAP);
            if (mx >= sx && mx < sx + GameRenderer.EDITOR_TILE_SQ_SIZE
             && my >= GameRenderer.EDITOR_TILE_SQ_Y && my < GameRenderer.EDITOR_TILE_SQ_Y + GameRenderer.EDITOR_TILE_SQ_SIZE) {
                panel.editorState.setHoverTooltip(tileNames[i] + " (" + (i + 1) + ")");
                return;
            }
        }

        // Objects toggle button
        if (mx >= btnX && mx < btnX + btnW
         && my >= GameRenderer.EDITOR_OBJ_TOGGLE_Y && my < GameRenderer.EDITOR_OBJ_TOGGLE_Y + GameRenderer.EDITOR_OBJ_TOGGLE_H) {
            panel.editorState.setHoverTooltip(
                panel.editorState.isObjectsExpanded() ? "Collapse objects panel" : "Expand objects panel");
            return;
        }

        if (panel.editorState.isObjectsExpanded()) {
            java.util.Map<String, java.util.List<String>> categories = panel.editorState.getObjectCategories();
            String expandedCat = panel.editorState.getExpandedCategory();
            int cy = GameRenderer.EDITOR_OBJ_BODY_Y;

            for (java.util.Map.Entry<String, java.util.List<String>> entry : categories.entrySet()) {
                String cat = entry.getKey();
                if (mx >= btnX && mx < btnX + btnW && my >= cy && my < cy + GameRenderer.EDITOR_CAT_BTN_H) {
                    panel.editorState.setHoverTooltip("Category: " + cat);
                    return;
                }
                cy += GameRenderer.EDITOR_CAT_BTN_H + GameRenderer.EDITOR_CAT_BTN_GAP;
                if (cat.equals(expandedCat)) {
                    java.util.List<String> catKeys = entry.getValue();
                    int scrollY = panel.editorState.getObjectPanelScrollY();
                    int halfW   = btnW / 2;
                    for (int i = 0; i < catKeys.size(); i++) {
                        int col = i % GameRenderer.EDITOR_ICON_COLS;
                        int row = i / GameRenderer.EDITOR_ICON_COLS;
                        int ix  = btnX + col * halfW + (halfW - GameRenderer.EDITOR_ICON_SIZE) / 2;
                        int iy  = cy + row * GameRenderer.EDITOR_ICON_ROW_H - scrollY;
                        if (mx >= ix && mx < ix + GameRenderer.EDITOR_ICON_SIZE
                         && my >= iy && my < iy + GameRenderer.EDITOR_ICON_SIZE) {
                            panel.editorState.setHoverTooltip("Place: " + catKeys.get(i));
                            return;
                        }
                    }
                    break;
                }
            }
        }

        panel.editorState.setHoverTooltip("1-4: Tiles | R: Rotate | F11: Fullscreen | Ctrl+Z: Undo");
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
