package com.classic.preservitory.client.rendering;

import com.classic.preservitory.entity.Enemy;
import com.classic.preservitory.entity.NPC;
import com.classic.preservitory.ui.framework.assets.AssetManager;
import com.classic.preservitory.ui.overlays.FloatingText;
import com.classic.preservitory.ui.panels.GamePanel;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.RenderUtils;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.List;

public class UIRenderer {

    private final RenderContext context;
    private final GamePanel panel;

    public UIRenderer(RenderContext context, GamePanel panel) {
        this.context = context;
        this.panel = panel;
    }

    public void render(Graphics2D g2) {
        int viewportW = context.getViewportWidth();
        int viewportH = context.getViewportHeight();

        if (!context.isInGame()) {
            return;
        }

        // Base UI layer — rendered before fullscreen modals
        renderViewportSeparator(g2);

        int chatX = 8;
        int chatY = viewportH - context.getChatHeight();
        int chatRight = Math.max(chatX + 220, panel.getPanelX() - 8);
        int chatW = Math.max(220, chatRight - chatX);
        context.getChatBox().render(g2, chatX, chatY,
                chatW, context.getChatHeight(),
                context.getCurrentChatInputText());

        if (panel.shouldRenderTargetPanel()) {
            renderTargetPanel(g2);
        }

        if (panel.shouldRenderGameMinimap()) {
            renderGameMinimap(g2, viewportW, viewportH);
        }

        renderXpDrops(g2);
        drawXpTracker(g2);
        drawContextMenu(g2);
        drawActionMessage(g2);
        drawHoverText(g2);
        renderMetrics(g2);
        renderTransitionOverlay(g2);

        // Fullscreen modal overlays — always rendered last so they appear on top of all other UI
        panel.renderFullscreenUiOverlays(g2);
    }

