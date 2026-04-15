package com.classic.preservitory.ui.panels;

import com.classic.preservitory.client.rendering.RenderContext;
import com.classic.preservitory.client.rendering.UIRenderer;
import com.classic.preservitory.client.editor.EditorObject;
import com.classic.preservitory.client.world.map.TileMap;
import com.classic.preservitory.entity.Enemy;
import com.classic.preservitory.entity.Entity;
import com.classic.preservitory.entity.NPC;
import com.classic.preservitory.entity.RemotePlayer;
import com.classic.preservitory.system.Pathfinding;
import com.classic.preservitory.ui.framework.assets.AssetManager;
import com.classic.preservitory.ui.overlays.FloatingText;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;
import com.classic.preservitory.util.RenderUtils;
import com.classic.preservitory.world.objects.Loot;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Handles all rendering for the game panel.
 *
 * This class is an internal collaborator of GamePanel; it accesses GamePanel
 * and GameInputHandler fields directly via package-private visibility.
 */
class GameRenderer {

    private final GamePanel panel;
    private final RenderContext renderContext;
    private final WorldRenderer worldRenderer;
    private final UIRenderer uiRenderer;

    private static final int TILE_SIZE = Constants.TILE_SIZE;

    // ---- Editor panel layout (shared with GameInputHandler via package-private access) ----
    static final int EDITOR_PANEL_W      = 220;
    // Tile section
    static final int EDITOR_TILE_Y       = 40;   // "TILE" header y
    static final int EDITOR_TILE_SQ_Y    = 56;   // tile color squares top-y
    static final int EDITOR_TILE_SQ_SIZE = 24;   // square size
    static final int EDITOR_TILE_SQ_GAP  = 6;    // gap between squares
    static final int EDITOR_TILE_COUNT   = 4;
    // Objects toggle button
    static final int EDITOR_OBJ_TOGGLE_Y = 100;
    static final int EDITOR_OBJ_TOGGLE_H = 28;
    // Objects body (categories + icon grid)
    static final int EDITOR_OBJ_BODY_Y   = 136;
    static final int EDITOR_CAT_BTN_H    = 26;
    static final int EDITOR_CAT_BTN_GAP  = 4;
    // Object icon grid
    static final int EDITOR_ICON_SIZE    = 36;
    static final int EDITOR_ICON_COLS    = 2;
    static final int EDITOR_ICON_ROW_H   = 42;
    // Minimap
    static final int MINIMAP_SIZE        = 150;
    // Legacy aliases (kept to avoid breaking any remaining references)
    static final int EDITOR_BTN_H        = 40;
    static final int EDITOR_BTN_GAP      = 50;
    static final int EDITOR_ACTION_Y     = 0;
    static final int EDITOR_OBJECT_Y     = EDITOR_OBJ_TOGGLE_Y;

    GameRenderer(GamePanel panel) {
        this.panel = panel;
        this.renderContext = new RenderContext(panel);
        this.worldRenderer = new WorldRenderer(renderContext, panel);
        this.uiRenderer = new UIRenderer(renderContext, panel);
    }

    // -------------------------------------------------------------------------
    //  Main render pass
    // -------------------------------------------------------------------------

    void render(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int viewportW = Constants.EDITOR_MODE ? panel.getWidth() : panel.getViewportW();
        int viewportH = panel.getHeight();

        if (!Constants.EDITOR_MODE && panel.isPreGameState()) {
            panel.renderLoadingScreen(g2);
            return;
        }

        if (!Constants.EDITOR_MODE && panel.shouldRenderLoginScreen()) {
            panel.loginScreen.render(g2, panel.getWidth(), panel.getHeight());
            return;
        }

        worldRenderer.render(g2);
        drawClientUI(g2);

        if (!Constants.EDITOR_MODE) {
            g2.setTransform(new AffineTransform());
            g2.setClip(null);
            uiRenderer.render(g2);
            if (panel.debugMode) drawDebugOverlay(g2);
        }

        if (Constants.EDITOR_MODE) {
            if (panel.editorState.isShowMinimap()) {
                drawEditorMinimap(g2, panel.getTileMap(), viewportW, viewportH);
            }
            drawEditorPanel(g2, viewportW, viewportH);
            drawEditorCoords(g2);
            drawEditorTooltip(g2);
        }

        // Direction indicator — only when logged in, in game, and setting is on
        if (panel.player != null && !Constants.EDITOR_MODE && panel.isInGame()
                && panel.settings.isShowDirectionIndicator()) {
            drawDirectionIndicator(g2, viewportW, viewportH);
        }
    }

