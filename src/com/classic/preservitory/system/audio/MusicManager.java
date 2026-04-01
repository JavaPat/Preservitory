package com.classic.preservitory.system.audio;

import javazoom.jl.player.advanced.AdvancedPlayer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Streams a looping MP3 track using JLayer (no Clip, no full-file loading).
 *
 * Thread model: one daemon "music-stream" thread owns the loop.
 * All public methods are safe to call from any thread.
 *
 * Lifecycle:
 *   play()           — start streaming and looping (no-op if already playing)
 *   stop()           — permanently stop; does not restart automatically
 *   setEnabled(bool) — pause/resume without destroying the loop thread
 *   isEnabled()      — current mute state
 */
public class MusicManager {

    private static final String MUSIC_PATH =
            System.getProperty("user.dir") + "/cache/midi/pre_login.mp3";

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean enabled = new AtomicBoolean(true);

    private Thread         playbackThread;
    private AdvancedPlayer currentPlayer;

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    /** Begin looping. Safe to call multiple times — only one thread is ever spawned. */
    public void play() {
        if (running.getAndSet(true)) return;
        playbackThread = new Thread(this::streamLoop, "music-stream");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    /**
     * Permanently stop and release resources.
     * Call once on login success — music will not restart after this.
     */
    public void stop() {
        running.set(false);
        closeCurrentPlayer();
        if (playbackThread != null) {
            playbackThread.interrupt();
            playbackThread = null;
        }
    }

    /** Toggle music on/off without killing the loop thread. Used by the UI button. */
    public void setEnabled(boolean on) {
        enabled.set(on);
        if (!on) closeCurrentPlayer(); // causes player.play() to return; loop will idle
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    // -----------------------------------------------------------------------
    //  Internal
    // -----------------------------------------------------------------------

    /** Runs on the daemon thread — opens the file, plays it, loops, or idles when muted. */
    private void streamLoop() {
        while (running.get()) {
            if (!enabled.get()) {
                sleep(100);
                continue;
            }

            File file = new File(MUSIC_PATH);
            if (!file.exists()) {
                sleep(1000);
                continue;
            }

            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                AdvancedPlayer player = new AdvancedPlayer(bis);
                synchronized (this) { currentPlayer = player; }
                player.play(); // blocks until track ends naturally or close() is called
            } catch (Exception ignored) {
                sleep(200); // brief pause on error before retrying
            } finally {
                synchronized (this) {
                    if (currentPlayer != null) { currentPlayer.close(); currentPlayer = null; }
                }
            }
            // falls through to top of while — seamless restart / loop
        }
    }

    private synchronized void closeCurrentPlayer() {
        if (currentPlayer != null) {
            currentPlayer.close();
            currentPlayer = null;
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}