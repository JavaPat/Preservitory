package com.classic.preservitory.entity;

import java.awt.Graphics;

/**
 * Base class for every game object that exists in the world.
 * Stores a position (double for smooth sub-pixel movement) and a size.
 * Subclasses must implement render() to draw themselves.
 */
public abstract class Entity {

    protected double x;
    protected double y;
    protected int width;
    protected int height;

    public Entity(double x, double y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width  = width;
        this.height = height;
    }

    /**
     * Draw this entity onto the given Graphics context.
     */
    public abstract void render(Graphics g);

    // --- Getters & Setters ---

    public double getX()            { return x; }
    public double getY()            { return y; }
    public int    getWidth()        { return width; }
    public int    getHeight()       { return height; }

    public void setX(double x)      { this.x = x; }
    public void setY(double y)      { this.y = y; }
    public void setWidth(int w)     { this.width  = w; }
    public void setHeight(int h)    { this.height = h; }

    /** Convenience: returns the horizontal center of this entity. */
    public double getCenterX() { return x + width  / 2.0; }

    /** Convenience: returns the vertical center of this entity. */
    public double getCenterY() { return y + height / 2.0; }
}
