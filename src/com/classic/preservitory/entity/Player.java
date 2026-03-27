package com.classic.preservitory.entity;

import com.classic.preservitory.item.Inventory;
import com.classic.preservitory.item.Item;
import com.classic.preservitory.system.SkillSystem;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.Map;

/**
 * The player character.
 *
 * Owns:
 *   - {@link Inventory}   (bag of items)
 *   - {@link SkillSystem} (levelled skills: Woodcutting, Mining, …)
 *   - {@link Animation}   (visual state driver — no sprite images needed)
 *
 * Combat stats (attack, strength, defence, hitpoints) start at level 5.
 * Movement and combat are handled by their respective systems.
 *
 * === Isometric rendering ===
 * The player is drawn as a vertical rectangle anchored at the bottom-centre
 * of the tile's isometric diamond, with a soft shadow ellipse on the ground.
 * A small coloured dot on the body indicates the current facing direction.
 */
public class Player extends Entity {

    // -----------------------------------------------------------------------
    //  Network identity — assigned by the server on connect
    // -----------------------------------------------------------------------

    /** Server-assigned player ID (e.g. "P1").  "local" until the server responds. */
    private String id = "local";

    // -----------------------------------------------------------------------
    //  Movement
    // -----------------------------------------------------------------------

    private double speed;

    // -----------------------------------------------------------------------
    //  Facing direction (updated by MovementSystem)
    // -----------------------------------------------------------------------

    /** Last horizontal movement direction: -1 = left, 0 = none, +1 = right. */
    private int facingX = 0;

    /** Last vertical movement direction:   -1 = up,   0 = none, +1 = down. */
    private int facingY = 1;   // default: facing south

    // -----------------------------------------------------------------------
    //  Inventory & skills
    // -----------------------------------------------------------------------

    private final Inventory            inventory;
    private final Map<String, Integer> inventoryState = new HashMap<>();
    private final SkillSystem          skillSystem;

    // -----------------------------------------------------------------------
    //  Combat stats
    // -----------------------------------------------------------------------

    private int attackLevel   = 5;
    private int strengthLevel = 5;
    private int defenceLevel  = 5;
    private int maxHp         = 25;
    private int hp;

    // -----------------------------------------------------------------------
    //  Animation
    // -----------------------------------------------------------------------

    private final Animation animation = new Animation();

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public Player(double startX, double startY) {
        super(startX, startY, Constants.TILE_SIZE, Constants.TILE_SIZE);
        this.speed       = Constants.PLAYER_SPEED;
        this.inventory   = new Inventory();
        this.skillSystem = new SkillSystem();
        this.hp          = maxHp;
    }

    // -----------------------------------------------------------------------
    //  Facing direction
    // -----------------------------------------------------------------------

    /**
     * Update the player's facing direction.
     * Only applies the change when at least one component is non-zero, so the
     * facing direction is retained when the player stops moving.
     */
    public void setFacing(int fx, int fy) {
        if (fx != 0 || fy != 0) {
            facingX = fx;
            facingY = fy;
        }
    }

    // -----------------------------------------------------------------------
    //  Combat
    // -----------------------------------------------------------------------

    /** Set HP to an authoritative server value (clamped to [0, maxHp]). */
    public void setHp(int value) {
        hp = Math.max(0, Math.min(maxHp, value));
    }

    public void setMaxHp(int value) {
        maxHp = Math.max(1, value);
        hp = Math.min(hp, maxHp);
    }

    public void setCombatLevels(int attackLevel, int strengthLevel, int defenceLevel) {
        this.attackLevel = Math.max(1, attackLevel);
        this.strengthLevel = Math.max(1, strengthLevel);
        this.defenceLevel = Math.max(1, defenceLevel);
    }

    public void applySkillSnapshot(Map<String, int[]> skillSnapshot) {
        if (skillSnapshot == null || skillSnapshot.isEmpty()) {
            return;
        }

        int attackLevel = this.attackLevel;
        int[] attack = skillSnapshot.get("attack");
        if (attack != null && attack.length >= 2) {
            attackLevel = attack[0];
        }

        int strengthLevel = this.strengthLevel;
        int[] strength = skillSnapshot.get("strength");
        if (strength != null && strength.length >= 2) {
            strengthLevel = strength[0];
        }

        int defenceLevel = this.defenceLevel;
        int[] defence = skillSnapshot.get("defence");
        if (defence != null && defence.length >= 2) {
            defenceLevel = defence[0];
        }

        setCombatLevels(attackLevel, strengthLevel, defenceLevel);

        int[] hitpoints = skillSnapshot.get("hitpoints");
        if (hitpoints != null && hitpoints.length >= 2) {
            setMaxHp(Math.max(1, hitpoints[0] * 5));
        }

        for (Map.Entry<String, int[]> entry : skillSnapshot.entrySet()) {
            int[] values = entry.getValue();
            if (values == null || values.length < 2) {
                continue;
            }
            skillSystem.applySnapshot(entry.getKey(), values[0], values[1]);
        }
    }

    /** True when HP has reached zero. */
    public boolean isDead() { return hp <= 0; }

