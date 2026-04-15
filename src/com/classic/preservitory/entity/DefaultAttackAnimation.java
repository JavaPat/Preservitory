package com.classic.preservitory.entity;

import com.classic.preservitory.ui.framework.assets.EntitySpriteManager;
import com.classic.preservitory.util.IsoUtils;

import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * Tick-based attack animation for NPCs and enemies.
 *
 * Requires NO animation frame files — only the 4 idle rotation sprites are needed.
 * The attack is simulated by shifting the idle sprite in the entity's facing direction
 * across three phases driven by {@link Entity#attackTick}:
 *
 * <pre>
 *   Ticks  0–3  (wind-up)  : sprite shifts 1 px backward (away from target)
 *   Ticks  4–7  (strike)   : sprite shifts 2 px forward  (toward target)
 *   Ticks  8–11 (recovery) : sprite returns to the neutral foot position
 * </pre>
 *
 * The visual "hit" peak is at {@link #HIT_TICK} (tick 6, middle of the strike phase).
 * Callers can query {@link #isHitTick} to sync visual effects (flash, damage pop) to the
 * animation midpoint — the server's authoritative damage is independent of this timer.
 *
 * Call {@link Entity#startAttack()} to begin a cycle.
 * {@link #update} auto-resets {@link Entity#attacking} when all 12 ticks are consumed.
 */
public final class DefaultAttackAnimation implements AnimationStrategy {

    /** Total ticks in one full attack cycle. */
    public static final int TOTAL_TICKS = 12;

    /** Tick index at which the hit is visually at its peak (middle of strike phase). */
    public static final int HIT_TICK    = 6;

    private final EntitySpriteManager spriteManager;

    /**
     * @param spriteManager the entity's sprite manager; only idle rotation frames are used.
     */
    public DefaultAttackAnimation(EntitySpriteManager spriteManager) {
        this.spriteManager = spriteManager;
    }

    // -----------------------------------------------------------------------
    //  AnimationStrategy
    // -----------------------------------------------------------------------

    /**
     * Advances {@link Entity#attackTick} by one each frame while
     * {@link Entity#attacking} is true, then auto-resets when the cycle ends.
     * No-op when the entity is not currently attacking.
     */
    @Override
    public void update(Entity entity) {
        if (!entity.attacking) return;
        entity.attackTick++;
        if (entity.attackTick >= TOTAL_TICKS) {
            entity.attackTick = 0;
            entity.attacking  = false;
        }
    }

    /**
     * Draws the idle rotation sprite offset by the current attack-phase displacement.
     * Only call this while {@link Entity#attacking} is true — the caller (NPC/Enemy)
     * selects this or the walk/idle path based on {@link AnimationState}.
     */
    @Override
    public void render(Entity entity, Graphics g) {
        int isoX  = IsoUtils.worldToIsoX(entity.getX(), entity.getY());
        int isoY  = IsoUtils.worldToIsoY(entity.getX(), entity.getY());
        int footX = isoX + IsoUtils.ISO_TILE_W / 2;
        int footY = isoY + IsoUtils.ISO_TILE_H;

        int[] off = phaseOffset(entity.attackTick, entity.getDirection());

        spriteManager.draw((Graphics2D) g,
                footX + off[0], footY + off[1],
                entity.getDirection(),
                false,   // always use idle rotation frame
                0.0);
    }

    // -----------------------------------------------------------------------
    //  Hit-frame query
    // -----------------------------------------------------------------------

    /**
     * Returns true on the exact tick when the attack is visually at its peak.
     * Use this to trigger hit-flash effects or other visual feedback that should
     * be synchronised to the animation rather than to the server packet arrival.
     */
    public boolean isHitTick(Entity entity) {
        return entity.attacking && entity.attackTick == HIT_TICK;
    }

    // -----------------------------------------------------------------------
    //  Internal — offset table
    // -----------------------------------------------------------------------

    /**
     * Returns the (xOff, yOff) screen-space pixel displacement for the given
     * attack tick and facing direction.
     *
     * <pre>
     *   phase          ticks  "along" value
     *   wind-up        0–3    −1  (backward lean)
     *   strike         4–7    +2  (forward lunge)
     *   recovery       8–11    0  (neutral)
     * </pre>
     */
    private static int[] phaseOffset(int tick, String dir) {
        int along;
        if (tick < 4) {
            along = -1;   // wind-up: pull back
        } else if (tick < 8) {
            along = 2;    // strike:  lunge forward
        } else {
            along = 0;    // recovery: neutral
        }

        return switch (dir) {
            case "north" -> new int[]{  0,     -along };
            case "south" -> new int[]{  0,      along };
            case "east"  -> new int[]{  along,  0     };
            case "west"  -> new int[]{ -along,  0     };
            default      -> new int[]{  0,      along };
        };
    }
}
