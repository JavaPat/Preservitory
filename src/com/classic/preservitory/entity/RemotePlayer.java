package com.classic.preservitory.entity;

import com.classic.preservitory.ui.framework.assets.EntitySpriteManager;
import com.classic.preservitory.ui.framework.assets.SharedSpriteManager;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;

import java.awt.*;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Represents another player connected to the same server.
 *
 * Smoothing is handled by {@link EntityInterpolation}.
 * Animation is driven by {@link AnimationController} using the packed sprite cache,
 * with SharedSpriteManager as fallback (player walk/punch sprites).
 */
public class RemotePlayer extends Entity {

    // -----------------------------------------------------------------------
    //  Identity
    // -----------------------------------------------------------------------

    /** Server-assigned player ID (e.g. "P2").  Shown above the character. */
    private final String id;

    // -----------------------------------------------------------------------
    //  Interpolation
    // -----------------------------------------------------------------------

    private final EntityInterpolation lerp;

    // -----------------------------------------------------------------------
    //  Animation
    // -----------------------------------------------------------------------

    private final AnimationController controller;

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public RemotePlayer(String id, double x, double y) {
        super(x, y, Constants.TILE_SIZE, Constants.TILE_SIZE);
        this.id         = id;
        this.lerp       = new EntityInterpolation(x, y);
        EntitySpriteManager sprites = new EntitySpriteManager("player", "", SharedSpriteManager.get());
        this.controller = new AnimationController(sprites);
    }

    // -----------------------------------------------------------------------
    //  Network update
    // -----------------------------------------------------------------------

    /**
     * Called from GamePanel.syncRemotePlayers each time a PLAYERS message arrives.
     * Updates position interpolation and server-authoritative animation state.
     *
     * @param nx        new target world-pixel X
     * @param ny        new target world-pixel Y
     * @param moving    server reports entity is mid-step
     * @param attacking server reports entity fired an attack this tick
     * @param dir       server-reported facing direction
     */
    public void setServerState(double nx, double ny, boolean moving, boolean attacking, String dir) {
        if (moving) {
            lerp.syncPosition((int) nx, (int) ny);
        } else {
            lerp.snapTo((int) nx, (int) ny);
        }
        this.isMoving  = moving;
        this.direction = dir;
        if (attacking) startAttack();
    }

    // -----------------------------------------------------------------------
    //  Per-frame update
    // -----------------------------------------------------------------------

    public void update(double dt) {
        lerp.tick();
        x = lerp.getRenderX();
        y = lerp.getRenderY();
        controller.update(this);
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    @Override
    public void render(Graphics g) {
        if (controller.isSpritesLoaded()) {
            controller.render(this, g);
            return;
        }

        // Fallback: steel-blue rectangle (no sprite available)
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int isoX  = IsoUtils.worldToIsoX(x, y);
        int isoY  = IsoUtils.worldToIsoY(x, y);
        int footX = isoX + IsoUtils.ISO_TILE_W / 2;
        int footY = isoY + IsoUtils.ISO_TILE_H;

        int bodyW = 14, bodyH = 26;
        int bodyX = footX - bodyW / 2;
        int bodyY = footY - bodyH;

        g2.setColor(new Color(0, 0, 0, 70));
        g2.fillOval(footX - 11, footY - 5, 22, 10);

        g2.setColor(new Color(65, 125, 210));
        g2.fillRect(bodyX, bodyY, bodyW, bodyH);
        g2.setColor(new Color(115, 170, 255));
        g2.fillRect(bodyX + 2, bodyY + 2, bodyW / 3, bodyH / 4);
        g2.setColor(Color.DARK_GRAY);
        g2.drawRect(bodyX, bodyY, bodyW, bodyH);
    }

    // -----------------------------------------------------------------------
    //  Hit test
    // -----------------------------------------------------------------------

    public boolean containsPoint(int px, int py) {
        int pad = Constants.TILE_SIZE / 2;
        return px >= x - pad && px <= x + Constants.TILE_SIZE + pad
            && py >= y - pad && py <= y + Constants.TILE_SIZE + pad;
    }

    // -----------------------------------------------------------------------
    //  Getters
    // -----------------------------------------------------------------------

    public String getId() { return id; }

    public boolean isInterpolating() { return lerp.isMoving(); }
}