    /**
     * Apply a full server-authoritative inventory snapshot.
     * The map is the source of truth; the Inventory object is rebuilt so
     * existing UI code can continue rendering without local mutation paths.
     */
    public void applyInventoryUpdate(Map<String, Integer> newInventory) {
        inventoryState.clear();
        inventory.clear();

        if (newInventory == null || newInventory.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Integer> entry : newInventory.entrySet()) {
            String itemName = entry.getKey();
            Integer amount = entry.getValue();
            if (itemName == null || itemName.isEmpty() || amount == null || amount <= 0) {
                continue;
            }

            inventoryState.put(itemName, amount);

            Item item = new Item(itemName, true);
            item.setCount(amount);
            inventory.addItem(item);
        }
    }

    // -----------------------------------------------------------------------
    //  Rendering — isometric
    // -----------------------------------------------------------------------

    @Override
    public void render(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        // Iso position of the tile top-left (world origin of this entity)
        int isoX = IsoUtils.worldToIsoX(x, y);
        int isoY = IsoUtils.worldToIsoY(x, y);

        // "Foot" = bottom-centre of the tile diamond; player stands here
        int footX = isoX + IsoUtils.ISO_TILE_W / 2;
        int footY = isoY + IsoUtils.ISO_TILE_H;

        Animation.State state = animation.getState();

        // ---- Walking bob: small vertical oscillation ----
        int bobY = 0;
        if (state == Animation.State.WALKING) {
            bobY = (int)(animation.sin(8) * 2.0);  // ±2 px at 8 Hz
        }

        // ---- Body dimensions ----
        int bodyW = 14;
        int bodyH = 26;
        int bodyX = footX - bodyW / 2;
        int bodyY = footY - bodyH + bobY;

        // ---- Shadow (dark ellipse on the ground) ----
        g2.setColor(new Color(0, 0, 0, 85));
        g2.fillOval(footX - 11, footY - 5, 22, 10);

        // ---- Body colour — changes per state ----
        Color bodyColor;
        Color highlightColor;

        switch (state) {
            case FIGHTING:
                // Pulse between green and orange-red to signal combat
                double fightPulse = animation.pulse(6);
                int r  = (int)(60  + 140 * fightPulse);
                int gv = (int)(180 - 130 * fightPulse);
                bodyColor      = new Color(r, gv, 60);
                highlightColor = bodyColor.brighter();
                break;

            case CHOPPING:
            case MINING:
                bodyColor      = new Color(75, 170, 75);
                highlightColor = new Color(120, 225, 120);
                break;

            default: // IDLE + WALKING
                bodyColor      = new Color(60, 180, 60);
                highlightColor = new Color(100, 220, 100);
                break;
        }

        // ---- Draw body ----
        g2.setColor(bodyColor);
        g2.fillRect(bodyX, bodyY, bodyW, bodyH);

        // Highlight quad (top-left corner of body)
        g2.setColor(highlightColor);
        g2.fillRect(bodyX + 2, bodyY + 2, bodyW / 3, bodyH / 4);

        // Outline
        g2.setColor(Color.DARK_GRAY);
        g2.drawRect(bodyX, bodyY, bodyW, bodyH);

        // ---- Tool flash (right side of body) ----
        if (state == Animation.State.CHOPPING) {
            double phase = animation.sin(5);
            if (phase > 0) {
                int alpha = (int)(phase * 220);
                g2.setColor(new Color(220, 170, 50, alpha));
                g2.fillRect(bodyX + bodyW + 1, bodyY + 6, 5, 12);
            }
        } else if (state == Animation.State.MINING) {
            double phase = animation.sin(4);
            if (phase > 0) {
                int alpha = (int)(phase * 220);
                g2.setColor(new Color(120, 160, 230, alpha));
                g2.fillRect(bodyX + bodyW + 1, bodyY + 6, 5, 12);
            }
        }

        // ---- Facing-direction dot ----
        // Small white oval drawn at the edge of the body that faces the
        // current movement direction.
        int dotX, dotY;
        if      (facingX > 0) { dotX = bodyX + bodyW - 5; dotY = bodyY + bodyH / 2 - 3; }
        else if (facingX < 0) { dotX = bodyX + 1;          dotY = bodyY + bodyH / 2 - 3; }
        else if (facingY < 0) { dotX = bodyX + bodyW / 2 - 3; dotY = bodyY + 1; }
        else                   { dotX = bodyX + bodyW / 2 - 3; dotY = bodyY + bodyH - 7; }

        g2.setColor(new Color(255, 255, 255, 200));
        g2.fillOval(dotX, dotY, 6, 6);
    }

    // -----------------------------------------------------------------------
    //  Getters & setters
    // -----------------------------------------------------------------------

    public String      getId()            { return id; }
    public void        setId(String id)   { this.id = id; }

    public double      getSpeed()         { return speed; }
    public void        setSpeed(double s) { this.speed = s; }

    public Inventory   getInventory()     { return inventory; }
    public Map<String, Integer> getInventoryState() { return inventoryState; }
    public SkillSystem getSkillSystem()   { return skillSystem; }
    public Animation   getAnimation()     { return animation; }

    public int getFacingX() { return facingX; }
    public int getFacingY() { return facingY; }

    // Combat stats
    public int getAttackLevel()   { return attackLevel; }
    public int getStrengthLevel() { return strengthLevel; }
    public int getDefenceLevel()  { return defenceLevel; }
    public int getHp()            { return hp; }
    public int getMaxHp()         { return maxHp; }
}
