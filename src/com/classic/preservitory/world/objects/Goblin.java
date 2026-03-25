package com.classic.preservitory.world.objects;

import com.classic.preservitory.entity.Enemy;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.util.IsoUtils;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;

/**
 * A Goblin — the simplest combat enemy.
 *
 * Stats  : Attack 3, Strength 4, Defence 2, HP 10
 *
 * Rendered in isometric style: a humanoid figure standing on its tile.
 * The foot (anchor) point is the bottom-centre of the tile's diamond.
 */
public class Goblin extends Enemy {

    public Goblin(double x, double y) {
        super("Goblin",
              x, y, Constants.TILE_SIZE, Constants.TILE_SIZE,
              /* maxHp */       10,
              /* attackLevel */ 3,
              /* strength */    4,
              /* defence */     2);
    }

    // -----------------------------------------------------------------------
    //  Rendering — isometric
    // -----------------------------------------------------------------------

    @Override
    public void render(Graphics g) {
        if (isDead()) return; // invisible while waiting to respawn

        // Iso position of tile top-left
        int isoX = IsoUtils.worldToIsoX(x, y);
        int isoY = IsoUtils.worldToIsoY(x, y);

        // "Foot" = bottom-centre of the tile diamond
        int footX = isoX + IsoUtils.ISO_TILE_W / 2;
        int footY = isoY + IsoUtils.ISO_TILE_H;

        // Shadow on ground
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(footX - 9, footY - 4, 18, 8);

        // Body (reddish-brown rectangle rising from ground)
        int bodyW = 14;
        int bodyH = 18;
        int bodyX = footX - bodyW / 2;
        int bodyTop = footY - bodyH;

        g.setColor(new Color(140, 75, 55));
        g.fillRect(bodyX, bodyTop, bodyW, bodyH);

        // Head (oval above body)
        int headW = 12;
        int headH = 11;
        int headX = footX - headW / 2;
        int headTop = bodyTop - headH + 2;  // slight overlap with body

        g.setColor(new Color(165, 95, 70));
        g.fillOval(headX, headTop, headW, headH);

        // Eyes (two dark squares)
        g.setColor(new Color(20, 20, 20));
        g.fillRect(headX + 2, headTop + 3, 3, 3);
        g.fillRect(headX + headW - 5, headTop + 3, 3, 3);

        // Outlines
        g.setColor(Color.DARK_GRAY);
        g.drawRect(bodyX, bodyTop, bodyW, bodyH);

        // HP bar above the head
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

        // Name tag above the HP bar
        g.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g.setColor(new Color(255, 170, 170));
        g.drawString(getName(), barX, barY - 2);
    }
}
