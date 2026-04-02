package com.classic.preservitory.system;

import com.classic.preservitory.entity.Player;
import com.classic.preservitory.ui.framework.assets.PlayerSpriteManager;

/**
 * Manages a single non-looping attack animation for the local player.
 *
 * Flow:
 *   notifyCombatTick(player) → called by GameInputHandler when a combat tick fires;
 *                              always (re)starts the animation for the current equipment.
 *   update(deltaTime)        → advances the frame timer each game tick.
 *   isAttacking()            → true while the animation is still playing.
 *   getCurrentFrame()        → 0-based frame index consumed by the renderer.
 *
 * Animation name is resolved from the player's equipment:
 *   no weapon  → "punch"
 *   (future)   → "sword_slash", "bow_shoot", …
 *
 * Direction falls back to "south" when the requested direction has no frames.
 */
public class AttackSystem {

    /** Seconds per animation frame — matches the walk frame speed. */
    private static final double FRAME_DURATION_SEC = 0.1;

    // -----------------------------------------------------------------------
    //  State
    // -----------------------------------------------------------------------

    private boolean attacking  = false;
    private String  animName   = "punch";
    private int     frameCount = 0;
    private double  timer      = 0.0;

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    /**
     * Called by {@code GameInputHandler.applyCombatResult()} each time a combat
     * tick fires.  Always (re)starts the animation so every attack is visible,
     * even if the previous animation hasn't fully finished yet.
     *
     * @param player the local player (weapon + facing direction resolved here)
     */
    public void notifyCombatTick(Player player) {
        String anim  = resolveAnimName(player);
        String dir   = facingDirection(player);
        int    count = PlayerSpriteManager.getAttackFrameCount(anim, dir);
        if (count == 0) return;   // no sprite frames — skip silently

        animName   = anim;
        frameCount = count;
        timer      = 0.0;
        attacking  = true;
    }

    /**
     * Advance the frame timer.  Must be called once per game-update tick.
     * Clears the attacking flag when the last frame has been shown.
     */
    public void update(double deltaTime) {
        if (!attacking) return;

        timer += deltaTime;
        if (timer >= frameCount * FRAME_DURATION_SEC) {
            attacking = false;
            timer     = 0.0;
        }
    }

    /** Force-cancel the current attack (e.g. on player death or logout). */
    public void stopAttack() {
        attacking = false;
        timer     = 0.0;
    }

    /** True while the punch animation is still playing. Movement is blocked. */
    public boolean isAttacking() { return attacking; }

    /** Animation name to pass to {@link PlayerSpriteManager#drawPlayerAction}. */
    public String getAnimName()  { return animName; }

    /**
     * 0-based frame index, clamped to the last frame so the renderer never
     * goes out-of-bounds during the final frame's display time.
     */
    public int getCurrentFrame() {
        if (frameCount == 0) return 0;
        return Math.min((int) (timer / FRAME_DURATION_SEC), frameCount - 1);
    }

    // -----------------------------------------------------------------------
    //  Hook for future hit detection
    // -----------------------------------------------------------------------

    /**
     * Returns the 0-based index of the frame in which a hit should be
     * registered.  Currently set to the midpoint of the animation.
     * Wire this into a hit-detection callback when ready.
     */
    public int getHitFrame() {
        return Math.max(0, frameCount / 2);
    }

    /** True on exactly the frame that the hit should land. */
    public boolean isHitFrame() {
        return attacking && getCurrentFrame() == getHitFrame();
    }

    // -----------------------------------------------------------------------
    //  Internal helpers
    // -----------------------------------------------------------------------

    private static String resolveAnimName(Player player) {
        // Future: check equipped weapon and return the appropriate animation.
        //   if (player.getEquippedItemId("WEAPON") > 0) return "sword_slash";
        return "punch";
    }

    /**
     * Converts the player's facingX / facingY into a direction string that
     * matches metadata.json keys.  Mirrors {@code Player.getSpriteDirection()}.
     */
    static String facingDirection(Player player) {
        int fx = player.getFacingX();
        int fy = player.getFacingY();
        if (fx > 0 && fy < 0) return "north-east";
        if (fx > 0 && fy > 0) return "south-east";
        if (fx < 0 && fy < 0) return "north-west";
        if (fx < 0 && fy > 0) return "south-west";
        if (fx > 0)            return "east";
        if (fx < 0)            return "west";
        if (fy < 0)            return "north";
        return "south";
    }
}
