package com.classic.preservitory.system;

import com.classic.preservitory.item.Inventory;
import com.classic.preservitory.item.Item;
import com.classic.preservitory.world.objects.Rock;

/**
 * Manages the mining mini-loop, mirroring WoodcuttingSystem but for rocks.
 *
 * Each MINE_INTERVAL seconds, update() returns true.
 * The caller then calls grantReward() to award Ore and Mining XP.
 */
public class MiningSystem {

    /** Seconds per mine swing. */
    private static final double MINE_INTERVAL = 2.0;

    /** XP awarded per successful mine. Public for message formatting. */
    public static final int XP_PER_MINE = 20;

    private boolean mining;
    private double  timer;
    private Rock    targetRock;

    // -----------------------------------------------------------------------
    //  State control
    // -----------------------------------------------------------------------

    /** Begin mining the given rock. Resets the swing timer. */
    public void startMining(Rock rock) {
        this.targetRock = rock;
        this.mining     = true;
        this.timer      = MINE_INTERVAL;
    }

    /** Stop mining (player moved away or rock became depleted). */
    public void stopMining() {
        mining     = false;
        timer      = 0;
        targetRock = null;
    }

    // -----------------------------------------------------------------------
    //  Per-frame update
    // -----------------------------------------------------------------------

    /**
     * @return true exactly once when a swing completes; false every other frame.
     */
    public boolean update(double deltaTime) {
        if (!mining || targetRock == null) return false;

        if (!targetRock.isSolid()) {
            stopMining();
            return false;
        }

        timer -= deltaTime;
        if (timer <= 0) {
            timer = MINE_INTERVAL;
            return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------
    //  Reward
    // -----------------------------------------------------------------------

    /**
     * Apply the mine reward: XP + Ore + deplete the rock.
     * Call immediately after update() returns true.
     *
     * @return true if ore was added to inventory; false if inventory was full.
     */
    public boolean grantReward(SkillSystem skillSystem, Inventory inventory) {
        skillSystem.addXp("mining", XP_PER_MINE);
        boolean oreAdded = inventory.addItem(new Item("Ore", true));
        if (targetRock != null) {
            targetRock.deplete();
        }
        stopMining();
        return oreAdded;
    }

    // -----------------------------------------------------------------------
    //  Getters
    // -----------------------------------------------------------------------

    public boolean isMining()      { return mining; }
    public Rock    getTargetRock() { return targetRock; }

    /** 0.0–1.0 fraction through the current swing. For a progress bar. */
    public double getMineProgress() {
        if (!mining) return 0.0;
        return 1.0 - (timer / MINE_INTERVAL);
    }
}
