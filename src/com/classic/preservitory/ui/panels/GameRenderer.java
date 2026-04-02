package com.classic.preservitory.ui.panels;

import com.classic.preservitory.client.editor.EditorObject;
import com.classic.preservitory.client.world.map.TileMap;
import com.classic.preservitory.entity.Entity;
import com.classic.preservitory.entity.RemotePlayer;
import com.classic.preservitory.system.Pathfinding;
import com.classic.preservitory.ui.framework.assets.AssetManager;
import com.classic.preservitory.ui.overlays.FloatingText;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;
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

    private static final int TAB_ICON_SIZE = 16;

    private final GamePanel panel;

    private static final int TILE_SIZE = Constants.TILE_SIZE;

    // ---- Editor panel layout (shared with GameInputHandler via package-private access) ----
    static final int EDITOR_PANEL_W   = 220;
    static final int EDITOR_BTN_H     = 40;
    static final int EDITOR_BTN_GAP   = 50;   // spacing between button tops
    static final int EDITOR_TILE_Y    = 60;   // y of first tile button (4 tiles × 50 = 200, ends at 250)
    static final int EDITOR_ACTION_Y  = 290;  // y of first action button — 30px gap after tile section ends at 250
    static final int EDITOR_OBJECT_Y  = 460;  // y of first object icon row (action section ends at 290+150=440)
    static final int EDITOR_ICON_SIZE = 36;   // icon square size
    static final int EDITOR_ICON_COLS = 2;    // icons per row
    static final int EDITOR_ICON_ROW_H = 42;  // icon row height including gap

    GameRenderer(GamePanel panel) {
        this.panel = panel;
    }

    // -------------------------------------------------------------------------
    //  Camera
    // -------------------------------------------------------------------------

    void updateCamera() {
        double zoom = panel.getZoom();
        int playerIsoX = IsoUtils.worldToIsoX(panel.player.getCenterX(), panel.player.getCenterY());
        int playerIsoY = IsoUtils.worldToIsoY(panel.player.getCenterX(), panel.player.getCenterY());

        // Player-centred base + accumulated zoom-pan offset
        double baseCamX = playerIsoX - panel.getViewportW() / 2.0;
        double baseCamY = playerIsoY - panel.getHeight() / 2.0;
        double rawCamX = baseCamX + panel.cameraZoomOffsetX;
        double rawCamY = baseCamY + panel.cameraZoomOffsetY;

        // Clamp to world ISO bounds, then sync the offset back so no
        // unclamped delta re-accumulates next frame (avoids edge jitter).
        if (!Constants.EDITOR_MODE) {
            double clampedCamX = clampCamX(rawCamX, zoom);
            double clampedCamY = clampCamY(rawCamY, zoom);
            panel.cameraOffsetX      = (int) Math.round(clampedCamX);
            panel.cameraOffsetY      = (int) Math.round(clampedCamY);
            panel.cameraZoomOffsetX  = clampedCamX - baseCamX;
            panel.cameraZoomOffsetY  = clampedCamY - baseCamY;
        } else {
            panel.cameraOffsetX = (int) Math.round(rawCamX);
            panel.cameraOffsetY = (int) Math.round(rawCamY);
        }
    }

    /**
     * Clamps a raw camera X value so the viewport never shows outside the
     * isometric world grid.
     *
     * Derivation:
     *   left-edge iso X  = camX + vpCx*(1 - 1/zoom)  >= isoMin
     *   right-edge iso X = camX + vpCx*(1 + 1/zoom)  <= isoMax
     *   → lo = isoMin - vpCx + vpCx/zoom
     *   → hi = isoMax - vpCx - vpCx/zoom
     */
    private double clampCamX(double camX, double zoom) {
        double vpCx   = panel.getViewportW() / 2.0;
        double isoMin = IsoUtils.tileToIsoX(0, panel.world.getRows() - 1);
        double isoMax = IsoUtils.tileToIsoX(panel.world.getCols() - 1, 0) + IsoUtils.ISO_TILE_W;
        double lo     = isoMin - vpCx + vpCx / zoom;
        double hi     = isoMax - vpCx - vpCx / zoom;
        return hi >= lo ? Math.max(lo, Math.min(hi, camX)) : (isoMin + isoMax) / 2.0 - vpCx;
    }

    /** Same as {@link #clampCamX} but for the Y axis. */
    private double clampCamY(double camY, double zoom) {
        double vpCy   = panel.getHeight() / 2.0;
        double isoMin = IsoUtils.tileToIsoY(0, 0);
        double isoMax = IsoUtils.tileToIsoY(panel.world.getCols() - 1, panel.world.getRows() - 1) + IsoUtils.ISO_TILE_H;
        double lo     = isoMin - vpCy + vpCy / zoom;
        double hi     = isoMax - vpCy - vpCy / zoom;
        return hi >= lo ? Math.max(lo, Math.min(hi, camY)) : (isoMin + isoMax) / 2.0 - vpCy;
    }

    // -------------------------------------------------------------------------
    //  Main render pass
    // -------------------------------------------------------------------------

    void render(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        updateCamera();
        if (Constants.EDITOR_MODE) updateHoveredTile();
        boolean renderWorld = shouldRenderWorld();

        // ---- World-space viewport (clipped, zoom-scaled, camera-translated) ----
        Shape           savedClip      = g2.getClip();
        AffineTransform savedTransform = g2.getTransform();

        double zoom = panel.getZoom();
        int viewportW = Constants.EDITOR_MODE ? panel.getWidth() : panel.getViewportW();
        int viewportH = panel.getHeight();
        int    vpCx = viewportW / 2;
        int    vpCy = viewportH / 2;

        g2.setClip(0, 0, viewportW, viewportH);
        // Scale around viewport centre so the player stays centred at all zoom levels
        g2.translate(vpCx, vpCy);
        g2.scale(zoom, zoom);
        g2.translate(-vpCx - panel.cameraOffsetX, -vpCy - panel.cameraOffsetY);

        // Tile layer
        if (renderWorld) panel.world.render(g2);

        if (Constants.EDITOR_MODE) {
            TileMap tileMap = panel.getTileMap();
            drawTiles(g2, tileMap);
            drawGrid(g2, tileMap);
            drawEditorObjects(g2);
            drawEditorHoverTile(g2);
        }

        // Hover highlight
        if (renderWorld) drawHoverHighlightWorld(g2);

        if (renderWorld) {
            // Depth-sorted entities (sort by Y so nearer entities render on top)
            List<Entity> depthSorted = new ArrayList<>();
            depthSorted.addAll(panel.clientWorld.getRocks());
            depthSorted.addAll(panel.clientWorld.getTrees());
            depthSorted.addAll(panel.clientWorld.getLoot());
            depthSorted.addAll(panel.clientWorld.getEnemies());
            depthSorted.addAll(panel.clientWorld.getNpcs());
            depthSorted.addAll(panel.getRemotePlayersSnapshot());
            depthSorted.add(panel.player);
            depthSorted.sort(Comparator
                    .comparingDouble((Entity e) -> e.getY() + e.getHeight())
                    .thenComparingInt(e -> (e instanceof Loot) ? 1 : 0));
            for (Entity e : depthSorted) e.render(g2);

            // World-space overlays
            drawClickIndicator(g2);
            renderFloatingTexts(g2);
        }

        // ---- Restore to screen space ----
        g2.setTransform(savedTransform);
        g2.setClip(savedClip);

        if (!Constants.EDITOR_MODE) {
            // Panel separator line (two-tone for depth)
            int panelX = panel.getPanelX();
            g2.setColor(new Color(55, 44, 22));
            g2.fillRect(panelX - 2, 0, 2, panel.getHeight());
            g2.setColor(new Color(25, 20, 10));
            g2.fillRect(panelX - 1, 0, 1, panel.getHeight());

            // Right panel
            GameInputHandler h = panel.inputHandler;
            panel.rightPanel.render(g2, panelX, panel.getHeight(), panel.player,
                    panel.shopWindow.isOpen(),
                    panel.shopWindow.getSellPrices(),
                    panel.woodcuttingSystem.isChopping(),
                    panel.miningSystem.isMining(),
                    h.activeEnemy != null,
                    h.activeEnemy != null ? h.activeEnemy.getName() : null);

            // Chat box
            panel.chatBox.render(g2, 0, panel.getHeight() - GamePanel.CHAT_H,
                    panel.getViewportW(), GamePanel.CHAT_H,
                    h.isTypingChat ? h.chatInput.toString() : null);

            // Full-screen overlays (rendered over the viewport only)
            panel.shopWindow.render(g2);
            panel.questCompleteWindow.render(g2);
            if (panel.shouldRenderLoginScreen()) panel.loginScreen.render(g2, panel.getWidth(), panel.getHeight());

            // HUD
            renderXpDrops(g2);
            drawXpTracker(g2);
            drawContextMenu(g2);
            drawActionMessage(g2);
            drawHoverText(g2);
            drawSettingsCog(g2);
            if (panel.debugMode) drawDebugOverlay(g2);
            drawMetrics(g2);
            drawLogoutButton(g2);
            drawTransitionOverlay(g2);
        }

        if (Constants.EDITOR_MODE) {
            drawEditorPanel(g2, viewportW, viewportH);
            drawEditorCoords(g2);
            drawEditorTooltip(g2);
        }
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

        // Section header — tiles
        g.setFont(new Font("Arial", Font.BOLD, 12));
        g.setColor(new Color(180, 180, 180));
        g.drawString("TILE", btnX, EDITOR_TILE_Y - 10);

        String[] tileLabels   = {"Grass", "Path", "Water", "Pavement"};
        Color[]  tileColors   = {new Color(34, 139, 34), new Color(194, 178, 128), new Color(70, 130, 180), new Color(120, 120, 120)};
        int selected = panel.getSelectedTileId();

        for (int i = 0; i < tileLabels.length; i++) {
            int by = EDITOR_TILE_Y + i * EDITOR_BTN_GAP;
            boolean active  = (i == selected);
            boolean hovered = mx >= btnX && mx <= btnX + btnW && my >= by && my <= by + EDITOR_BTN_H;
            g.setColor(active ? tileColors[i] : tileColors[i].darker().darker());
            g.fillRoundRect(btnX, by, btnW, EDITOR_BTN_H, 6, 6);
            if (hovered && !active) {
                g.setColor(new Color(255, 255, 255, 40));
                g.fillRoundRect(btnX, by, btnW, EDITOR_BTN_H, 6, 6);
            }
            g.setColor(active ? Color.WHITE : new Color(160, 160, 160));
            g.drawRoundRect(btnX, by, btnW, EDITOR_BTN_H, 6, 6);
            g.setFont(new Font("Arial", Font.BOLD, 13));
            FontMetrics fm = g.getFontMetrics();
            String label = (i + 1) + "  " + tileLabels[i];
            g.setColor(active ? Color.WHITE : new Color(200, 200, 200));
            g.drawString(label, btnX + (btnW - fm.stringWidth(label)) / 2, by + EDITOR_BTN_H / 2 + fm.getAscent() / 2 - 2);
        }

        // Section header — actions
        g.setFont(new Font("Arial", Font.BOLD, 12));
        g.setColor(new Color(180, 180, 180));
        g.drawString("MAP ACTIONS", btnX, EDITOR_ACTION_Y - 10);

        String[] actionLabels = {"N  New Map", "S  Save Map", "L  Load Map"};
        for (int i = 0; i < actionLabels.length; i++) {
            int by = EDITOR_ACTION_Y + i * EDITOR_BTN_GAP;
            boolean hovered = mx >= btnX && mx <= btnX + btnW && my >= by && my <= by + EDITOR_BTN_H;
            g.setColor(new Color(50, 50, 70));
            g.fillRoundRect(btnX, by, btnW, EDITOR_BTN_H, 6, 6);
            if (hovered) {
                g.setColor(new Color(255, 255, 255, 40));
                g.fillRoundRect(btnX, by, btnW, EDITOR_BTN_H, 6, 6);
            }
            g.setColor(new Color(100, 100, 140));
            g.drawRoundRect(btnX, by, btnW, EDITOR_BTN_H, 6, 6);
            g.setFont(new Font("Arial", Font.BOLD, 13));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(200, 200, 220));
            g.drawString(actionLabels[i], btnX + (btnW - fm.stringWidth(actionLabels[i])) / 2, by + EDITOR_BTN_H / 2 + fm.getAscent() / 2 - 2);
        }

        // Section header — objects
        g.setFont(new Font("Arial", Font.BOLD, 12));
        g.setColor(new Color(180, 180, 180));
        String objHeader = "OBJECTS  (R=rotate " + panel.editorState.getSelectedRotation() + "\u00b0)";
        g.drawString(objHeader, btnX, EDITOR_OBJECT_Y - 10);

        java.util.List<String> available = panel.editorState.getAvailableObjects();
        String selectedKey = panel.editorState.getSelectedObjectKey();
        int halfW = btnW / 2;

        for (int i = 0; i < available.size(); i++) {
            String key = available.get(i);
            int col = i % EDITOR_ICON_COLS;
            int row = i / EDITOR_ICON_COLS;
            int ix  = btnX + col * halfW + (halfW - EDITOR_ICON_SIZE) / 2;
            int iy  = EDITOR_OBJECT_Y + row * EDITOR_ICON_ROW_H;
            boolean isSelected = key.equals(selectedKey);
            boolean isHovered  = mx >= ix && mx <= ix + EDITOR_ICON_SIZE && my >= iy && my <= iy + EDITOR_ICON_SIZE;

            g.setColor(isSelected ? new Color(60, 100, 60) : new Color(35, 35, 35));
            g.fillRect(ix, iy, EDITOR_ICON_SIZE, EDITOR_ICON_SIZE);
            if (isHovered && !isSelected) {
                g.setColor(new Color(255, 255, 255, 40));
                g.fillRect(ix, iy, EDITOR_ICON_SIZE, EDITOR_ICON_SIZE);
            }
            g.setColor(isSelected ? new Color(100, 200, 100) : new Color(80, 80, 80));
            g.drawRect(ix, iy, EDITOR_ICON_SIZE, EDITOR_ICON_SIZE);

            BufferedImage img = AssetManager.getImage(key);
            if (img != null) {
                g.drawImage(img, ix + 2, iy + 2, EDITOR_ICON_SIZE - 4, EDITOR_ICON_SIZE - 4, null);
            }
        }

        // "Deselect object" button when one is selected
        if (selectedKey != null) {
            int dby = EDITOR_OBJECT_Y + ((available.size() + EDITOR_ICON_COLS - 1) / EDITOR_ICON_COLS) * EDITOR_ICON_ROW_H + 4;
            boolean dHovered = mx >= btnX && mx <= btnX + btnW && my >= dby && my <= dby + 24;
            g.setColor(dHovered ? new Color(120, 50, 50) : new Color(70, 30, 30));
            g.fillRoundRect(btnX, dby, btnW, 24, 4, 4);
            g.setColor(new Color(180, 80, 80));
            g.drawRoundRect(btnX, dby, btnW, 24, 4, 4);
            g.setFont(new Font("Arial", Font.BOLD, 11));
            FontMetrics dfm = g.getFontMetrics();
            String dlabel = "Clear Object";
            g.setColor(new Color(220, 150, 150));
            g.drawString(dlabel, btnX + (btnW - dfm.stringWidth(dlabel)) / 2, dby + 17);
        }
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
    //  Hover highlight (world space — called inside camera-translated block)
    // -------------------------------------------------------------------------

    private void drawHoverHighlightWorld(Graphics2D g) {
        GameInputHandler h = panel.inputHandler;
        if (h.hoverX < 0 || h.hoverX >= panel.getViewportW()
         || h.hoverY < 0 || h.hoverY >= panel.getHeight()
         || panel.gameState != GamePanel.GameState.PLAYING
         || !panel.isInGame()) return;

        // Unproject screen hover through the zoom-centred transform
        double zoom  = panel.getZoom();
        int    vpCx  = panel.getViewportW() / 2;
        int    vpCy  = panel.getHeight() / 2;
        int isoHX   = (int)((h.hoverX - vpCx) / zoom + vpCx) + panel.cameraOffsetX;
        int isoHY   = (int)((h.hoverY - vpCy) / zoom + vpCy) + panel.cameraOffsetY;
        int tileCol = IsoUtils.isoToTileCol(isoHX, isoHY);
        int tileRow = IsoUtils.isoToTileRow(isoHX, isoHY);

        if (tileCol < 0 || tileCol >= panel.world.getCols() ||
            tileRow < 0 || tileRow >= panel.world.getRows()) return;

        // Subtle diamond highlight for hovered tile
        int hw = IsoUtils.ISO_TILE_W / 2;
        int hh = IsoUtils.ISO_TILE_H / 2;
        int tx = IsoUtils.tileToIsoX(tileCol, tileRow);
        int ty = IsoUtils.tileToIsoY(tileCol, tileRow);
        int[] txPts = { tx + hw, tx + IsoUtils.ISO_TILE_W, tx + hw, tx };
        int[] tyPts = { ty,      ty + hh, ty + IsoUtils.ISO_TILE_H, ty + hh };
        g.setColor(new Color(255, 255, 255, 50));
        g.fillPolygon(txPts, tyPts, 4);

        // Yellow glow around interactable entity on the hovered tile
        int worldX = tileCol * Constants.TILE_SIZE + Constants.TILE_SIZE / 2;
        int worldY = tileRow * Constants.TILE_SIZE + Constants.TILE_SIZE / 2;
        Entity hovered = panel.clientWorld.getNpcAt(worldX, worldY);
        if (hovered == null) hovered = panel.clientWorld.getEnemyAt(worldX, worldY);
        if (hovered == null) hovered = panel.clientWorld.getTreeAt(worldX, worldY);
        if (hovered == null) hovered = panel.clientWorld.getRockAt(worldX, worldY);
        if (hovered == null) hovered = panel.clientWorld.getLootAt(worldX, worldY);
        if (hovered == null) return;

        int ex = IsoUtils.worldToIsoX(hovered.getX(), hovered.getY());
        int ey = IsoUtils.worldToIsoY(hovered.getX(), hovered.getY());
        int[] exPts = { ex + hw, ex + IsoUtils.ISO_TILE_W, ex + hw, ex };
        int[] eyPts = { ey - 2,  ey + hh, ey + IsoUtils.ISO_TILE_H + 2, ey + hh };
        g.setColor(new Color(255, 255, 100, 55));
        g.fillPolygon(exPts, eyPts, 4);
        g.setColor(new Color(255, 255, 100, 200));
        g.drawPolygon(exPts, eyPts, 4);
    }

    // -------------------------------------------------------------------------
    //  Click indicator (world space — cross at movement destination)
    // -------------------------------------------------------------------------

    private void drawClickIndicator(Graphics2D g) {
        if (!panel.mouseHandler.hasTarget()) return;
        int cx = IsoUtils.worldToIsoX(panel.mouseHandler.getTargetX(), panel.mouseHandler.getTargetY())
                 + IsoUtils.ISO_TILE_W / 2;
        int cy = IsoUtils.worldToIsoY(panel.mouseHandler.getTargetX(), panel.mouseHandler.getTargetY())
                 + IsoUtils.ISO_TILE_H / 2;
        int r = 6;
        g.setColor(new Color(255, 220, 0, 200));
        g.drawLine(cx - r, cy - r, cx + r, cy + r);
        g.drawLine(cx + r, cy - r, cx - r, cy + r);
    }

    // -------------------------------------------------------------------------
    //  Floating texts — damage numbers / XP (world space)
    // -------------------------------------------------------------------------

    private void renderFloatingTexts(Graphics2D g) {
        g.setFont(new Font("Arial", Font.BOLD, 13));
        List<FloatingText> snapshot = new ArrayList<>(panel.floatingTexts);
        for (FloatingText ft : snapshot) {
            int a = (int)(ft.alpha * 255);
            if (a <= 0) continue;
            g.setColor(new Color(0, 0, 0, Math.min(a, 180)));
            g.drawString(ft.text, (int)ft.x + 1, (int)ft.y + 1);
            g.setColor(new Color(ft.color.getRed(), ft.color.getGreen(), ft.color.getBlue(), a));
            g.drawString(ft.text, (int)ft.x, (int)ft.y);
        }
    }

    private void renderXpDrops(Graphics2D g) {
        if (panel.xpDrops.isEmpty() || !panel.settings.isShowTotalXp()) {
            return;
        }
        g.setFont(new Font("Arial", Font.BOLD, 12));
        List<GamePanel.XpDrop> snapshot = new ArrayList<>(panel.xpDrops);
        Composite originalComposite = g.getComposite();
        for (GamePanel.XpDrop drop : snapshot) {
            int alpha = (int) (drop.alpha * 255);
            if (alpha <= 0) {
                continue;
            }
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, drop.alpha));
            g.setColor(new Color(0, 0, 0, Math.min(alpha, 170)));
            g.drawString(drop.text, (int) drop.x + 1, (int) drop.y + 1);
            g.setColor(drop.color);
            g.drawString(drop.text, (int) drop.x, (int) drop.y);
        }
        g.setComposite(originalComposite);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }

    // -------------------------------------------------------------------------
    //  HUD / screen-space overlays
    // -------------------------------------------------------------------------

    private void drawLogoutButton(Graphics2D g) {
        if (panel.isPreGameState()) return;
        boolean confirming = panel.inputHandler.logoutConfirmTimer > 0;

        int btnX = panel.getLogoutBtnX();
        int btnY = panel.getLogoutBtnY();
        int btnW = panel.getLogoutBtnW();

        g.setColor(confirming ? new Color(160, 40, 40, 220) : new Color(50, 18, 18, 210));
        g.fillRoundRect(btnX, btnY, btnW, GamePanel.LOGOUT_BTN_H, 4, 4);

        g.setColor(confirming ? new Color(220, 80, 80) : new Color(90, 45, 45));
        g.drawRoundRect(btnX, btnY, btnW, GamePanel.LOGOUT_BTN_H, 4, 4);

        g.setFont(new Font("Arial", Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        String label = confirming ? "Click again to confirm" : "Logout";
        g.setColor(new Color(220, 160, 160));
        int lx = btnX + (btnW - fm.stringWidth(label)) / 2;
        int ly = btnY + GamePanel.LOGOUT_BTN_H - 4;
        g.drawString(label, lx, ly);
    }

    private void drawActionMessage(Graphics2D g) {
        if (panel.isPreGameState()) return;
        if (panel.messageTimer <= 0 || panel.actionMessage.isEmpty()) return;
        float alpha = (float) Math.min(1.0, panel.messageTimer / 0.8);
        g.setFont(new Font("Arial", Font.BOLD, 13));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(panel.actionMessage);
        int tx = (panel.getViewportW() - tw) / 2;
        int ty = 38;
        g.setColor(new Color(0, 0, 0, (int)(alpha * 200)));
        g.drawString(panel.actionMessage, tx + 1, ty + 1);
        g.setColor(new Color(1f, 1f, 0.75f, alpha));
        g.drawString(panel.actionMessage, tx, ty);
    }

    private void drawHoverText(Graphics2D g) {
        if (panel.hoverText == null || panel.hoverText.isEmpty() || panel.isPreGameState()) {
            return;
        }
        g.setFont(new Font("Arial", Font.BOLD, 13));
        FontMetrics fm = g.getFontMetrics();
        int x = 8;
        int y = 32;
        int padding = 6;
        int boxW = fm.stringWidth(panel.hoverText) + padding * 2;
        int boxH = fm.getHeight() + 4;
        g.setColor(new Color(0, 0, 0, 175));
        g.fillRoundRect(x - padding, y - fm.getAscent() - 2, boxW, boxH, 6, 6);
        g.setColor(new Color(255, 215, 90));
        g.drawRoundRect(x - padding, y - fm.getAscent() - 2, boxW, boxH, 6, 6);
        g.drawString(panel.hoverText, x, y);
    }

    private void drawXpTracker(Graphics2D g) {
        if (panel.isPreGameState() || !panel.settings.isShowTotalXp()) {
            return;
        }
        String label = "Total XP: " + formatXp(panel.totalXp);
        g.setFont(new Font("Arial", Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();
        int boxW = fm.stringWidth(label) + 18;
        int boxH = 22;
        // Align flush against the right panel with an 8 px gap
        int x = panel.getPanelX() - boxW - 8;
        int y = GamePanel.XP_TRACKER_Y;
        g.setColor(new Color(18, 18, 18, 185));
        g.fillRoundRect(x, y, boxW, boxH, 8, 8);
        g.setColor(new Color(110, 220, 110));
        g.drawRoundRect(x, y, boxW, boxH, 8, 8);
        g.drawString(label, x + 9, y + 15);
    }

    private void drawContextMenu(Graphics2D g) {
        if (panel.isPreGameState()) {
            return;
        }
        if (!panel.contextMenuOpen || panel.contextMenuOptions.isEmpty()) {
            return;
        }

        int menuWidth = panel.inputHandler.getContextMenuWidth();
        int menuHeight = panel.inputHandler.getContextMenuHeight();
        int menuX = panel.inputHandler.getContextMenuX(menuWidth);
        int menuY = panel.inputHandler.getContextMenuY(menuHeight);

        g.setColor(new Color(16, 12, 7, 235));
        g.fillRoundRect(menuX, menuY, menuWidth, menuHeight, 6, 6);
        g.setColor(new Color(190, 165, 70));
        g.drawRoundRect(menuX, menuY, menuWidth, menuHeight, 6, 6);
        g.setFont(new Font("Arial", Font.PLAIN, 11));

        int hovered = panel.inputHandler.getContextMenuOptionIndex(panel.inputHandler.hoverX, panel.inputHandler.hoverY);
        for (int i = 0; i < panel.contextMenuOptions.size(); i++) {
            int rowY = menuY + i * GamePanel.CONTEXT_MENU_OPTION_H;
            g.setColor(i % 2 == 0 ? new Color(34, 26, 16, 180) : new Color(26, 20, 12, 180));
            g.fillRect(menuX + 2, rowY + 1, menuWidth - 4, GamePanel.CONTEXT_MENU_OPTION_H - 2);
            if (i == hovered) {
                g.setColor(new Color(255, 220, 90, 70));
                g.fillRect(menuX + 2, rowY + 1, menuWidth - 4, GamePanel.CONTEXT_MENU_OPTION_H - 2);
            }
            g.setColor(new Color(115, 92, 46));
            g.drawRect(menuX + 2, rowY + 1, menuWidth - 5, GamePanel.CONTEXT_MENU_OPTION_H - 3);
            g.setColor(Color.WHITE);
            g.drawString(panel.contextMenuOptions.get(i).label, menuX + 5, rowY + 13);
        }
    }

    private void drawMetrics(Graphics2D g) {
        if (!panel.isInGame() || (!panel.settings.isShowFps() && !panel.settings.isShowPing())) {
            return;
        }
        int x = Math.max(8, panel.getWidth() / 100);
        int y = Math.max(14, panel.getHeight() / 36);
        g.setFont(new Font("Arial", Font.PLAIN, 11));
        g.setColor(new Color(180, 180, 180, 160));
        if (panel.settings.isShowFps()) {
            String fpsLabel = "FPS: " + panel.displayedFps;
            g.drawString(fpsLabel, x, y);
            y += 14;
        }
        if (panel.settings.isShowPing()) {
            long pingMs = panel.clientConnection.getPingMs();
            g.drawString(pingMs >= 0 ? "Ping: " + pingMs + " ms" : "Ping: N/A", x, y);
        }
    }

    private void drawSettingsCog(Graphics2D g) {
        if (!panel.isInGame()) {
            panel.settingsCogBounds.setBounds(0, 0, 0, 0);
            return;
        }

        int size = Math.max(20, Math.min(28, panel.getWidth() / 32));
        int x = panel.getWidth() - size - Math.max(8, panel.getWidth() / 100);
        int y = Math.max(8, panel.getHeight() / 60);
        panel.settingsCogBounds.setBounds(x, y, size, size);

        BufferedImage cog = AssetManager.getImage("settings");
        g.setColor(new Color(45, 38, 22, 220));
        g.fillRoundRect(x, y, size, size, 6, 6);
        g.setColor(new Color(200, 170, 70));
        g.drawRoundRect(x, y, size, size, 6, 6);

        if (cog != null) {
            g.drawImage(cog, x + 2, y + 2, size - 4, size - 4, null);
        } else {
            int cx = x + size / 2;
            int cy = y + size / 2;
            int rOuter = Math.max(6, size / 4);
            int rInner = Math.max(2, size / 10);
            g.setColor(new Color(220, 200, 120));
            g.drawOval(cx - rOuter, cy - rOuter, rOuter * 2, rOuter * 2);
            g.drawOval(cx - rInner, cy - rInner, rInner * 2, rInner * 2);
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4.0;
                int x1 = cx + (int) Math.round(Math.cos(angle) * (rInner + 2));
                int y1 = cy + (int) Math.round(Math.sin(angle) * (rInner + 2));
                int x2 = cx + (int) Math.round(Math.cos(angle) * (rOuter + 2));
                int y2 = cy + (int) Math.round(Math.sin(angle) * (rOuter + 2));
                g.drawLine(x1, y1, x2, y2);
            }
        }

    }

    private void drawTransitionOverlay(Graphics2D g) {
        if (panel.fadeAlpha <= 0f) {
            return;
        }

        Composite savedComposite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, panel.fadeAlpha));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, panel.getWidth(), panel.getHeight());
        g.setComposite(savedComposite);

        if (panel.isLoadingState()) {
            String text = "Loading - please wait";
            g.setFont(new Font("Arial", Font.BOLD, 16));
            FontMetrics fm = g.getFontMetrics();
            int x = (panel.getWidth() - fm.stringWidth(text)) / 2;
            int y = panel.getHeight() / 2;
            drawOutlinedString(g, text, x, y,
                    new Color(220, 205, 120), new Color(0, 0, 0, 180));
        }
    }

    private String formatXp(long xp) {
        if (xp >= 1_000_000L) {
            return String.format("%.1fm", xp / 1_000_000.0);
        }
        if (xp >= 1_000L) {
            return String.format("%.1fk", xp / 1_000.0);
        }
        return Long.toString(xp);
    }

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
            double hz     = panel.clientConnection.getUpdatesPerSecond();
            double maxLag = 0;
            List<RemotePlayer> remotePlayers = panel.getRemotePlayersSnapshot();
            for (RemotePlayer rp : remotePlayers)
                maxLag = Math.max(maxLag, rp.getInterpolationDistance());
            g.setColor(new Color(80, 220, 80));
            g.drawString(String.format("Net:   Online  id=%s  peers=%d  %.0f Hz  lag=%.0fpx",
                    panel.player.getId(), remotePlayers.size(), hz, maxLag), tx, ty);
        } else {
            g.setColor(new Color(200, 100, 100));
            g.drawString("Net:   Offline (no server)", tx, ty);
        }
        g.setColor(Color.WHITE);

        // Draw A* path waypoints
        List<Point> path = panel.movementSystem.getPath();
        if (!path.isEmpty()) {
            g.setColor(new Color(255, 220, 0, 160));
            int prevSx = 0, prevSy = 0;
            boolean hasPrev = false;
            int wi = panel.movementSystem.getWaypointIndex();
            for (int i = wi; i < path.size(); i++) {
                Point p  = path.get(i);
                int   sx = IsoUtils.worldToIsoX(p.x, p.y) + IsoUtils.ISO_TILE_W / 2 - panel.cameraOffsetX;
                int   sy = IsoUtils.worldToIsoY(p.x, p.y) + IsoUtils.ISO_TILE_H / 2 - panel.cameraOffsetY;
                if (hasPrev) g.drawLine(prevSx, prevSy, sx, sy);
                g.fillOval(sx - 3, sy - 3, 7, 7);
                prevSx = sx; prevSy = sy; hasPrev = true;
            }
        }
    }

    // -------------------------------------------------------------------------
    //  Static helpers (used by GamePanel and RightPanel)
    // -------------------------------------------------------------------------

    static void drawTabBar(Graphics2D g, RightPanel rightPanel, int panelX) {
        TabType activeTab = rightPanel.getActiveTab();
        int hoveredTabIndex = rightPanel.getHoveredTabIndex();

        for (int i = 0; i < TabConfig.TABS.size(); i++) {
            TabConfig tab = TabConfig.TABS.get(i);
            Rectangle slotBounds = rightPanel.getTabBounds(i, panelX);
            Rectangle bounds = new Rectangle(slotBounds.x + 3, slotBounds.y + 2,
                    Math.max(18, slotBounds.width - 6), Math.max(18, slotBounds.height - 4));
            boolean isActive = tab.type == activeTab;
            boolean isHovered = i == hoveredTabIndex;
            int slotY = bounds.y + (isActive ? 1 : 0);
            int slotH = bounds.height - (isActive ? 1 : 0);

            g.setColor(isActive ? new Color(60, 50, 30) : new Color(28, 22, 14));
            g.fillRect(bounds.x, slotY, bounds.width, slotH);

            if (isHovered && !isActive) {
                g.setColor(new Color(255, 245, 210, 18));
                g.fillRect(bounds.x, slotY, bounds.width, slotH);
            }

            if (isActive) {
                g.setColor(new Color(200, 170, 70));
                g.drawLine(bounds.x + 1, bounds.y, bounds.x + bounds.width - 2, bounds.y);
            }

            g.setColor(isActive
                    ? new Color(112, 90, 44)
                    : isHovered ? new Color(82, 66, 34) : new Color(55, 44, 22));
            g.drawRect(bounds.x, slotY, bounds.width - 1, slotH - 1);

            int iconX = bounds.x + (bounds.width - TAB_ICON_SIZE) / 2;
            int iconY = slotY + (slotH - TAB_ICON_SIZE) / 2;
            drawTabIcon(g, tab, iconX, iconY, isActive, isHovered);
        }
    }

    static void drawOutlinedString(Graphics2D g, String text, int x, int y,
                                   Color textColor, Color outlineColor) {
        g.setColor(outlineColor);
        g.drawString(text, x + 1, y + 1);
        g.drawString(text, x - 1, y + 1);
        g.drawString(text, x + 1, y - 1);
        g.drawString(text, x - 1, y - 1);
        g.setColor(textColor);
        g.drawString(text, x, y);
    }

    static Color iconColorFor(String name) {
        if ("Logs".equals(name))   return new Color(139,  90,  43);
        if ("Ore".equals(name))    return new Color(160,  88,  65);
        if ("Coins".equals(name))  return new Color(240, 200,  40);
        if ("Stone".equals(name))  return new Color(130, 130, 130);
        if ("Candle".equals(name)) return new Color(240, 230, 120);
        if ("Rope".equals(name))   return new Color(160, 130,  80);
        if ("Gem".equals(name))    return new Color( 80, 180, 220);
        return new Color(180, 180, 60);
    }

    private static void drawTabIcon(Graphics2D g, TabConfig tab, int x, int y,
                                    boolean active, boolean hovered) {
        BufferedImage icon = AssetManager.getImage(tab.iconKey);
        if (icon != null) {
            Object savedHint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            Composite savedComposite = g.getComposite();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            if (!active) {
                float alpha = hovered ? 0.88f : 0.68f;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            }
            g.drawImage(icon, x, y, TAB_ICON_SIZE, TAB_ICON_SIZE, null);
            if (!active) {
                g.setComposite(savedComposite);
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    savedHint != null ? savedHint : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            return;
        }

        Color fallbackColor = fallbackColorFor(tab.type);
        Color iconColor = active
                ? fallbackColor
                : hovered ? fallbackColor.darker() : fallbackColor.darker().darker();
        g.setColor(iconColor);
        g.fillRoundRect(x + 2, y + 2, TAB_ICON_SIZE - 4, TAB_ICON_SIZE - 4, 4, 4);
        g.setColor(iconColor.darker());
        g.drawRoundRect(x + 2, y + 2, TAB_ICON_SIZE - 4, TAB_ICON_SIZE - 4, 4, 4);

        String fallbackLetter = tab.type.name().substring(0, 1);
        g.setFont(new Font("Arial", Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Color.WHITE);
        g.drawString(fallbackLetter,
                x + (TAB_ICON_SIZE - fm.stringWidth(fallbackLetter)) / 2,
                y + (TAB_ICON_SIZE + fm.getAscent() - fm.getDescent()) / 2);
    }

    private static Color fallbackColorFor(TabType tabType) {
        switch (tabType) {
            case COMBAT:
                return new Color(200, 60, 60);
            case INVENTORY:
                return new Color(60, 140, 200);
            case SKILLS:
                return new Color(80, 180, 80);
            case EQUIPMENT:
                return new Color(160, 90, 200);
            case QUESTS:
                return new Color(200, 160, 50);
            default:
                return new Color(180, 180, 60);
        }
    }

    private void updateHoveredTile() {
        int mouseX = panel.inputHandler.hoverX;
        int mouseY = panel.inputHandler.hoverY;
        int viewportW = Constants.EDITOR_MODE ? panel.getWidth() : panel.getViewportW();
        int viewportH = panel.getHeight();
        if (mouseX < 0 || mouseX >= viewportW ||
            mouseY < 0 || mouseY >= viewportH) return;

        double zoom = panel.getZoom();
        int vpCx = viewportW / 2;
        int vpCy = viewportH / 2;
        int isoX = (int)((mouseX - vpCx) / zoom + vpCx) + panel.cameraOffsetX;
        int isoY = (int)((mouseY - vpCy) / zoom + vpCy) + panel.cameraOffsetY;

        panel.setHoveredTile(IsoUtils.isoToTileCol(isoX, isoY), IsoUtils.isoToTileRow(isoX, isoY));
    }

    private void drawEditorObjects(Graphics2D g) {
        for (EditorObject obj : panel.editorState.getObjects()) {
            BufferedImage img = AssetManager.getImage(obj.key);
            if (img == null) continue;
            int isoX = IsoUtils.tileToIsoX(obj.tileX, obj.tileY);
            int isoY = IsoUtils.tileToIsoY(obj.tileX, obj.tileY);
            int cx   = isoX + IsoUtils.ISO_TILE_W / 2;
            int cy   = isoY + IsoUtils.ISO_TILE_H / 2;
            int drawX = cx - img.getWidth() / 2;
            int drawY = cy - img.getHeight() / 2;
            if (obj.rotation != 0) {
                AffineTransform current = g.getTransform();
                g.translate(drawX + img.getWidth() / 2.0, drawY + img.getHeight() / 2.0);
                g.rotate(Math.toRadians(obj.rotation));
                g.drawImage(img, -img.getWidth() / 2, -img.getHeight() / 2, null);
                g.setTransform(current);
            } else {
                g.drawImage(img, drawX, drawY, null);
            }
        }
    }

    private void drawEditorHoverTile(Graphics2D g) {
        int tileX = panel.getHoveredTileX();
        int tileY = panel.getHoveredTileY();
        TileMap tileMap = panel.getTileMap();
        if (tileX < 0 || tileY < 0 || tileX >= tileMap.getWidth() || tileY >= tileMap.getHeight()) return;

        int hw = IsoUtils.ISO_TILE_W / 2;
        int hh = IsoUtils.ISO_TILE_H / 2;
        int isoX = IsoUtils.tileToIsoX(tileX, tileY);
        int isoY = IsoUtils.tileToIsoY(tileX, tileY);
        int[] xPts = { isoX + hw, isoX + IsoUtils.ISO_TILE_W, isoX + hw, isoX };
        int[] yPts = { isoY,      isoY + hh, isoY + IsoUtils.ISO_TILE_H, isoY + hh };
        g.setColor(new Color(255, 255, 255, 80));
        g.fillPolygon(xPts, yPts, 4);
        g.setColor(new Color(255, 255, 255, 180));
        g.drawPolygon(xPts, yPts, 4);
    }

    private void drawTiles(Graphics2D g, TileMap tileMap) {
        int hw = IsoUtils.ISO_TILE_W / 2;
        int hh = IsoUtils.ISO_TILE_H / 2;
        for (int x = 0; x < tileMap.getWidth(); x++) {
            for (int y = 0; y < tileMap.getHeight(); y++) {
                int tileId = tileMap.getTile(x, y);
                switch (tileId) {
                    case 0: g.setColor(new Color(34,  139,  34));  break; // grass
                    case 1: g.setColor(new Color(194, 178, 128));  break; // sand
                    case 2: g.setColor(new Color(70,  130, 180));  break; // water
                    case 3: g.setColor(new Color(120, 120, 120));  break; // rocky pavement
                    default: g.setColor(Color.MAGENTA);            break;
                }
                int isoX = IsoUtils.tileToIsoX(x, y);
                int isoY = IsoUtils.tileToIsoY(x, y);
                int[] xPts = { isoX + hw, isoX + IsoUtils.ISO_TILE_W, isoX + hw, isoX };
                int[] yPts = { isoY,      isoY + hh, isoY + IsoUtils.ISO_TILE_H, isoY + hh };
                g.fillPolygon(xPts, yPts, 4);
            }
        }
    }

    private void drawGrid(Graphics2D g, TileMap tileMap) {
        int hw = IsoUtils.ISO_TILE_W / 2;
        int hh = IsoUtils.ISO_TILE_H / 2;
        g.setColor(new Color(0, 0, 0, 50));
        for (int x = 0; x < tileMap.getWidth(); x++) {
            for (int y = 0; y < tileMap.getHeight(); y++) {
                int isoX = IsoUtils.tileToIsoX(x, y);
                int isoY = IsoUtils.tileToIsoY(x, y);
                int[] xPts = { isoX + hw, isoX + IsoUtils.ISO_TILE_W, isoX + hw, isoX };
                int[] yPts = { isoY,      isoY + hh, isoY + IsoUtils.ISO_TILE_H, isoY + hh };
                g.drawPolygon(xPts, yPts, 4);
            }
        }
    }

    private boolean shouldRenderWorld() {
        return !Constants.EDITOR_MODE && panel.isInGame();
    }

}
