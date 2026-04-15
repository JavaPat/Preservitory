package com.classic.preservitory.entity;

import com.classic.preservitory.ui.framework.assets.EntitySpriteManager;
import com.classic.preservitory.util.IsoUtils;

import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * Animation controller for NPCs, enemies, and remote players.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Determines {@link AnimationState} each frame (attack > walk > idle priority).</li>
 *   <li>Advances {@link Entity#walkTick} and {@link Entity#attackTick}.</li>
 *   <li>Renders using sprite-cache frames: walk frames during WALK, punch frames (or
 *       procedural lunge fallback) during ATTACK, idle rotation during IDLE.</li>
 * </ul>
 *
 * <h3>Walk animation</h3>
 * {@code walkTick} advances each render frame (0 → 20, then resets).
 * Walk frame index = {@code walkTick / WALK_TICKS_PER_FRAME} (6 ticks ≈ 100 ms at 60 fps).
 *
 * <h3>Attack animation</h3>
 * {@link DefaultAttackAnimation} is used as fallback when no "punch" sprite frames exist.
 */
public final class AnimationController {

    private final EntitySpriteManager    spriteManager;
    private final DefaultAttackAnimation attackAnim;

    /**
     * @param spriteManager the entity's sprite manager; walk and punch frames are used
     *                      when present, with idle rotation as fallback.
     */
    public AnimationController(EntitySpriteManager spriteManager) {
        this.spriteManager = spriteManager;
        this.attackAnim    = new DefaultAttackAnimation(spriteManager);
    }

    /** True when at least one idle rotation sprite was loaded successfully. */
    public boolean isSpritesLoaded() {
        return spriteManager.isLoaded();
    }

    // -----------------------------------------------------------------------
    //  Update — state machine
    // -----------------------------------------------------------------------

    /**
     * Determines the entity's {@link AnimationState} for this frame and
     * advances the relevant tick counter.  Call once per frame before rendering.
     */
    public void update(Entity e) {
        // Priority: attack overrides walking, walking overrides idle
        if (e.attacking) {
            e.animationState = AnimationState.ATTACK;
        } else if (e.isMoving) {
            e.animationState = AnimationState.WALK;
        } else {
            e.animationState = AnimationState.IDLE;
        }

        switch (e.animationState) {
            case ATTACK -> updateAttack(e);
            case WALK   -> updateWalk(e);
            case IDLE   -> reset(e);
        }
    }

    private void updateAttack(Entity e) {
        e.walkTick = 0;
        e.attackTick++;
        if (e.attackTick >= DefaultAttackAnimation.TOTAL_TICKS) {
            e.attackTick = 0;
            e.attacking  = false;
        }
    }

    private void updateWalk(Entity e) {
        e.walkTick++;
        if (e.walkTick > 20) e.walkTick = 0;
    }

    private void reset(Entity e) {
        e.walkTick = 0;
    }

    // -----------------------------------------------------------------------
    //  Render — switch on animationState
    // -----------------------------------------------------------------------

    /** Ticks per walk animation frame (6 render ticks ≈ 100 ms at 60 fps). */
    private static final int WALK_TICKS_PER_FRAME = 6;

    /**
     * Draws the entity using sprite-cache frames where available,
     * falling back to procedural rendering.  Does nothing when no sprites are loaded;
     * the caller (NPC/Enemy) must render a fallback shape in that case.
     */
    public void render(Entity e, Graphics g) {
        int isoX  = IsoUtils.worldToIsoX(e.getX(), e.getY());
        int isoY  = IsoUtils.worldToIsoY(e.getX(), e.getY());
        int footX = isoX + IsoUtils.ISO_TILE_W / 2;
        int footY = isoY + IsoUtils.ISO_TILE_H;
        Graphics2D g2 = (Graphics2D) g;

        switch (e.animationState) {
            case ATTACK -> {
                // Use punch sprite frames if available; else procedural lunge
                int frameCount = spriteManager.getActionFrameCount("punch", e.getDirection());
                if (frameCount > 0) {
                    int frameIdx = Math.min(e.attackTick / 2, frameCount - 1);
                    spriteManager.drawAction(g2, footX, footY, "punch", e.getDirection(), frameIdx);
                } else {
                    attackAnim.render(e, g);
                }
            }
            case WALK -> {
                // Use walk animation frames; drawWalkFrame falls back to idle if absent
                int frameIdx = e.walkTick / WALK_TICKS_PER_FRAME;
                spriteManager.drawWalkFrame(g2, footX, footY, e.getDirection(), frameIdx);
            }
            case IDLE -> {
                spriteManager.draw(g2, footX, footY, e.getDirection(), false, 0.0);
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Hit-frame query (convenience passthrough)
    // -----------------------------------------------------------------------

    /**
     * Returns true on tick 6 of an attack — the visual midpoint of the strike.
     * Use to synchronise hit-flash effects to the animation.
     */
    public boolean isHitTick(Entity e) {
        return attackAnim.isHitTick(e);
    }
}
