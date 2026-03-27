package com.classic.preservitory.game;

import com.classic.preservitory.cache.CacheDownloader;
import com.classic.preservitory.ui.panels.GamePanel;
import com.classic.preservitory.ui.screens.LoadingScreen;
import com.classic.preservitory.util.Constants;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class Game {

    private JFrame    window;
    private GamePanel panel;

    public void start() {
        SwingUtilities.invokeLater(() -> {
            LoadingScreen loadingScreen = new LoadingScreen();
            window = buildWindow(loadingScreen);
            window.setVisible(true);

            Thread cacheThread = new Thread(() -> {
                CacheDownloader.init((percent, status) ->
                    SwingUtilities.invokeLater(() -> loadingScreen.setProgress(percent, status))
                );

                SwingUtilities.invokeLater(() -> {
                    panel = new GamePanel();
                    panel.setLoginSuccessListener(this::setUsername);
                    panel.setDisconnectListener(this::resetTitle);
                    window.getContentPane().removeAll();
                    window.getContentPane().add(panel);
                    window.pack();
                    panel.requestFocusInWindow();
                    panel.startGameLoop();
                });
            }, "cache-loader");

            cacheThread.setDaemon(true);
            cacheThread.start();
        });
    }

    public void setUsername(String username) {
        SwingUtilities.invokeLater(() ->
            window.setTitle(Constants.GAME_NAME + " - Logged in as " + username)
        );
    }

    public void resetTitle() {
        SwingUtilities.invokeLater(() ->
            window.setTitle(Constants.GAME_NAME + " - Version " + Constants.GAME_VERSION)
        );
    }

    private JFrame buildWindow(java.awt.Component content) {
        JFrame frame = new JFrame(Constants.GAME_NAME + " - Version " + Constants.GAME_VERSION);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.add(content);
        frame.pack();
        frame.setLocationRelativeTo(null);
        return frame;
    }

    public JFrame    getWindow() { return window; }
    public GamePanel getPanel()  { return panel;  }
}
