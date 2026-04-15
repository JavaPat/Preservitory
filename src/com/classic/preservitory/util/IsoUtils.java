package com.classic.preservitory.util;

/**
 * Square-grid coordinate utilities.
 *
 * World space  : entities and tiles use pixel coordinates (col * TILE_SIZE, row * TILE_SIZE).
 * Screen space : identical to world space — no projection is applied.
 *                The camera translates but does not skew.
 *
 * Kept the original method names and constants so all call-sites compile without change.
 * ISO_TILE_W / ISO_TILE_H are now both equal to TILE_SIZE (32 px).
 */
public class IsoUtils {

    /** Width of one tile in pixels. */
    public static final int ISO_TILE_W = Constants.TILE_SIZE;   // 32

    /** Height of one tile in pixels. */
    public static final int ISO_TILE_H = Constants.TILE_SIZE;   // 32

    // -----------------------------------------------------------------------
    //  Tile grid → screen  (col/row → pixel top-left of tile)
    // -----------------------------------------------------------------------

    /** Screen X of the top-left corner of tile (col, row). */
    public static int tileToIsoX(int col, int row) {
        return col * Constants.TILE_SIZE;
    }

    /** Screen Y of the top-left corner of tile (col, row). */
    public static int tileToIsoY(int col, int row) {
        return row * Constants.TILE_SIZE;
    }

    // -----------------------------------------------------------------------
    //  World pixel → screen  (supports sub-tile float positions)
    // -----------------------------------------------------------------------

    /** Screen X for a world-space pixel position. Identity in the square grid. */
    public static int worldToIsoX(double wx, double wy) {
        return (int) wx;
    }

    /** Screen Y for a world-space pixel position. Identity in the square grid. */
    public static int worldToIsoY(double wx, double wy) {
        return (int) wy;
    }

    // -----------------------------------------------------------------------
    //  Screen → tile grid  (used by click / hover detection)
    // -----------------------------------------------------------------------

    /** Tile column for a screen point (sx, sy). */
    public static int isoToTileCol(int sx, int sy) {
        return Math.floorDiv(sx, Constants.TILE_SIZE);
    }

    /** Tile row for a screen point (sx, sy). */
    public static int isoToTileRow(int sx, int sy) {
        return Math.floorDiv(sy, Constants.TILE_SIZE);
    }
}
