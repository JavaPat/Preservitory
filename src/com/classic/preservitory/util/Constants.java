package com.classic.preservitory.util;

/**
 * Central place for all game-wide constants.
 * Change values here to affect the whole game.
 */
public class Constants {

    public static final String GAME_NAME = "Preservitory";

    // -----------------------------------------------------------------------
    //  Window / screen
    // -----------------------------------------------------------------------

    public static final int SCREEN_WIDTH  = 800;
    public static final int SCREEN_HEIGHT = 600;

    // -----------------------------------------------------------------------
    //  Layout: game viewport + right side panel
    //
    //  ┌──────────────────────────┬───────────────┐
    //  │   Viewport (game world)  │  Side panel   │
    //  │   566 × 600 px           │  234 × 600 px │
    //  └──────────────────────────┴───────────────┘
    // -----------------------------------------------------------------------

    /** Width of the scrollable game-world viewport. */
    public static final int VIEWPORT_W = 566;

    /** Height of the game-world viewport (full screen height). */
    public static final int VIEWPORT_H = SCREEN_HEIGHT;

    /** X-coordinate where the right side panel begins. */
    public static final int PANEL_X = VIEWPORT_W;

    /** Width of the right side panel. */
    public static final int PANEL_W = SCREEN_WIDTH - VIEWPORT_W;   // 234

    // -----------------------------------------------------------------------
    //  World / map dimensions
    //
    //  The world is larger than the viewport; the camera scrolls to follow
    //  the player.  Expand these to add more map space.
    // -----------------------------------------------------------------------

    /** World width in tiles. */
    public static final int WORLD_COLS = 30;

    /** World height in tiles. */
    public static final int WORLD_ROWS = 24;

    // -----------------------------------------------------------------------
    //  Tile / player / timing
    // -----------------------------------------------------------------------

    /** Size of a single map tile in pixels. */
    public static final int TILE_SIZE = 32;

    /** Target frames per second. */
    public static final int FPS = 60;

    /** Player movement speed in pixels per second. */
    public static final double PLAYER_SPEED = 150.0;

    /** Distance (pixels) at which the player is considered to have arrived. */
    public static final double ARRIVAL_THRESHOLD = 2.0;
}
