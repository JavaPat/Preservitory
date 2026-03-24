package com.classic.preservitory.game;

import com.classic.preservitory.ui.GamePanel;
import com.classic.preservitory.util.Constants;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Bootstraps the game: creates the Swing window, attaches the GamePanel,
 * and starts the game loop.
 *
 * All Swing work is posted to the Event Dispatch Thread (EDT) via
 * SwingUtilities.invokeLater() as required by Swing's threading rules.
 */
public class Game {

    private JFrame    window;
    private GamePanel panel;

    /** Create the window and start everything. Call this from Main. */
    public void start() {
        SwingUtilities.invokeLater(() -> {
            panel  = new GamePanel();
            window = buildWindow(panel);
            window.setVisible(true);
            panel.startGameLoop();
        });
    }

    /** Build and configure the JFrame. */
    private JFrame buildWindow(GamePanel gamePanel) {
        JFrame frame = new JFrame(Constants.GAME_NAME);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.add(gamePanel);
        frame.pack(); // Size the frame to fit the panel's preferred size
        frame.setLocationRelativeTo(null); // Center on screen
        return frame;
    }

    // --- Getters (useful for future expansion) ---

    public JFrame    getWindow() { return window; }
    public GamePanel getPanel()  { return panel;  }
}
