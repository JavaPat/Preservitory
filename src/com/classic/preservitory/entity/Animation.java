package com.classic.preservitory.entity;

/**
 * Lightweight animation controller for a single entity.
 *
 * No sprite images are needed — the state and timer are used by the entity's
 * {@code render()} method to drive colour shifts, bobs, and tool-flash effects.
 *
 * Usage:
 *   animation.setState(Animation.State.WALKING);
 *   animation.tick(deltaTime);            // call once per frame
 *   double bob = animation.pulse(8);      // 0‥1 oscillation at 8 Hz
 */
public class Animation {

    // -----------------------------------------------------------------------
    //  Animation states
    // -----------------------------------------------------------------------

    /**
     * All possible visual states.
     * GamePanel sets the appropriate state each frame based on active systems.
     */
    public enum State {
        IDLE,
        WALKING,
        CHOPPING,
        MINING,
        FIGHTING
    }

    // -----------------------------------------------------------------------
    //  Fields
    // -----------------------------------------------------------------------

    private State  state = State.IDLE;
    private double timer = 0.0;   // seconds since the last state change

    // -----------------------------------------------------------------------
    //  State management
    // -----------------------------------------------------------------------

    /**
     * Change to a new state.  The timer resets to 0 on any state transition
     * so each animation always starts from its beginning.
     */
    public void setState(State newState) {
        if (this.state != newState) {
            this.state = newState;
            this.timer = 0.0;
        }
    }

    /**
     * Advance the internal timer by {@code deltaTime} seconds.
     * Call this once per frame.
     */
    public void tick(double deltaTime) {
        timer += deltaTime;
    }

    // -----------------------------------------------------------------------
    //  Query helpers
    // -----------------------------------------------------------------------

    /** Current animation state. */
    public State getState() { return state; }

    /** Seconds elapsed since the current state started. */
    public double getTimer() { return timer; }

    /**
     * A smooth sine-wave oscillation in the range [0, 1] at the given frequency.
     *
     * @param freqHz cycles per second  (e.g. 8 for a quick walking bob)
     * @return value in [0, 1]
     */
    public double pulse(double freqHz) {
        return 0.5 + 0.5 * Math.sin(2 * Math.PI * freqHz * timer);
    }

    /**
     * Same as {@link #pulse} but returns the raw sine value in [-1, 1].
     * Useful when you need both positive and negative displacement.
     */
    public double sin(double freqHz) {
        return Math.sin(2 * Math.PI * freqHz * timer);
    }
}
