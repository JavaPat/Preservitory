package com.classic.preservitory.entity;

import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;

import java.awt.*;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Represents another player connected to the same server.
 *
 * === Smoothing strategy ===
 *
 * The server sends position snapshots at ~20 Hz (every 50 ms).  At 60 fps the
 * renderer runs ~3 frames between network updates, so naive "snap to position"
 * produces visible 7–8 px jumps.
 *
 * We solve this with two complementary techniques:
 *
 *  1. Exponential lerp  — each frame the render position moves a fixed fraction
 *     of the remaining gap toward the target:
 *
 *       pos += (target - pos) × (1 − e^(−SMOOTH_K × dt))
 *
 *     This is frame-rate independent and always decelerates smoothly.
 *     SMOOTH_K = 15 means the render position covers ~63% of any gap in 67ms
 *     (≈ 1.3 update intervals), giving a subtle but pleasant follow lag.
 *
 *  2. Velocity extrapolation  — on each received update we estimate velocity
 *     from consecutive positions.  Between updates we continue moving in that
 *     direction (blended at 35% weight, fading to 0 after MAX_EXTRAP_MS).
 *     This partly fills the 50 ms gap so the lerp has less distance to cover.
 *
 * The render position stored in Entity.x / Entity.y is what the depth sorter
 * and renderer both use.  targetX/targetY are never drawn directly.
 */
public class RemotePlayer extends Entity {

    // -----------------------------------------------------------------------
    //  Tuning knobs
    // -----------------------------------------------------------------------

    /**
     * Exponential-lerp stiffness.  Higher = snappier but can feel jittery on
     * high-latency connections; lower = smoother but visually lags behind.
     * k=15 → 63% of gap covered in 1/15 s ≈ 67 ms (≈ 1.3 update intervals).
     */
    private static final double SMOOTH_K = 15.0;

    /**
     * How much of the estimated velocity to blend into the extrapolation.
     * 0 = pure lerp, 1 = full dead-reckoning.  0.35 is a conservative middle.
     */
    private static final double PREDICTION_BLEND = 0.35;

    /**
     * Stop extrapolating after this many milliseconds without a fresh update
     * (avoids the player sliding off into space during a disconnect).
     */
    private static final long MAX_EXTRAP_MS = 120;

    /**
     * Snap render position to target when within this many pixels — avoids
     * infinite asymptotic approach on the last sub-pixel.
     */
    private static final double SNAP_THRESHOLD = 0.4;

    // -----------------------------------------------------------------------
    //  Identity
    // -----------------------------------------------------------------------

    /** Server-assigned player ID (e.g. "P2").  Shown above the character. */
    private final String id;

    // -----------------------------------------------------------------------
    //  Network target (latest received position)
    // -----------------------------------------------------------------------

    private double targetX;
    private double targetY;

    // -----------------------------------------------------------------------
    //  Velocity estimation (from consecutive network updates)
    // -----------------------------------------------------------------------

    /** Position at the second-to-last network update — used to compute velocity. */
    private double prevTargetX;
    private double prevTargetY;

    /** Estimated velocity in world-pixels per second (computed per update). */
    private double velX;
    private double velY;

    // -----------------------------------------------------------------------
    //  Update-arrival timestamps
    // -----------------------------------------------------------------------

    /** System.currentTimeMillis() when the last network update was received. */
    private long lastUpdateMs;

    /** System.currentTimeMillis() when the second-to-last update was received. */
    private long prevUpdateMs;

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    /**
     * @param id  Server-assigned player ID
     * @param x   Initial world-pixel X
     * @param y   Initial world-pixel Y
     */
    public RemotePlayer(String id, double x, double y) {
        super(x, y, Constants.TILE_SIZE, Constants.TILE_SIZE);
        this.id           = id;
        this.targetX      = x;
        this.targetY      = y;
        this.prevTargetX  = x;
        this.prevTargetY  = y;
        this.lastUpdateMs = System.currentTimeMillis();
        this.prevUpdateMs = lastUpdateMs;
    }

    // -----------------------------------------------------------------------
    //  Network update (called from GamePanel.syncRemotePlayers)
    // -----------------------------------------------------------------------

    /**
     * Record a new target position arriving from the server.
     *
     * We use the time delta between consecutive calls to estimate velocity,
     * which feeds the extrapolation in {@link #update}.
     *
     * @param nx  New target world-pixel X
     * @param ny  New target world-pixel Y
     */
    public void setTargetPosition(double nx, double ny) {
        long now = System.currentTimeMillis();

        // Roll previous state forward
        prevTargetX = targetX;
        prevTargetY = targetY;
        prevUpdateMs = lastUpdateMs;

        // Estimate velocity from the delta between the last two positions.
        // Guard against zero or unreasonably large intervals (e.g. first update
        // after a freeze) to avoid velocity spikes.
        double dtNet = (now - prevUpdateMs) / 1000.0;
        if (dtNet > 0.010 && dtNet < 0.500) {
            velX = (nx - prevTargetX) / dtNet;
            velY = (ny - prevTargetY) / dtNet;
        } else {
            // Interval too small or too large — discard; keep previous velocity
            velX = 0;
            velY = 0;
        }

        targetX      = nx;
        targetY      = ny;
        lastUpdateMs = now;
    }