    private void renderXpDrops(Graphics2D g) {
        if (panel.getXpDropsSnapshot().isEmpty() || !panel.isShowTotalXpEnabled()) {
            return;
        }
        g.setFont(new Font("Arial", Font.BOLD, 12));
        List<FloatingText> snapshot = panel.getXpDropsSnapshot();
        Composite originalComposite = g.getComposite();
        for (FloatingText drop : snapshot) {
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

    private void drawXpTracker(Graphics2D g) {
        if (!panel.shouldRenderXpTracker()) {
            return;
        }
        String label = "Total XP: " + formatXp(panel.getTotalXpValue());
        g.setFont(new Font("Arial", Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();
        int boxW = fm.stringWidth(label) + 18;
        int boxH = 22;
        int x = panel.getPanelBoundaryX() - boxW - 8;
        int y = panel.isShowMinimapEnabled()
                ? 10 + panel.getMinimapSize() + 14
                : panel.getXpTrackerBaseY();
        g.setColor(new Color(18, 18, 18, 185));
        g.fillRoundRect(x, y, boxW, boxH, 8, 8);
        g.setColor(new Color(110, 220, 110));
        g.drawRoundRect(x, y, boxW, boxH, 8, 8);
        g.drawString(label, x + 9, y + 15);
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

    private void drawActionMessage(Graphics2D g) {
        if (!panel.shouldRenderActionMessage()) return;
        float alpha = (float) Math.min(1.0, panel.getMessageTimerValue() / 0.8);
        g.setFont(new Font("Arial", Font.BOLD, 13));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(panel.getActionMessageText());
        int tx = (context.getPanelX() - tw) / 2;
        int ty = 38;
        g.setColor(new Color(0, 0, 0, (int)(alpha * 200)));
        g.drawString(panel.getActionMessageText(), tx + 1, ty + 1);
        g.setColor(new Color(1f, 1f, 0.75f, alpha));
        g.drawString(panel.getActionMessageText(), tx, ty);
    }

    private void drawHoverText(Graphics2D g) {
        if (!panel.shouldRenderHoverText()) {
            return;
        }
        g.setFont(new Font("Arial", Font.BOLD, 13));
        FontMetrics fm = g.getFontMetrics();
        int x = 8;
        int y = 32;
        int padding = 6;
        int boxW = fm.stringWidth(panel.getHoverTextValue()) + padding * 2;
        int boxH = fm.getHeight() + 4;
        g.setColor(new Color(0, 0, 0, 175));
        g.fillRoundRect(x - padding, y - fm.getAscent() - 2, boxW, boxH, 6, 6);
        g.setColor(new Color(255, 215, 90));
        g.drawRoundRect(x - padding, y - fm.getAscent() - 2, boxW, boxH, 6, 6);
        g.drawString(panel.getHoverTextValue(), x, y);
    }

    private void drawContextMenu(Graphics2D g) {
        if (!panel.shouldRenderContextMenu()) {
            return;
        }

        int menuWidth = panel.getContextMenuRenderWidth();
        int menuHeight = panel.getContextMenuRenderHeight();
        int menuX = panel.getContextMenuRenderX(menuWidth);
        int menuY = panel.getContextMenuRenderY(menuHeight);

        g.setColor(new Color(16, 12, 7, 235));
        g.fillRoundRect(menuX, menuY, menuWidth, menuHeight, 6, 6);
        g.setColor(new Color(190, 165, 70));
        g.drawRoundRect(menuX, menuY, menuWidth, menuHeight, 6, 6);
        g.setFont(new Font("Arial", Font.PLAIN, 11));

        int hovered = panel.getContextMenuHoveredIndex();
        List<String> labels = panel.getContextMenuLabelsSnapshot();
        int optionHeight = panel.getContextMenuOptionHeight();
        for (int i = 0; i < labels.size(); i++) {
            int rowY = menuY + i * optionHeight;
            g.setColor(i % 2 == 0 ? new Color(34, 26, 16, 180) : new Color(26, 20, 12, 180));
            g.fillRect(menuX + 2, rowY + 1, menuWidth - 4, optionHeight - 2);
            if (i == hovered) {
                g.setColor(new Color(255, 220, 90, 70));
                g.fillRect(menuX + 2, rowY + 1, menuWidth - 4, optionHeight - 2);
            }
            g.setColor(new Color(115, 92, 46));
            g.drawRect(menuX + 2, rowY + 1, menuWidth - 5, optionHeight - 3);
            g.setColor(Color.WHITE);
            g.drawString(labels.get(i), menuX + 5, rowY + 13);
        }
    }

    // -------------------------------------------------------------------------
    //  UI overlays moved from GamePanel
    // -------------------------------------------------------------------------

    private void renderViewportSeparator(Graphics2D g2) {
        int panelX = context.getPanelX();
        int screenH = context.getScreenHeight();
        g2.setColor(new Color(55, 44, 22));
        g2.fillRect(panelX - 2, 0, 2, screenH);
        g2.setColor(new Color(25, 20, 10));
        g2.fillRect(panelX - 1, 0, 1, screenH);
    }

    private void renderTargetPanel(Graphics2D g) {
        Enemy target = context.getCombatTargetEnemy();
        if (target == null || !target.isAlive()) return;

        String name = target.getName();
        String pct  = (int)(target.getHpFraction() * 100) + "%";

        g.setFont(new Font("Arial", Font.BOLD, 11));
        FontMetrics nameFm = g.getFontMetrics();
        g.setFont(new Font("Arial", Font.PLAIN, 10));
        FontMetrics pctFm = g.getFontMetrics();

        int padH   = 8;
        int padV   = 6;
        int lineH  = nameFm.getHeight();
        int pWidth = Math.max(nameFm.stringWidth(name), pctFm.stringWidth(pct)) + padH * 2;
        int pHeight = lineH * 2 + padV * 2;
        int pX = 10;
        int pY = 10;

        g.setColor(new Color(20, 16, 8, 215));
        g.fillRoundRect(pX, pY, pWidth, pHeight, 6, 6);
        g.setColor(new Color(110, 88, 44));
        g.drawRoundRect(pX, pY, pWidth, pHeight, 6, 6);

        g.setFont(new Font("Arial", Font.BOLD, 11));
        g.setColor(new Color(255, 200, 100));
        g.drawString(name, pX + padH, pY + padV + nameFm.getAscent());

        g.setFont(new Font("Arial", Font.PLAIN, 10));
        g.setColor(Color.WHITE);
        g.drawString(pct, pX + padH, pY + padV + lineH + pctFm.getAscent());
    }

    /**
     * Renders the game minimap as a circle in the top-right corner of the viewport.
     * Uses a circular clip so tile/entity pixels outside the circle are hidden.
     */
    private void renderGameMinimap(Graphics2D g, int viewportW, int viewportH) {
        int mmSize = panel.getMinimapSize();
        int mmX = viewportW - mmSize - 10;
        int mmY = 10;

        int mapW = panel.getWorld().getCols();
        int mapH = panel.getWorld().getRows();

        int zoom = panel.getMinimapZoom();
        int visibleW = Math.max(1, mapW >> (zoom - 1));
        int visibleH = Math.max(1, mapH >> (zoom - 1));

        float playerTileX = (float)(context.getPlayer().getX() / Constants.TILE_SIZE);
        float playerTileY = (float)(context.getPlayer().getY() / Constants.TILE_SIZE);

        int startCol = (int) Math.max(0, Math.min(mapW - visibleW, playerTileX - visibleW / 2.0f));
        int startRow = (int) Math.max(0, Math.min(mapH - visibleH, playerTileY - visibleH / 2.0f));

        float tileW = (float) mmSize / visibleW;
        float tileH = (float) mmSize / visibleH;

        // Apply circular clip so all drawing stays within the circle
        Shape savedClip = g.getClip();
        g.setClip(new Ellipse2D.Float(mmX, mmY, mmSize, mmSize));

        // Dark background fill (circular)
        g.setColor(new Color(0, 0, 0, 180));
        g.fillOval(mmX, mmY, mmSize, mmSize);

        // Tile colors
        for (int row = startRow; row < startRow + visibleH; row++) {
            for (int col = startCol; col < startCol + visibleW; col++) {
                if (col < 0 || col >= mapW || row < 0 || row >= mapH) continue;
                com.classic.preservitory.world.Tile tile = panel.getWorld().getTile(col, row);
                g.setColor(minimapTileColor(tile.getType()));
                int px = mmX + Math.round((col - startCol) * tileW);
                int py = mmY + Math.round((row - startRow) * tileH);
                g.fillRect(px, py, Math.max(1, Math.round(tileW)), Math.max(1, Math.round(tileH)));
            }
        }

        // NPC dots — yellow
        for (NPC npc : panel.getClientWorld().getNpcs()) {
            float nx = (float)(npc.getX() / Constants.TILE_SIZE);
            float ny = (float)(npc.getY() / Constants.TILE_SIZE);
            if (nx < startCol || nx >= startCol + visibleW || ny < startRow || ny >= startRow + visibleH) continue;
            int dotX = mmX + (int) Math.round((nx - startCol) * tileW);
            int dotY = mmY + (int) Math.round((ny - startRow) * tileH);
            g.setColor(new Color(0, 0, 0, 180));
            g.fillOval(dotX - 3, dotY - 3, 6, 6);
            g.setColor(Color.YELLOW);
            g.fillOval(dotX - 2, dotY - 2, 4, 4);
        }

        // Enemy dots — red
        for (Enemy enemy : panel.getClientWorld().getEnemies()) {
            float ex = (float)(enemy.getX() / Constants.TILE_SIZE);
            float ey = (float)(enemy.getY() / Constants.TILE_SIZE);
            if (ex < startCol || ex >= startCol + visibleW || ey < startRow || ey >= startRow + visibleH) continue;
            int dotX = mmX + (int) Math.round((ex - startCol) * tileW);
            int dotY = mmY + (int) Math.round((ey - startRow) * tileH);
            g.setColor(new Color(0, 0, 0, 180));
            g.fillOval(dotX - 3, dotY - 3, 6, 6);
            g.setColor(new Color(220, 60, 60));
            g.fillOval(dotX - 2, dotY - 2, 4, 4);
        }

        // Player dot — white, on top
        int ptX = mmX + (int) Math.round((playerTileX - startCol) * tileW);
        int ptY = mmY + (int) Math.round((playerTileY - startRow) * tileH);
        g.setColor(new Color(0, 0, 0, 180));
        g.fillOval(ptX - 3, ptY - 3, 6, 6);
        g.setColor(Color.WHITE);
        g.fillOval(ptX - 2, ptY - 2, 4, 4);

        // Restore clip, then draw circular border on top
        g.setClip(savedClip);
        g.setColor(new Color(100, 100, 120));
        g.drawOval(mmX, mmY, mmSize, mmSize);
        g.setFont(new Font("Arial", Font.BOLD, 9));
        g.setColor(new Color(180, 180, 180));
        g.drawString("MINIMAP", mmX + (mmSize - 42) / 2, mmY + mmSize + 11);
    }

    private static Color minimapTileColor(com.classic.preservitory.world.Tile.TileType type) {
        switch (type) {
            case GRASS:      return new Color(55, 130, 50);
            case DARK_GRASS: return new Color(30, 90, 28);
            case DIRT:       return new Color(140, 100, 55);
            case SAND:       return new Color(195, 175, 110);
            default:         return new Color(60, 120, 180);
        }
    }

    private void renderMetrics(Graphics2D g) {
        if (!context.isInGame() || (!panel.isShowFpsEnabled() && !panel.isShowPingEnabled())) {
            return;
        }
        int x = Math.max(8, context.getScreenWidth() / 100);
        int y = Math.max(14, context.getScreenHeight() / 36);
        g.setFont(new Font("Arial", Font.PLAIN, 11));
        g.setColor(new Color(180, 180, 180, 160));
        if (panel.isShowFpsEnabled()) {
            g.drawString("FPS: " + panel.getDisplayedFps(), x, y);
            y += 14;
        }
        if (panel.isShowPingEnabled()) {
            long pingMs = panel.getPingMs();
            g.drawString(pingMs >= 0 ? "Ping: " + pingMs + " ms" : "Ping: N/A", x, y);
        }
    }

    private void renderTransitionOverlay(Graphics2D g) {
        float alpha = panel.getFadeAlpha();
        if (alpha <= 0f) {
            return;
        }
        Composite savedComposite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, context.getScreenWidth(), context.getScreenHeight());
        g.setComposite(savedComposite);

        if (panel.isLoadingState()) {
            String text = "Loading - please wait";
            g.setFont(new Font("Arial", Font.BOLD, 16));
            FontMetrics fm = g.getFontMetrics();
            int x = (context.getScreenWidth() - fm.stringWidth(text)) / 2;
            int y = context.getScreenHeight() / 2;
            drawOutlinedString(g, text, x, y, new Color(220, 205, 120), new Color(0, 0, 0, 180));
        }
    }

    private static void drawOutlinedString(Graphics2D g, String text, int x, int y,
                                            Color textColor, Color outlineColor) {
        RenderUtils.drawOutlinedString(g, text, x, y, textColor, outlineColor);
    }
}
