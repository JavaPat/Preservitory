package com.classic.preservitory.util;

/**
 * Isometric coordinate utilities.
 *
 * World space  : entities and tiles use pixel coordinates (col * TILE_SIZE, row * TILE_SIZE).
 * Iso screen   : the 2.5D projected view drawn by every render() method.
 *
 * Projection formulas (tile units → iso screen pixels):
 *   isoX = (tileCol - tileRow) * ISO_TILE_W / 2
 *   isoY = (tileCol + tileRow) * ISO_TILE_H / 2
 *
 * Inverse (iso screen pixels → tile units):
 *   tileCol = (isoX / halfW + isoY / halfH) / 2
 *   tileRow = (isoY / halfH - isoX / halfW) / 2
 */
public class IsoUtils {

    /** Width of one isometric tile diamond in pixels. */
    public static final int ISO_TILE_W = 64;

    /** Height of one isometric tile diamond in pixels. */
    public static final int ISO_TILE_H = 32;

    private static final double HALF_W = ISO_TILE_W / 2.0;   // 32.0
    private static final double HALF_H = ISO_TILE_H / 2.0;   // 16.0

    // -----------------------------------------------------------------------
    //  Tile grid → iso screen
    // -----------------------------------------------------------------------

    /** Iso screen X for an integer tile at (col, row). */
    public static int tileToIsoX(int col, int row) {
        return (col - row) * (ISO_TILE_W / 2);
    }

    /** Iso screen Y for an integer tile at (col, row). */
    public static int tileToIsoY(int col, int row) {
        return (col + row) * (ISO_TILE_H / 2);
    }

    // -----------------------------------------------------------------------
    //  World pixel → iso screen  (supports sub-tile float positions)
    // -----------------------------------------------------------------------

    /**
     * Iso screen X for a world-space pixel position (wx, wy).
     * Uses the same scale as {@link Constants#TILE_SIZE} (32 px per tile).
     */
    public static int worldToIsoX(double wx, double wy) {
        double col = wx / Constants.TILE_SIZE;
        double row = wy / Constants.TILE_SIZE;
        return (int) ((col - row) * HALF_W);
    }

    /** Iso screen Y for a world-space pixel position (wx, wy). */
    public static int worldToIsoY(double wx, double wy) {
        double col = wx / Constants.TILE_SIZE;
        double row = wy / Constants.TILE_SIZE;
        return (int) ((col + row) * HALF_H);
    }

    // -----------------------------------------------------------------------
    //  Iso screen → tile grid  (used by click / hover detection)
    // -----------------------------------------------------------------------

    /**
     * Tile column for an iso-space screen point (sx, sy).
     * Returns the integer tile column the point falls inside.
     */
    public static int isoToTileCol(int sx, int sy) {
        double tileX = (sx / HALF_W + sy / HALF_H) / 2.0;
        return (int) Math.floor(tileX);
    }

    /** Tile row for an iso-space screen point (sx, sy). */
    public static int isoToTileRow(int sx, int sy) {
        double tileY = (sy / HALF_H - sx / HALF_W) / 2.0;
        return (int) Math.floor(tileY);
    }
}
