package com.classic.preservitory.item;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Fixed-size slot-based player inventory (28 slots, OSRS-style).
 *
 * <p>Backed by a {@code Item[28]} array so slot indices map directly to server slots.
 * Null entries represent empty slots.</p>
 *
 * <p>Populated exclusively via {@link #setSlot(int, Item)} from the server's
 * {@code INVENTORY_UPDATE} snapshot. {@link #addItem(Item)} is kept for
 * cases where exact slot placement is not required (e.g. pickup prediction).</p>
 */
public class Inventory {

    public static final int MAX_SLOTS = 28;

    private final Item[] slots = new Item[MAX_SLOTS];

    // -----------------------------------------------------------------------
    //  Slot-level access
    // -----------------------------------------------------------------------

    /**
     * Place {@code item} directly into slot {@code index}, overwriting whatever was there.
     * Ignores out-of-bounds indices silently.
     */
    public void setSlot(int index, Item item) {
        if (index >= 0 && index < MAX_SLOTS) slots[index] = item;
    }

    /** Empty slot {@code index}. Ignores out-of-bounds indices silently. */
    public void clearSlot(int index) {
        if (index >= 0 && index < MAX_SLOTS) slots[index] = null;
    }

    // -----------------------------------------------------------------------
    //  Collection operations
    // -----------------------------------------------------------------------

    /**
     * Add an item to the first available slot, merging stackable items.
     *
     * @return {@code true} if the item was added; {@code false} if all slots are occupied.
     */
    public boolean addItem(Item item) {
        if (item.isStackable()) {
            for (int i = 0; i < MAX_SLOTS; i++) {
                if (slots[i] != null && slots[i].getItemId() == item.getItemId()) {
                    slots[i].addCount(item.getCount());
                    return true;
                }
            }
        }
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (slots[i] == null) {
                slots[i] = item;
                return true;
            }
        }
        return false;
    }

    public void removeItem(int itemId, int amount) {
        for (int i = 0; i < MAX_SLOTS && amount > 0; i++) {
            Item item = slots[i];
            if (item == null || item.getItemId() != itemId) continue;
            if (item.isStackable()) {
                int take = Math.min(amount, item.getCount());
                item.setCount(item.getCount() - take);
                amount -= take;
                if (item.getCount() <= 0) slots[i] = null;
            } else {
                slots[i] = null;
                amount--;
            }
        }
    }

    public void clear() {
        Arrays.fill(slots, null);
    }

    // -----------------------------------------------------------------------
    //  Queries
    // -----------------------------------------------------------------------

    /** Total count of {@code itemId} across all slots. */
    public int countOf(int itemId) {
        int total = 0;
        for (Item slot : slots) {
            if (slot != null && slot.getItemId() == itemId) total += slot.getCount();
        }
        return total;
    }

    /** Number of non-empty slots. */
    public int getSlotCount() {
        int count = 0;
        for (Item s : slots) if (s != null) count++;
        return count;
    }

    public boolean isFull() {
        for (Item s : slots) if (s == null) return false;
        return true;
    }

    // -----------------------------------------------------------------------
    //  View
    // -----------------------------------------------------------------------

    /**
     * Unmodifiable list view of all 28 slots, indexed by position.
     * {@code null} entries are empty slots.
     */
    public List<Item> getSlots() {
        return Collections.unmodifiableList(Arrays.asList(slots));
    }
}
