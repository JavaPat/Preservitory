package com.classic.preservitory.item;

/**
 * One item stack in the player's inventory.
 *
 * Authoritative identifier: {@link #itemId} (int).
 * Display name: {@link #name} — populated from ItemDefinitionManager on receipt.
 *
 * Stackable items share a slot; non-stackable items each occupy their own slot.
 */
public class Item {

    private final int     itemId;
    private final String  name;
    private final boolean stackable;
    private       int     count;

    public Item(int itemId, String name, boolean stackable) {
        this.itemId    = itemId;
        this.name      = name;
        this.stackable = stackable;
        this.count     = 1;
    }

    public void incrementCount()      { count++;                           }
    public void addCount(int amount)  { count += amount;                   }
    public void setCount(int count)   { this.count = Math.max(0, count);   }

    public int     getItemId()    { return itemId;    }
    public String  getName()      { return name;      }
    public boolean isStackable()  { return stackable; }
    public int     getCount()     { return count;     }
}
