package com.classic.preservitory.util;

/**
 * Central place for all game-wide constants.
 * Change values here to affect the whole game.
 */
public class Constants {

    public static final String GAME_NAME = "Preservitory";

    public static final int PORT = 5555;

    public static final String LOCALHOST = "localhost";

    public static final boolean EDITOR_MODE = false;

    public static final String GAME_VERSION = "1.0";

    public static final String GAME_NAME_TO_LOWER = GAME_NAME.toLowerCase();

    // -----------------------------------------------------------------------
    //  Window / screen
    // -----------------------------------------------------------------------

    public static final int SCREEN_WIDTH  = 765;
    public static final int SCREEN_HEIGHT = 503;

    // -----------------------------------------------------------------------
    //  Layout: game viewport + right side panel
    //
    //  ┌──────────────────────────┬───────────────┐
    //  │   Viewport (game world)  │  Side panel   │
    //  │   512 × 503 px           │  253 × 503 px │
    //  └──────────────────────────┴───────────────┘
    // -----------------------------------------------------------------------

    /** Width of the scrollable game-world viewport. */
    public static final int VIEWPORT_W = 512;

    /** Height of the game-world viewport (full screen height). */
    public static final int VIEWPORT_H = SCREEN_HEIGHT;

    /** X-coordinate where the right side panel begins. */
    public static final int PANEL_X = VIEWPORT_W;

    /** Width of the right side panel. */
    public static final int PANEL_W = SCREEN_WIDTH - VIEWPORT_W;   // 253

    // -----------------------------------------------------------------------
    //  World / map dimensions
    //
    //  The world is larger than the viewport; the camera scrolls to follow
    //  the player.  Expand these to add more map space.
    // -----------------------------------------------------------------------

    /** World width in tiles. */
    public static final int WORLD_COLS = 64;

    /** World height in tiles. */
    public static final int WORLD_ROWS = 64;

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
