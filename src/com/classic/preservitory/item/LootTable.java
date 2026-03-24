package com.classic.preservitory.item;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines what items an enemy drops when killed.
 *
 * Each entry has:
 *   - item name and whether it stacks
 *   - min/max quantity
 *   - chance to drop  (0.0 = never, 1.0 = always)
 *
 * Usage:
 *   LootTable table = new LootTable();
 *   table.addEntry("Coins", true, 3, 15, 1.0);   // always drop 3-15 coins
 *   table.addEntry("Logs",  true, 1,  1, 0.25);  // 25% chance of 1 log
 *   List<Item> drops = table.rollLoot();
 */
public class LootTable {

    /** One possible drop. */
    private static class LootEntry {
        final String  name;
        final boolean stackable;
        final int     minQty;
        final int     maxQty;
        final double  chance;

        LootEntry(String name, boolean stackable, int minQty, int maxQty, double chance) {
            this.name      = name;
            this.stackable = stackable;
            this.minQty    = minQty;
            this.maxQty    = maxQty;
            this.chance    = chance;
        }
    }

    private final List<LootEntry> entries = new ArrayList<>();

    /**
     * Register a potential drop.
     *
     * @param name       item name (must match the name used elsewhere)
     * @param stackable  whether this item stacks in one slot
     * @param minQty     minimum quantity to award (inclusive, >= 1)
     * @param maxQty     maximum quantity to award (inclusive)
     * @param chance     0.0–1.0 probability of this entry dropping
     */
    public void addEntry(String name, boolean stackable, int minQty, int maxQty, double chance) {
        entries.add(new LootEntry(name, stackable, minQty, maxQty, chance));
    }

    /**
     * Roll every entry and return the items that drop this kill.
     * For stackable items the full quantity is packed into one Item.
     *
     * @return a list of dropped items (may be empty)
     */
    public List<Item> rollLoot() {
        List<Item> drops = new ArrayList<>();

        for (LootEntry entry : entries) {
            if (Math.random() <= entry.chance) {
                int qty = entry.minQty
                        + (int)(Math.random() * (entry.maxQty - entry.minQty + 1));

                Item item = new Item(entry.name, entry.stackable);
                // count starts at 1; increment qty-1 more times
                for (int i = 1; i < qty; i++) {
                    item.incrementCount();
                }
                drops.add(item);
            }
        }

        return drops;
    }
}
