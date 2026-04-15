package com.classic.preservitory.entity;

import com.classic.preservitory.client.definitions.ItemDefinition;
import com.classic.preservitory.client.definitions.ItemDefinitionManager;
import com.classic.preservitory.item.Inventory;
import com.classic.preservitory.item.Item;
import com.classic.preservitory.system.SkillSystem;
import com.classic.preservitory.ui.framework.assets.PlayerSpriteManager;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    /** Set true temporarily to render the direction string above the player head. */
    public static boolean DEBUG_DIRECTION = false;

    // -----------------------------------------------------------------------
    //  Network identity — assigned by the server on connect
    // -----------------------------------------------------------------------

    /** Server-assigned player ID (e.g. "P1").  "local" until the server responds. */
    private String id = "local";

    // -----------------------------------------------------------------------
    //  Movement
    // -----------------------------------------------------------------------

    private double speed;

    /** Server-authoritative moving flag — true while the server is stepping the player. */
    private volatile boolean serverMoving = false;

    // -----------------------------------------------------------------------
    //  Facing direction — authoritative from server, never derived on client
    // -----------------------------------------------------------------------

    private String direction = "south";

    // -----------------------------------------------------------------------
    //  Inventory & skills
    // -----------------------------------------------------------------------

    private final Inventory            inventory;
    private final Map<String, Integer> inventoryState = new HashMap<>();
    private final SkillSystem          skillSystem;

    // -----------------------------------------------------------------------
    //  Equipment
    // -----------------------------------------------------------------------

    /** Slot name (e.g. "WEAPON") → itemId. Updated by server EQUIPMENT packets. */
    private final Map<String, Integer> equipment = new HashMap<>();

    // -----------------------------------------------------------------------
    //  Combat stats
    // -----------------------------------------------------------------------

    // Defaults match the server's starting values (combat skills start at level 3).
    // These are overwritten by the SKILLS snapshot sent on login.
    private int attackLevel   = 3;
    private int strengthLevel = 3;
    private int defenceLevel  = 3;
    private int maxHp         = 15; // level 3 × 5
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
     * Set the server-authoritative direction string.
     * Only valid values are "north", "south", "east", "west".
     */
    public void setDirection(String dir) {
        if (dir != null && !dir.isBlank()) {
            this.direction = dir.trim().toLowerCase();
        }
    }

    /** Returns the current facing direction as sent by the server. */
    public String getDirection() {
        return direction;
    }

    /**
     * Face toward the given entity using axis-priority (X first).
     * Used by combat; converts to a 4-cardinal direction string.
     */
    public void faceTarget(com.classic.preservitory.entity.Entity target) {
        double dx = target.getCenterX() - getCenterX();
        double dy = target.getCenterY() - getCenterY();
        if (Math.abs(dx) >= Math.abs(dy)) {
            direction = dx > 0 ? "east" : "west";
        } else {
            direction = dy > 0 ? "south" : "north";
        }
    }

    /**
     * Integer accessors kept for compatibility with legacy rendering code
     * (the dot position in {@link #renderLegacyBody}).
     */
    public int getFacingX() {
        return switch (direction) {
            case "east"  ->  1;
            case "west"  -> -1;
            default      ->  0;
        };
    }

    public int getFacingY() {
        return switch (direction) {
            case "south" ->  1;
            case "north" -> -1;
            default      ->  0;
        };
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

    /** Apply a server-authoritative equipment snapshot. Pass null to clear. */
    public void applyEquipmentUpdate(Map<String, Integer> snapshot) {
        equipment.clear();
        if (snapshot != null) equipment.putAll(snapshot);
    }

    public Map<String, Integer> getEquipment() { return equipment; }

    public int getEquippedItemId(String slot) {
        return equipment.getOrDefault(slot, -1);
    }

    /** True when HP has reached zero. */
    public boolean isDead() { return hp <= 0; }

    /**
     * Apply a full server-authoritative slot-based inventory snapshot.
     *
     * <p>{@code slotData} is a 28-element array where each entry is
     * {@code [itemId, amount]}.  An itemId of -1 (or ≤ 0) means the slot is empty.</p>
     */
    public void applyInventorySlots(int[][] slotData) {
        inventory.clear();
        inventoryState.clear();

        for (int i = 0; i < slotData.length && i < 28; i++) {
            int itemId = slotData[i][0];
            int amount = slotData[i][1];
            if (itemId <= 0 || amount <= 0) continue;

            ItemDefinition def = ItemDefinitionManager.get(itemId);
            Item item = new Item(itemId, def.name, def.stackable);
            item.setCount(amount);
            inventory.setSlot(i, item);
            inventoryState.merge(def.name, amount, Integer::sum);
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

        // ---- Shadow (dark ellipse on the ground) ----
        g2.setColor(new Color(0, 0, 0, 85));
        g2.fillOval(footX - 11, footY - 5, 22, 10);

        boolean moving = state == Animation.State.WALKING;
        int spriteFootY = footY + bobY;

        if (PlayerSpriteManager.isLoaded()) {
            if (state == Animation.State.ATTACKING) {
                PlayerSpriteManager.drawPlayerAction(g2, footX, spriteFootY,
                        animation.getAttackAnimName(), getSpriteDirection(), animation.getAttackFrame());
            } else {
                PlayerSpriteManager.drawPlayer(g2, footX, spriteFootY,
                        getSpriteDirection(), moving, animation.getTimer());
            }
        } else {
            renderLegacyBody(g2, footX, spriteFootY, state);
        }

        // ---- DEBUG: direction label above head (set to true to diagnose sprite mapping) ----
        if (DEBUG_DIRECTION) {
            String dbgDir = getSpriteDirection().toUpperCase();
            g2.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 10));
            g2.setColor(java.awt.Color.YELLOW);
            g2.drawString(dbgDir, footX - g2.getFontMetrics().stringWidth(dbgDir) / 2, spriteFootY - 40);
        }

        // ---- Tool flash (right side of body) ----
        if (state == Animation.State.CHOPPING) {
            double phase = animation.sin(5);
            if (phase > 0) {
                int alpha = (int)(phase * 220);
                g2.setColor(new Color(220, 170, 50, alpha));
                g2.fillRect(footX + 9, spriteFootY - 20, 5, 12);
            }
        } else if (state == Animation.State.MINING) {
            double phase = animation.sin(4);
            if (phase > 0) {
                int alpha = (int)(phase * 220);
                g2.setColor(new Color(120, 160, 230, alpha));
                g2.fillRect(footX + 9, spriteFootY - 20, 5, 12);
            }
        }
    }

    private void renderLegacyBody(Graphics2D g2, int footX, int footY, Animation.State state) {
        int bodyW = 14;
        int bodyH = 26;
        int bodyX = footX - bodyW / 2;
        int bodyY = footY - bodyH;

        Color bodyColor;
        Color highlightColor;

        switch (state) {
            case FIGHTING:
                double fightPulse = animation.pulse(6);
                int r = (int) (60 + 140 * fightPulse);
                int gv = (int) (180 - 130 * fightPulse);
                bodyColor = new Color(r, gv, 60);
                highlightColor = bodyColor.brighter();
                break;

            case CHOPPING:
            case MINING:
                bodyColor = new Color(75, 170, 75);
                highlightColor = new Color(120, 225, 120);
                break;

            default:
                bodyColor = new Color(60, 180, 60);
                highlightColor = new Color(100, 220, 100);
                break;
        }

        g2.setColor(bodyColor);
        g2.fillRect(bodyX, bodyY, bodyW, bodyH);

        g2.setColor(highlightColor);
        g2.fillRect(bodyX + 2, bodyY + 2, bodyW / 3, bodyH / 4);

        g2.setColor(Color.DARK_GRAY);
        g2.drawRect(bodyX, bodyY, bodyW, bodyH);

        int dotX;
        int dotY;
        int fx = getFacingX();
        int fy = getFacingY();
        if (fx > 0) {
            dotX = bodyX + bodyW - 5;
            dotY = bodyY + bodyH / 2 - 3;
        } else if (fx < 0) {
            dotX = bodyX + 1;
            dotY = bodyY + bodyH / 2 - 3;
        } else if (fy < 0) {
            dotX = bodyX + bodyW / 2 - 3;
            dotY = bodyY + 1;
        } else {
            dotX = bodyX + bodyW / 2 - 3;
            dotY = bodyY + bodyH - 7;
        }

        g2.setColor(new Color(255, 255, 255, 200));
        g2.fillOval(dotX, dotY, 6, 6);
    }

    /**
     * Returns the direction string for sprite selection.
     * The server is the sole authority; no derivation happens here.
     * If sprites appear visually flipped for a direction, swap the string
     * returned for that case (e.g. return "west" when direction is "east").
     */
    private String getSpriteDirection() {
        return direction;
    }

    // -----------------------------------------------------------------------
    //  Getters & setters
    // -----------------------------------------------------------------------

    public String      getId()            { return id; }
    public void        setId(String id)   { this.id = id; }

    public double      getSpeed()               { return speed; }
    public void        setSpeed(double s)       { this.speed = s; }

    public boolean     isServerMoving()         { return serverMoving; }
    public void        setServerMoving(boolean m) { this.serverMoving = m; }

    public Inventory   getInventory()     { return inventory; }
    public Map<String, Integer> getInventoryState() { return inventoryState; }
    public SkillSystem getSkillSystem()   { return skillSystem; }
    public Animation   getAnimation()     { return animation; }

    // Combat stats
    public int getAttackLevel()   { return attackLevel; }
    public int getStrengthLevel() { return strengthLevel; }
    public int getDefenceLevel()  { return defenceLevel; }
    public int getHp()            { return hp; }
    public int getMaxHp()         { return maxHp; }

    // -----------------------------------------------------------------------
    //  Active prayers — set exclusively by server via ACTIVE_PRAYERS packet
    // -----------------------------------------------------------------------

    private final Set<String> activePrayers = new HashSet<>();

    /**
     * Replace the active prayer set with a fresh server snapshot.
     * Pass an empty array (or null) to clear all active prayers.
     */
    public void setActivePrayers(String[] prayerIds) {
        activePrayers.clear();
        if (prayerIds != null) Collections.addAll(activePrayers, prayerIds);
    }

    /** Returns true if the prayer with the given id is currently active. */
    public boolean isPrayerActive(String prayerId) {
        return activePrayers.contains(prayerId);
    }
}
