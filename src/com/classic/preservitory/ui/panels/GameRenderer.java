package com.classic.preservitory.ui.panels;

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

    GameRenderer(GamePanel panel) {
        this.panel = panel;
    }

    // -------------------------------------------------------------------------
    //  Camera
    // -------------------------------------------------------------------------

    void updateCamera() {
        double zoom       = panel.getZoom();
        int    playerIsoX = IsoUtils.worldToIsoX(panel.player.getCenterX(), panel.player.getCenterY());
        int    playerIsoY = IsoUtils.worldToIsoY(panel.player.getCenterX(), panel.player.getCenterY());

        // Player-centred base + accumulated zoom-pan offset
        double baseCamX = playerIsoX - Constants.VIEWPORT_W / 2.0;
        double baseCamY = playerIsoY - Constants.VIEWPORT_H / 2.0;
        double rawCamX  = baseCamX + panel.cameraZoomOffsetX;
        double rawCamY  = baseCamY + panel.cameraZoomOffsetY;

        // Clamp to world ISO bounds, then sync the offset back so no
        // unclamped delta re-accumulates next frame (avoids edge jitter).
        double clampedCamX = clampCamX(rawCamX, zoom);
        double clampedCamY = clampCamY(rawCamY, zoom);
        panel.cameraOffsetX      = (int) Math.round(clampedCamX);
        panel.cameraOffsetY      = (int) Math.round(clampedCamY);
        panel.cameraZoomOffsetX  = clampedCamX - baseCamX;
        panel.cameraZoomOffsetY  = clampedCamY - baseCamY;
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
        double vpCx   = Constants.VIEWPORT_W / 2.0;
        double isoMin = IsoUtils.tileToIsoX(0, panel.world.getRows() - 1);
        double isoMax = IsoUtils.tileToIsoX(panel.world.getCols() - 1, 0) + IsoUtils.ISO_TILE_W;
        double lo     = isoMin - vpCx + vpCx / zoom;
        double hi     = isoMax - vpCx - vpCx / zoom;
        return hi >= lo ? Math.max(lo, Math.min(hi, camX)) : (isoMin + isoMax) / 2.0 - vpCx;
    }

    /** Same as {@link #clampCamX} but for the Y axis. */
    private double clampCamY(double camY, double zoom) {
        double vpCy   = Constants.VIEWPORT_H / 2.0;
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

        // ---- World-space viewport (clipped, zoom-scaled, camera-translated) ----
        Shape           savedClip      = g2.getClip();
        AffineTransform savedTransform = g2.getTransform();

        double zoom = panel.getZoom();
        int    vpCx = Constants.VIEWPORT_W / 2;
        int    vpCy = Constants.VIEWPORT_H / 2;

        g2.setClip(0, 0, Constants.VIEWPORT_W, Constants.VIEWPORT_H);
        // Scale around viewport centre so the player stays centred at all zoom levels
        g2.translate(vpCx, vpCy);
        g2.scale(zoom, zoom);
        g2.translate(-vpCx - panel.cameraOffsetX, -vpCy - panel.cameraOffsetY);

        // Tile layer
        panel.world.render(g2);

        // Hover highlight
        drawHoverHighlightWorld(g2);

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

        // ---- Restore to screen space ----
        g2.setTransform(savedTransform);
        g2.setClip(savedClip);

        // Panel separator line (two-tone for depth)
        g2.setColor(new Color(55, 44, 22));
        g2.fillRect(Constants.PANEL_X - 2, 0, 2, Constants.SCREEN_HEIGHT);
        g2.setColor(new Color(25, 20, 10));
        g2.fillRect(Constants.PANEL_X - 1, 0, 1, Constants.SCREEN_HEIGHT);

        // Right panel
        GameInputHandler h = panel.inputHandler;
        panel.rightPanel.render(g2, panel.player,
                panel.shopWindow.isOpen(),
                panel.shopWindow.getSellPrices(),
                h.activeTree  != null,
                h.activeRock  != null,
                h.activeEnemy != null,
                h.activeEnemy != null ? h.activeEnemy.getName() : null);

        // Chat box
        panel.chatBox.render(g2, 0, Constants.VIEWPORT_H - GamePanel.CHAT_H,
                Constants.VIEWPORT_W, GamePanel.CHAT_H,
                h.isTypingChat ? h.chatInput.toString() : null);

        // Full-screen overlays (rendered over the viewport only)
        panel.shopWindow.render(g2);
        panel.questCompleteWindow.render(g2);
        if (panel.authRequired) panel.loginScreen.render(g2);

        // HUD
        renderXpDrops(g2);
        drawXpTracker(g2);
        drawContextMenu(g2);
        drawActionMessage(g2);
        drawHoverText(g2);
        if (panel.debugMode) drawDebugOverlay(g2);
        drawFps(g2);
        drawLogoutButton(g2);
    }

    // -------------------------------------------------------------------------
    //  Hover highlight (world space — called inside camera-translated block)
    // -------------------------------------------------------------------------

    private void drawHoverHighlightWorld(Graphics2D g) {
        GameInputHandler h = panel.inputHandler;
        if (h.hoverX < 0 || h.hoverX >= Constants.VIEWPORT_W
         || h.hoverY < 0 || h.hoverY >= Constants.VIEWPORT_H
         || panel.gameState != GamePanel.GameState.PLAYING) return;

        // Unproject screen hover through the zoom-centred transform
        double zoom  = panel.getZoom();
        int    vpCx  = Constants.VIEWPORT_W / 2;
        int    vpCy  = Constants.VIEWPORT_H / 2;
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
        g.setFont(new Font("Monospaced", Font.BOLD, 13));
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
        if (panel.xpDrops.isEmpty()) {
            return;
        }
        g.setFont(new Font("Monospaced", Font.BOLD, 12));
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
        if (panel.authRequired) return;
        boolean confirming = panel.inputHandler.logoutConfirmTimer > 0;

        g.setColor(confirming ? new Color(160, 40, 40, 220) : new Color(50, 18, 18, 210));
        g.fillRoundRect(GamePanel.LOGOUT_BTN_X, GamePanel.LOGOUT_BTN_Y,
                GamePanel.LOGOUT_BTN_W, GamePanel.LOGOUT_BTN_H, 4, 4);

        g.setColor(confirming ? new Color(220, 80, 80) : new Color(90, 45, 45));
        g.drawRoundRect(GamePanel.LOGOUT_BTN_X, GamePanel.LOGOUT_BTN_Y,
                GamePanel.LOGOUT_BTN_W, GamePanel.LOGOUT_BTN_H, 4, 4);

        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        String label = confirming ? "Click again to confirm" : "Logout";
        g.setColor(new Color(220, 160, 160));
        int lx = GamePanel.LOGOUT_BTN_X + (GamePanel.LOGOUT_BTN_W - fm.stringWidth(label)) / 2;
        int ly = GamePanel.LOGOUT_BTN_Y + GamePanel.LOGOUT_BTN_H - 4;
        g.drawString(label, lx, ly);
    }

    private void drawActionMessage(Graphics2D g) {
        if (panel.messageTimer <= 0 || panel.actionMessage.isEmpty()) return;
        float alpha = (float) Math.min(1.0, panel.messageTimer / 0.8);
        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(panel.actionMessage);
        int tx = (Constants.VIEWPORT_W - tw) / 2;
        int ty = 38;
        g.setColor(new Color(0, 0, 0, (int)(alpha * 200)));
        g.drawString(panel.actionMessage, tx + 1, ty + 1);
        g.setColor(new Color(1f, 1f, 0.75f, alpha));
        g.drawString(panel.actionMessage, tx, ty);
    }

    private void drawHoverText(Graphics2D g) {
        if (panel.hoverText == null || panel.hoverText.isEmpty() || panel.authRequired) {
            return;
        }
        g.setFont(new Font("Monospaced", Font.BOLD, 13));
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
        if (panel.authRequired) {
            return;
        }
        String label = "Total XP: " + formatXp(panel.totalXp);
        g.setFont(new Font("Monospaced", Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();
        int boxW = fm.stringWidth(label) + 18;
        int boxH = 22;
        int x = GamePanel.XP_TRACKER_X;
        int y = GamePanel.XP_TRACKER_Y;
        g.setColor(new Color(18, 18, 18, 185));
        g.fillRoundRect(x, y, boxW, boxH, 8, 8);
        g.setColor(new Color(110, 220, 110));
        g.drawRoundRect(x, y, boxW, boxH, 8, 8);
        g.drawString(label, x + 9, y + 15);
    }

    private void drawContextMenu(Graphics2D g) {
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
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));

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

    private void drawFps(Graphics2D g) {
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g.setColor(new Color(180, 180, 180, 160));
        String label = panel.currentAccountName.isEmpty()
                ? "FPS: " + panel.displayedFps
                : "FPS: " + panel.displayedFps + "  Account: " + panel.currentAccountName;
        g.drawString(label, 8, 14);
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

        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        g.setColor(new Color(100, 255, 100));
        g.drawString("DEBUG  (D=toggle)", px + 6, py + 13);

        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(Color.WHITE);

        int tx = px + 6, ty = py + 26, lineH = 13;
        int tileCol = Pathfinding.pixelToTileCol(panel.player.getCenterX());
        int tileRow = Pathfinding.pixelToTileRow(panel.player.getCenterY());

        g.drawString(String.format("Pixel: (%.0f, %.0f)", panel.player.getX(), panel.player.getY()), tx, ty); ty += lineH;
        g.drawString("Tile:  (" + tileCol + ", " + tileRow + ")",                                    tx, ty); ty += lineH;
        g.drawString("Cam:   (" + panel.cameraOffsetX + ", " + panel.cameraOffsetY + ")",             tx, ty); ty += lineH;
        g.drawString("State: " + panel.gameState.name(),                                              tx, ty); ty += lineH;
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

    static void drawTabBar(Graphics2D g, RightPanel rightPanel) {
        TabType activeTab = rightPanel.getActiveTab();
        int hoveredTabIndex = rightPanel.getHoveredTabIndex();

        for (int i = 0; i < TabConfig.TABS.size(); i++) {
            TabConfig tab = TabConfig.TABS.get(i);
            Rectangle slotBounds = rightPanel.getTabBounds(i);
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
        g.setFont(new Font("Monospaced", Font.BOLD, 9));
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
}
