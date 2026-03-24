package com.classic.preservitory.item;

/**
 * Represents one kind of item.
 *
 * Stackable items (like Logs) share a single inventory slot and track
 * how many are in the stack via a count.
 *
 * Non-stackable items (armour, tools, etc.) each occupy their own slot
 * with count always equal to 1.
 */
public class Item {

    private final String  name;
    private final boolean stackable;
    private       int     count;

    public Item(String name, boolean stackable) {
        this.name      = name;
        this.stackable = stackable;
        this.count     = 1;
    }

    /**
     * Increase this stack by one.
     * Only meaningful when stackable == true.
     */
    public void incrementCount() {
        count++;
    }

    /**
     * Increase this stack by the given amount.
     * Used when merging a multi-count loot drop into an existing stack.
     */
    public void addCount(int amount) {
        count += amount;
    }

    /**
     * Set the count directly — used when loading a save file.
     */
    public void setCount(int count) { this.count = Math.max(0, count); }

    // --- Getters ---

    public String  getName()     { return name; }
    public boolean isStackable() { return stackable; }
    public int     getCount()    { return count; }
}