    private void drawClientUI(Graphics2D g) {
        if (Constants.EDITOR_MODE || !panel.isInGame()) {
            return;
        }

        g.setTransform(new AffineTransform());
        g.setClip(null);

        panel.renderRightPanelAt(g, panel.getPlayer(), panel.getPanelX());
    }

    private void drawDirectionIndicator(Graphics2D g, int viewportW, int viewportH) {
        int fx = panel.player.getFacingX();
        int fy = panel.player.getFacingY();
        String dir;
        if      (fx > 0) dir = "EAST";
        else if (fx < 0) dir = "WEST";
        else if (fy < 0) dir = "NORTH";
        else             dir = "SOUTH";

        String label = "Direction: " + dir;

        g.setFont(new Font("Arial", Font.PLAIN, 13));
        FontMetrics fm = g.getFontMetrics();
        int textW = fm.stringWidth(label);
        int x = viewportW - textW - 10;
        int y = viewportH - GamePanel.CHAT_H - 10;

        g.setColor(new Color(0, 0, 0, 150));
        g.fillRoundRect(x - 6, y - fm.getAscent() - 3, textW + 12, fm.getAscent() + fm.getDescent() + 6, 6, 6);

        g.setColor(Color.WHITE);
        g.drawString(label, x, y);
    }

