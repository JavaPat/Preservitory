package com.classic.preservitory.ui;

import java.awt.Color;

/**
 * A short-lived text label (damage number, XP gain, etc.) that floats upward
 * and fades out over ~1.2 seconds.
 *
 * Coordinates are stored in isometric screen space so they sit correctly
 * inside the camera-translated drawing context in GamePanel.
 *
 * Usage:
 *   FloatingText ft = new FloatingText(isoX, isoY, "+25 WC XP", COLOR_SKILL);
 *   ft.tick(deltaTime);   // each frame
 *   if (ft.isDone()) remove it;
 */
public class FloatingText {

    // -----------------------------------------------------------------------
    //  Predefined colours
    // -----------------------------------------------------------------------

    public static final Color COLOR_DAMAGE_PLAYER = new Color(255,  80,  80);  // red   — damage taken
    public static final Color COLOR_DAMAGE_ENEMY  = new Color(255, 220,  50);  // gold  — damage dealt
    public static final Color COLOR_MISS          = new Color(160, 160, 160);  // grey  — miss / 0 damage
    public static final Color COLOR_SKILL         = new Color(120, 230, 120);  // green — XP gained
    public static final Color COLOR_LEVEL         = new Color(255, 255,  80);  // bright yellow — level up

    // -----------------------------------------------------------------------
    //  Fields  (package-visible so GamePanel can read them without getters)
    // -----------------------------------------------------------------------

    double       x, y;
    final String text;
    final Color  color;
    float        alpha;

    private double timer;

    private static final double LIFETIME    = 1.2;
    private static final double FLOAT_SPEED = 32.0;

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public FloatingText(double x, double y, String text, Color color) {
        this.x     = x;
        this.y     = y;
        this.text  = text;
        this.color = color;
        this.alpha = 1.0f;
        this.timer = LIFETIME;
    }

    // -----------------------------------------------------------------------
    //  Per-frame update
    // -----------------------------------------------------------------------

    /** Advance the animation by {@code dt} seconds. */
    public void tick(double dt) {
        timer -= dt;
        y     -= FLOAT_SPEED * dt;
        alpha  = (float) Math.max(0, timer / LIFETIME);
    }

    /** Returns true when fully faded — safe to remove from the list. */
    public boolean isDone() { return timer <= 0; }
}
