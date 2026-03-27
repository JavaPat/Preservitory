package com.classic.preservitory.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A fixed-size bag of items, modelled after RuneScape's 28-slot inventory
 * (we use 20 here for a tighter UI).
 *
 * Rules:
 *   - Stackable items share a single slot; only the count grows.
 *   - Non-stackable items each take a new slot.
 *   - When all slots are filled, addItem() returns false.
 */
public class Inventory {

    public static final int MAX_SLOTS = 20;

    private final List<Item> slots = new ArrayList<>();

    /**
     * Try to add an item.
     *
     * @return true  if the item was added (or stacked)
     *         false if the inventory is full and no existing stack was found
     */
    public boolean addItem(Item item) {
        // Stackable: merge the full count into any existing stack of the same name
        if (item.isStackable()) {
            for (Item existing : slots) {
                if (existing.getName().equals(item.getName())) {
                    existing.addCount(item.getCount()); // handles qty > 1 (e.g. loot drops)
                    return true;
                }
            }
        }

        // New slot needed — check if there is room
        if (slots.size() >= MAX_SLOTS) {
            return false;
        }

        slots.add(item);
        return true;
    }

    /**
     * Total count of all items with the given name across all slots.
     * For stackable items this is the stack count; for non-stackable it is
     * the number of occupied slots (each has count 1).
     */
    public int countOf(String name) {
        int total = 0;
        for (Item item : slots) {
            if (item.getName().equals(name)) total += item.getCount();
        }
        return total;
    }

    /**
     * Remove up to {@code amount} units of the named item.
     * For stackable stacks the count is reduced; the slot is removed when it
     * reaches zero.  For non-stackable items, individual slots are removed.
     */
    public void removeItem(String name, int amount) {
        for (int i = 0; i < slots.size() && amount > 0; i++) {
            Item item = slots.get(i);
            if (!item.getName().equals(name)) continue;

            if (item.isStackable()) {
                int take = Math.min(amount, item.getCount());
                item.setCount(item.getCount() - take);
                amount -= take;
                if (item.getCount() <= 0) { slots.remove(i); i--; }
            } else {
                slots.remove(i);
                i--;
                amount--;
            }
        }
    }

    /** Remove all items so a fresh authoritative inventory snapshot can be applied. */
    public void clear() {
        slots.clear();
    }

    /** Unmodifiable view of occupied slots (one entry per inventory slot). */
    public List<Item> getSlots() {
        return Collections.unmodifiableList(slots);
    }

    public int     getSlotCount() { return slots.size(); }
    public boolean isFull()       { return slots.size() >= MAX_SLOTS; }
}
