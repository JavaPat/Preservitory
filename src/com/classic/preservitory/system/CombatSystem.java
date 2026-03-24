package com.classic.preservitory.system;

import com.classic.preservitory.entity.Enemy;
import com.classic.preservitory.entity.Player;

/**
 * Tick-based combat system.
 *
 * Every TICK_INTERVAL seconds (0.6 s, matching RuneScape's combat tick):
 *   1. The player rolls an attack against the enemy.
 *   2. The enemy rolls an attack against the player.
 *   Both rolls happen simultaneously in the same tick.
 *
 * Attack formula:
 *   hitChance = attackLevel / (attackLevel + defenceLevel)
 *   damage    = 0 (miss) OR random 1..strengthLevel (hit)
 *
 * The caller (GamePanel) receives a CombatResult each time a tick fires
 * and is responsible for applying the damage values to each combatant.
 */
public class CombatSystem {

    /** Seconds between attack exchanges. RuneScape uses 0.6 s per tick. */
    public static final double TICK_INTERVAL = 0.6;

    private boolean inCombat;
    private double  tickTimer;
    private Enemy   targetEnemy;

    // -----------------------------------------------------------------------
    //  State control
    // -----------------------------------------------------------------------

    /**
     * Begin fighting the given enemy.
     * Resets the tick timer so the first exchange waits a full tick.
     */
    public void startCombat(Enemy enemy) {
        this.targetEnemy = enemy;
        this.inCombat    = true;
        this.tickTimer   = TICK_INTERVAL;
    }

    /**
     * Abort combat immediately.
     * Called when the player moves away, dies, or targets something else.
     */
    public void stopCombat() {
        inCombat    = false;
        tickTimer   = 0;
        targetEnemy = null;
    }

    // -----------------------------------------------------------------------
    //  Per-frame update
    // -----------------------------------------------------------------------

    /**
     * Tick the combat timer.
     *
     * @return a CombatResult when a tick fires (null every other frame).
     *         The caller must apply playerDmg to the enemy and enemyDmg to the player.
     */
    public CombatResult update(Player player, double deltaTime) {
        if (!inCombat || targetEnemy == null) return null;

        // If the enemy died from a previous tick result, stop automatically
        if (!targetEnemy.isAlive()) {
            stopCombat();
            return null;
        }

        tickTimer -= deltaTime;
        if (tickTimer <= 0) {
            tickTimer = TICK_INTERVAL;  // ready for next tick
            return resolveTick(player);
        }
        return null;
    }

    /** Roll a full attack exchange for one tick. */
    private CombatResult resolveTick(Player player) {
        int playerDmg = rollDamage(
                player.getAttackLevel(),
                targetEnemy.getDefenceLevel(),
                player.getStrengthLevel()
        );
        int enemyDmg = rollDamage(
                targetEnemy.getAttackLevel(),
                player.getDefenceLevel(),
                targetEnemy.getStrengthLevel()
        );
        return new CombatResult(playerDmg, enemyDmg);
    }

    /**
     * Calculate a single attack roll.
     *
     * @param attack   attacker's attack level
     * @param defence  defender's defence level
     * @param strength attacker's strength level (caps the maximum hit)
     * @return 0 for a miss; 1..strength for a hit
     */
    private int rollDamage(int attack, int defence, int strength) {
        double hitChance = (double) attack / (attack + defence);
        if (Math.random() > hitChance) {
            return 0; // miss
        }
        return 1 + (int)(Math.random() * strength);
    }

    // -----------------------------------------------------------------------
    //  Getters
    // -----------------------------------------------------------------------

    public boolean isInCombat()     { return inCombat; }
    public Enemy   getTargetEnemy() { return targetEnemy; }

    /** 0.0–1.0 fraction through the current tick. For a progress bar. */
    public double getTickProgress() {
        return inCombat ? 1.0 - (tickTimer / TICK_INTERVAL) : 0.0;
    }

    // -----------------------------------------------------------------------
    //  Inner class: tick result
    // -----------------------------------------------------------------------

    /**
     * Holds the outcome of one combat tick.
     *
     * playerDmg = damage the player deals TO the enemy this tick (0 = miss)
     * enemyDmg  = damage the enemy deals TO the player this tick (0 = miss)
     */
    public static class CombatResult {
        public final int playerDmg;
        public final int enemyDmg;

        public CombatResult(int playerDmg, int enemyDmg) {
            this.playerDmg = playerDmg;
            this.enemyDmg  = enemyDmg;
        }
    }
}
