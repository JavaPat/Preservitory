package com.classic.preservitory.world.objects;

import com.classic.preservitory.entity.Entity;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;

import java.awt.Color;
import java.awt.Graphics;

/**
 * A tree object whose alive/stump state is driven entirely by the server.
 * The client never runs respawn timers or chop logic — it is a pure renderer
 * of server-authoritative state.
 */
public class Tree extends Entity {

    public enum State { ALIVE, STUMP }

    private State state;

    private final String id;

    public Tree(String id, double x, double y) {
        super(x, y, Constants.TILE_SIZE, Constants.TILE_SIZE);
        this.id    = id;
        this.state = State.ALIVE;
    }

    public String getId() { return id; }

    public void setPosition(double x, double y) {
        setX(x);
        setY(y);
    }

    // -----------------------------------------------------------------------
    //  Visual state — set by server events only
    // -----------------------------------------------------------------------

    /**
     * Set the visual state of this tree.
     * {@code true} → full tree (alive).
     * {@code false} → stump (chopped).
     * No timers are started or modified.
     */
    public void setAlive(boolean alive) {
        state = alive ? State.ALIVE : State.STUMP;
    }

    /** Convenience alias for {@code setAlive(false)} — kept for WoodcuttingSystem compatibility. */
    public void chop() {
        setAlive(false);
    }

    /** Convenience alias for {@code setAlive(true)} — kept for addTree compatibility. */
    public void respawn() {
        setAlive(true);
    }

    public boolean isAlive() {
        return state == State.ALIVE;
    }

    public boolean containsPoint(int px, int py) {
        return state == State.ALIVE
                && px >= x && px <= x + width
                && py >= y && py <= y + height;
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    @Override
    public void render(Graphics g) {
        int isoX = IsoUtils.worldToIsoX(x, y);
        int isoY = IsoUtils.worldToIsoY(x, y);

        int footX = isoX + IsoUtils.ISO_TILE_W / 2;
        int footY = isoY + IsoUtils.ISO_TILE_H;

        if (state == State.ALIVE) {
            renderAlive(g, footX, footY);
        } else {
            renderStump(g, footX, footY);
        }
    }

    private void renderAlive(Graphics g, int footX, int footY) {
        int trunkW = 8;
        int trunkH = 20;
        g.setColor(new Color(101, 67, 20));
        g.fillRect(footX - trunkW / 2, footY - trunkH, trunkW, trunkH);
        g.setColor(new Color(70, 45, 12));
        g.drawRect(footX - trunkW / 2, footY - trunkH, trunkW, trunkH);

        int canopyW = 38;
        int canopyH = 28;
        int canopyX = footX - canopyW / 2;
        int canopyY = footY - trunkH - canopyH + 8;

        g.setColor(new Color(22, 100, 22));
        g.fillOval(canopyX, canopyY, canopyW, canopyH);

        g.setColor(new Color(55, 150, 55));
        g.fillOval(canopyX + 7, canopyY + 4, canopyW / 2, canopyH / 2);

        g.setColor(new Color(0, 55, 0));
        g.drawOval(canopyX, canopyY, canopyW, canopyH);
    }

    private void renderStump(Graphics g, int footX, int footY) {
        g.setColor(new Color(90, 55, 18));
        g.fillRect(footX - 8, footY - 12, 16, 12);
        g.setColor(new Color(60, 38, 10));
        g.drawRect(footX - 8, footY - 12, 16, 12);
    }
}
