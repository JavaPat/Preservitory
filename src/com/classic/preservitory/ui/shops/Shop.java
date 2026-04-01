package com.classic.preservitory.ui.shops;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Client-side snapshot of a shop sent by the server.
 * Immutable after construction — ShopWindow treats this as read-only data.
 */
public final class Shop {

    public final String id;
    public final String name;

    private final List<ShopItem>       stockItems;
    private final Map<Integer,Integer> sellPrices;   // itemId → sell price

    public Shop(String id, String name, List<ShopItem> stockItems, Map<Integer,Integer> sellPrices) {
        this.id         = id;
        this.name       = name;
        this.stockItems = Collections.unmodifiableList(stockItems);
        this.sellPrices = Collections.unmodifiableMap(sellPrices);
    }

    public List<ShopItem>       getStockItems() { return stockItems; }
    public Map<Integer,Integer> getSellPrices() { return sellPrices; }
    public boolean canSell(int itemId)          { return sellPrices.containsKey(itemId); }
}
