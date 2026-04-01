package com.classic.preservitory.world.objects;

import com.classic.preservitory.client.definitions.ItemDefinition;
import com.classic.preservitory.client.definitions.ItemDefinitionManager;
import com.classic.preservitory.client.definitions.ItemIds;
import com.classic.preservitory.entity.Entity;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;

/**
 * A ground-loot item dropped by an enemy and waiting to be picked up.
 *
 * Coins → gold oval.  Everything else → pale green oval.
 * The item's first three display-name characters (or stack count) are shown inside.
 */
public class Loot extends Entity {

    private final String id;
    private final int    itemId;
    private final int    count;

    public Loot(String id, double x, double y, int itemId, int count) {
        super(x, y, Constants.TILE_SIZE, Constants.TILE_SIZE);
        this.id     = id;
        this.itemId = itemId;
        this.count  = count;
    }

    public String getId()     { return id;     }
    public int    getItemId() { return itemId; }
    public int    getCount()  { return count;  }

    public boolean containsPoint(int px, int py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    @Override
    public void render(Graphics g) {
        int isoX = IsoUtils.worldToIsoX(x, y);
        int isoY = IsoUtils.worldToIsoY(x, y);

        int cx = isoX + IsoUtils.ISO_TILE_W / 2;
        int cy = isoY + IsoUtils.ISO_TILE_H / 2 + 4;

        boolean isCoins = (itemId == ItemIds.COINS);
        Color fill   = isCoins ? new Color(255, 210, 0)   : new Color(90, 185, 80);
        Color border = isCoins ? new Color(180, 140, 0)   : new Color(50, 120, 40);
        Color shadow = new Color(0, 0, 0, 55);

        g.setColor(shadow);
        g.fillOval(cx - 7, cy - 3, 14, 7);
        g.setColor(fill);
        g.fillOval(cx - 6, cy - 8, 12, 9);
        g.setColor(border);
        g.drawOval(cx - 6, cy - 8, 12, 9);

        // Label: stack count if > 1, else first 3 chars of item name
        String displayName = ItemDefinitionManager.get(itemId).name;
        String label = count > 1
                ? String.valueOf(count)
                : displayName.substring(0, Math.min(3, displayName.length()));
        g.setFont(new Font("Monospaced", Font.BOLD, 7));
        g.setColor(Color.BLACK);
        g.drawString(label, cx - 5, cy - 1);
    }
}
