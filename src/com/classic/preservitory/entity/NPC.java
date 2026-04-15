package com.classic.preservitory.entity;

import com.classic.preservitory.ui.framework.assets.EntitySpriteManager;
import com.classic.preservitory.ui.framework.assets.SharedSpriteManager;
import com.classic.preservitory.util.IsoUtils;

import java.awt.*;

/**
 * A Non-Player Character the player can talk to.
 *
 * Animation is driven by {@link AnimationController}: a single state machine
 * that requires only the 4 idle rotation sprites (north/south/east/west).
 * No walk-frame or attack-frame files are needed for new NPCs.
 *
 * Position is interpolated over one server tick (600 ms) using {@link EntityInterpolation}.
 */
public class NPC extends Entity {

    private String  id;
    private final String  name;
    private final boolean shopkeeper;

    // -----------------------------------------------------------------------
    //  Sprites + animation — only idle rotation sprites required
    // -----------------------------------------------------------------------

    private final EntitySpriteManager spriteManager;
    private final AnimationController  controller;

    // -----------------------------------------------------------------------
    //  Interpolation
    // -----------------------------------------------------------------------

    private final EntityInterpolation lerp;

    // direction, isMoving, attacking, walkTick, attackTick, animationState
    // are all inherited from Entity and driven by AnimationController.

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public NPC(double startX, double startY, String name, boolean shopkeeper) {
        super(startX, startY, IsoUtils.ISO_TILE_W, IsoUtils.ISO_TILE_H);
        this.name       = name;
        this.shopkeeper = shopkeeper;
        this.lerp       = new EntityInterpolation(startX, startY);
        // Sprite key = first word of name, lower-cased (e.g. "Captain Jack" → "captain")
        String spriteKey   = name.toLowerCase().split("[^a-z]")[0];
        this.spriteManager = new EntitySpriteManager("npc", spriteKey, SharedSpriteManager.get());
        this.controller    = new AnimationController(spriteManager);
    }

    // -----------------------------------------------------------------------
    //  Network sync
    // -----------------------------------------------------------------------

    /**
     * Record a new server-confirmed position and state.
     * Only restarts the lerp when the position actually changes.
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

    // -----------------------------------------------------------------------
    //  Per-frame update — call each frame before rendering
    // -----------------------------------------------------------------------

    public void updateLerp() {
        lerp.tick();
        x = lerp.getRenderX();
        y = lerp.getRenderY();
        controller.update(this);  // determines animationState, advances walkTick / attackTick
    }

    // -----------------------------------------------------------------------
    //  Click detection
    // -----------------------------------------------------------------------

    public boolean containsPoint(int px, int py) {
        int pad = 16;
        return px >= x - pad && px <= x + width  + pad
            && py >= y - pad && py <= y + height + pad;
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    @Override
    public void render(Graphics g) {
        int isoX  = IsoUtils.worldToIsoX(x, y);
        int isoY  = IsoUtils.worldToIsoY(x, y);
        int footX = isoX + IsoUtils.ISO_TILE_W / 2;
        int footY = isoY + IsoUtils.ISO_TILE_H;

        if (controller.isSpritesLoaded()) {
            controller.render(this, g);
        } else {
            renderFallback(g, footX, footY);
        }

        // Interaction indicator — name shown on hover by GameRenderer
        g.setFont(new Font("Arial", Font.BOLD, 13));
        g.setColor(new Color(255, 220, 40));
        g.drawString("?", footX - 4, footY - 44);
    }

    private void renderFallback(Graphics g, int footX, int footY) {
        // Use the same walkTick-driven bob as the sprite path for consistency
        int bobY = (animationState == AnimationState.WALK)
                ? ((walkTick % 10 < 5) ? 1 : -1)
                : 0;

        // Shadow
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(footX - 9, footY - 4, 18, 8);

        // Body — teal vertical rectangle
        int bodyW = 14;
        int bodyH = 26;
        int bodyX = footX - bodyW / 2;
        int bodyY = footY - bodyH + bobY;

        g.setColor(new Color(30, 140, 180));
        g.fillRect(bodyX, bodyY, bodyW, bodyH);

        // Highlight
        g.setColor(new Color(80, 200, 230));
        g.fillRect(bodyX + 2, bodyY + 2, bodyW / 3, bodyH / 4);

        // Direction dot
        renderDirectionDot(g, bodyX, bodyY, bodyW, bodyH);

        // Outline
        g.setColor(Color.DARK_GRAY);
        g.drawRect(bodyX, bodyY, bodyW, bodyH);
    }

    private void renderDirectionDot(Graphics g, int bodyX, int bodyY, int bodyW, int bodyH) {
        int dotX, dotY;
        switch (direction) {
            case "east"  -> { dotX = bodyX + bodyW - 5; dotY = bodyY + bodyH / 2 - 3; }
            case "west"  -> { dotX = bodyX + 1;         dotY = bodyY + bodyH / 2 - 3; }
            case "north" -> { dotX = bodyX + bodyW / 2 - 3; dotY = bodyY + 1; }
            default      -> { dotX = bodyX + bodyW / 2 - 3; dotY = bodyY + bodyH - 7; }
        }
        g.setColor(new Color(255, 255, 255, 180));
        g.fillOval(dotX, dotY, 5, 5);
    }

    // -----------------------------------------------------------------------
    //  Getters
    // -----------------------------------------------------------------------

    public String  getName()        { return name; }
    public boolean isShopkeeper()   { return shopkeeper; }
    public String  getId()          { return id; }
    public void    setId(String id) { this.id = id; }
}
