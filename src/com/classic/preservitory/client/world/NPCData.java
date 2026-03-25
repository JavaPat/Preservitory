package com.classic.preservitory.client.world;

/**
 * Typed client-side transfer object for a single NPC received from the server.
 *
 * Parsed from the {@code NPCS} protocol message by {@code ClientConnection}
 * and consumed by {@code ClientWorld.updateNpcs()}.
 */
public final class NPCData {

    public final int     x;
    public final int     y;
    public final String  name;
    public final boolean shopkeeper;

    public NPCData(int x, int y, String name, boolean shopkeeper) {
        this.x          = x;
        this.y          = y;
        this.name       = name;
        this.shopkeeper = shopkeeper;
    }
}
