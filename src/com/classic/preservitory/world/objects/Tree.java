package com.classic.preservitory.world.objects;

import com.classic.preservitory.entity.Entity;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;

import java.awt.Color;
import java.awt.Graphics;

/**
 * A tree that the player can chop for Woodcutting XP and Logs.
 *
 * Life-cycle:
 *   ALIVE  — full tree; player can interact with it
 *   STUMP  — just chopped; cannot be interacted with; respawns after RESPAWN_TIME seconds
 *
 * Rendered in isometric style: trunk + rounded canopy sitting above the tile.
 * The foot (anchor) point is the bottom-centre of the tile's diamond.
 */
public class Tree extends Entity {

    public enum State { ALIVE, STUMP }

    /** Seconds before a stump regrows. */
    private static final double RESPAWN_TIME = 12.0;

    private State  state;
    private double respawnTimer;

    public Tree(double x, double y) {
        super(x, y, Constants.TILE_SIZE, Constants.TILE_SIZE);
        this.state = State.ALIVE;
    }

    // -----------------------------------------------------------------------
    //  Logic
    // -----------------------------------------------------------------------

    /** Must be called every frame. Ticks the respawn counter so stumps regrow. */
    public void update(double deltaTime) {
        if (state == State.STUMP) {
            respawnTimer -= deltaTime;
            if (respawnTimer <= 0) {
                state = State.ALIVE;
            }
        }
    }

    /** Called by WoodcuttingSystem when the chop timer fires. */
    public void chop() {
        state        = State.STUMP;
        respawnTimer = RESPAWN_TIME;
    }

    /** True only when the tree is alive and can be chopped. */
    public boolean isAlive() { return state == State.ALIVE; }

    /**
     * True if the world-space point (px, py) falls inside this tree's tile bounding box.
     * Stump tiles are intentionally not clickable.
     */
    public boolean containsPoint(int px, int py) {
        return state == State.ALIVE
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

        // "Foot" = bottom-centre of the tile diamond (where the tree stands)
        int footX = isoX + IsoUtils.ISO_TILE_W / 2;
        int footY = isoY + IsoUtils.ISO_TILE_H;

        if (state == State.ALIVE) {
            renderAlive(g, footX, footY);
        } else {
            renderStump(g, footX, footY);
        }
    }

    private void renderAlive(Graphics g, int footX, int footY) {
        // Trunk (brown rectangle rising from the ground)
        int trunkW = 8;
        int trunkH = 20;
        g.setColor(new Color(101, 67, 20));
        g.fillRect(footX - trunkW / 2, footY - trunkH, trunkW, trunkH);
        g.setColor(new Color(70, 45, 12));
        g.drawRect(footX - trunkW / 2, footY - trunkH, trunkW, trunkH);

        // Canopy (oval above the trunk)
        int canopyW = 38;
        int canopyH = 28;
        int canopyX = footX - canopyW / 2;
        int canopyY = footY - trunkH - canopyH + 8;   // slight overlap with trunk top

        g.setColor(new Color(22, 100, 22));
        g.fillOval(canopyX, canopyY, canopyW, canopyH);

        // Canopy highlight
        g.setColor(new Color(55, 150, 55));
        g.fillOval(canopyX + 7, canopyY + 4, canopyW / 2, canopyH / 2);

        // Canopy outline
        g.setColor(new Color(0, 55, 0));
        g.drawOval(canopyX, canopyY, canopyW, canopyH);
    }

    private void renderStump(Graphics g, int footX, int footY) {
        // Small brown stump
        g.setColor(new Color(90, 55, 18));
        g.fillRect(footX - 8, footY - 12, 16, 12);
        g.setColor(new Color(60, 38, 10));
        g.drawRect(footX - 8, footY - 12, 16, 12);

        // Regrow progress bar below stump
        double progress = 1.0 - (respawnTimer / RESPAWN_TIME);
        int barW = IsoUtils.ISO_TILE_W / 2;
        int barX = footX - barW / 2;
        int barY = footY + 4;
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(barX, barY, barW, 4);
        g.setColor(new Color(80, 180, 80, 200));
        g.fillRect(barX, barY, (int)(barW * progress), 4);
    }
}
