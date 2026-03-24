package com.classic.preservitory.entity;

import com.classic.preservitory.item.Item;
import com.classic.preservitory.item.LootTable;

import java.util.List;

/**
 * Base class for all combat enemies.
 *
 * Tracks hitpoints, combat stats, and a respawn timer.
 * Subclasses provide a name, starting stats, a loot table, and rendering.
 *
 * Life-cycle:
 *   ALIVE → takeDamage() → hp reaches 0 → DEAD
 *   DEAD  → respawnTimer counts down → ALIVE (back at spawn position)
 */
public abstract class Enemy extends Entity {

    public enum State { ALIVE, DEAD }

    private final String name;
    private State        state;

    // --- Combat stats ---
    private final int maxHp;
    private       int hp;
    // These are read by CombatSystem to calculate hit/damage rolls
    protected final int attackLevel;
    protected final int strengthLevel;
    protected final int defenceLevel;

    // --- Respawn ---
    private final double respawnTime;
    private       double respawnTimer;

    // Original spawn coordinates so the enemy returns here after death
    private final double spawnX;
    private final double spawnY;

    private final LootTable lootTable;

    protected Enemy(String name,
                    double x, double y, int width, int height,
                    int maxHp,
                    int attackLevel, int strengthLevel, int defenceLevel,
                    double respawnTime) {
        super(x, y, width, height);
        this.name          = name;
        this.state         = State.ALIVE;
        this.maxHp         = maxHp;
        this.hp            = maxHp;
        this.attackLevel   = attackLevel;
        this.strengthLevel = strengthLevel;
        this.defenceLevel  = defenceLevel;
        this.respawnTime   = respawnTime;
        this.spawnX        = x;
        this.spawnY        = y;

        this.lootTable = new LootTable();
        buildLootTable(this.lootTable);
    }

    /**
     * Subclasses call table.addEntry() here to define their drops.
     */
    protected abstract void buildLootTable(LootTable table);

    // -----------------------------------------------------------------------
    //  Per-frame update
    // -----------------------------------------------------------------------

    /**
     * Must be called every frame.
     * Ticks the respawn counter and revives the enemy when it expires.
     */
    public void update(double deltaTime) {
        if (state == State.DEAD) {
            respawnTimer -= deltaTime;
            if (respawnTimer <= 0) {
                x     = spawnX;
                y     = spawnY;
                hp    = maxHp;
                state = State.ALIVE;
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Combat
    // -----------------------------------------------------------------------

    /**
     * Apply incoming damage.
     * Ignores the call if already dead.
     */
    public void takeDamage(int amount) {
        if (state != State.ALIVE) return;
        hp = Math.max(0, hp - amount);
        if (hp == 0) {
            state        = State.DEAD;
            respawnTimer = respawnTime;
        }
    }

    /**
     * Roll and return the items dropped on this kill.
     * Call once, right after the enemy's hp reaches 0.
     */
    public List<Item> rollLoot() {
        return lootTable.rollLoot();
    }

    // -----------------------------------------------------------------------
    //  Click detection
    // -----------------------------------------------------------------------

    /**
     * Returns true when the screen-space point (cx, cy) is inside this
     * enemy's tile and the enemy is alive (dead enemies can't be targeted).
     */
    public boolean containsPoint(int cx, int cy) {
        return state == State.ALIVE
                && cx >= x && cx <= x + width
                && cy >= y && cy <= y + height;
    }

    // -----------------------------------------------------------------------
    //  Getters
    // -----------------------------------------------------------------------

    public boolean isAlive()          { return state == State.ALIVE; }
    public boolean isDead()           { return state == State.DEAD; }

    public String  getName()          { return name; }
    public int     getHp()            { return hp; }
    public int     getMaxHp()         { return maxHp; }
    public int     getAttackLevel()   { return attackLevel; }
    public int     getStrengthLevel() { return strengthLevel; }
    public int     getDefenceLevel()  { return defenceLevel; }

    /** Current HP as a 0.0–1.0 fraction. Used to draw a health bar. */
    public float getHpFraction() {
        return (float) hp / maxHp;
    }
}
