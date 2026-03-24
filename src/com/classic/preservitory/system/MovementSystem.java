package com.classic.preservitory.system;

import com.classic.preservitory.entity.Player;
import com.classic.preservitory.input.MouseHandler;
import com.classic.preservitory.util.Constants;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Moves the player each frame, either along a pre-computed A* path or
 * directly toward a MouseHandler target (fallback / manual movement).
 *
 * === Priority ===
 *   1. A* path  — set via {@link #setPath(List)}; followed waypoint by waypoint
 *   2. MouseHandler target — classic direct movement used as a fallback when
 *      pathfinding is unavailable or not requested
 *
 * After each movement step, {@link Player#setFacing(int, int)} is called so
 * the sprite direction indicator always matches the last direction of travel.
 *
 * The caller (GamePanel) checks {@link #isMoving()} to drive animation states.
 */
public class MovementSystem {

    // -----------------------------------------------------------------------
    //  Path-following state
    // -----------------------------------------------------------------------

    private final List<Point> path = new ArrayList<>();
    private int     waypointIndex  = 0;
    private boolean moving         = false;

    // -----------------------------------------------------------------------
    //  Path control
    // -----------------------------------------------------------------------

    /**
     * Set a new A* path.  Replaces any existing path immediately.
     * The player begins moving toward the first waypoint on the next update.
     */
    public void setPath(List<Point> newPath) {
        path.clear();
        path.addAll(newPath);
        waypointIndex = 0;
        moving = !path.isEmpty();
    }

    /**
     * Cancel the current path.
     * Called by stopAllActivities() and on player death.
     */
    public void clearPath() {
        path.clear();
        waypointIndex = 0;
        moving = false;
    }

    /** True while the player is actively moving (either along a path or toward a direct target). */
    public boolean isMoving() { return moving; }

    /** True if there are still unvisited waypoints in the current path. */
    public boolean hasPath() { return !path.isEmpty() && waypointIndex < path.size(); }

    /** Read-only view of the current waypoint list (for the debug overlay). */
    public List<Point> getPath() { return Collections.unmodifiableList(path); }

    /** Index of the next waypoint the player is heading toward. */
    public int getWaypointIndex() { return waypointIndex; }

    // -----------------------------------------------------------------------
    //  Per-frame update
    // -----------------------------------------------------------------------

    /**
     * Move the player one step toward the current destination.
     *
     * @param player       the player to move
     * @param mouseHandler legacy direct-movement fallback (world-space coords)
     * @param deltaTime    seconds elapsed since the last frame
     */
    public void update(Player player, MouseHandler mouseHandler, double deltaTime) {
        if (hasPath()) {
            followPath(player, deltaTime);
        } else if (mouseHandler.hasTarget()) {
            moveToward(player, mouseHandler.getTargetX(), mouseHandler.getTargetY(),
                       deltaTime, mouseHandler);
        } else {
            moving = false;
        }
    }

    // -----------------------------------------------------------------------
    //  Internal movement logic
    // -----------------------------------------------------------------------

    /**
     * Advance along the A* waypoint list.
     * On reaching a waypoint, increments the index and continues to the next.
     * Updates the player's facing direction based on the movement delta.
     */
    private void followPath(Player player, double deltaTime) {
        if (waypointIndex >= path.size()) {
            path.clear();
            moving = false;
            return;
        }

        Point wp = path.get(waypointIndex);
        double dx   = wp.x - player.getCenterX();
        double dy   = wp.y - player.getCenterY();
        double dist = Math.sqrt(dx * dx + dy * dy);

        // Update facing before snapping so the dot is correct even when idle
        updateFacing(player, dx, dy);

        if (dist <= Constants.ARRIVAL_THRESHOLD) {
            // Snap to waypoint centre and advance
            player.setX(wp.x - player.getWidth()  / 2.0);
            player.setY(wp.y - player.getHeight() / 2.0);
            waypointIndex++;
            if (waypointIndex >= path.size()) {
                path.clear();
                moving = false;
            }
            return;
        }

        double step = player.getSpeed() * deltaTime;
        moving = true;

        if (step >= dist) {
            // Would overshoot — snap and advance
            player.setX(wp.x - player.getWidth()  / 2.0);
            player.setY(wp.y - player.getHeight() / 2.0);
            waypointIndex++;
            if (waypointIndex >= path.size()) {
                path.clear();
                moving = false;
            }
        } else {
            player.setX(player.getX() + (dx / dist) * step);
            player.setY(player.getY() + (dy / dist) * step);
        }
    }

    /**
     * Original direct-movement logic — moves the player's CENTER toward
     * (targetX, targetY) in a straight line.  Uses world-space coordinates.
     */
    private void moveToward(Player player, int targetX, int targetY,
                             double deltaTime, MouseHandler mouseHandler) {
        double dx   = targetX - player.getCenterX();
        double dy   = targetY - player.getCenterY();
        double dist = Math.sqrt(dx * dx + dy * dy);

        updateFacing(player, dx, dy);

        if (dist <= Constants.ARRIVAL_THRESHOLD) {
            player.setX(targetX - player.getWidth()  / 2.0);
            player.setY(targetY - player.getHeight() / 2.0);
            mouseHandler.clearTarget();
            moving = false;
            return;
        }

        moving = true;
        double step = player.getSpeed() * deltaTime;

        if (step >= dist) {
            player.setX(targetX - player.getWidth()  / 2.0);
            player.setY(targetY - player.getHeight() / 2.0);
            mouseHandler.clearTarget();
            moving = false;
        } else {
            player.setX(player.getX() + (dx / dist) * step);
            player.setY(player.getY() + (dy / dist) * step);
        }
    }

    /**
     * Determine the dominant axis from (dx, dy) and call
     * {@link Player#setFacing(int, int)} with a cardinal/diagonal value.
     */
    private void updateFacing(Player player, double dx, double dy) {
        if (Math.abs(dx) >= Math.abs(dy)) {
            player.setFacing(dx >= 0 ? 1 : -1, 0);
        } else {
            player.setFacing(0, dy >= 0 ? 1 : -1);
        }
    }
}
