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
import com.classic.preservitory.client.settings.ClientSettings;
import com.classic.preservitory.system.audio.MusicManager;
import com.classic.preservitory.ui.panels.GamePanel;
import com.classic.preservitory.ui.screens.LoadingScreen;
import com.classic.preservitory.util.Constants;

import java.awt.BorderLayout;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class Game {

    private JFrame window;
    private GamePanel panel;
    private boolean fullscreen = false;
    private boolean windowedResizable = false;
    private boolean maximizeAllowed = false;
    private Rectangle windowedBounds;

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
                ClientSettings clientSettings = ClientSettings.load();

                SwingUtilities.invokeLater(() -> {
                    panel = new GamePanel(musicManager, clientSettings);
                    panel.setLoginSuccessListener(this::setUsername);
                    panel.setDisconnectListener(this::resetTitle);
                    panel.setAuthStateListener(this::setMaximizeAllowed);
                    panel.setFullscreenListener(() -> SwingUtilities.invokeLater(this::toggleFullscreen));
                    panel.setResizableListener(() -> SwingUtilities.invokeLater(this::toggleResizable));
                    panel.setFpsListener(panel::toggleShowFps);
                    panel.setPingListener(panel::toggleShowPing);
                    panel.setTotalXpListener(panel::toggleShowTotalXp);
                    panel.setShiftDropListener(panel::toggleShiftClickDrop);
                    setMaximizeAllowed(false);
                    panel.setFullscreenState(fullscreen);
                    panel.setResizableState(windowedResizable);
                    panel.syncSettingsUi();
                    window.getContentPane().removeAll();
                    window.getContentPane().add(panel, BorderLayout.CENTER);
                    window.revalidate();
                    window.repaint();
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
            window.setTitle(Constants.EDITOR_MODE
                    ? Constants.GAME_NAME + " - Editor Mode"
                    : Constants.GAME_NAME + " - Version " + Constants.GAME_VERSION)
        );
    }

    private JFrame buildWindow(java.awt.Component content) {
        JFrame frame = new JFrame(Constants.EDITOR_MODE
                ? Constants.GAME_NAME + " - Editor Mode"
                : Constants.GAME_NAME + " - Version " + Constants.GAME_VERSION);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.getContentPane().setLayout(new BorderLayout());
        frame.add(content, BorderLayout.CENTER);
        frame.pack();
        frame.addWindowStateListener(new WindowAdapter() {
            @Override
            public void windowStateChanged(WindowEvent e) {
                if (!fullscreen && (e.getNewState() & JFrame.MAXIMIZED_BOTH) != 0) {
                    SwingUtilities.invokeLater(() -> window.setExtendedState(JFrame.NORMAL));
                }
            }
        });
        if (Constants.EDITOR_MODE) {
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
        frame.setLocationRelativeTo(null);
        return frame;
    }

    private void toggleResizable() {
        if (fullscreen || window == null || !maximizeAllowed) {
            if (panel != null) panel.setResizableState(false);
            return;
        }
        windowedResizable = !windowedResizable;
        window.setResizable(windowedResizable);
        if (panel != null) panel.setResizableState(windowedResizable);
    }

    private void toggleFullscreen() {
        if (window == null) return;
        if (fullscreen) {
            exitFullscreen();
        } else {
            enterFullscreen();
        }
        if (panel != null) {
            panel.setFullscreenState(fullscreen);
            panel.setResizableState(fullscreen ? false : windowedResizable);
            panel.requestFocusInWindow();
        }
    }

    private void enterFullscreen() {
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        windowedBounds = window.getBounds();
        fullscreen = true;

        window.dispose();
        window.setExtendedState(JFrame.NORMAL);
        window.setResizable(false);
        window.setUndecorated(true);
        device.setFullScreenWindow(window);
        window.setVisible(true);
    }

    private void exitFullscreen() {
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        fullscreen = false;

        device.setFullScreenWindow(null);
        window.dispose();
        window.setUndecorated(false);
        window.setExtendedState(JFrame.NORMAL);
        window.setResizable(maximizeAllowed && windowedResizable);
        if (windowedBounds != null) {
            window.setBounds(windowedBounds);
        }
        window.setVisible(true);
        window.toFront();
    }

    private void setMaximizeAllowed(boolean allowed) {
        maximizeAllowed = allowed;
        if (window == null || fullscreen) return;
        window.setExtendedState(JFrame.NORMAL);
        window.setResizable(maximizeAllowed && windowedResizable);
        if (panel != null) {
            panel.setResizableState(maximizeAllowed && windowedResizable);
        }
    }

    public JFrame getWindow() { return window; }
    public GamePanel getPanel()  { return panel;  }
}
