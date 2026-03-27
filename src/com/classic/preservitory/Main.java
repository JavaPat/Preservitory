package com.classic.preservitory;

import com.classic.preservitory.game.Game;
import java.io.IOException;

/**
 * Entry point for Preservitory.
 *
 * Start the server first, then launch one client per player:
 *   java com.classic.preservitory.Main
 *   (Each client auto-connects to localhost:5555.)
 */
public class Main {

    public static void main(String[] args) throws IOException {
        // Launch the game client; gameplay depends on the server connection.
        Game game = new Game();
        game.start();
    }
}
