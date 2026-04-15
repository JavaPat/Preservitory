package com.classic.preservitory.system;

import com.classic.preservitory.util.Constants;
import com.classic.preservitory.world.World;

import java.awt.Point;
import java.util.*;
import java.util.function.BiPredicate;

/**
 * Tile-grid A* pathfinding.
 *
 * Obstacles are ALIVE trees and SOLID rocks (queried via {@link World#isTileWalkable}).
 * Enemies and NPCs are treated as passable so the player can walk up to them.
 *
 * 4-directional cardinal movement only (no diagonals).
 * Uses Manhattan distance heuristic.
 *
 * Returns a list of pixel-space waypoints (tile centres) representing the path
 * from start to goal.  An empty list means no path was found (or start == goal).
 */
public class Pathfinding {

    // 4-directional cardinal neighbour offsets only
    private static final int[] DC = { 1, -1, 0,  0 };
    private static final int[] DR = { 0,  0, 1, -1 };

    // Cost value (×10 for integer arithmetic)
    private static final int COST_CARDINAL  = 10;

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    /**
     * Find a path on the tile grid from {@code (startCol, startRow)} to
     * {@code (goalCol, goalRow)}.
     *
     * @param startCol      starting tile column
     * @param startRow      starting tile row
     * @param goalCol       destination tile column
     * @param goalRow       destination tile row
     * @param world         used to query base tile walkability (rocks, bounds)
     * @param extraBlocked  optional extra predicate — returns {@code true} when
     *                      a tile (col, row) is additionally blocked (e.g. trees
     *                      from ClientWorld).  Pass {@code null} to skip.
     * @return ordered list of pixel-space tile-centre waypoints, or empty if
     *         no path exists or start == goal
     */
    public static List<Point> findPath(int startCol, int startRow,
                                        int goalCol,  int goalRow,
                                        World world,
                                        BiPredicate<Integer, Integer> extraBlocked) {
        int cols = world.getCols();
        int rows = world.getRows();

        // Clamp goal inside the grid
        goalCol = Math.max(0, Math.min(cols - 1, goalCol));
        goalRow = Math.max(0, Math.min(rows - 1, goalRow));

        if (startCol == goalCol && startRow == goalRow) {
            return Collections.emptyList();
        }

        // If goal tile is blocked, reroute to the nearest walkable tile beside it
        if (!isWalkable(goalCol, goalRow, world, extraBlocked)) {
            Point near = nearestWalkable(goalCol, goalRow, world, extraBlocked);
            if (near == null) return Collections.emptyList();
            goalCol = near.x;
            goalRow = near.y;
            if (startCol == goalCol && startRow == goalRow) return Collections.emptyList();
        }

        // --- A* ---
        // Use flat arrays for speed; size = cols × rows
        int size = cols * rows;
        int[] gScore     = new int[size];
        int[] parent     = new int[size];      // packed (parentCol | parentRow << 16)
        boolean[] closed = new boolean[size];

        Arrays.fill(gScore, Integer.MAX_VALUE);
        Arrays.fill(parent, -1);

        int startIdx = idx(startCol, startRow, cols);
        int goalIdx  = idx(goalCol,  goalRow,  cols);

        gScore[startIdx] = 0;

        // Open set ordered by f = g + h (Chebyshev heuristic × 10)
        PriorityQueue<long[]> open = new PriorityQueue<>(Comparator.comparingLong(e -> e[1]));
        open.add(new long[]{ startIdx, h(startCol, startRow, goalCol, goalRow) });

        while (!open.isEmpty()) {
            long[] entry = open.poll();
            int curIdx = (int) entry[0];

            if (curIdx == goalIdx) {
                return reconstruct(parent, goalIdx, startIdx, cols);
            }

            if (closed[curIdx]) continue;
            closed[curIdx] = true;

            int curCol = curIdx % cols;
            int curRow = curIdx / cols;

            for (int i = 0; i < 4; i++) {
                int nc = curCol + DC[i];
                int nr = curRow + DR[i];

                if (nc < 0 || nc >= cols || nr < 0 || nr >= rows) continue;

                int nIdx = idx(nc, nr, cols);
                if (closed[nIdx]) continue;

                // Only the exact goal tile may be unwalkable (to handle "stand next to" cases)
                if (!isWalkable(nc, nr, world, extraBlocked) && nIdx != goalIdx) continue;

                int tentG = gScore[curIdx] + COST_CARDINAL;

                if (tentG < gScore[nIdx]) {
                    gScore[nIdx] = tentG;
                    parent[nIdx] = curIdx;
                    long f = tentG + h(nc, nr, goalCol, goalRow);
                    open.add(new long[]{ nIdx, f });
                }
            }
        }

        return Collections.emptyList(); // No path found
    }

