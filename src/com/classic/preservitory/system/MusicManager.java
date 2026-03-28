package com.classic.preservitory.system;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.io.File;

/**
 * Background music manager using JavaFX MediaPlayer.
 *
 * Supports MP3, WAV, and MIDI — ready for in-game music tracks and
 * area ambience as the project grows.
 *
 * Pre-login track lives at:  cache/midi/pre_login.mp3  (project root)
 */
public class MusicManager {

    private static final String MUSIC_PATH =
            System.getProperty("user.dir") + "/cache/midi/pre_login.mp3";

    private MediaPlayer player;
    private boolean     muted = false;

    public MusicManager() {
        // Boots the JavaFX runtime inside a Swing application.
        // Must happen before any Platform.runLater() calls.
        new JFXPanel();
    }

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    /** Begin looping the pre-login track. Safe to call multiple times. */
    public void start() {
        Platform.runLater(() -> {
            if (player != null) return;

            File file = new File(MUSIC_PATH);
            if (!file.exists()) return;

            try {
                Media media = new Media(file.toURI().toString());
                player = new MediaPlayer(media);
                player.setCycleCount(MediaPlayer.INDEFINITE);
                player.setOnError(() -> player = null);
                if (!muted) player.play();
            } catch (Exception ignored) {
                // Audio unavailable — fail silently
            }
        });
    }

    /**
     * Stop and release the player permanently.
     * Call on login success — music will not resume after this.
     */
    public void stop() {
        Platform.runLater(() -> {
            if (player == null) return;
            player.stop();
            player.dispose();
            player = null;
        });
    }

    /**
     * Pause or resume without releasing the player.
     * Used by the UI toggle button.
     */
    public void setMuted(boolean muted) {
        this.muted = muted;
        Platform.runLater(() -> {
            if (player == null) return;
            if (muted) player.pause();
            else       player.play();
        });
    }

    public boolean isMuted() { return muted; }
}
