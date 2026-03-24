package com.classic.preservitory.system;

import com.classic.preservitory.item.Inventory;
import com.classic.preservitory.item.Item;
import com.classic.preservitory.world.objects.Tree;

/**
 * Manages the woodcutting mini-loop for a single player.
 *
 * How it works each frame:
 *   1. update() counts down the chop timer.
 *   2. When the timer expires it returns true — the caller (GamePanel)
 *      then calls grantReward() to apply XP and items.
 *   3. grantReward() chops the tree (turning it into a stump) and resets state.
 *
 * The caller is responsible for checking proximity and calling startChopping()
 * when the player first arrives next to a tree.
 */
public class WoodcuttingSystem {

    /** Seconds between chop completions. */
    private static final double CHOP_INTERVAL = 1.5;

    /** XP awarded per successful chop. Public so GamePanel can show it in messages. */
    public static final int XP_PER_CHOP = 25;

    private boolean chopping;
    private double  timer;
    private Tree    targetTree;

    // -----------------------------------------------------------------------
    //  State control
    // -----------------------------------------------------------------------

    /**
     * Begin chopping the given tree.
     * Resets the timer so the first chop always takes a full CHOP_INTERVAL.
     */
    public void startChopping(Tree tree) {
        this.targetTree = tree;
        this.chopping   = true;
        this.timer      = CHOP_INTERVAL;
    }

    /**
     * Abort chopping (e.g. player walked away or tree became a stump mid-chop).
     */
    public void stopChopping() {
        chopping   = false;
        timer      = 0;
        targetTree = null;
    }

    // -----------------------------------------------------------------------
    //  Per-frame update
    // -----------------------------------------------------------------------

    /**
     * Tick the chop timer.
     *
     * @return true exactly once when a chop completes; false every other frame.
     *         The caller must then call grantReward().
     */
    public boolean update(double deltaTime) {
        if (!chopping || targetTree == null) return false;

        // If the tree somehow became a stump before we finished (e.g. already cut)
        if (!targetTree.isAlive()) {
            stopChopping();
            return false;
        }

        timer -= deltaTime;
        if (timer <= 0) {
            timer = CHOP_INTERVAL; // ready for next chop if still active
            return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------
    //  Rewards
    // -----------------------------------------------------------------------

    /**
     * Apply the chop reward: add XP, add Logs, chop the tree.
     * Always call this immediately after update() returns true.
     *
     * @return true  if logs were successfully added to the inventory
     *         false if the inventory was full (XP is still granted)
     */
    public boolean grantReward(SkillSystem skillSystem, Inventory inventory) {
        // XP is always awarded
        skillSystem.addXp("woodcutting", XP_PER_CHOP);

        // Logs go into inventory (might fail if full)
        boolean logsAdded = inventory.addItem(new Item("Logs", true));

        // Chop the tree — it becomes a stump for RESPAWN_TIME seconds
        if (targetTree != null) {
            targetTree.chop();
        }

        stopChopping();
        return logsAdded;
    }

    // -----------------------------------------------------------------------
    //  Getters
    // -----------------------------------------------------------------------

    public boolean isChopping()    { return chopping; }
    public Tree    getTargetTree() { return targetTree; }

    /**
     * How far through the current chop swing we are, as a 0.0–1.0 fraction.
     * Use this to draw a progress bar.
     */
    public double getChopProgress() {
        if (!chopping) return 0.0;
        return 1.0 - (timer / CHOP_INTERVAL);
    }
}
