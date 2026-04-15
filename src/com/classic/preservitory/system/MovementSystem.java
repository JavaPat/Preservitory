package com.classic.preservitory.system;

import com.classic.preservitory.entity.EntityInterpolation;
import com.classic.preservitory.entity.Player;

/**
 * Client-side movement handler for the local player.
 *
 * Delegates all interpolation math to {@link EntityInterpolation}, which is
 * shared with RemotePlayer, NPC, and Enemy so the lerp behaviour is identical
 * across all entity types.
 *
 * The server is fully authoritative:
 *   - The server runs A* pathfinding and queues tile steps.
 *   - The server moves the player exactly one tile per 600 ms game tick.
 *   - The server broadcasts the confirmed position and {@code moving} flag.
 *
 * This class receives those broadcasts and feeds them into
 * {@link EntityInterpolation#syncPosition}, which starts a fresh one-tile
 * lerp only when the position actually changes.  Between ticks the same
 * position arrives every frame; those calls are silently ignored.
 */
public class MovementSystem {

    // -----------------------------------------------------------------------
    //  Timing constant (kept public for any callers that reference it)
    // -----------------------------------------------------------------------

    /** Duration of one server game tick in milliseconds. */
    public static final long TICK_DURATION_MS = EntityInterpolation.TICK_MS;

    // -----------------------------------------------------------------------
    //  State
    // -----------------------------------------------------------------------

    /** Shared lerp component — lazy-initialised on first server sync. */
    private EntityInterpolation lerp;

    /** Server-authoritative facing direction ("north/south/east/west"). */
    private String serverDirection = "south";

    /** Lerp-based moving flag — true while the render position is in flight. */
    private boolean moving;

    // -----------------------------------------------------------------------
    //  Server position update  (called every frame from GamePanel)
    // -----------------------------------------------------------------------

    /**
     * Process a PLAYERS broadcast entry for the local player.
     *
     * Safe to call every frame — direction and the moving flag are always
     * updated immediately, but the lerp state is only reset when the server
     * sends a genuinely new position.
     *
     * @param player       the local player whose visual position to update
     * @param serverX      server-confirmed pixel X (tileCol × TILE_SIZE)
     * @param serverY      server-confirmed pixel Y (tileRow × TILE_SIZE)
     * @param direction    facing direction from server
     * @param serverMoving true if the server stepped the player this tick
     */
    public void syncServerPosition(Player player, int serverX, int serverY,
                                   String direction, boolean serverMoving) {
        if (direction != null && !direction.isBlank()) {
            serverDirection = direction.trim().toLowerCase();
        }
        player.setServerMoving(serverMoving);

        if (lerp == null) {
            lerp = new EntityInterpolation(serverX, serverY);
        }
        lerp.syncPosition(serverX, serverY);
    }

    /** @deprecated Use {@link #syncServerPosition(Player, int, int, String, boolean)} */
    @Deprecated
    public void onServerPosition(Player player, int serverX, int serverY, String direction) {
        syncServerPosition(player, serverX, serverY, direction, false);
    }

    /** @deprecated Use {@link #syncServerPosition(Player, int, int, String, boolean)} */
    @Deprecated
    public void onServerPosition(Player player, int serverX, int serverY,
                                 String direction, boolean serverMoving) {
        syncServerPosition(player, serverX, serverY, direction, serverMoving);
    }

    // -----------------------------------------------------------------------
    //  Per-frame update
    // -----------------------------------------------------------------------

    /**
     * Advance the interpolation and apply the result to the player's position.
     * Called once per frame by the game loop.
     */
    public void update(Player player, double deltaTime) {
        if (lerp == null) {
            lerp = new EntityInterpolation(player.getX(), player.getY());
        }
        lerp.tick();
        player.setX(lerp.getRenderX());
        player.setY(lerp.getRenderY());
        moving = lerp.isMoving();
        player.setDirection(serverDirection);
    }

    // -----------------------------------------------------------------------
    //  State queries
    // -----------------------------------------------------------------------

    /** True while the player's visual position is lerping toward the server target. */
    public boolean isMoving() { return moving; }

    /** True if the player is actively approaching a destination (same as isMoving). */
    public boolean hasPath() { return moving; }

    /** Cancel any in-progress visual movement (clears the moving flag). */
    public void clearPath() { moving = false; }
}
