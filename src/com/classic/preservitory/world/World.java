package com.classic.preservitory.world;

import com.classic.preservitory.util.Constants;
import com.classic.preservitory.world.Tile.TileType;

import java.awt.Graphics;

/**
 * Owns the static tile grid only.
 *
 * Dynamic world state such as enemies, trees, rocks, NPCs, and loot is
 * managed by ClientWorld from server snapshots.
 */
public class World {

    private static final int COLS = Constants.WORLD_COLS;
    private static final int ROWS = Constants.WORLD_ROWS;

    private final Tile[][] tiles;

    public World() {
        tiles = new Tile[COLS][ROWS];
        generateTiles();
    }

    // -----------------------------------------------------------------------
    //  Initialisation
    // -----------------------------------------------------------------------

    private void generateTiles() {
        for (int col = 0; col < COLS; col++) {
            for (int row = 0; row < ROWS; row++) {
                tiles[col][row] = new Tile(col, row, true, tileTypeFor(col, row));
            }
        }
    }

    private TileType tileTypeFor(int col, int row) {
        if (row == 0) return TileType.WATER_EDGE;
        if (row == 1) return TileType.SAND;
        if (row >= 21) return TileType.DARK_GRASS;
        if (col <= 1) return TileType.DARK_GRASS;
        if (col >= 27) return TileType.DARK_GRASS;
        if (col >= 13 && col <= 20 && row >= 6 && row <= 12) return TileType.SAND;

        int dirtHash = (col * 11 + row * 17) % 13;
        if (dirtHash == 0 && row > 3 && row < 20) return TileType.DIRT;

        int darkHash = (col * 7 + row * 13) % 9;
        if (darkHash == 0 && row > 2 && row < 21) return TileType.DARK_GRASS;

        return TileType.GRASS;
    }

    public void render(Graphics g) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                tiles[col][row].render(g);
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Pathfinding
    // -----------------------------------------------------------------------

    /** Returns false only for out-of-bounds tiles. Rock/tree blocking is handled
     *  via the ClientWorld::isBlocked predicate injected into Pathfinding. */
    public boolean isTileWalkable(int col, int row) {
        return col >= 0 && col < COLS && row >= 0 && row < ROWS;
    }

    // -----------------------------------------------------------------------
    //  Getters
    // -----------------------------------------------------------------------

    public int getCols() { return COLS; }
    public int getRows() { return ROWS; }
    public Tile getTile(int c, int r) { return tiles[c][r]; }
}