    private void drawEditorPanel(Graphics2D g, int viewportW, int viewportH) {
        int panelX = viewportW - EDITOR_PANEL_W;
        int btnW   = EDITOR_PANEL_W - 20;
        int btnX   = panelX + 10;
        int mx     = panel.inputHandler.hoverX;
        int my     = panel.inputHandler.hoverY;

        // Background
        g.setColor(new Color(20, 20, 20, 220));
        g.fillRect(panelX, 0, EDITOR_PANEL_W, viewportH);
        g.setColor(new Color(80, 80, 80, 180));
        g.drawLine(panelX, 0, panelX, viewportH);

        // ---- TILE SECTION ----
        g.setFont(new Font("Arial", Font.BOLD, 11));
        g.setColor(new Color(150, 150, 150));
        g.drawString("TILE", btnX, EDITOR_TILE_Y);

        String[] tileLabels = {"Grass", "Path", "Water", "Pavement"};
        Color[]  tileColors = {
            new Color(34, 139, 34),
            new Color(194, 178, 128),
            new Color(70, 130, 180),
            new Color(120, 120, 120)
        };
        int selected    = panel.getSelectedTileId();
        int totalSqW    = EDITOR_TILE_COUNT * EDITOR_TILE_SQ_SIZE + (EDITOR_TILE_COUNT - 1) * EDITOR_TILE_SQ_GAP;
        int sqStartX    = btnX + (btnW - totalSqW) / 2;

        for (int i = 0; i < EDITOR_TILE_COUNT; i++) {
            int sx       = sqStartX + i * (EDITOR_TILE_SQ_SIZE + EDITOR_TILE_SQ_GAP);
            int sy       = EDITOR_TILE_SQ_Y;
            boolean active  = (i == selected);
            boolean hovered = mx >= sx && mx < sx + EDITOR_TILE_SQ_SIZE && my >= sy && my < sy + EDITOR_TILE_SQ_SIZE;

            g.setColor(active ? tileColors[i] : tileColors[i].darker().darker());
            g.fillRect(sx, sy, EDITOR_TILE_SQ_SIZE, EDITOR_TILE_SQ_SIZE);
            if (hovered && !active) {
                g.setColor(new Color(255, 255, 255, 50));
                g.fillRect(sx, sy, EDITOR_TILE_SQ_SIZE, EDITOR_TILE_SQ_SIZE);
            }
            g.setColor(active ? Color.WHITE : new Color(80, 80, 80));
            g.drawRect(sx, sy, EDITOR_TILE_SQ_SIZE, EDITOR_TILE_SQ_SIZE);
            // Hotkey number in top-left corner
            g.setFont(new Font("Arial", Font.BOLD, 9));
            g.setColor(active ? Color.WHITE : new Color(150, 150, 150));
            g.drawString(String.valueOf(i + 1), sx + 2, sy + 11);
        }

        // ---- SEPARATOR ----
        g.setColor(new Color(55, 55, 55));
        g.drawLine(btnX, EDITOR_OBJ_TOGGLE_Y - 8, btnX + btnW, EDITOR_OBJ_TOGGLE_Y - 8);

        // ---- OBJECTS TOGGLE BUTTON ----
        com.classic.preservitory.client.editor.EditorState es = panel.editorState;
        boolean objExpanded    = es.isObjectsExpanded();
        boolean toggleHovered  = mx >= btnX && mx < btnX + btnW
                               && my >= EDITOR_OBJ_TOGGLE_Y && my < EDITOR_OBJ_TOGGLE_Y + EDITOR_OBJ_TOGGLE_H;

        g.setColor(toggleHovered ? new Color(60, 60, 80) : new Color(40, 40, 55));
        g.fillRoundRect(btnX, EDITOR_OBJ_TOGGLE_Y, btnW, EDITOR_OBJ_TOGGLE_H, 5, 5);
        g.setColor(new Color(100, 100, 140));
        g.drawRoundRect(btnX, EDITOR_OBJ_TOGGLE_Y, btnW, EDITOR_OBJ_TOGGLE_H, 5, 5);
        g.setFont(new Font("Arial", Font.BOLD, 12));
        FontMetrics tfm  = g.getFontMetrics();
        String toggleLbl = "OBJECTS " + (objExpanded ? "\u25b2" : "\u25bc");
        g.setColor(new Color(200, 200, 220));
        g.drawString(toggleLbl, btnX + (btnW - tfm.stringWidth(toggleLbl)) / 2, EDITOR_OBJ_TOGGLE_Y + 19);

        if (!objExpanded) return;

        // ---- CATEGORIES ----
        java.util.Map<String, java.util.List<String>> categories = es.getObjectCategories();
        String expandedCat = es.getExpandedCategory();
        int cy = EDITOR_OBJ_BODY_Y;

        for (java.util.Map.Entry<String, java.util.List<String>> entry : categories.entrySet()) {
            String cat        = entry.getKey();
            boolean catOpen   = cat.equals(expandedCat);
            boolean catHov    = mx >= btnX && mx < btnX + btnW && my >= cy && my < cy + EDITOR_CAT_BTN_H;

            g.setColor(catOpen ? new Color(50, 80, 50) : (catHov ? new Color(50, 50, 65) : new Color(35, 35, 45)));
            g.fillRoundRect(btnX, cy, btnW, EDITOR_CAT_BTN_H, 4, 4);
            g.setColor(catOpen ? new Color(80, 160, 80) : new Color(80, 80, 100));
            g.drawRoundRect(btnX, cy, btnW, EDITOR_CAT_BTN_H, 4, 4);
            g.setFont(new Font("Arial", Font.BOLD, 11));
            String catLbl = (catOpen ? "\u25bc " : "\u25b6 ") + cat;
            g.setColor(catOpen ? new Color(150, 220, 150) : new Color(180, 180, 200));
            g.drawString(catLbl, btnX + 8, cy + EDITOR_CAT_BTN_H - 7);

            cy += EDITOR_CAT_BTN_H + EDITOR_CAT_BTN_GAP;

            if (catOpen) {
                java.util.List<String> catKeys = entry.getValue();
                String selectedKey  = es.getSelectedObjectKey();
                int    scrollY      = es.getObjectPanelScrollY();
                int    halfW        = btnW / 2;
                int    objAreaTop   = cy;
                int    objAreaBot   = selectedKey != null ? viewportH - 32 : viewportH - 10;
                int    objAreaH     = Math.max(0, objAreaBot - objAreaTop);

                // Clip icon rendering to the visible object area
                Shape savedClip = g.getClip();
                g.setClip(panelX, objAreaTop, EDITOR_PANEL_W, objAreaH);

                for (int i = 0; i < catKeys.size(); i++) {
                    String key   = catKeys.get(i);
                    int    col   = i % EDITOR_ICON_COLS;
                    int    row   = i / EDITOR_ICON_COLS;
                    int    ix    = btnX + col * halfW + (halfW - EDITOR_ICON_SIZE) / 2;
                    int    iy    = objAreaTop + row * EDITOR_ICON_ROW_H - scrollY;

                    if (iy + EDITOR_ICON_SIZE < objAreaTop || iy > objAreaTop + objAreaH) continue;

                    boolean isSel = key.equals(selectedKey);
                    boolean isHov = mx >= ix && mx < ix + EDITOR_ICON_SIZE && my >= iy && my < iy + EDITOR_ICON_SIZE;

                    g.setColor(isSel ? new Color(60, 100, 60) : new Color(35, 35, 35));
                    g.fillRect(ix, iy, EDITOR_ICON_SIZE, EDITOR_ICON_SIZE);
                    if (isHov && !isSel) {
                        g.setColor(new Color(255, 255, 255, 40));
                        g.fillRect(ix, iy, EDITOR_ICON_SIZE, EDITOR_ICON_SIZE);
                    }
                    g.setColor(isSel ? new Color(100, 200, 100) : new Color(80, 80, 80));
                    g.drawRect(ix, iy, EDITOR_ICON_SIZE, EDITOR_ICON_SIZE);

                    BufferedImage img = AssetManager.getImage(
                            com.classic.preservitory.client.editor.EditorActions.getSpriteKey(key));
                    if (img != null) {
                        g.drawImage(img, ix + 2, iy + 2, EDITOR_ICON_SIZE - 4, EDITOR_ICON_SIZE - 4, null);
                    }
                }

                g.setClip(savedClip);

                // "Clear Object" button — pinned to bottom of panel
                if (selectedKey != null) {
                    int dby  = viewportH - 30;
                    boolean dHov = mx >= btnX && mx < btnX + btnW && my >= dby && my < dby + 24;
                    g.setColor(dHov ? new Color(120, 50, 50) : new Color(70, 30, 30));
                    g.fillRoundRect(btnX, dby, btnW, 24, 4, 4);
                    g.setColor(new Color(180, 80, 80));
                    g.drawRoundRect(btnX, dby, btnW, 24, 4, 4);
                    g.setFont(new Font("Arial", Font.BOLD, 11));
                    FontMetrics dfm = g.getFontMetrics();
                    String dlabel = "Clear Object";
                    g.setColor(new Color(220, 150, 150));
                    g.drawString(dlabel, btnX + (btnW - dfm.stringWidth(dlabel)) / 2, dby + 17);
                }

                break; // only one category is expanded at a time
            }
        }

    }

