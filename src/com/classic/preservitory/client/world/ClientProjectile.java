package com.classic.preservitory.client.world;

/**
 * Client-side representation of an in-flight projectile.
 *
 * This is a pure visual object — it holds the data received from the server
 * and provides a time-based interpolated position for rendering.
 * No game logic runs here; the server is the only authority on damage.
 *
 * Protocol tokens (from the server {@code PROJECTILES} message):
 *   id type fromX fromY toX toY startMs durationMs
 */
public final class ClientProjectile {

    public final String id;
    /** "RANGED" or "MAGIC" — controls which visual is drawn. */
    public final String type;
    public final int fromX, fromY;
    public final int toX,   toY;
    public final long startMs;
    public final long durationMs;

    public ClientProjectile(String id, String type,
                             int fromX, int fromY,
                             int toX,   int toY,
                             long startMs, long durationMs) {
        this.id         = id;
        this.type       = type;
        this.fromX      = fromX;
        this.fromY      = fromY;
        this.toX        = toX;
        this.toY        = toY;
        this.startMs    = startMs;
        this.durationMs = durationMs;
    }

    // -----------------------------------------------------------------------
    //  Visual interpolation
    // -----------------------------------------------------------------------

    /**
     * Current world-pixel position of the projectile, interpolated between
     * origin and destination based on elapsed time.
     *
     * @return float[2] — { worldX, worldY }
     */
    public float[] getPosition() {
        long elapsed = System.currentTimeMillis() - startMs;
        float t = (durationMs > 0) ? Math.min(1f, (float) elapsed / durationMs) : 1f;
        return new float[]{
            fromX + (toX - fromX) * t,
            fromY + (toY - fromY) * t
        };
    }

    /**
     * True when the projectile has (visually) reached its destination.
     * A short grace period after the travel time lets the renderer display
     * the projectile on the final frame before it is removed.
     */
    public boolean isExpired() {
        return System.currentTimeMillis() >= startMs + durationMs + 150;
    }
}
