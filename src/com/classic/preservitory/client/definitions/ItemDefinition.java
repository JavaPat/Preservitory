package com.classic.preservitory.client.definitions;

/**
 * Immutable client-side definition of a single item.
 *
 * The client loads these from {@code cache/items/*.json} at startup.
 * Item names, stackability, and display properties are looked up here —
 * the server never sends item names over the wire.
 */
public final class ItemDefinition {

    public final int     id;
    public final String  name;
    public final int     value;
    public final boolean stackable;
    public final boolean tradable;
    /** Equip slot name (e.g. "WEAPON", "HELMET"), or null if not equippable. */
    public final String  equipSlot;
    public final int     attackBonus;
    public final int     strengthBonus;

    /** Sentinel id used by {@link #UNKNOWN} — never a valid loaded id. */
    public static final int INVALID_ID = -1;

    /** Returned for any unrecognised item ID. */
    public static final ItemDefinition UNKNOWN =
            new ItemDefinition(INVALID_ID, "Unknown", 0, false, false, null, 0, 0);

    public ItemDefinition(int id, String name, int value, boolean stackable, boolean tradable,
                          String equipSlot, int attackBonus, int strengthBonus) {
        this.id            = id;
        this.name          = name;
        this.value         = value;
        this.stackable     = stackable;
        this.tradable      = tradable;
        this.equipSlot     = equipSlot;
        this.attackBonus   = attackBonus;
        this.strengthBonus = strengthBonus;
    }

    public ItemDefinition(int id, String name, int value, boolean stackable, boolean tradable) {
        this(id, name, value, stackable, tradable, null, 0, 0);
    }
}
