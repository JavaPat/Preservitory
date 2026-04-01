package com.classic.preservitory.ui.panels;

import com.classic.preservitory.client.definitions.ItemDefinitionManager;
import com.classic.preservitory.entity.Enemy;
import com.classic.preservitory.entity.Entity;
import com.classic.preservitory.entity.NPC;
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
import java.util.function.BooleanSupplier;

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

    // -------------------------------------------------------------------------
    //  Hover position (screen-space, updated by mouse motion)
    // -------------------------------------------------------------------------

    int hoverX = -1;
    int hoverY = -1;
    int hoveredTabIndex = -1;
    private Runnable pendingAction;
    private int targetX;
    private int targetY;
    private BooleanSupplier pendingActionValid = () -> false;

    // -------------------------------------------------------------------------

    GameInputHandler(GamePanel panel) {
        this.panel = panel;
    }

    // -------------------------------------------------------------------------
    //  Keyboard
    // -------------------------------------------------------------------------

    void handleKey(int keyCode) {
        if (panel.authRequired) {
            panel.loginScreen.handleKey(keyCode);
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
        if (panel.authRequired) {
            panel.loginScreen.handleChar(c);
            return;
        }
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
            panel.chatBox.post("Server offline. Message not sent.", ChatBox.COLOR_SYSTEM);
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
        if (panel.authRequired) {
            panel.loginScreen.handleClick(cx, cy);
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

        if (isTypingChat && cx < Constants.PANEL_X) return;

        // ---- Right panel ----
        if (cx >= Constants.PANEL_X) {
            if (cx >= GamePanel.LOGOUT_BTN_X && cx < GamePanel.LOGOUT_BTN_X + GamePanel.LOGOUT_BTN_W
                    && cy >= GamePanel.LOGOUT_BTN_Y && cy < GamePanel.LOGOUT_BTN_Y + GamePanel.LOGOUT_BTN_H) {
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
                    panel.rightPanel.tabManager.setTab(clickedTab.type);
                    panel.rightPanel.onTabSelected(clickedTab.type);
                }
                return;
            }

            // Shop sell via inventory click
            if (panel.shopWindow.isOpen()) {
                int sellItemId = panel.rightPanel.getClickedInventoryItemId(cx, cy, panel.player);
                if (sellItemId >= 0 && panel.shopWindow.canSell(sellItemId)) {
                    panel.clientConnection.sendSell(sellItemId);
                    String name = com.classic.preservitory.client.definitions.ItemDefinitionManager.get(sellItemId).name;
                    panel.showMessage("Sell request sent: " + name);
                    return;
                }
            }

            // Inventory click → equip (only when shop is closed)
            if (!panel.shopWindow.isOpen()) {
                int equipItemId = panel.rightPanel.getClickedInventoryItemId(cx, cy, panel.player);
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

            panel.rightPanel.handleClick(cx, cy);
            return;
        }

        // ---- Dialogue mode: chatbox area advances / selects options ----
        if (panel.gameState == GamePanel.GameState.IN_DIALOGUE) {
            int chatBoxTop = Constants.VIEWPORT_H - GamePanel.CHAT_H;
            if (cy >= chatBoxTop) {
                if (panel.chatBox.hasOptions()) {
                    int optIndex = panel.chatBox.getOptionIndexAtY(cy - chatBoxTop);
                    if (optIndex >= 0) panel.clientConnection.sendDialogueOption(optIndex);
                } else {
                    panel.clientConnection.sendDialogueNext();
                }
            }
            return;
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
            panel.showMessage("Server offline. Start the server to play.");
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
        closeInterfaces();
        stopAllActivities();
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

    private void handleRightClick(int cx, int cy) {
        panel.closeContextMenu();

        if (panel.authRequired || panel.gameState != GamePanel.GameState.PLAYING) {
            return;
        }

        if (cx >= Constants.PANEL_X) {
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

    void updateCursorForHover(int mx, int my) {
        panel.setHoverText(null);
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

        if (mx >= Constants.PANEL_X) {
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

        if (mx >= Constants.PANEL_X || panel.gameState != GamePanel.GameState.PLAYING) {
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
        queueInteraction(npc,
                () -> activeNPC = npc,
                () -> panel.clientWorld.getNpc(npc.getId()) != null);
    }

    private void activateEnemy(Enemy enemy) {
        queueInteraction(enemy,
                () -> activeEnemy = enemy,
                () -> isEnemyStillPresent(enemy.getId()));
    }

    private void activateTree(Tree tree) {
        queueInteraction(tree,
                () -> activeTree = tree,
                tree::isAlive);
    }

    private void activateRock(Rock rock) {
        queueInteraction(rock,
                () -> activeRock = rock,
                rock::isSolid);
    }

    private void activateLoot(Loot loot) {
        queueInteraction(loot,
                () -> activeLoot = loot,
                () -> isLootStillPresent(loot.getId()));
    }

    private String buildLootExamineText(Loot loot) {
        String itemName = ItemDefinitionManager.get(loot.getItemId()).name;
        return loot.getCount() > 1
                ? itemName + " x" + loot.getCount() + "."
                : itemName + ".";
    }

    private void openInventoryContextMenu(int cx, int cy) {
        String itemName = panel.rightPanel.getHoveredInventoryItemName(panel.player);
        int itemId = panel.rightPanel.getClickedInventoryItemId(cx, cy, panel.player);
        if (itemName == null || itemId < 0) {
            return;
        }

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
        panel.clientConnection.sendDrop(itemId);
        panel.showMessage("Drop " + itemName);
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
        Font font = new Font("Monospaced", Font.PLAIN, 11);
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

    private void queueInteraction(Entity target, Runnable action, BooleanSupplier validator) {
        closeInterfaces();
        stopAllActivities();
        pendingAction = action;
        pendingActionValid = validator != null ? validator : () -> true;
        targetX = Pathfinding.pixelToTileCol(target.getCenterX());
        targetY = Pathfinding.pixelToTileRow(target.getCenterY());
        setApproachTarget(target);
    }

    private void clearPendingAction() {
        pendingAction = null;
        pendingActionValid = () -> false;
        targetX = 0;
        targetY = 0;
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
        clearPendingAction();
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
        if (panel.shopWindow.isOpen()) panel.shopWindow.close();
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
        updatePendingAction();
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

    private void updatePendingAction() {
        if (pendingAction == null) {
            return;
        }
        if (!pendingActionValid.getAsBoolean()) {
            clearPendingAction();
            return;
        }
        if (isPlayerInRange(targetX, targetY)) {
            Runnable action = pendingAction;
            clearPendingAction();
            action.run();
            return;
        }
        if (!panel.movementSystem.isMoving() && !panel.movementSystem.hasPath() && !panel.mouseHandler.hasTarget()) {
            clearPendingAction();
        }
    }

    private void updateTreeInteraction(double deltaTime) {
        if (activeTree == null) return;
        if (!activeTree.isAlive()) {
            panel.woodcuttingSystem.stopChopping();
            activeTree = null;
            return;
        }
        if (isWithinInteractionRange(panel.player, activeTree)) {
            if (!panel.woodcuttingSystem.isChopping()) {
                panel.woodcuttingSystem.startChopping(activeTree);
                panel.showMessage("You swing your axe...");
            }
            if (panel.woodcuttingSystem.update(deltaTime)) sendChopRequest();
        }
    }

    private void sendChopRequest() {
        if (activeTree != null) panel.clientConnection.sendChop(activeTree.getId());
        panel.woodcuttingSystem.stopChopping();
        activeTree = null;
        panel.soundSystem.play(SoundSystem.Sound.CHOP);
        panel.showMessage("Chop request sent to server.");
    }

    private void updateRockInteraction(double deltaTime) {
        if (activeRock == null) return;
        if (!activeRock.isSolid()) {
            panel.miningSystem.stopMining();
            activeRock = null;
            return;
        }
        if (isWithinInteractionRange(panel.player, activeRock)) {
            if (!panel.miningSystem.isMining()) {
                panel.miningSystem.startMining(activeRock);
                panel.showMessage("You swing your pickaxe...");
            }
            if (panel.miningSystem.update(deltaTime)) sendMineRequest();
        }
    }

    private void sendMineRequest() {
        String rockId = (activeRock != null) ? activeRock.getId() : null;
        if (rockId != null) panel.clientConnection.sendMine(rockId);
        panel.miningSystem.stopMining();
        activeRock = null;
        panel.soundSystem.play(SoundSystem.Sound.MINE);
        panel.showMessage("Mine request sent to server.");
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
        return (int)((screenX - Constants.VIEWPORT_W / 2.0) / zoom + Constants.VIEWPORT_W / 2.0);
    }

    /** Convert a raw screen Y inside the viewport to the equivalent pre-zoom Y. */
    private int adjustedY(int screenY) {
        double zoom = panel.getZoom();
        if (zoom == 1.0) return screenY;
        return (int)((screenY - Constants.VIEWPORT_H / 2.0) / zoom + Constants.VIEWPORT_H / 2.0);
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

    private boolean isPlayerInRange(int x, int y) {
        int playerTileX = Pathfinding.pixelToTileCol(panel.player.getCenterX());
        int playerTileY = Pathfinding.pixelToTileRow(panel.player.getCenterY());
        return Math.abs(playerTileX - x) <= 1
                && Math.abs(playerTileY - y) <= 1;
    }

    private int getHoveredTabIndex(int mx, int my) {
        if (mx < Constants.PANEL_X || my < RightPanel.TAB_Y || my >= RightPanel.CONTENT_Y) {
            return -1;
        }

        for (int i = 0; i < TabConfig.TABS.size(); i++) {
            if (panel.rightPanel.getTabBounds(i).contains(mx, my)) {
                return i;
            }
        }
        return -1;
    }
}
