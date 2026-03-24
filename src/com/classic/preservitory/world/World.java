package com.classic.preservitory.world;

import com.classic.preservitory.entity.Enemy;
import com.classic.preservitory.entity.NPC;
import com.classic.preservitory.util.Constants;
import com.classic.preservitory.world.Tile.TileType;
import com.classic.preservitory.world.objects.Goblin;
import com.classic.preservitory.world.objects.Rock;
import com.classic.preservitory.world.objects.Tree;

import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds the tile grid plus all world objects: Trees, Rocks, Enemies, and NPCs.
 *
 * The world is {@code WORLD_COLS × WORLD_ROWS} tiles (960 × 768 px), which is
 * larger than the viewport; the camera scrolls to follow the player.
 *
 * Terrain layout (rough):
 *   Row  0          → WATER_EDGE  (decorative shore)
 *   Row  1          → SAND        (beach transition)
 *   Rows 21-23      → DARK_GRASS  (dense undergrowth at south edge)
 *   Cols 0, 28-29   → DARK_GRASS  (forest edges)
 *   Cols 14-19 / rows 7-11 → SAND clearing near NPC Guide
 *   Scattered noise → DARK_GRASS, DIRT patches
 *   Everything else → GRASS (two-tone checkerboard)
 *
 * Rendering order (back → front):
 *   tiles → rocks → trees → enemies → NPCs
 * The player is drawn on top by GamePanel after calling renderNPCs().
 */
public class World {

    private static final int COLS = Constants.WORLD_COLS;   // 30
    private static final int ROWS = Constants.WORLD_ROWS;   // 24

    private final Tile[][]    tiles;
    private final List<Tree>  trees;
    private final List<Rock>  rocks;
    private final List<Enemy> enemies;
    private final List<NPC>   npcs;

