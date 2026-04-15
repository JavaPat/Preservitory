package com.classic.preservitory.ui.framework.assets;

/**
 * Holds the shared walk and attack animations used as fallback by all NPCs and enemies.
 *
 * Animations are loaded from the packed cache under the {@code player/} prefix,
 * making the player walk/attack sprites available as shared fallback frames for
 * any NPC or enemy that has no animations of its own.
 *
 * When an NPC or enemy has no walk/attack frames of its own, their
 * {@link EntitySpriteManager} delegates to this shared instance automatically.
 *
 * Adding a new NPC or enemy requires only 4 rotation (idle) sprites —
 * walk and attack animations are inherited from here.
 */
public final class SharedSpriteManager {

    private static final EntitySpriteManager INSTANCE = new EntitySpriteManager("player", "");

    public static EntitySpriteManager get() {
        return INSTANCE;
    }

    private SharedSpriteManager() {}
}
