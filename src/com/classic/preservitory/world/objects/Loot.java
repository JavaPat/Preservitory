package com.classic.preservitory.world.objects;

import com.classic.preservitory.client.definitions.ItemDefinition;
import com.classic.preservitory.client.definitions.ItemDefinitionManager;
import com.classic.preservitory.client.definitions.ItemIds;
import com.classic.preservitory.entity.Entity;
import com.classic.preservitory.ui.framework.assets.AssetManager;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

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

    public String getId() { return id;     }
    public int getItemId() { return itemId; }
    public int getCount()  { return count;  }

    public boolean containsPoint(int px, int py) {
        int pad = 12;
        return px >= x - pad && px <= x + width  + pad
            && py >= y - pad && py <= y + height + pad;
    }

    @Override
    public void render(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;

        int isoX = IsoUtils.worldToIsoX(x, y);
        int isoY = IsoUtils.worldToIsoY(x, y);

        int cx = isoX + IsoUtils.ISO_TILE_W / 2;
        int cy = isoY + IsoUtils.ISO_TILE_H / 2 + 4;

        // Ground shadow (drawn under every item type)
        g2.setColor(new Color(0, 0, 0, 55));
        g2.fillOval(cx - 7, cy - 3, 14, 7);

        if (itemId == ItemIds.COINS) {
            // OSRS-style coin stack sprite + formatted amount.
            // Pass (cx, cy) as the tile centre; drawCoinStack centres the icon there.
            AssetManager.drawCoinStack(g2, count, cx, cy, false);
        } else {
            // Non-coin: sprite if available, otherwise pale-green oval fallback
            String spriteKey = ItemDefinitionManager.get(itemId).spriteKey;
            BufferedImage sprite = spriteKey != null ? AssetManager.getImage(spriteKey) : null;
            if (sprite != null) {
                int size = 24;
                g2.drawImage(sprite, cx - size / 2, cy - size / 2, size, size, null);
                if (count > 1) {
                    String cnt = String.valueOf(count);
                    g2.setFont(new Font("Arial", Font.BOLD, 7));
                    g2.setColor(Color.BLACK);
                    g2.drawString(cnt, cx - 5 + 1, cy - 1 + 1);
                    g2.setColor(Color.WHITE);
                    g2.drawString(cnt, cx - 5, cy - 1);
                }
            } else {
                g2.setColor(new Color(90, 185, 80));
                g2.fillOval(cx - 6, cy - 8, 12, 9);
                g2.setColor(new Color(50, 120, 40));
                g2.drawOval(cx - 6, cy - 8, 12, 9);

                String displayName = ItemDefinitionManager.get(itemId).name;
                String label = count > 1
                        ? String.valueOf(count)
                        : displayName.substring(0, Math.min(3, displayName.length()));
                g2.setFont(new Font("Arial", Font.BOLD, 7));
                g2.setColor(Color.BLACK);
                g2.drawString(label, cx - 5, cy - 1);
            }
        }
    }
}