    /**
     * Convenience overload — no extra blocked predicate (rocks only).
     * Kept for backwards compatibility with any call sites that don't need
     * tree awareness.
     */
    public static List<Point> findPath(int startCol, int startRow,
                                        int goalCol,  int goalRow,
                                        World world) {
        return findPath(startCol, startRow, goalCol, goalRow, world, null);
    }

    // -----------------------------------------------------------------------
    //  Coordinate helpers (used by GamePanel)
    // -----------------------------------------------------------------------

    /** Pixel X → tile column. */
    public static int pixelToTileCol(double px) {
        return Math.max(0, (int)(px / Constants.TILE_SIZE));
    }

    /** Pixel Y → tile row. */
    public static int pixelToTileRow(double py) {
        return Math.max(0, (int)(py / Constants.TILE_SIZE));
    }

    /** Centre of tile (col, row) in pixel space. */
    public static Point tileCentre(int col, int row) {
        return new Point(
            col * Constants.TILE_SIZE + Constants.TILE_SIZE / 2,
            row * Constants.TILE_SIZE + Constants.TILE_SIZE / 2
        );
    }

    // -----------------------------------------------------------------------
    //  Private helpers
    // -----------------------------------------------------------------------

    private static int idx(int col, int row, int cols) {
        return row * cols + col;
    }

    /** Manhattan distance heuristic (admissible for 4-dir cardinal movement) × 10. */
    private static long h(int c1, int r1, int c2, int r2) {
        return (long)(Math.abs(c2 - c1) + Math.abs(r2 - r1)) * COST_CARDINAL;
    }

    /** Walk the parent chain from goal back to start and return pixel waypoints. */
    private static List<Point> reconstruct(int[] parent, int goalIdx,
                                            int startIdx, int cols) {
        List<Point> path = new ArrayList<>();
        int cur = goalIdx;
        while (cur != startIdx) {
            int col = cur % cols;
            int row = cur / cols;
            path.add(tileCentre(col, row));
            cur = parent[cur];
            if (cur == -1) break; // safety — should not happen
        }
        Collections.reverse(path);
        return path;
    }

    /**
     * Combined walkability check: base tile must be walkable AND the optional
     * extra-blocked predicate must not flag the tile as blocked.
     */
    private static boolean isWalkable(int col, int row, World world,
                                       BiPredicate<Integer, Integer> extraBlocked) {
        if (!world.isTileWalkable(col, row)) return false;
        if (extraBlocked != null && extraBlocked.test(col, row)) return false;
        return true;
    }

    /**
     * Scan outward from (col, row) in a ring pattern until we find a walkable tile.
     * Returns null if nothing is found within radius 3.
     */
    private static Point nearestWalkable(int col, int row, World world,
                                          BiPredicate<Integer, Integer> extraBlocked) {
        for (int r = 1; r <= 3; r++) {
            for (int dc = -r; dc <= r; dc++) {
                for (int dr = -r; dr <= r; dr++) {
                    if (Math.abs(dc) != r && Math.abs(dr) != r) continue; // ring only
                    int nc = col + dc;
                    int nr = row + dr;
                    if (nc >= 0 && nc < world.getCols()
                     && nr >= 0 && nr < world.getRows()
                     && isWalkable(nc, nr, world, extraBlocked)) {
                        return new Point(nc, nr);
                    }
                }
            }
        }
        return null;
    }
}
