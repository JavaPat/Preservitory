package com.classic.preservitory.system;

import com.classic.preservitory.util.Constants;
import com.classic.preservitory.world.World;

import java.awt.Point;
import java.util.*;

/**
 * Tile-grid A* pathfinding.
 *
 * Obstacles are ALIVE trees and SOLID rocks (queried via {@link World#isTileWalkable}).
 * Enemies and NPCs are treated as passable so the player can walk up to them.
 *
 * 8-directional movement — diagonal cost ≈ 1.4 × cardinal cost (approximated as
 * 14 vs 10 in integer arithmetic).  Corner-cutting through two diagonally touching
 * obstacles is blocked.
 *
 * Returns a list of pixel-space waypoints (tile centres) representing the path
 * from start to goal.  An empty list means no path was found (or start == goal).
 */
public class Pathfinding {

    // 8-directional neighbour offsets
    private static final int[] DC = { 1, -1, 0,  0, 1,  1, -1, -1 };
    private static final int[] DR = { 0,  0, 1, -1, 1, -1,  1, -1 };

    // Manhattan / Chebyshev cost values (×10 for integer arithmetic)
    private static final int COST_CARDINAL  = 10;
    private static final int COST_DIAGONAL  = 14;   // ≈ 10 × √2

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    /**
     * Find a path on the tile grid from {@code (startCol, startRow)} to
     * {@code (goalCol, goalRow)}.
     *
     * @param startCol  starting tile column
     * @param startRow  starting tile row
     * @param goalCol   destination tile column
     * @param goalRow   destination tile row
     * @param world     used to query tile walkability
     * @return ordered list of pixel-space tile-centre waypoints, or empty if
     *         no path exists or start == goal
     */
    public static List<Point> findPath(int startCol, int startRow,
                                        int goalCol,  int goalRow,
                                        World world) {
        int cols = world.getCols();
        int rows = world.getRows();

        // Clamp goal inside the grid
        goalCol = Math.max(0, Math.min(cols - 1, goalCol));
        goalRow = Math.max(0, Math.min(rows - 1, goalRow));

        if (startCol == goalCol && startRow == goalRow) {
            return Collections.emptyList();
        }

        // If goal tile is blocked, reroute to the nearest walkable tile beside it
        if (!world.isTileWalkable(goalCol, goalRow)) {
            Point near = nearestWalkable(goalCol, goalRow, world);
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

            for (int i = 0; i < 8; i++) {
                int nc = curCol + DC[i];
                int nr = curRow + DR[i];

                if (nc < 0 || nc >= cols || nr < 0 || nr >= rows) continue;

                int nIdx = idx(nc, nr, cols);
                if (closed[nIdx]) continue;

                // Only the exact goal tile may be unwalkable (to handle "stand next to" cases)
                if (!world.isTileWalkable(nc, nr) && nIdx != goalIdx) continue;

                // Prevent corner-cutting: both cardinal neighbours must be walkable
                boolean diagonal = (DC[i] != 0 && DR[i] != 0);
                if (diagonal) {
                    if (!world.isTileWalkable(curCol + DC[i], curRow)
                     || !world.isTileWalkable(curCol, curRow + DR[i])) continue;
                }

                int moveCost = diagonal ? COST_DIAGONAL : COST_CARDINAL;
                int tentG    = gScore[curIdx] + moveCost;

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

    /** Chebyshev distance heuristic (admissible for 8-dir movement) × 10. */
    private static long h(int c1, int r1, int c2, int r2) {
        return (long) Math.max(Math.abs(c2 - c1), Math.abs(r2 - r1)) * COST_CARDINAL;
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
     * Scan outward from (col, row) in a ring pattern until we find a walkable tile.
     * Returns null if nothing is found within radius 3.
     */
    private static Point nearestWalkable(int col, int row, World world) {
        for (int r = 1; r <= 3; r++) {
            for (int dc = -r; dc <= r; dc++) {
                for (int dr = -r; dr <= r; dr++) {
                    if (Math.abs(dc) != r && Math.abs(dr) != r) continue; // ring only
                    int nc = col + dc;
                    int nr = row + dr;
                    if (nc >= 0 && nc < world.getCols()
                     && nr >= 0 && nr < world.getRows()
                     && world.isTileWalkable(nc, nr)) {
                        return new Point(nc, nr);
                    }
                }
            }
        }
        return null;
    }
}
