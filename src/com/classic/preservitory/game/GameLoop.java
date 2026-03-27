package com.classic.preservitory.game;

import com.classic.preservitory.ui.panels.GamePanel;
import com.classic.preservitory.util.Constants;

/**
 * Drives the update/render cycle at a fixed target FPS.
 *
 * Uses System.nanoTime() to calculate delta time (seconds since last frame)
 * so movement speed stays consistent regardless of actual frame rate.
 *
 * How it works:
 *   1. Record when each frame starts.
 *   2. Ask the panel to update game state (passing deltaTime).
 *   3. Ask the panel to repaint (render).
 *   4. Sleep for whatever time remains in the frame budget.
 */
public class GameLoop implements Runnable {

    /** Nanoseconds allowed per frame at the target FPS. */
    private static final long FRAME_BUDGET_NS = 1_000_000_000L / Constants.FPS;

    private final GamePanel panel;
    private volatile boolean running;
    private Thread thread;

    public GameLoop(GamePanel panel) {
        this.panel = panel;
    }

    /** Start the game loop on a new background thread. */
    public void start() {
        running = true;
        thread  = new Thread(this, "game-loop");
        thread.setDaemon(true); // Killed automatically when the JVM exits
        thread.start();
    }

    /** Request a clean stop. The loop finishes its current frame first. */
    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        long previousTime = System.nanoTime();

        while (running) {
            long currentTime = System.nanoTime();
            double deltaTime = (currentTime - previousTime) / 1_000_000_000.0;
            previousTime = currentTime;

            // Cap deltaTime to avoid a huge jump if the thread was paused
            if (deltaTime > 0.05) {
                deltaTime = 0.05;
            }

            panel.update(deltaTime);
            panel.repaint();

            // Sleep for the remainder of this frame's budget
            long elapsed   = System.nanoTime() - currentTime;
            long sleepMs   = (FRAME_BUDGET_NS - elapsed) / 1_000_000;
            if (sleepMs > 1) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public boolean isRunning() { return running; }
}
