package com.classic.preservitory.entity;

/**
 * Base class for all client-rendered combat enemies.
 *
 * The client stores only renderable server snapshot state: id, stats, HP,
 * alive/dead status, and drawing behavior. Damage, respawn, loot, and AI are
 * owned by the server.
 */
public abstract class Enemy extends Entity {

    public enum State { ALIVE, DEAD }

    private String id    = "";
    private final String name;
    private State        state;

    // --- Combat stats ---
    private final int maxHp;
    private       int hp;
    // These are read by CombatSystem to calculate hit/damage rolls
    protected final int attackLevel;
    protected final int strengthLevel;
    protected final int defenceLevel;

    protected Enemy(String name,
                    double x, double y, int width, int height,
                    int maxHp,
                    int attackLevel, int strengthLevel, int defenceLevel) {
        super(x, y, width, height);
        this.name          = name;
        this.state         = State.ALIVE;
        this.maxHp         = maxHp;
        this.hp            = maxHp;
        this.attackLevel   = attackLevel;
        this.strengthLevel = strengthLevel;
        this.defenceLevel  = defenceLevel;
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

    public String  getId()            { return id; }
    public void    setId(String id)   { this.id = id; }

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

    /**
     * Sync HP from an authoritative server snapshot.
     * Transitions state to ALIVE or DEAD based on the new value;
     * no local respawn timer is started (the server owns that).
     */
    public void setHp(int newHp) {
        this.hp = Math.max(0, newHp);
        state = (this.hp > 0) ? State.ALIVE : State.DEAD;
    }
}
