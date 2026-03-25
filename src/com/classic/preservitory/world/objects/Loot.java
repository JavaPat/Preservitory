package com.classic.preservitory.world.objects;

import com.classic.preservitory.entity.Entity;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;

/**
 * A ground-loot item dropped by an enemy and waiting to be picked up.
 *
 * Rendered as a small coloured oval in isometric space.
 * Coins → gold, everything else → pale green.
 * The item name (or count, if > 1) is printed inside the oval.
 */
public class Loot extends Entity {

    private final String id;
    private final String itemName;
    private final int    count;

    public Loot(String id, double x, double y, String itemName, int count) {
        super(x, y, Constants.TILE_SIZE, Constants.TILE_SIZE);
        this.id       = id;
        this.itemName = itemName;
        this.count    = count;
    }

    public String getId()       { return id; }
    public String getItemName() { return itemName; }
    public int    getCount()    { return count; }

    public boolean containsPoint(int px, int py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    @Override
    public void render(Graphics g) {
        int isoX = IsoUtils.worldToIsoX(x, y);
        int isoY = IsoUtils.worldToIsoY(x, y);

        // Centre of the iso tile
        int cx = isoX + IsoUtils.ISO_TILE_W / 2;
        int cy = isoY + IsoUtils.ISO_TILE_H / 2 + 4;  // sit slightly toward tile bottom

        boolean isCoins = "Coins".equalsIgnoreCase(itemName);
        Color fill   = isCoins ? new Color(255, 210, 0)   : new Color(90, 185, 80);
        Color border = isCoins ? new Color(180, 140, 0)   : new Color(50, 120, 40);
        Color shadow = new Color(0, 0, 0, 55);

        // Drop shadow
        g.setColor(shadow);
        g.fillOval(cx - 7, cy - 3, 14, 7);

        // Icon oval
        g.setColor(fill);
        g.fillOval(cx - 6, cy - 8, 12, 9);
        g.setColor(border);
        g.drawOval(cx - 6, cy - 8, 12, 9);

        // Label: count if > 1, otherwise first 3 chars of name
        String label = count > 1
                ? String.valueOf(count)
                : itemName.substring(0, Math.min(3, itemName.length()));
        g.setFont(new Font("Monospaced", Font.BOLD, 7));
        g.setColor(Color.BLACK);
        g.drawString(label, cx - 5, cy - 1);
    }
}
