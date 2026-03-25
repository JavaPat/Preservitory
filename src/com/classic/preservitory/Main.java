package com.classic.preservitory;

import com.classic.preservitory.game.Game;
import java.io.IOException;

/**
 * Entry point for Preservitory.
 *
 * === Single-player (default) ===
 *   java com.classic.preservitory.Main
 *
 * === Multiplayer: start the server first ===
 *   java com.classic.preservitory.Main --server
 *
 * === Multiplayer: then launch one client per player ===
 *   java com.classic.preservitory.Main
 *   (Each client auto-connects to localhost:5555.)
 */
public class Main {

    public static void main(String[] args) throws IOException {
        // Default: launch the game client (connects to server automatically)
        Game game = new Game();
        game.start();
    }
}
