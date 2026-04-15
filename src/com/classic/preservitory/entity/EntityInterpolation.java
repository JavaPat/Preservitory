package com.classic.preservitory.entity;

import com.classic.preservitory.util.Constants;

/**
 * Shared tick-based linear interpolation for all renderable entities.
 *
 * Interpolates from the previous tile boundary to the next over one 600 ms
 * server tick. Identical logic used by Player (via MovementSystem),
 * RemotePlayer, NPC, and Enemy — no duplication.
 *
 * Usage per entity:
 *   - Construct once with the entity's spawn position.
 *   - Call {@link #syncPosition} whenever a server snapshot arrives (safe to
 *     call every frame — repeated identical positions are ignored).
 *   - Call {@link #tick} once per render frame, then read
 *     {@link #getRenderX()} / {@link #getRenderY()} for drawing.
 */
public final class EntityInterpolation {

    /** One server game tick in milliseconds. */
    public static final long TICK_MS = 600L;

    /** Snap instantly when the server position differs by more than this many tiles. */
    private static final double SNAP_THRESHOLD_PX = 4.0 * Constants.TILE_SIZE;

    // -----------------------------------------------------------------------
    //  Interpolation state
    // -----------------------------------------------------------------------

    /** Current interpolated (render) position. */
    private double renderX, renderY;

    /** Start of the current step — always a tile boundary, never a mid-lerp position. */
    private double prevX, prevY;

    /** Server-confirmed end of the current step. */
    private double targetX, targetY;

    /** Wall-clock time when the current step began. */
    private long lastUpdateMs;

    /** Guards against restarting the lerp when the same position arrives every frame. */
    private int lastReceivedX = Integer.MIN_VALUE;
    private int lastReceivedY = Integer.MIN_VALUE;

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public EntityInterpolation(double startX, double startY) {
        prevX        = startX;
        prevY        = startY;
        targetX      = startX;
        targetY      = startY;
        renderX      = startX;
        renderY      = startY;
        lastUpdateMs = System.currentTimeMillis();
    }

    // -----------------------------------------------------------------------
    //  Network sync
    // -----------------------------------------------------------------------

    /**
     * Record a new server-authoritative tile position.
     *
     * Safe to call every render frame — repeated identical positions leave the
     * in-progress lerp undisturbed (no restart, no jitter).
     *
     * When a genuinely new position arrives the lerp starts from the previous
     * tile boundary ({@code targetX/Y}), not the current render position.
     * This guarantees each step covers exactly one tile regardless of timing.
     */
    public void syncPosition(int serverX, int serverY) {
        if (serverX == lastReceivedX && serverY == lastReceivedY) return;
        lastReceivedX = serverX;
        lastReceivedY = serverY;

        if (Math.abs(renderX - serverX) > SNAP_THRESHOLD_PX
                || Math.abs(renderY - serverY) > SNAP_THRESHOLD_PX) {
            // Teleport / first login — snap immediately
            prevX   = serverX;
            prevY   = serverY;
            renderX = serverX;
            renderY = serverY;
        } else {
            // New step: start from the last tile boundary, not the visual mid-lerp position
            prevX = targetX;
            prevY = targetY;
        }

        targetX      = serverX;
        targetY      = serverY;
        lastUpdateMs = System.currentTimeMillis();
    }

    // -----------------------------------------------------------------------
    //  Per-frame update
    // -----------------------------------------------------------------------

    /**
     * Advance the interpolation by one render frame.
     * Call once per frame before reading the render coordinates.
     */
    public void tick() {
        long   elapsed = System.currentTimeMillis() - lastUpdateMs;
        double t       = Math.min(1.0, (double) elapsed / TICK_MS);
        renderX = prevX + (targetX - prevX) * t;
        renderY = prevY + (targetY - prevY) * t;
    }

    /**
     * Instantly snap the render position to the given server coordinates.
     *
     * Use this when the entity has stopped moving ({@code moving=false} in the
     * server snapshot).  Snapping eliminates the final-tile slide that occurs
     * when the entity halts mid-lerp — the render position jumps immediately
     * to the authoritative tile boundary instead of slowly drifting there.
     *
     * Safe to call every frame for a stopped entity — subsequent calls with the
     * same coordinates are cheap (just an assignment to the same values).
     */
    public void snapTo(int serverX, int serverY) {
        prevX         = serverX;
        prevY         = serverY;
        targetX       = serverX;
        targetY       = serverY;
        renderX       = serverX;
        renderY       = serverY;
        lastReceivedX = serverX;
        lastReceivedY = serverY;
        lastUpdateMs  = System.currentTimeMillis();
    }

    // -----------------------------------------------------------------------
    //  Queries
    // -----------------------------------------------------------------------

    /** Interpolated X coordinate for rendering this frame. */
    public double getRenderX() { return renderX; }

    /** Interpolated Y coordinate for rendering this frame. */
    public double getRenderY() { return renderY; }

    /** True while the visual position has not yet reached the server target. */
    public boolean isMoving() {
        return Math.abs(targetX - renderX) > 0.5 || Math.abs(targetY - renderY) > 0.5;
    }
}
