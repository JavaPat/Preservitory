package com.classic.preservitory.entity;

import com.classic.preservitory.client.definitions.EnemyDefinition;
import com.classic.preservitory.client.definitions.EnemyDefinitionManager;
import com.classic.preservitory.ui.framework.assets.EntitySpriteManager;
import com.classic.preservitory.ui.framework.assets.SharedSpriteManager;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;

import java.awt.Color;
import java.awt.Graphics;

/**
 * Client-rendered combat enemy driven by a server-side definition.
 *
 * Animation is driven by {@link AnimationController}: a single state machine
 * that requires only the 4 idle rotation sprites.  No walk-frame or attack-frame
 * files are needed for new enemy types.
 *
 * Stats (name, maxHp, attack, defence) come from {@link EnemyDefinition} loaded
 * from {@code cache/enemies/*.json}.
 */
public class Enemy extends Entity {

    public enum State { ALIVE, DEAD }

    private String id = "";
    private State  state;

    private final EnemyDefinition def;
    private       int             hp;

    // -----------------------------------------------------------------------
    //  Interpolation
    // -----------------------------------------------------------------------

    private final EntityInterpolation lerp;

    // direction, isMoving, attacking, walkTick, attackTick, animationState
    // are all inherited from Entity and driven by AnimationController.

    // -----------------------------------------------------------------------
    //  Sprites + animation — only idle rotation sprites required
    // -----------------------------------------------------------------------

    private final EntitySpriteManager spriteManager;
    private final AnimationController  controller;

    // -----------------------------------------------------------------------
    //  Combat state — used for conditional HP bar display
    // -----------------------------------------------------------------------

    /** Timestamp of the last time this enemy's HP decreased (i.e. was hit). */
    private long lastDamagedMs = 0L;

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public Enemy(int definitionId, double x, double y) {
        super(x, y, Constants.TILE_SIZE, Constants.TILE_SIZE);
        this.def           = EnemyDefinitionManager.get(definitionId);
        this.hp            = this.def.maxHp;
        this.state         = State.ALIVE;
        this.lerp          = new EntityInterpolation(x, y);
        this.spriteManager = new EntitySpriteManager("enemy", def.key, SharedSpriteManager.get());
        this.controller    = new AnimationController(spriteManager);
    }

    // -----------------------------------------------------------------------
    //  Network sync + per-frame lerp
    // -----------------------------------------------------------------------

    /**
     * Record a new server-authoritative position and state.
     * Safe to call every frame — identical positions leave the lerp undisturbed.
     */
    public void syncPosition(int serverX, int serverY, String dir, boolean moving) {
        if (dir != null && !dir.isBlank()) this.direction = dir.trim().toLowerCase();
        this.isMoving = moving;
        if (moving) {
            lerp.syncPosition(serverX, serverY);
        } else {
            lerp.snapTo(serverX, serverY);
        }
    }

    /** Advance interpolation one render frame. Call before rendering. */
    public void updateLerp() {
        lerp.tick();
        x = lerp.getRenderX();
        y = lerp.getRenderY();
        controller.update(this);  // determines animationState, advances walkTick / attackTick
    }

    // -----------------------------------------------------------------------
    //  Click detection
    // -----------------------------------------------------------------------

    public boolean containsPoint(int cx, int cy) {
        int pad = 16;
        return state == State.ALIVE
                && cx >= x - pad && cx <= x + width  + pad
                && cy >= y - pad && cy <= y + height + pad;
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    @Override
    public void render(Graphics g) {
        if (isDead()) return;

        int isoX  = IsoUtils.worldToIsoX(x, y);
        int isoY  = IsoUtils.worldToIsoY(x, y);
        int footX = isoX + IsoUtils.ISO_TILE_W / 2;
        int footY = isoY + IsoUtils.ISO_TILE_H;

        if (controller.isSpritesLoaded()) {
            controller.render(this, g);
        } else {
            renderHumanoid(g, footX, footY);
        }
        // HP bar and name are rendered by GameRenderer (overlay pass)
    }

    private void renderHumanoid(Graphics g, int footX, int footY) {
        // Use the same walkTick-driven bob as the sprite path for consistency
        int bobY = (animationState == AnimationState.WALK)
                ? ((walkTick % 10 < 5) ? 1 : -1)
                : 0;

        // Shadow on ground
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(footX - 9, footY - 4, 18, 8);

        // Body
        int bodyW   = 14;
        int bodyH   = 18;
        int bodyX   = footX - bodyW / 2;
        int bodyTop = footY - bodyH + bobY;

        g.setColor(new Color(140, 75, 55));
        g.fillRect(bodyX, bodyTop, bodyW, bodyH);

        // Head
        int headW   = 12;
        int headH   = 11;
        int headX   = footX - headW / 2;
        int headTop = bodyTop - headH + 2;

        g.setColor(new Color(165, 95, 70));
        g.fillOval(headX, headTop, headW, headH);

        // Eyes
        g.setColor(new Color(20, 20, 20));
        g.fillRect(headX + 2,         headTop + 3, 3, 3);
        g.fillRect(headX + headW - 5, headTop + 3, 3, 3);

        // Outline
        g.setColor(Color.DARK_GRAY);
        g.drawRect(bodyX, bodyTop, bodyW, bodyH);
    }

    /**
     * Height of this entity's rendered body above the foot anchor.
     * Used to position the HP bar above the sprite or fallback shape.
     */
    private int getEntityHeightAboveFoot() {
        return controller.isSpritesLoaded() ? 45 : 27;
    }

    public void renderHpBar(Graphics g, int footX, int footY) {
        int barW = IsoUtils.ISO_TILE_W / 2;
        int barX = footX - barW / 2;
        int barY = footY - getEntityHeightAboveFoot() - 10;
        int barH = 4;

        g.setColor(new Color(60, 0, 0));
        g.fillRect(barX, barY, barW, barH);
        g.setColor(new Color(200, 30, 30));
        g.fillRect(barX, barY, (int)(barW * getHpFraction()), barH);
        g.setColor(Color.DARK_GRAY);
        g.drawRect(barX, barY, barW, barH);
    }

    // -----------------------------------------------------------------------
    //  Getters
    // -----------------------------------------------------------------------

    public boolean isAlive()          { return state == State.ALIVE; }
    public boolean isDead()           { return state == State.DEAD; }

    public String  getId()            { return id; }
    public void    setId(String id)   { this.id = id; }

    public String  getName()          { return def.name; }
    public int     getHp()            { return hp; }
    public int     getMaxHp()         { return def.maxHp; }
    public int     getAttackLevel()   { return def.attackLevel; }
    public int     getDefenceLevel()  { return def.defenceLevel; }

    public float getHpFraction() {
        return def.maxHp > 0 ? (float) hp / def.maxHp : 0f;
    }

    /**
     * Sync HP from an authoritative server snapshot.
     * Transitions state to ALIVE or DEAD based on the new value.
     * Records damage time so {@link #recentlyInCombat()} works correctly.
     */
    public void setHp(int newHp) {
        int clamped = Math.max(0, newHp);
        if (clamped < this.hp) lastDamagedMs = System.currentTimeMillis();
        this.hp    = clamped;
        this.state = (this.hp > 0) ? State.ALIVE : State.DEAD;
    }

    /**
     * Returns true if this enemy was damaged within the last 3 seconds.
     * Used to keep the HP bar visible briefly after combat ends.
     */
    public boolean recentlyInCombat() {
        return System.currentTimeMillis() - lastDamagedMs < 3_000L;
    }
}
