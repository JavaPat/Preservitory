package com.classic.preservitory;

import com.classic.preservitory.game.Game;
import java.io.IOException;

public class Main {

    static void main(String[] args) throws IOException {
        // Launch the game client; gameplay depends on the server connection.
        Game game = new Game();
        game.start();
    }
}