    // -----------------------------------------------------------------------
    //  Per-frame update (called from GamePanel.syncRemotePlayers)
    // -----------------------------------------------------------------------

    /**
     * Advance the render position one frame closer to the network target.
     *
     * Two forces act on the render position each frame:
     *
     *   a) Exponential lerp toward target — covers most of the gap.
     *   b) Velocity extrapolation        — adds a small predictive nudge in
     *      the estimated movement direction, fading to zero after
     *      MAX_EXTRAP_MS without a fresh server update.
     *
     * @param dt  Elapsed seconds since last frame (from the game loop)
     */
    public void update(double dt) {

        // ---- a) Exponential lerp ----
        // Frame-rate independent: fraction covered = 1 − e^(−k·dt)
        double lerpFactor = 1.0 - Math.exp(-SMOOTH_K * dt);
        x += (targetX - x) * lerpFactor;
        y += (targetY - y) * lerpFactor;

        // ---- b) Velocity extrapolation (dead reckoning) ----
        // Only apply while the last update is "fresh" enough and the player
        // appears to be moving (non-trivial velocity).
        long   staleness   = System.currentTimeMillis() - lastUpdateMs;
        double speedSq     = velX * velX + velY * velY;
        boolean moving     = speedSq > 4.0;           // > 2 px/s threshold

        if (moving && staleness < MAX_EXTRAP_MS) {
            // Weight fades linearly from PREDICTION_BLEND → 0 as data goes stale
            double freshness = 1.0 - (staleness / (double) MAX_EXTRAP_MS);
            double blend     = PREDICTION_BLEND * freshness;
            x += velX * dt * blend;
            y += velY * dt * blend;
        }

        // ---- Snap when close enough ----
        // Avoids the asymptotic crawl on the last fraction of a pixel.
        if (Math.abs(targetX - x) < SNAP_THRESHOLD) x = targetX;
        if (Math.abs(targetY - y) < SNAP_THRESHOLD) y = targetY;
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    /**
     * Draw this remote player at the current interpolated position.
     * The Graphics context is inside the camera-translated world-space block.
     */
    @Override
    public void render(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        // Convert smoothed world position → isometric screen coords
        int isoX  = IsoUtils.worldToIsoX(x, y);
        int isoY  = IsoUtils.worldToIsoY(x, y);

        // "Foot" = bottom-centre of the tile diamond; character stands here
        int footX = isoX + IsoUtils.ISO_TILE_W / 2;
        int footY = isoY + IsoUtils.ISO_TILE_H;

        // Body dimensions (same proportions as local Player)
        int bodyW = 14;
        int bodyH = 26;
        int bodyX = footX - bodyW / 2;
        int bodyY = footY - bodyH;

        // ---- Ground shadow ----
        g2.setColor(new Color(0, 0, 0, 70));
        g2.fillOval(footX - 11, footY - 5, 22, 10);

        // ---- Body — steel blue; distinct from local player's green ----
        g2.setColor(new Color(65, 125, 210));
        g2.fillRect(bodyX, bodyY, bodyW, bodyH);

        // ---- Highlight (top-left corner) ----
        g2.setColor(new Color(115, 170, 255));
        g2.fillRect(bodyX + 2, bodyY + 2, bodyW / 3, bodyH / 4);

        // ---- Outline ----
        g2.setColor(Color.DARK_GRAY);
        g2.drawRect(bodyX, bodyY, bodyW, bodyH);

        // ---- Name tag above the head ----
        g2.setFont(new Font("Arial", Font.BOLD, 9));
        FontMetrics fm = g2.getFontMetrics();
        int tagW  = fm.stringWidth(id) + 6;
        int tagX  = footX - tagW / 2;
        int tagTY = bodyY - 3;

        // Dark semi-transparent pill background
        g2.setColor(new Color(0, 0, 0, 165));
        g2.fillRoundRect(tagX - 1, tagTY - fm.getAscent() - 1,
                         tagW + 2, fm.getHeight() + 2, 3, 3);

        // Cyan text — readable on all tile colours
        g2.setColor(new Color(80, 200, 255));
        g2.drawString(id, tagX + 3, tagTY);
    }

    // -----------------------------------------------------------------------
    //  Getters
    // -----------------------------------------------------------------------

    public String getId() { return id; }

    /** Euclidean distance between the render position and network target (px). */
    public double getInterpolationDistance() {
        double dx = targetX - x;
        double dy = targetY - y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
