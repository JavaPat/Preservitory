package com.classic.preservitory.system;

import com.classic.preservitory.item.Inventory;
import com.classic.preservitory.item.Item;

import java.util.*;

/**
 * The Guide's shop.
 *
 * BUY  — items available for purchase with Coins.
 * SELL — items the player can sell for Coins (Logs, Ore).
 *
 * All transactions operate directly on the player's Inventory.
 */
public class ShopSystem {

    // -----------------------------------------------------------------------
    //  Data
    // -----------------------------------------------------------------------

    public static class ShopEntry {
        public final String  name;
        public final boolean stackable;
        public final int     buyPrice;

        ShopEntry(String name, boolean stackable, int buyPrice) {
            this.name      = name;
            this.stackable = stackable;
            this.buyPrice  = buyPrice;
        }
    }

    private final List<ShopEntry>       stock;
    private final Map<String, Integer>  sellPrices;

    public ShopSystem() {
        stock      = new ArrayList<>();
        sellPrices = new LinkedHashMap<>();

        // Items for sale
        stock.add(new ShopEntry("Candle", true,  5));
        stock.add(new ShopEntry("Rope",   true, 10));
        stock.add(new ShopEntry("Gem",    true, 100));

        // Player items → Coins
        sellPrices.put("Logs", 2);
        sellPrices.put("Ore",  3);
    }

    // -----------------------------------------------------------------------
    //  Transactions
    // -----------------------------------------------------------------------

    /**
     * Buy one unit of {@code name} from the shop.
     *
     * @return null on success, or a human-readable error string.
     */
    public String buyItem(String name, Inventory inventory) {
        ShopEntry entry = findEntry(name);
        if (entry == null) return "That item is not in stock.";

        if (inventory.countOf("Coins") < entry.buyPrice) {
            return "Not enough Coins! (need " + entry.buyPrice + ")";
        }

        inventory.removeItem("Coins", entry.buyPrice);
        inventory.addItem(new Item(entry.name, entry.stackable));
        return null;
    }

    /**
     * Sell one unit of {@code name} from the player's inventory.
     *
     * @return null on success, or a human-readable error string.
     */
    public String sellItem(String name, Inventory inventory) {
        Integer price = sellPrices.get(name);
        if (price == null) return "That item has no sell value.";
        if (inventory.countOf(name) <= 0) return "You don't have that item.";

        inventory.removeItem(name, 1);

        Item coins = new Item("Coins", true);
        coins.addCount(price - 1); // Item starts at count 1; add the remainder
        inventory.addItem(coins);
        return null;
    }

    // -----------------------------------------------------------------------
    //  Getters
    // -----------------------------------------------------------------------

    public List<ShopEntry>      getStock()      { return Collections.unmodifiableList(stock); }
    public Map<String, Integer> getSellPrices() { return Collections.unmodifiableMap(sellPrices); }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    private ShopEntry findEntry(String name) {
        for (ShopEntry e : stock) {
            if (e.name.equals(name)) return e;
        }
        return null;
    }
}
