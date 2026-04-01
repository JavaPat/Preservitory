package com.classic.preservitory.game;

import com.classic.preservitory.cache.CacheDownloader;
import com.classic.preservitory.client.definitions.EnemyDefinitionLoader;
import com.classic.preservitory.client.definitions.EnemyDefinitionManager;
import com.classic.preservitory.client.definitions.ItemDefinitionLoader;
import com.classic.preservitory.client.definitions.ItemDefinitionManager;
import com.classic.preservitory.client.definitions.NpcDefinitionLoader;
import com.classic.preservitory.client.definitions.NpcDefinitionManager;
import com.classic.preservitory.client.definitions.ObjectDefinitionLoader;
import com.classic.preservitory.client.definitions.ObjectDefinitionManager;
import com.classic.preservitory.system.audio.MusicManager;
import com.classic.preservitory.ui.panels.GamePanel;
import com.classic.preservitory.ui.screens.LoadingScreen;
import com.classic.preservitory.util.Constants;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class Game {

    private JFrame window;
    private GamePanel panel;

    public void start() {
        MusicManager musicManager = new MusicManager();
        musicManager.play();

        SwingUtilities.invokeLater(() -> {
            LoadingScreen loadingScreen = new LoadingScreen();
            window = buildWindow(loadingScreen);
            window.setVisible(true);

            Thread cacheThread = new Thread(() -> {
                CacheDownloader.init((percent, status) ->
                    SwingUtilities.invokeLater(() -> loadingScreen.setProgress(percent, status))
                );
                ItemDefinitionManager.load(ItemDefinitionLoader.loadAll());
                ObjectDefinitionManager.load(ObjectDefinitionLoader.loadAll());
                EnemyDefinitionManager.load(EnemyDefinitionLoader.loadAll());
                NpcDefinitionManager.load(NpcDefinitionLoader.loadAll());

                SwingUtilities.invokeLater(() -> {
                    panel = new GamePanel(musicManager);
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

    public JFrame getWindow() { return window; }
    public GamePanel getPanel()  { return panel;  }
}
