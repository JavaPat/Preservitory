package com.classic.preservitory.world;

import com.classic.preservitory.util.IsoUtils;

import java.awt.Color;
import java.awt.Graphics;

/**
 * Represents a single map tile.
 *
 * Each tile knows its grid position (col/row), whether it is walkable, and its
 * {@link TileType}.  The type drives the render colour.
 *
 * Rendering uses isometric projection: each tile is drawn as a diamond
 * (64 × 32 px bounding box) at the position returned by {@link IsoUtils}.
 *
 * Checkerboard variation: the two colour variants within each type are chosen
 * by {@code (col + row) % 2}, giving a subtle chessboard pattern across the
 * whole map without requiring random numbers.
 */
public class Tile {

    // -----------------------------------------------------------------------
    //  Terrain type
    // -----------------------------------------------------------------------

    /** Visual terrain categories — all types are fully walkable. */
    public enum TileType {
        GRASS,
        DARK_GRASS,
        DIRT,
        SAND,
        WATER_EDGE
    }

    // -----------------------------------------------------------------------
    //  Fields
    // -----------------------------------------------------------------------

    private final int      col;
    private final int      row;
    private final boolean  walkable;
    private final TileType type;

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public Tile(int col, int row, boolean walkable, TileType type) {
        this.col      = col;
        this.row      = row;
        this.walkable = walkable;
        this.type     = type;
    }

    // -----------------------------------------------------------------------
    //  Rendering — isometric diamond
    // -----------------------------------------------------------------------

    /**
     * Draw this tile as an isometric diamond at its projected screen position.
     *
     * Diamond corners (relative to the bounding-box top-left at isoX, isoY):
     *   Top    (isoX + halfW,      isoY)
     *   Right  (isoX + ISO_TILE_W, isoY + halfH)
     *   Bottom (isoX + halfW,      isoY + ISO_TILE_H)
     *   Left   (isoX,              isoY + halfH)
     */
    public void render(Graphics g) {
        int isoX = IsoUtils.tileToIsoX(col, row);
        int isoY = IsoUtils.tileToIsoY(col, row);

        int hw = IsoUtils.ISO_TILE_W / 2;   // 32
        int hh = IsoUtils.ISO_TILE_H / 2;   // 16

        // Diamond polygon corners
        int[] xPts = { isoX + hw, isoX + IsoUtils.ISO_TILE_W, isoX + hw, isoX };
        int[] yPts = { isoY,      isoY + hh,                   isoY + IsoUtils.ISO_TILE_H, isoY + hh };

        boolean alt = (col + row) % 2 == 0;

        // Pick base colour from terrain type + checkerboard variant
        Color base;
        switch (type) {
            case GRASS:
                base = alt ? new Color(55, 130, 50) : new Color(45, 115, 40);
                break;
            case DARK_GRASS:
                base = alt ? new Color(30, 90, 28) : new Color(25, 78, 22);
                break;
            case DIRT:
                base = alt ? new Color(140, 100, 55) : new Color(125, 88, 45);
                break;
            case SAND:
                base = alt ? new Color(195, 175, 110) : new Color(180, 162, 98);
                break;
            default: // WATER_EDGE
                base = alt ? new Color(60, 120, 180) : new Color(50, 105, 165);
                break;
        }

        // Fill diamond
        g.setColor(base);
        g.fillPolygon(xPts, yPts, 4);

        // Subtle terrain details inside the diamond
        if (type == TileType.WATER_EDGE) {
            // Horizontal ripple across the diamond mid-line
            g.setColor(new Color(120, 180, 235, 110));
            int wy = isoY + hh + (col % 3) - 1;
            g.drawLine(isoX + hw / 2, wy, isoX + hw + hw / 2, wy);

        } else if (type == TileType.DIRT) {
            // Two small pebble ovals on some tiles (deterministic)
            if ((col * 3 + row * 5) % 7 < 2) {
                g.setColor(new Color(165, 125, 72, 150));
                g.fillOval(isoX + hw - 6, isoY + hh - 3, 4, 3);
                g.fillOval(isoX + hw + 4, isoY + hh + 1, 3, 2);
            }
        }

        // Grid outline (subtle dark edge on each diamond)
        g.setColor(new Color(0, 0, 0, 35));
        g.drawPolygon(xPts, yPts, 4);
    }

    // -----------------------------------------------------------------------
    //  Getters
    // -----------------------------------------------------------------------

    public int      getCol()      { return col; }
    public int      getRow()      { return row; }
    public boolean  isWalkable()  { return walkable; }
    public TileType getType()     { return type; }
}
