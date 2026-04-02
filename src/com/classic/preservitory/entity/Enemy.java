package com.classic.preservitory.entity;

import com.classic.preservitory.client.definitions.EnemyDefinition;
import com.classic.preservitory.client.definitions.EnemyDefinitionManager;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;

/**
 * Client-rendered combat enemy driven by a server-side definition.
 *
 * Stats (name, maxHp, attack, defence) come from {@link EnemyDefinition} loaded
 * from {@code cache/enemies/*.json}. No stats are hardcoded here.
 *
 * Rendering style is selected by {@link EnemyDefinition#key}. Unknown keys fall
 * back to the humanoid (goblin) style so new enemy types are always visible.
 */
public class Enemy extends Entity {

    public enum State { ALIVE, DEAD }

    private String id = "";
    private State  state;

    private final EnemyDefinition def;
    private       int             hp;

    public Enemy(int definitionId, double x, double y) {
        super(x, y, Constants.TILE_SIZE, Constants.TILE_SIZE);
        this.def   = EnemyDefinitionManager.get(definitionId);
        this.hp    = this.def.maxHp;
        this.state = State.ALIVE;
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

        switch (def.key) {
            case "goblin":
            default:
                renderHumanoid(g);
                break;
        }
    }

    private void renderHumanoid(Graphics g) {
        int isoX = IsoUtils.worldToIsoX(x, y);
        int isoY = IsoUtils.worldToIsoY(x, y);

        int footX = isoX + IsoUtils.ISO_TILE_W / 2;
        int footY = isoY + IsoUtils.ISO_TILE_H;

        // Shadow on ground
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(footX - 9, footY - 4, 18, 8);

        // Body
        int bodyW   = 14;
        int bodyH   = 18;
        int bodyX   = footX - bodyW / 2;
        int bodyTop = footY - bodyH;

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
        g.fillRect(headX + 2,          headTop + 3, 3, 3);
        g.fillRect(headX + headW - 5,  headTop + 3, 3, 3);

        // Outline
        g.setColor(Color.DARK_GRAY);
        g.drawRect(bodyX, bodyTop, bodyW, bodyH);

        // HP bar
        int barW = IsoUtils.ISO_TILE_W / 2;
        int barX = footX - barW / 2;
        int barY = headTop - 9;
        int barH = 4;

        g.setColor(new Color(60, 0, 0));
        g.fillRect(barX, barY, barW, barH);
        g.setColor(new Color(200, 30, 30));
        g.fillRect(barX, barY, (int)(barW * getHpFraction()), barH);
        g.setColor(Color.DARK_GRAY);
        g.drawRect(barX, barY, barW, barH);

        // Name tag
        g.setFont(new Font("Arial", Font.PLAIN, 9));
        g.setColor(new Color(255, 170, 170));
        g.drawString(def.name, barX, barY - 2);
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
     */
    public void setHp(int newHp) {
        this.hp    = Math.max(0, newHp);
        this.state = (this.hp > 0) ? State.ALIVE : State.DEAD;
    }
}
