package com.classic.preservitory.world.objects;

import com.classic.preservitory.entity.Entity;
import com.classic.preservitory.world.DefinitionLoader;
import com.classic.preservitory.world.ObjectDefinition;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;

import java.awt.Color;
import java.awt.Graphics;

/**
 * A minable rock whose alive/depleted state is driven entirely by the server.
 * The client never runs respawn timers or mine logic — it is a pure renderer
 * of server-authoritative state.
 *
 * Visual states:
 *   SOLID    — intact rock, player can mine it
 *   DEPLETED — just mined; a fragment remains; server triggers respawn
 */
public class Rock extends Entity {

    public enum State { SOLID, DEPLETED }

    private State state;

    private final String id;
    private final String typeId;
    private static final java.util.Map<String, ObjectDefinition> DEFINITIONS = DefinitionLoader.loadAll();

    public Rock(String id, String typeId, double x, double y) {
        super(x, y, widthFor(typeId), heightFor(typeId));
        this.id    = id;
        this.typeId = typeId;
        this.state = State.SOLID;
    }

    public String getId() { return id; }
    public String getTypeId() { return typeId; }

    public void setPosition(double x, double y) {
        setX(x);
        setY(y);
    }

    // -----------------------------------------------------------------------
    //  Visual state — set by server events only
    // -----------------------------------------------------------------------

    /**
     * Set the visual state of this rock.
     * {@code true} → solid (alive).
     * {@code false} → depleted (mined).
     * No timers are started or modified.
     */
    public void setAlive(boolean alive) {
        state = alive ? State.SOLID : State.DEPLETED;
    }

    /** Convenience alias for {@code setAlive(false)} — kept for MiningSystem compatibility. */
    public void deplete() {
        setAlive(false);
    }

    /** Convenience alias for {@code setAlive(true)}. */
    public void restore() {
        setAlive(true);
    }

    public boolean isSolid() {
        return state == State.SOLID;
    }

    public boolean containsPoint(int px, int py) {
        return state == State.SOLID
                && px >= x && px <= x + width
                && py >= y && py <= y + height;
    }

    // -----------------------------------------------------------------------
    //  Rendering — isometric
    // -----------------------------------------------------------------------

    @Override
    public void render(Graphics g) {
        int isoX = IsoUtils.worldToIsoX(x, y);
        int isoY = IsoUtils.worldToIsoY(x, y);

        int footX = isoX + IsoUtils.ISO_TILE_W / 2;
        int footY = isoY + IsoUtils.ISO_TILE_H;

        if (state == State.SOLID) {
            renderSolid(g, footX, footY);
        } else {
            renderDepleted(g, footX, footY);
        }
    }

    private void renderSolid(Graphics g, int footX, int footY) {
        int rw = 30;
        int rh = 20;
        int rx = footX - rw / 2;
        int ry = footY - rh;

        g.setColor(new Color(118, 118, 124));
        g.fillOval(rx, ry, rw, rh);

        g.setColor(new Color(172, 172, 178));
        g.fillOval(rx + 4, ry + 3, rw / 2, rh / 2);

        // Ore vein — reddish-brown specks
        g.setColor(new Color(148, 78, 58));
        g.fillRect(rx + rw / 2 - 2, ry + rh / 2, 5, 4);
        g.fillRect(rx + rw / 2 + 4, ry + rh / 3, 4, 3);

        g.setColor(new Color(65, 65, 70));
        g.drawOval(rx, ry, rw, rh);
    }

    private void renderDepleted(Graphics g, int footX, int footY) {
        int rw = 16;
        int rh = 10;
        int rx = footX - rw / 2;
        int ry = footY - rh;

        g.setColor(new Color(65, 65, 70));
        g.fillOval(rx, ry, rw, rh);
        g.setColor(new Color(45, 45, 50));
        g.drawOval(rx, ry, rw, rh);
    }

    private static int widthFor(String typeId) {
        ObjectDefinition def = DEFINITIONS.get(typeId);
        return def != null ? def.width : Constants.TILE_SIZE;
    }

    private static int heightFor(String typeId) {
        ObjectDefinition def = DEFINITIONS.get(typeId);
        return def != null ? def.height : Constants.TILE_SIZE;
    }
}