    private static final Color[] MINIMAP_TILE_COLORS = {
        new Color(34, 139, 34),    // 0 Grass
        new Color(194, 178, 128),  // 1 Path
        new Color(70, 130, 180),   // 2 Water
        new Color(120, 120, 120),  // 3 Pavement
    };

    private void drawEditorMinimap(Graphics2D g, TileMap tileMap, int viewportW, int viewportH) {
        int gameAreaW = viewportW - EDITOR_PANEL_W;
        int mmX       = gameAreaW - MINIMAP_SIZE - 10;
        int mmY       = 10;
        int mapW      = tileMap.getWidth();
        int mapH      = tileMap.getHeight();

        // Zoom: compute visible tile window centred on camera position
        int zoom       = panel.minimapZoom;                          // 1–4
        int visibleW   = Math.max(1, mapW >> (zoom - 1));
        int visibleH   = Math.max(1, mapH >> (zoom - 1));

        // Camera centre tile (use camera iso offset → tile coords)
        int camCentreIsoX = panel.cameraOffsetX + gameAreaW / 2;
        int camCentreIsoY = panel.cameraOffsetY + viewportH / 2;
        int camTileCol    = IsoUtils.isoToTileCol(camCentreIsoX, camCentreIsoY);
        int camTileRow    = IsoUtils.isoToTileRow(camCentreIsoX, camCentreIsoY);

        int startCol = Math.max(0, Math.min(mapW - visibleW, camTileCol - visibleW / 2));
        int startRow = Math.max(0, Math.min(mapH - visibleH, camTileRow - visibleH / 2));

        float tileW = (float) MINIMAP_SIZE / visibleW;
        float tileH = (float) MINIMAP_SIZE / visibleH;

        // Dark background
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(mmX, mmY, MINIMAP_SIZE, MINIMAP_SIZE);

        // Tile grid (only visible window)
        for (int row = startRow; row < startRow + visibleH; row++) {
            for (int col = startCol; col < startCol + visibleW; col++) {
                if (col < 0 || col >= mapW || row < 0 || row >= mapH) continue;
                int id = tileMap.getTile(col, row);
                g.setColor(id >= 0 && id < MINIMAP_TILE_COLORS.length
                        ? MINIMAP_TILE_COLORS[id] : Color.DARK_GRAY);
                int px = mmX + Math.round((col - startCol) * tileW);
                int py = mmY + Math.round((row - startRow) * tileH);
                g.fillRect(px, py, Math.max(1, Math.round(tileW)), Math.max(1, Math.round(tileH)));
            }
        }

        // Camera viewport rectangle (bounding box of visible tiles)
        double camZoom = panel.getZoom();
        int vpCx       = gameAreaW / 2;
        int vpCy       = viewportH / 2;
        double invZ    = 1.0 / camZoom;
        int[] sxs      = {0, gameAreaW, 0, gameAreaW};
        int[] sys      = {0, 0, viewportH, viewportH};
        int minCol = Integer.MAX_VALUE, maxCol = Integer.MIN_VALUE;
        int minRow2 = Integer.MAX_VALUE, maxRow2 = Integer.MIN_VALUE;
        for (int i = 0; i < 4; i++) {
            int isoX = (int)((sxs[i] - vpCx) * invZ + vpCx) + panel.cameraOffsetX;
            int isoY = (int)((sys[i] - vpCy) * invZ + vpCy) + panel.cameraOffsetY;
            int col  = IsoUtils.isoToTileCol(isoX, isoY);
            int row  = IsoUtils.isoToTileRow(isoX, isoY);
            minCol  = Math.min(minCol, col);
            maxCol  = Math.max(maxCol, col);
            minRow2 = Math.min(minRow2, row);
            maxRow2 = Math.max(maxRow2, row);
        }
        int cvX = mmX + Math.round((Math.max(startCol, minCol) - startCol) * tileW);
        int cvY = mmY + Math.round((Math.max(startRow, minRow2) - startRow) * tileH);
        int cvW = Math.round((Math.min(startCol + visibleW, maxCol) - Math.max(startCol, minCol)) * tileW);
        int cvH = Math.round((Math.min(startRow + visibleH, maxRow2) - Math.max(startRow, minRow2)) * tileH);
        if (cvW > 0 && cvH > 0) {
            g.setColor(new Color(255, 255, 255, 40));
            g.fillRect(cvX, cvY, cvW, cvH);
            g.setColor(new Color(255, 255, 255, 160));
            g.drawRect(cvX, cvY, cvW, cvH);
        }

        // Placed editor objects as mini sprites (no player dot in editor mode)
        int objSize = Math.max(2, Math.round(Math.min(tileW, tileH)));
        for (com.classic.preservitory.client.editor.EditorObject obj : panel.editorState.getObjects()) {
            if (obj.tileX < startCol || obj.tileX >= startCol + visibleW) continue;
            if (obj.tileY < startRow || obj.tileY >= startRow + visibleH) continue;
            int ox = mmX + Math.round((obj.tileX - startCol) * tileW);
            int oy = mmY + Math.round((obj.tileY - startRow) * tileH);
            String spriteKey = com.classic.preservitory.client.editor.EditorActions.getSpriteKey(obj.key);
            java.awt.image.BufferedImage img = AssetManager.getImage(spriteKey);
            if (img != null) {
                g.drawImage(img, ox, oy, objSize, objSize, null);
            } else {
                g.setColor(new Color(80, 180, 80));
                g.fillRect(ox, oy, objSize, objSize);
            }
        }

        // Border + label
        g.setColor(new Color(100, 100, 120));
        g.drawRect(mmX, mmY, MINIMAP_SIZE, MINIMAP_SIZE);
        g.setFont(new Font("Arial", Font.BOLD, 9));
        g.setColor(new Color(180, 180, 180));
        g.drawString("MINIMAP", mmX + 2, mmY + MINIMAP_SIZE + 11);
    }

