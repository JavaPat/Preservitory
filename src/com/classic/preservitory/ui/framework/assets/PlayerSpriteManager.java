package com.classic.preservitory.ui.framework.assets;

import java.awt.Graphics2D;
import java.util.logging.Logger;

/**
 * Static facade over {@link EntitySpriteManager} for the local player.
 *
 * Loads from the packed sprite cache under the {@code player/} prefix.
 * All rendering is delegated to {@link EntitySpriteManager}, so the sprite format
 * and walk-animation logic are identical across player, NPC, and enemy.
 */
public final class PlayerSpriteManager {

    private static final Logger LOGGER = Logger.getLogger(PlayerSpriteManager.class.getName());

    private static EntitySpriteManager delegate;
    private static volatile boolean    loaded;

    private PlayerSpriteManager() {}

    // -----------------------------------------------------------------------
    //  Initialisation
    // -----------------------------------------------------------------------

    public static synchronized void load() {
        if (loaded) return;
        delegate = new EntitySpriteManager("player", "");
        loaded   = delegate.isLoaded();
        if (!loaded) {
            LOGGER.warning("[PlayerSprites] Sprite pack not found — falling back to legacy renderer.");
        }
    }

    public static boolean isLoaded() { return loaded; }

    // -----------------------------------------------------------------------
    //  Rendering — delegate to EntitySpriteManager
    // -----------------------------------------------------------------------

    public static void drawPlayer(Graphics2D g2, int footX, int footY,
                                  String direction, boolean moving, double animationTimerSeconds) {
        if (delegate == null) return;
        delegate.draw(g2, footX, footY, direction, moving, animationTimerSeconds);
    }

    public static void drawPlayerAction(Graphics2D g2, int footX, int footY,
                                        String animName, String direction, int frameIndex) {
        if (delegate == null) return;
        delegate.drawAction(g2, footX, footY, animName, direction, frameIndex);
    }

    public static int getAttackFrameCount(String animName, String direction) {
        if (delegate == null) return 0;
        return delegate.getActionFrameCount(animName, direction);
    }
}
