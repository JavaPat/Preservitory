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

    /** Draw this tile as a filled square at its grid position. */
    public void render(Graphics g) {
        int sx = IsoUtils.tileToIsoX(col, row);
        int sy = IsoUtils.tileToIsoY(col, row);
        int ts = IsoUtils.ISO_TILE_W;   // tile size (32)

        boolean alt = (col + row) % 2 == 0;

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

        g.setColor(base);
        g.fillRect(sx, sy, ts, ts);

        // Subtle terrain details
        if (type == TileType.WATER_EDGE) {
            g.setColor(new Color(120, 180, 235, 110));
            int wy = sy + ts / 2 + (col % 3) - 1;
            g.drawLine(sx + 4, wy, sx + ts - 4, wy);
        } else if (type == TileType.DIRT) {
            if ((col * 3 + row * 5) % 7 < 2) {
                g.setColor(new Color(165, 125, 72, 150));
                g.fillOval(sx + ts / 2 - 5, sy + ts / 2 - 2, 4, 3);
                g.fillOval(sx + ts / 2 + 3, sy + ts / 2 + 1, 3, 2);
            }
        }

        // Subtle grid line
        g.setColor(new Color(0, 0, 0, 25));
        g.drawRect(sx, sy, ts, ts);
    }

    // -----------------------------------------------------------------------
    //  Getters
    // -----------------------------------------------------------------------

    public int      getCol()      { return col; }
    public int      getRow()      { return row; }
    public boolean  isWalkable()  { return walkable; }
    public TileType getType()     { return type; }
}
