package com.classic.preservitory.client.world;

/**
 * Typed client-side transfer object for a single NPC received from the server.
 *
 * Parsed from the {@code NPCS} protocol message by {@code ClientConnection}
 * and consumed by {@code ClientWorld.updateNpcs()}.
 *
 * Wire format: {@code id x y name shopkeeper direction moving}
 */
public final class NPCData {

    public final String  id;
    public final int     x;
    public final int     y;
    public final String  name;
    public final boolean shopkeeper;
    public final String  direction;
    public final boolean moving;

    public NPCData(String id, int x, int y, String name, boolean shopkeeper,
                   String direction, boolean moving) {
        this.id         = id;
        this.x          = x;
        this.y          = y;
        this.name       = name;
        this.shopkeeper = shopkeeper;
        this.direction  = direction;
        this.moving     = moving;
    }

    /** Legacy constructor for callers that don't yet supply direction/moving. */
    public NPCData(String id, int x, int y, String name, boolean shopkeeper) {
        this(id, x, y, name, shopkeeper, "south", false);
    }
}