    private void drawEditorCoords(Graphics2D g) {
        int tx = panel.getHoveredTileX();
        int ty = panel.getHoveredTileY();
        if (tx < 0 || ty < 0) return;
        String coords = "Tile: (" + tx + ", " + ty + ")";
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.setColor(Color.BLACK);
        g.drawString(coords, 11, 21);
        g.setColor(Color.WHITE);
        g.drawString(coords, 10, 20);
    }

    private void drawEditorTooltip(Graphics2D g) {
        String tip = panel.editorState.getHoverTooltip();
        if (tip == null || tip.isEmpty()) return;
        int x = panel.inputHandler.hoverX + 12;
        int y = panel.inputHandler.hoverY + 12;
        g.setFont(new Font("Arial", Font.PLAIN, 12));
        int tw = tip.length() * 7 + 10;
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRoundRect(x, y - 14, tw, 18, 6, 6);
        g.setColor(Color.WHITE);
        g.drawString(tip, x + 5, y);
    }

    // -------------------------------------------------------------------------
    //  HUD / screen-space overlays
    // -------------------------------------------------------------------------

    private void drawDebugOverlay(Graphics2D g) {
        int px = 8, py = 20, pw = 310, ph = 148;

        g.setColor(new Color(0, 0, 0, 185));
        g.fillRoundRect(px, py, pw, ph, 6, 6);
        g.setColor(new Color(80, 200, 80, 200));
        g.drawRoundRect(px, py, pw, ph, 6, 6);

        g.setFont(new Font("Arial", Font.BOLD, 10));
        g.setColor(new Color(100, 255, 100));
        g.drawString("DEBUG  (D=toggle)", px + 6, py + 13);

        g.setFont(new Font("Arial", Font.PLAIN, 10));
        g.setColor(Color.WHITE);

        int tx = px + 6, ty = py + 26, lineH = 13;
        int tileCol = Pathfinding.pixelToTileCol(panel.player.getCenterX());
        int tileRow = Pathfinding.pixelToTileRow(panel.player.getCenterY());

        g.drawString(String.format("Pixel: (%.0f, %.0f)", panel.player.getX(), panel.player.getY()), tx, ty); ty += lineH;
        g.drawString("Tile:  (" + tileCol + ", " + tileRow + ")",                                    tx, ty); ty += lineH;
        g.drawString("Cam:   (" + panel.cameraOffsetX + ", " + panel.cameraOffsetY + ")",             tx, ty); ty += lineH;
        g.drawString("State: " + panel.clientState.name() + " / " + panel.gameState.name(),          tx, ty); ty += lineH;
        g.drawString("Anim:  " + panel.player.getAnimation().getState().name(),                       tx, ty); ty += lineH;
        g.drawString("FPS:   " + panel.displayedFps,                                                  tx, ty); ty += lineH;
        g.drawString("Sound: " + (panel.soundSystem.isEnabled() ? "ON (M=mute)" : "OFF (M=on)"),      tx, ty); ty += lineH;

        if (panel.clientConnection.isConnected()) {
            double hz            = panel.clientConnection.getUpdatesPerSecond();
            List<RemotePlayer> remotePlayers = panel.getRemotePlayersSnapshot();
            long movingCount     = remotePlayers.stream().filter(RemotePlayer::isInterpolating).count();
            g.setColor(new Color(80, 220, 80));
            g.drawString(String.format("Net:   Online  id=%s  peers=%d  %.0f Hz  moving=%d",
                    panel.player.getId(), remotePlayers.size(), hz, movingCount), tx, ty);
        } else {
            g.setColor(new Color(200, 100, 100));
            g.drawString("Net:   Offline (no server)", tx, ty);
        }
        g.setColor(Color.WHITE);

    }

    // -------------------------------------------------------------------------
    //  Static helpers
    // -------------------------------------------------------------------------

    /** @deprecated Use {@link RenderUtils#drawOutlinedString} directly. */
    static void drawOutlinedString(Graphics2D g, String text, int x, int y,
                                   Color textColor, Color outlineColor) {
        RenderUtils.drawOutlinedString(g, text, x, y, textColor, outlineColor);
    }

}