    public World() {
        tiles   = new Tile[COLS][ROWS];
        trees   = new ArrayList<>();
        rocks   = new ArrayList<>();
        enemies = new ArrayList<>();
        npcs    = new ArrayList<>();

        generateTiles();
        spawnTrees();
        spawnRocks();
        spawnEnemies();
        spawnNPCs();
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

    /**
     * Deterministic terrain type for a given (col, row).
     * Priority order: border features first, then clearing, then noise patches.
     */
    private TileType tileTypeFor(int col, int row) {
        // Northern water border
        if (row == 0) return TileType.WATER_EDGE;

        // Sandy beach (row 1, and column edges near water)
        if (row == 1) return TileType.SAND;

        // Southern dense undergrowth
        if (row >= 21) return TileType.DARK_GRASS;

        // Western forest edge
        if (col <= 1) return TileType.DARK_GRASS;

        // Eastern forest edge
        if (col >= 27) return TileType.DARK_GRASS;

        // Sandy clearing around the Guide NPC (col 16, row 9)
        if (col >= 13 && col <= 20 && row >= 6 && row <= 12) return TileType.SAND;

        // Dirt patches (deterministic hash — scattered mid-field)
        int dirtHash = (col * 11 + row * 17) % 13;
        if (dirtHash == 0 && row > 3 && row < 20) return TileType.DIRT;

        // Dark-grass patches (denser noise)
        int darkHash = (col * 7 + row * 13) % 9;
        if (darkHash == 0 && row > 2 && row < 21) return TileType.DARK_GRASS;

        return TileType.GRASS;
    }

    /**
     * Tree positions {col, row}.
     * Player starts near col 12, row 9 — all trees placed away from that spot.
     * A few extra trees fill the expanded eastern/western columns.
     */
    private void spawnTrees() {
        int[][] positions = {
            { 2,  2}, { 6,  1}, {10,  3}, {16,  2}, {21,  1},
            { 1, 10}, {22, 11}, { 5, 15}, {15, 15}, {20, 13},
            // Expanded area (cols 23-27)
            {23,  4}, {25,  7}, {24, 14}, {26, 17}, {27, 10}
        };
        for (int[] pos : positions) {
            trees.add(new Tree(pos[0] * Constants.TILE_SIZE,
                               pos[1] * Constants.TILE_SIZE));
        }
    }

    /**
     * Rock positions placed away from trees and goblins.
     * A few extra rocks in the expanded area.
     */
    private void spawnRocks() {
        int[][] positions = {
            { 3,  6}, { 4,  7}, {19,  5},
            {20,  6}, { 8, 13}, {14, 12},
            // Expanded area
            {23, 12}, {25, 16}, {26,  4}
        };
        for (int[] pos : positions) {
            rocks.add(new Rock(pos[0] * Constants.TILE_SIZE,
                               pos[1] * Constants.TILE_SIZE));
        }
    }

    /**
     * NPC positions.  The Guide is at col 16, row 9 — visible near the player's
     * starting position (col 12, row 9) but clear of trees/rocks/goblins.
     */
    private void spawnNPCs() {
        npcs.add(new NPC(16 * Constants.TILE_SIZE, 9 * Constants.TILE_SIZE,
                         "Guide", true));
    }

    /**
     * Goblin positions scattered around the mid-field, away from resources.
     */
    private void spawnEnemies() {
        int[][] positions = {
            { 8,  6}, {13,  5}, { 7, 11},
            {18,  7}, {11, 13},
            // Expanded area
            {24, 10}, {22, 17}
        };
        for (int[] pos : positions) {
            enemies.add(new Goblin(pos[0] * Constants.TILE_SIZE,
                                   pos[1] * Constants.TILE_SIZE));
        }
    }

    // -----------------------------------------------------------------------
    //  Per-frame update
    // -----------------------------------------------------------------------

    public void updateTrees(double deltaTime)   { for (Tree  t : trees)   t.update(deltaTime); }
    public void updateRocks(double deltaTime)   { for (Rock  r : rocks)   r.update(deltaTime); }
    public void updateEnemies(double deltaTime) { for (Enemy e : enemies) e.update(deltaTime); }
    // NPCs are stationary — no update needed

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    /**
     * Draw the tile grid in isometric depth order.
     * Rows are iterated first (outer loop) so that tiles further from the
     * camera (smaller row index) are drawn before tiles closer to the camera.
     * Within each row, columns are drawn left-to-right.
     */
    public void render(Graphics g) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                tiles[col][row].render(g);
            }
        }
    }

    public void renderTrees(Graphics g)   { for (Tree  t : trees)   t.render(g); }
    public void renderRocks(Graphics g)   { for (Rock  r : rocks)   r.render(g); }
    public void renderEnemies(Graphics g) { for (Enemy e : enemies) e.render(g); }
    public void renderNPCs(Graphics g)    { for (NPC   n : npcs)    n.render(g); }

    // -----------------------------------------------------------------------
    //  Click queries — first match wins (world-space pixel coords)
    // -----------------------------------------------------------------------

    /** Returns the first alive enemy whose bounding box contains (px, py), or null. */
    public Enemy getEnemyAt(int px, int py) {
        for (Enemy e : enemies) {
            if (e.containsPoint(px, py)) return e;
        }
        return null;
    }

    /** Returns the first alive tree whose bounding box contains (px, py), or null. */
    public Tree getTreeAt(int px, int py) {
        for (Tree t : trees) {
            if (t.containsPoint(px, py)) return t;
        }
        return null;
    }

    /** Returns the first solid rock whose bounding box contains (px, py), or null. */
    public Rock getRockAt(int px, int py) {
        for (Rock r : rocks) {
            if (r.containsPoint(px, py)) return r;
        }
        return null;
    }

    /** Returns the first NPC whose bounding box contains (px, py), or null. */
    public NPC getNPCAt(int px, int py) {
        for (NPC n : npcs) {
            if (n.containsPoint(px, py)) return n;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    //  Pathfinding support
    // -----------------------------------------------------------------------

    /**
     * Returns true if tile (col, row) contains no static obstacles.
     *
     * An ALIVE tree or a SOLID rock blocks the tile.
     * Dead/depleted variants are passable.
     * NPCs and enemies are NOT included so the player can walk adjacent.
     */
    public boolean isTileWalkable(int col, int row) {
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return false;

        for (Tree t : trees) {
            if (t.isAlive()
             && (int)(t.getX() / Constants.TILE_SIZE) == col
             && (int)(t.getY() / Constants.TILE_SIZE) == row) {
                return false;
            }
        }
        for (Rock r : rocks) {
            if (r.isSolid()
             && (int)(r.getX() / Constants.TILE_SIZE) == col
             && (int)(r.getY() / Constants.TILE_SIZE) == row) {
                return false;
            }
        }
        return true;
    }

    // -----------------------------------------------------------------------
    //  Getters
    // -----------------------------------------------------------------------

    public int          getCols()               { return COLS; }
    public int          getRows()               { return ROWS; }
    public Tile         getTile(int c, int r)   { return tiles[c][r]; }
    public List<Tree>   getTrees()              { return Collections.unmodifiableList(trees); }
    public List<Rock>   getRocks()              { return Collections.unmodifiableList(rocks); }
    public List<Enemy>  getEnemies()            { return Collections.unmodifiableList(enemies); }
    public List<NPC>    getNPCs()               { return Collections.unmodifiableList(npcs); }
}
