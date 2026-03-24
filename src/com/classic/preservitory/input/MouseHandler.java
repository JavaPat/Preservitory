package com.classic.preservitory.input;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Listens for mouse clicks and stores the last clicked position.
 *
 * Two ways a target can be set:
 *   1. Automatically, when the user clicks the panel (mouseClicked).
 *   2. Programmatically via setTarget(), used by GamePanel when a tree
 *      click needs a computed approach position rather than the raw click.
 *
 * MovementSystem reads hasTarget() / getTargetX/Y() each frame,
 * and calls clearTarget() when the player arrives.
 */
public class MouseHandler extends MouseAdapter {

    private int     targetX;
    private int     targetY;
    private boolean hasTarget;

    // -----------------------------------------------------------------------
    //  MouseListener
    // -----------------------------------------------------------------------

    @Override
    public void mouseClicked(MouseEvent e) {
        setTarget(e.getX(), e.getY());
    }

    // -----------------------------------------------------------------------
    //  Programmatic control
    // -----------------------------------------------------------------------

    /**
     * Set a movement target directly (bypassing a real mouse event).
     * Used by GamePanel to send the player to an approach point near a tree.
     */
    public void setTarget(int x, int y) {
        targetX   = x;
        targetY   = y;
        hasTarget = true;
    }

    /**
     * Clear the target once the player has arrived.
     * Called by MovementSystem.
     */
    public void clearTarget() {
        hasTarget = false;
    }

    // --- Getters ---

    public int     getTargetX() { return targetX; }
    public int     getTargetY() { return targetY; }
    public boolean hasTarget()  { return hasTarget; }
}
