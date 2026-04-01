package com.classic.preservitory.client.world;

/** Lightweight DTO carrying one ground-loot item received from the server. */
public class LootData {

    public final String id;       // unique loot instance ID
    public final int    x;
    public final int    y;
    public final int    itemId;   // references ItemDefinition
    public final int    count;

    public LootData(String id, int x, int y, int itemId, int count) {
        this.id     = id;
        this.x      = x;
        this.y      = y;
        this.itemId = itemId;
        this.count  = count;
    }
}
