package com.classic.preservitory.entity;

import java.awt.Graphics;

/**
 * Base class for every game object that exists in the world.
 * Stores a position (double for smooth sub-pixel movement), a size,
 * and shared animation state used by {@link AnimationStrategy} implementations.
 *
 * Subclasses must implement render() to draw themselves.
 */
public abstract class Entity {

    protected double x;
    protected double y;
    protected int    width;
    protected int    height;

    // -----------------------------------------------------------------------
    //  Shared animation state — read/written by AnimationStrategy
    // -----------------------------------------------------------------------

    /** Server-authoritative facing direction: "north", "south", "east", or "west". */
    protected String  direction = "south";

    /** True while the server reports this entity as mid-step. */
    protected boolean isMoving  = false;

    /**
     * Current visual animation state, set by {@link AnimationController#update} each frame.
     * Determines which rendering offset is applied: bob (WALK), lunge (ATTACK), none (IDLE).
     */
    protected AnimationState animationState = AnimationState.IDLE;

    /**
     * Raw render-frame counter advanced by {@link AnimationStrategy#update} each
     * frame while {@link #isMoving} is true, reset to 0 when the entity stops.
     * Used by {@link AnimationController} to drive the procedural walk bob.
     */
    protected int walkTick = 0;

    /** True while a {@link DefaultAttackAnimation} cycle is in progress. */
    protected boolean attacking  = false;

    /**
     * Tick counter advanced by {@link DefaultAttackAnimation#update} each frame
     * while {@link #attacking} is true.  Resets to 0 at the end of each cycle.
     */
    protected int     attackTick = 0;

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public Entity(double x, double y, int width, int height) {
        this.x      = x;
        this.y      = y;
        this.width  = width;
        this.height = height;
    }

    /**
     * Draw this entity onto the given Graphics context.
     */
    public abstract void render(Graphics g);

    // -----------------------------------------------------------------------
    //  Getters & setters
    // -----------------------------------------------------------------------

    public double getX()         { return x; }
    public double getY()         { return y; }
    public int    getWidth()     { return width; }
    public int    getHeight()    { return height; }

    public void setX(double x)   { this.x = x; }
    public void setY(double y)   { this.y = y; }
    public void setWidth(int w)  { this.width  = w; }
    public void setHeight(int h) { this.height = h; }

    /** Convenience: returns the horizontal center of this entity. */
    public double getCenterX() { return x + width  / 2.0; }

    /** Convenience: returns the vertical center of this entity. */
    public double getCenterY() { return y + height / 2.0; }

    /**
     * Current facing direction.  Subclasses that manage direction themselves
     * (e.g. {@link Player}) override this method.
     */
    public String getDirection() { return direction; }

    /**
     * Begin a {@link DefaultAttackAnimation} cycle.
     * Resets {@link #attackTick} to 0 and sets {@link #attacking} to true.
     * Safe to call mid-animation — restarts from tick 0.
     */
    public void startAttack() {
        this.attacking  = true;
        this.attackTick = 0;
    }
}
