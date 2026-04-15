package com.classic.preservitory.ui.panels;

import com.classic.preservitory.client.editor.EditorObject;
import com.classic.preservitory.client.rendering.RenderContext;
import com.classic.preservitory.client.world.ClientProjectile;
import com.classic.preservitory.client.world.map.TileMap;
import com.classic.preservitory.entity.Enemy;
import com.classic.preservitory.entity.Entity;
import com.classic.preservitory.entity.NPC;
import com.classic.preservitory.entity.RemotePlayer;
import com.classic.preservitory.ui.framework.assets.AssetManager;
import com.classic.preservitory.ui.overlays.FloatingText;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;
import com.classic.preservitory.world.objects.Loot;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class WorldRenderer {

    private final RenderContext context;
    private final GamePanel panel;

    private static final int TILE_SIZE = Constants.TILE_SIZE;
    private static final Color COLOR_PROJECTILE_RANGED = new Color(220, 180, 60);
    private static final Color COLOR_PROJECTILE_MAGIC = new Color(80, 200, 255);
    private static final int PROJECTILE_RADIUS = 4;

    public WorldRenderer(RenderContext context, GamePanel panel) {
        this.context = context;
        this.panel = panel;
    }

    void updateCamera() {
        double zoom = context.getZoom();
        double playerIsoX = context.getPlayer().getCenterX();
        double playerIsoY = context.getPlayer().getCenterY();

        double baseCamX = playerIsoX - context.getViewportWidth() / 2.0;
        double baseCamY = playerIsoY - context.getViewportHeight() / 2.0;
        double rawCamX = baseCamX + panel.getCameraZoomOffsetX();
        double rawCamY = baseCamY + panel.getCameraZoomOffsetY();

        if (!Constants.EDITOR_MODE) {
            double clampedCamX = clampCamX(rawCamX, zoom);
            double clampedCamY = clampCamY(rawCamY, zoom);
            if (Double.isNaN(panel.getSmoothCamX())) {
                panel.setSmoothCamX(clampedCamX);
                panel.setSmoothCamY(clampedCamY);
            } else {
                panel.setSmoothCamX(panel.getSmoothCamX() + (clampedCamX - panel.getSmoothCamX()) * 0.1);
                panel.setSmoothCamY(panel.getSmoothCamY() + (clampedCamY - panel.getSmoothCamY()) * 0.1);
            }
            panel.setCameraOffsetX((int) Math.round(panel.getSmoothCamX()));
            panel.setCameraOffsetY((int) Math.round(panel.getSmoothCamY()));
            panel.setCameraZoomOffsetX(clampedCamX - baseCamX);
            panel.setCameraZoomOffsetY(clampedCamY - baseCamY);
        } else {
            panel.setCameraOffsetX((int) Math.round(rawCamX));
            panel.setCameraOffsetY((int) Math.round(rawCamY));
        }
    }

    private double clampCamX(double camX, double zoom) {
        double vpCx = context.getViewportWidth() / 2.0;
        double worldW = context.getWorld().getCols() * (double) Constants.TILE_SIZE;
        double lo = -vpCx + vpCx / zoom;
        double hi = worldW - vpCx - vpCx / zoom;
        return hi >= lo ? Math.max(lo, Math.min(hi, camX)) : worldW / 2.0 - vpCx;
    }

    private double clampCamY(double camY, double zoom) {
        double vpCy = context.getViewportHeight() / 2.0;
        double worldH = context.getWorld().getRows() * (double) Constants.TILE_SIZE;
        double lo = -vpCy + vpCy / zoom;
        double hi = worldH - vpCy - vpCy / zoom;
        return hi >= lo ? Math.max(lo, Math.min(hi, camY)) : worldH / 2.0 - vpCy;
    }

    public void render(Graphics2D g2) {
        if (!context.isInGame()) return;

        int viewportW = context.getViewportWidth();
        int viewportH = context.getViewportHeight();
        int panelX = context.getPanelX();

        updateCamera();
        if (Constants.EDITOR_MODE) updateHoveredTile();
        boolean renderWorld = shouldRenderWorld();

        AffineTransform savedTransform = g2.getTransform();

        double zoom = context.getZoom();
        int vpCx = viewportW / 2;
        int vpCy = viewportH / 2;

        g2.setClip(0, 0, viewportW, viewportH);
        g2.translate(vpCx, vpCy);
        g2.scale(zoom, zoom);
        g2.translate(-vpCx - context.getCameraOffsetX(), -vpCy - context.getCameraOffsetY());

        if (renderWorld) context.getWorld().render(g2);

        if (Constants.EDITOR_MODE) {
            TileMap tileMap = context.getTileMap();
            drawTiles(g2, tileMap);
            drawGrid(g2, tileMap);
            drawEditorObjects(g2);
            drawEditorHoverTile(g2);
        }

        if (renderWorld) drawHoverHighlightWorld(g2);

        if (renderWorld) {
            List<Entity> depthSorted = new ArrayList<>();
            depthSorted.addAll(context.getClientWorld().getRocks());
            depthSorted.addAll(context.getClientWorld().getTrees());
            depthSorted.addAll(context.getClientWorld().getLoot());
            depthSorted.addAll(context.getClientWorld().getEnemies());
            depthSorted.addAll(context.getClientWorld().getNpcs());
            depthSorted.addAll(context.getRemotePlayersSnapshot());
            depthSorted.add(context.getPlayer());
            depthSorted.sort(Comparator
                    .comparingDouble((Entity e) -> e.getY() + e.getHeight())
                    .thenComparingInt(e -> (e instanceof Loot) ? 1 : 0));
            for (Entity e : depthSorted) e.render(g2);

            drawClickIndicator(g2);
            renderProjectiles(g2);
            renderFloatingTexts(g2);
            drawEntityOverlays(g2, viewportW);
        }

        g2.setTransform(savedTransform);
        g2.setClip(null);
    }

    private void drawEntityOverlays(Graphics2D g, int viewportW) {
        if (!context.isInGame() || !context.isPlayingGameState()) return;

        double zoom = context.getZoom();
        int vpCx = viewportW / 2;
        int vpCy = context.getViewportHeight() / 2;
        int isoHX = (int)((context.getMouseX() - vpCx) / zoom + vpCx) + context.getCameraOffsetX();
        int isoHY = (int)((context.getMouseY() - vpCy) / zoom + vpCy) + context.getCameraOffsetY();
        int tileCol = IsoUtils.isoToTileCol(isoHX, isoHY);
        int tileRow = IsoUtils.isoToTileRow(isoHX, isoHY);
        int worldHX = tileCol * TILE_SIZE + TILE_SIZE / 2;
        int worldHY = tileRow * TILE_SIZE + TILE_SIZE / 2;

        Entity hoveredEntity = context.getClientWorld().getEnemyAt(worldHX, worldHY);
        if (hoveredEntity == null) hoveredEntity = context.getClientWorld().getNpcAt(worldHX, worldHY);
        if (hoveredEntity == null) {
            for (RemotePlayer rp : context.getRemotePlayersSnapshot()) {
                if (rp.containsPoint(worldHX, worldHY)) { hoveredEntity = rp; break; }
            }
        }

        Enemy currentTarget = context.getCombatTargetEnemy();

        for (Enemy e : context.getClientWorld().getEnemies()) {
            if (!e.isAlive()) continue;
            int isoX = IsoUtils.worldToIsoX(e.getX(), e.getY());
            int isoY = IsoUtils.worldToIsoY(e.getX(), e.getY());
            int footX = isoX + IsoUtils.ISO_TILE_W / 2;
            int footY = isoY + IsoUtils.ISO_TILE_H;

            if (e == currentTarget || e.recentlyInCombat()) {
                e.renderHpBar(g, footX, footY);
            }
            if (e == hoveredEntity) {
                drawEntityName(g, e.getName(), footX, footY, new Color(255, 170, 170));
            }
        }

        for (NPC npc : context.getClientWorld().getNpcs()) {
            if (npc != hoveredEntity) continue;
            int isoX = IsoUtils.worldToIsoX(npc.getX(), npc.getY());
            int isoY = IsoUtils.worldToIsoY(npc.getX(), npc.getY());
            int footX = isoX + IsoUtils.ISO_TILE_W / 2;
            int footY = isoY + IsoUtils.ISO_TILE_H;
            drawEntityName(g, npc.getName(), footX, footY, new Color(180, 230, 255));
        }

        for (RemotePlayer rp : context.getRemotePlayersSnapshot()) {
            if (rp != hoveredEntity) continue;
            int isoX = IsoUtils.worldToIsoX(rp.getX(), rp.getY());
            int isoY = IsoUtils.worldToIsoY(rp.getX(), rp.getY());
            int footX = isoX + IsoUtils.ISO_TILE_W / 2;
            int footY = isoY + IsoUtils.ISO_TILE_H;
            drawEntityName(g, rp.getId(), footX, footY, new Color(80, 200, 255));
        }
    }

    private void drawEntityName(Graphics2D g, String name, int footX, int footY, Color textColor) {
        g.setFont(new Font("Arial", Font.PLAIN, 9));
        FontMetrics fm = g.getFontMetrics();
        int nameW = fm.stringWidth(name);
        int nameX = footX - nameW / 2;
        int nameY = footY - 62;
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(nameX - 2, nameY - fm.getAscent() - 1, nameW + 4, fm.getHeight() + 2);
        g.setColor(textColor);
        g.drawString(name, nameX, nameY);
    }

    private void drawHoverHighlightWorld(Graphics2D g) {
        if (context.getMouseX() < 0 || context.getMouseX() >= context.getViewportWidth()
         || context.getMouseY() < 0 || context.getMouseY() >= context.getViewportHeight()
         || !context.isPlayingGameState()
         || !context.isInGame()) return;

        double zoom = context.getZoom();
        int vpCx = context.getViewportWidth() / 2;
        int vpCy = context.getViewportHeight() / 2;
        int isoHX = (int)((context.getMouseX() - vpCx) / zoom + vpCx) + context.getCameraOffsetX();
        int isoHY = (int)((context.getMouseY() - vpCy) / zoom + vpCy) + context.getCameraOffsetY();
        int tileCol = IsoUtils.isoToTileCol(isoHX, isoHY);
        int tileRow = IsoUtils.isoToTileRow(isoHX, isoHY);

        if (tileCol < 0 || tileCol >= context.getWorld().getCols() ||
            tileRow < 0 || tileRow >= context.getWorld().getRows()) return;

        int tx = IsoUtils.tileToIsoX(tileCol, tileRow);
        int ty = IsoUtils.tileToIsoY(tileCol, tileRow);
        int ts = Constants.TILE_SIZE;
        g.setColor(new Color(255, 255, 255, 50));
        g.fillRect(tx, ty, ts, ts);

        int worldX = tileCol * Constants.TILE_SIZE + Constants.TILE_SIZE / 2;
        int worldY = tileRow * Constants.TILE_SIZE + Constants.TILE_SIZE / 2;
        Entity hovered = context.getClientWorld().getNpcAt(worldX, worldY);
        if (hovered == null) hovered = context.getClientWorld().getEnemyAt(worldX, worldY);
        if (hovered == null) hovered = context.getClientWorld().getTreeAt(worldX, worldY);
        if (hovered == null) hovered = context.getClientWorld().getRockAt(worldX, worldY);
        if (hovered == null) hovered = context.getClientWorld().getLootAt(worldX, worldY);
        if (hovered == null) return;

        int ex = IsoUtils.worldToIsoX(hovered.getX(), hovered.getY());
        int ey = IsoUtils.worldToIsoY(hovered.getX(), hovered.getY());
        g.setColor(new Color(255, 255, 100, 55));
        g.fillRect(ex, ey, ts, ts);
        g.setColor(new Color(255, 255, 100, 200));
        g.drawRect(ex, ey, ts, ts);
    }

    private void drawClickIndicator(Graphics2D g) {
        if (!context.hasMouseTarget()) return;
        int cx = IsoUtils.worldToIsoX(context.getMouseTargetX(), context.getMouseTargetY())
                + Constants.TILE_SIZE / 2;
        int cy = IsoUtils.worldToIsoY(context.getMouseTargetX(), context.getMouseTargetY())
                + Constants.TILE_SIZE / 2;
        int r = 6;
        g.setColor(new Color(255, 220, 0, 200));
        g.drawLine(cx - r, cy - r, cx + r, cy + r);
        g.drawLine(cx + r, cy - r, cx - r, cy + r);
    }

    private void renderProjectiles(Graphics2D g) {
        List<ClientProjectile> projs = context.getClientWorld().getProjectiles();
        if (projs.isEmpty()) return;

        Composite orig = g.getComposite();
        for (ClientProjectile proj : projs) {
            float[] wp = proj.getPosition();
            double isoX = IsoUtils.worldToIsoX(wp[0], wp[1]);
            double isoY = IsoUtils.worldToIsoY(wp[0], wp[1]);

            Color color = "MAGIC".equals(proj.type) ? COLOR_PROJECTILE_MAGIC : COLOR_PROJECTILE_RANGED;
            int cx = (int) isoX;
            int cy = (int) isoY;

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
            g.setColor(color);
            g.fillOval(cx - PROJECTILE_RADIUS - 2, cy - PROJECTILE_RADIUS - 2,
                    (PROJECTILE_RADIUS + 2) * 2, (PROJECTILE_RADIUS + 2) * 2);

            g.setComposite(orig);
            g.setColor(color);
            g.fillOval(cx - PROJECTILE_RADIUS, cy - PROJECTILE_RADIUS,
                    PROJECTILE_RADIUS * 2, PROJECTILE_RADIUS * 2);

            g.setColor(color.darker().darker());
            g.drawOval(cx - PROJECTILE_RADIUS, cy - PROJECTILE_RADIUS,
                    PROJECTILE_RADIUS * 2, PROJECTILE_RADIUS * 2);
        }
        g.setComposite(orig);
    }

    private void renderFloatingTexts(Graphics2D g) {
        g.setFont(new Font("Arial", Font.BOLD, 11));
        List<FloatingText> snapshot = new ArrayList<>(context.getFloatingTextsSnapshot());
        for (FloatingText ft : snapshot) {
            ft.renderInContext(g);
        }
    }

    private void updateHoveredTile() {
        int mouseX = context.getMouseX();
        int mouseY = context.getMouseY();
        int viewportW = context.getViewportWidth();
        int viewportH = context.getViewportHeight();
        if (mouseX < 0 || mouseX >= viewportW ||
            mouseY < 0 || mouseY >= viewportH) return;

        double zoom = context.getZoom();
        int vpCx = viewportW / 2;
        int vpCy = viewportH / 2;
        int isoX = (int)((mouseX - vpCx) / zoom + vpCx) + context.getCameraOffsetX();
        int isoY = (int)((mouseY - vpCy) / zoom + vpCy) + context.getCameraOffsetY();

        panel.setHoveredTile(IsoUtils.isoToTileCol(isoX, isoY), IsoUtils.isoToTileRow(isoX, isoY));
    }

    private void drawEditorObjects(Graphics2D g) {
        for (EditorObject obj : context.getEditorObjects()) {
            BufferedImage img = AssetManager.getImage(
                    com.classic.preservitory.client.editor.EditorActions.getSpriteKey(obj.key));
            if (img == null) continue;
            int isoX = IsoUtils.tileToIsoX(obj.tileX, obj.tileY);
            int isoY = IsoUtils.tileToIsoY(obj.tileX, obj.tileY);
            int cx = isoX + Constants.TILE_SIZE / 2;
            int cy = isoY + Constants.TILE_SIZE / 2;
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
        int tileX = context.getHoveredTileX();
        int tileY = context.getHoveredTileY();
        TileMap tileMap = context.getTileMap();
        if (tileX < 0 || tileY < 0 || tileX >= tileMap.getWidth() || tileY >= tileMap.getHeight()) return;

        int isoX = IsoUtils.tileToIsoX(tileX, tileY);
        int isoY = IsoUtils.tileToIsoY(tileX, tileY);
        int ts = Constants.TILE_SIZE;
        g.setColor(new Color(255, 255, 255, 80));
        g.fillRect(isoX, isoY, ts, ts);
        g.setColor(new Color(255, 255, 255, 180));
        g.drawRect(isoX, isoY, ts, ts);
    }

    private void drawTiles(Graphics2D g, TileMap tileMap) {
        int ts = Constants.TILE_SIZE;
        for (int x = 0; x < tileMap.getWidth(); x++) {
            for (int y = 0; y < tileMap.getHeight(); y++) {
                int tileId = tileMap.getTile(x, y);
                switch (tileId) {
                    case 0: g.setColor(new Color(34, 139, 34)); break;
                    case 1: g.setColor(new Color(194, 178, 128)); break;
                    case 2: g.setColor(new Color(70, 130, 180)); break;
                    case 3: g.setColor(new Color(120, 120, 120)); break;
                    default: g.setColor(Color.MAGENTA); break;
                }
                int isoX = IsoUtils.tileToIsoX(x, y);
                int isoY = IsoUtils.tileToIsoY(x, y);
                g.fillRect(isoX, isoY, ts, ts);
            }
        }
    }

    private void drawGrid(Graphics2D g, TileMap tileMap) {
        int ts = Constants.TILE_SIZE;
        g.setColor(new Color(0, 0, 0, 50));
        for (int x = 0; x < tileMap.getWidth(); x++) {
            for (int y = 0; y < tileMap.getHeight(); y++) {
                int isoX = IsoUtils.tileToIsoX(x, y);
                int isoY = IsoUtils.tileToIsoY(x, y);
                g.drawRect(isoX, isoY, ts, ts);
            }
        }
    }

    private boolean shouldRenderWorld() {
        return !Constants.EDITOR_MODE && context.isInGame();
    }
}
