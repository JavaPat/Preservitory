package com.classic.preservitory.ui.shops;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the raw SHOP packet into a {@link Shop} object.
 * All string-splitting lives here — ShopWindow never touches packet strings.
 *
 * Packet format (tab-delimited):
 *   SHOP \t shopId \t shopName \t buyItems \t sellItems
 *
 * buyItems  — comma-separated  itemId:price:stock   (stock -1 = unlimited)
 * sellItems — comma-separated  itemId:price
 */
public final class ShopParser {

    private ShopParser() {}

    /**
     * Parse a full SHOP packet line (including the "SHOP" token).
     * Returns null if malformed.
     */
    public static Shop parse(String packet) {
        String[] fields = packet.split("\t", 5);
        if (fields.length < 3) return null;

        String id   = fields[1].trim();
        String name = fields[2].trim();
        if (id.isEmpty()) return null;

        List<ShopItem>       stock = fields.length >= 4 ? parseStock(fields[3]) : new ArrayList<>();
        Map<Integer,Integer> sell  = fields.length >= 5 ? parseSell(fields[4])  : new LinkedHashMap<>();

        return new Shop(id, name.isEmpty() ? "Shop" : name, stock, sell);
    }

    private static List<ShopItem> parseStock(String payload) {
        List<ShopItem> items = new ArrayList<>();
        if (payload == null || payload.isBlank()) return items;

        for (String entry : payload.split(",")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            String[] f = entry.split(":");
            if (f.length < 2) continue;
            try {
                int itemId = Integer.parseInt(f[0].trim());
                int price  = Integer.parseInt(f[1].trim());
                int stock  = f.length >= 3 ? Integer.parseInt(f[2].trim()) : -1;
                items.add(new ShopItem(itemId, price, stock));
            } catch (NumberFormatException ignored) {}
        }
        return items;
    }

    private static Map<Integer,Integer> parseSell(String payload) {
        Map<Integer,Integer> map = new LinkedHashMap<>();
        if (payload == null || payload.isBlank()) return map;

        for (String entry : payload.split(",")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            String[] f = entry.split(":");
            if (f.length != 2) continue;
            try {
                map.put(Integer.parseInt(f[0].trim()), Integer.parseInt(f[1].trim()));
            } catch (NumberFormatException ignored) {}
        }
        return map;
    }
}
