package com.classic.preservitory.world.objects;

import com.classic.preservitory.entity.Entity;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;

import java.awt.Color;
import java.awt.Graphics;

/**
 * A minable rock that gives Ore and Mining XP when clicked.
 *
 * Life-cycle mirrors Tree:
 *   SOLID    — intact, player can mine it
 *   DEPLETED — just mined; a fragment remains; respawns after RESPAWN_TIME seconds
 *
 * Rendered in isometric style: a grey oval sitting on the tile surface.
 */
public class Rock extends Entity {

    public enum State { SOLID, DEPLETED }

    private static final double RESPAWN_TIME = 8.0;

    private State  state;
    private double respawnTimer;

    public Rock(double x, double y) {
        super(x, y, Constants.TILE_SIZE, Constants.TILE_SIZE);
        this.state = State.SOLID;
    }

    // -----------------------------------------------------------------------
    //  Logic
    // -----------------------------------------------------------------------

    /** Must be called every frame. Ticks the respawn counter. */
    public void update(double deltaTime) {
        if (state == State.DEPLETED) {
            respawnTimer -= deltaTime;
            if (respawnTimer <= 0) {
                state = State.SOLID;
            }
        }
    }

    /** Called by MiningSystem when a mine swing completes. */
    public void deplete() {
        state        = State.DEPLETED;
        respawnTimer = RESPAWN_TIME;
    }

    public boolean isSolid() { return state == State.SOLID; }

    /** True when the world-space point (px, py) is inside this rock and it is solid. */
    public boolean containsPoint(int px, int py) {
        return state == State.SOLID
                && px >= x && px <= x + width
                && py >= y && py <= y + height;
    }

    // -----------------------------------------------------------------------
    //  Rendering — isometric
    // -----------------------------------------------------------------------

    @Override
    public void render(Graphics g) {
        // Iso position of tile top-left
        int isoX = IsoUtils.worldToIsoX(x, y);
        int isoY = IsoUtils.worldToIsoY(x, y);

        // "Foot" = bottom-centre of the tile diamond
        int footX = isoX + IsoUtils.ISO_TILE_W / 2;
        int footY = isoY + IsoUtils.ISO_TILE_H;

        if (state == State.SOLID) {
            renderSolid(g, footX, footY);
        } else {
            renderDepleted(g, footX, footY);
        }
    }

    private void renderSolid(Graphics g, int footX, int footY) {
        // Main rock oval sitting on the ground
        int rw = 30;
        int rh = 20;
        int rx = footX - rw / 2;
        int ry = footY - rh;

        g.setColor(new Color(118, 118, 124));
        g.fillOval(rx, ry, rw, rh);

        // Lighter highlight on top-left
        g.setColor(new Color(172, 172, 178));
        g.fillOval(rx + 4, ry + 3, rw / 2, rh / 2);

        // Ore vein — reddish-brown specks (visual cue it's minable)
        g.setColor(new Color(148, 78, 58));
        g.fillRect(rx + rw / 2 - 2, ry + rh / 2, 5, 4);
        g.fillRect(rx + rw / 2 + 4, ry + rh / 3, 4, 3);

        // Outline
        g.setColor(new Color(65, 65, 70));
        g.drawOval(rx, ry, rw, rh);
    }

    private void renderDepleted(Graphics g, int footX, int footY) {
        // Small dark fragment to show the rock has been mined
        int rw = 16;
        int rh = 10;
        int rx = footX - rw / 2;
        int ry = footY - rh;

        g.setColor(new Color(65, 65, 70));
        g.fillOval(rx, ry, rw, rh);
        g.setColor(new Color(45, 45, 50));
        g.drawOval(rx, ry, rw, rh);

        // Respawn progress bar
        double progress = 1.0 - (respawnTimer / RESPAWN_TIME);
        int barW = IsoUtils.ISO_TILE_W / 2;
        int barX = footX - barW / 2;
        int barY = footY + 4;
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(barX, barY, barW, 4);
        g.setColor(new Color(140, 140, 175, 200));
        g.fillRect(barX, barY, (int)(barW * progress), 4);
    }
}
