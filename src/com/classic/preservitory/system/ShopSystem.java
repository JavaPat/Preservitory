package com.classic.preservitory.system;

import com.classic.preservitory.item.Inventory;
import java.util.*;

/**
 * The Guide's shop.
 *
 * BUY  — items available for purchase with Coins.
 * SELL — items the player can sell for Coins (Logs, Ore).
 *
 * Transaction resolution is server-owned. The client keeps stock/pricing data
 * only for rendering and must not mutate inventory locally.
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
        return "Shop transactions are server-authoritative and unavailable on this client.";
    }

    /**
     * Sell one unit of {@code name} from the player's inventory.
     *
     * @return null on success, or a human-readable error string.
     */
    public String sellItem(String name, Inventory inventory) {
        return "Shop transactions are server-authoritative and unavailable on this client.";
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
