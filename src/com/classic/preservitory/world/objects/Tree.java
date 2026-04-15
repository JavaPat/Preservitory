package com.classic.preservitory.world.objects;

import com.classic.preservitory.entity.Entity;
import com.classic.preservitory.ui.framework.assets.AssetManager;
import com.classic.preservitory.world.DefinitionLoader;
import com.classic.preservitory.world.ObjectDefinition;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

/**
 * A tree object whose alive/stump state is driven entirely by the server.
 * The client never runs respawn timers or chop logic — it is a pure renderer
 * of server-authoritative state.
 */
public class Tree extends Entity {

    public enum State { ALIVE, STUMP }

    private State state;

    private final String id;
    private final String typeId;
    private static final java.util.Map<String, ObjectDefinition> DEFINITIONS = DefinitionLoader.loadAll();

    public Tree(String id, String typeId, double x, double y) {
        super(x, y, widthFor(typeId), heightFor(typeId));
        this.id    = id;
        this.typeId = typeId;
        this.state = State.ALIVE;
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
     * Set the visual state of this tree.
     * {@code true} → full tree (alive).
     * {@code false} → stump (chopped).
     * No timers are started or modified.
     */
    public void setAlive(boolean alive) {
        state = alive ? State.ALIVE : State.STUMP;
    }

    /** Convenience alias for {@code setAlive(false)} — kept for WoodcuttingSystem compatibility. */
    public void chop() {
        setAlive(false);
    }

    /** Convenience alias for {@code setAlive(true)} — kept for addTree compatibility. */
    public void respawn() {
        setAlive(true);
    }

    public boolean isAlive() {
        return state == State.ALIVE;
    }

    public boolean containsPoint(int px, int py) {
        int pad = 16;
        return state == State.ALIVE
                && px >= x - pad && px <= x + width  + pad
                && py >= y - pad && py <= y + height + pad;
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    @Override
    public void render(Graphics g) {
        int isoX = IsoUtils.worldToIsoX(x, y);
        int isoY = IsoUtils.worldToIsoY(x, y);

        int footX = isoX + IsoUtils.ISO_TILE_W / 2;
        int footY = isoY + IsoUtils.ISO_TILE_H;

        if (state == State.ALIVE) {
            ObjectDefinition def = DEFINITIONS.get(typeId);
            String spriteKey = (def != null && def.spriteKey != null) ? def.spriteKey : typeId;
            BufferedImage sprite = AssetManager.getImage(spriteKey);
            if (sprite != null) {
                g.drawImage(sprite, footX - sprite.getWidth() / 2, footY - sprite.getHeight() + 16, null);
            } else {
                renderAlive(g, footX, footY);
            }
        } else {
            renderStump(g, footX, footY);
        }
    }

    private void renderAlive(Graphics g, int footX, int footY) {
        int trunkW = 8;
        int trunkH = 20;
        g.setColor(new Color(101, 67, 20));
        g.fillRect(footX - trunkW / 2, footY - trunkH, trunkW, trunkH);
        g.setColor(new Color(70, 45, 12));
        g.drawRect(footX - trunkW / 2, footY - trunkH, trunkW, trunkH);

        int canopyW = 38;
        int canopyH = 28;
        int canopyX = footX - canopyW / 2;
        int canopyY = footY - trunkH - canopyH + 8;

        g.setColor(new Color(22, 100, 22));
        g.fillOval(canopyX, canopyY, canopyW, canopyH);

        g.setColor(new Color(55, 150, 55));
        g.fillOval(canopyX + 7, canopyY + 4, canopyW / 2, canopyH / 2);

        g.setColor(new Color(0, 55, 0));
        g.drawOval(canopyX, canopyY, canopyW, canopyH);
    }

    private void renderStump(Graphics g, int footX, int footY) {
        g.setColor(new Color(90, 55, 18));
        g.fillRect(footX - 8, footY - 12, 16, 12);
        g.setColor(new Color(60, 38, 10));
        g.drawRect(footX - 8, footY - 12, 16, 12);
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
