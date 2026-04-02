package com.classic.preservitory.entity;

import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;

import java.awt.*;

/**
 * A stationary Non-Player Character the player can talk to.
 *
 * Clicking on an NPC walks the player up to it and opens a dialogue.
 * If {@code shopkeeper} is true, the NPC also opens the shop after
 * the final dialogue line.
 *
 * Rendered in isometric style: a vertical teal figure with a name tag
 * and "?" marker, anchored at the bottom-centre of the tile's diamond.
 */
public class NPC extends Entity {

    private String id;
    private final String  name;
    private final boolean shopkeeper;

    public NPC(double x, double y, String name, boolean shopkeeper) {
        super(x, y, Constants.TILE_SIZE, Constants.TILE_SIZE);
        this.name       = name;
        this.shopkeeper = shopkeeper;
    }

    /** True when the world-space point (px, py) is inside this NPC's bounding box. */
    public boolean containsPoint(int px, int py) {
        int pad = 16;
        return px >= x - pad && px <= x + width  + pad
            && py >= y - pad && py <= y + height + pad;
    }

    // -----------------------------------------------------------------------
    //  Rendering — isometric
    // -----------------------------------------------------------------------

    @Override
    public void render(Graphics g) {
        // Iso position of tile top-left
        int isoX = IsoUtils.worldToIsoX(x, y);
        int isoY = IsoUtils.worldToIsoY(x, y);

        // "Foot" = bottom-centre of the tile diamond
        int footX = isoX + IsoUtils.ISO_TILE_W / 2;
        int footY = isoY + IsoUtils.ISO_TILE_H;

        // Shadow on ground
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(footX - 9, footY - 4, 18, 8);

        // Body — teal vertical rectangle
        int bodyW = 14;
        int bodyH = 26;
        int bodyX = footX - bodyW / 2;
        int bodyY = footY - bodyH;

        g.setColor(new Color(30, 140, 180));
        g.fillRect(bodyX, bodyY, bodyW, bodyH);

        // Highlight on upper-left corner
        g.setColor(new Color(80, 200, 230));
        g.fillRect(bodyX + 2, bodyY + 2, bodyW / 3, bodyH / 4);

        // Outline
        g.setColor(Color.DARK_GRAY);
        g.drawRect(bodyX, bodyY, bodyW, bodyH);

        // "?" marker (yellow, above body)
        g.setFont(new Font("Arial", Font.BOLD, 13));
        g.setColor(new Color(255, 220, 40));
        g.drawString("?", footX - 4, bodyY - 14);

        // Name tag background + text
        g.setFont(new Font("Arial", Font.PLAIN, 10));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(name);
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(footX - tw / 2 - 2, bodyY - 13, tw + 4, 12);
        g.setColor(new Color(180, 230, 255));
        g.drawString(name, footX - tw / 2, bodyY - 3);
    }

    // -----------------------------------------------------------------------
    //  Getters
    // -----------------------------------------------------------------------

    public String  getName()       { return name; }
    public boolean isShopkeeper()  { return shopkeeper; }
    public String  getId()         { return id; }
    public void    setId(String id) { this.id = id; }
}
