package com.classic.preservitory.client.rendering;

import com.classic.preservitory.client.editor.EditorObject;
import com.classic.preservitory.client.world.ClientWorld;
import com.classic.preservitory.client.world.map.TileMap;
import com.classic.preservitory.entity.Enemy;
import com.classic.preservitory.entity.Player;
import com.classic.preservitory.entity.RemotePlayer;
import com.classic.preservitory.item.Item;
import com.classic.preservitory.ui.overlays.ChatBox;
import com.classic.preservitory.ui.overlays.FloatingText;
import com.classic.preservitory.ui.panels.GamePanel;

import com.classic.preservitory.util.Constants;
import com.classic.preservitory.world.World;

import java.awt.Graphics2D;
import java.util.List;

public class RenderContext {

    private final GamePanel panel;

    public RenderContext(GamePanel panel) {
        this.panel = panel;
    }

    public double getZoom() {
        return panel.getZoom();
    }

    public int getCameraOffsetX() {
        return panel.getCameraOffsetX();
    }

    public int getCameraOffsetY() {
        return panel.getCameraOffsetY();
    }

    public int getViewportWidth() {
        return getScreenWidth() - getPanelWidth();
    }

    public int getViewportHeight() {
        return getScreenHeight();
    }

    public int getScreenWidth() {
        return panel.getWidth();
    }

    public int getScreenHeight() {
        return panel.getHeight();
    }

    public int getPanelWidth() {
        return Constants.PANEL_W;
    }

    public int getPanelX() {
        return getViewportWidth();
    }

    public ChatBox getChatBox() {
        return panel.getChatBox();
    }

    public int getChatHeight() {
        return panel.getChatHeight();
    }

    public String getCurrentChatInputText() {
        return panel.getCurrentChatInputText();
    }

    public int getMouseX() {
        return panel.getMouseX();
    }

    public int getMouseY() {
        return panel.getMouseY();
    }

    public World getWorld() {
        return panel.getWorld();
    }

    public ClientWorld getClientWorld() {
        return panel.getClientWorld();
    }

    public Player getPlayer() {
        return panel.getPlayer();
    }

    public void renderRightPanelChrome(Graphics2D g2, Player player) {
        panel.renderRightPanelChrome(g2, player);
    }

    public void renderRightPanelContent(Graphics2D g2, Player player) {
        panel.renderRightPanelContent(g2, player);
    }

    public List<Item> getInventoryItems() {
        return panel.getInventoryItems();
    }

    public int getSelectedInventorySlot() {
        return panel.getSelectedInventorySlot();
    }

    public boolean isInGame() {
        return panel.isInGame();
    }

    public boolean isPlayingGameState() {
        return panel.isPlayingGameState();
    }

    public List<RemotePlayer> getRemotePlayersSnapshot() {
        return panel.getRemotePlayersSnapshot();
    }

    public Enemy getCombatTargetEnemy() {
        return panel.getCombatTargetEnemy();
    }

    public boolean hasMouseTarget() {
        return panel.hasMouseTarget();
    }

    public int getMouseTargetX() {
        return panel.getMouseTargetX();
    }

    public int getMouseTargetY() {
        return panel.getMouseTargetY();
    }

    public List<FloatingText> getFloatingTextsSnapshot() {
        return panel.getFloatingTextsSnapshot();
    }

    public TileMap getTileMap() {
        return panel.getTileMap();
    }

    public int getHoveredTileX() {
        return panel.getHoveredTileX();
    }

    public int getHoveredTileY() {
        return panel.getHoveredTileY();
    }

    public List<EditorObject> getEditorObjects() {
        return panel.getEditorObjects();
    }
}
