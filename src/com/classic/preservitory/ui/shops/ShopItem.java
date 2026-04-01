package com.classic.preservitory.ui.shops;

/**
 * One item offered for purchase in a shop.
 * Immutable — constructed from the server's SHOP packet.
 *
 * Future fields (add here, not in ShopWindow):
 *   String iconKey   — AssetManager key for the item sprite
 *   String tooltip   — description shown on hover
 */
public final class ShopItem {

    /** Item identifier sent back to the server in BUY requests. */
    public final int itemId;

    public final int price;

    /** Available stock. -1 means unlimited. */
    public final int stock;

    public ShopItem(int itemId, int price, int stock) {
        this.itemId = itemId;
        this.price  = price;
        this.stock  = stock;
    }

    /** Display string for the stock count. */
    public String stockLabel() {
        return stock < 0 ? "\u221e" : String.valueOf(stock);  // ∞ or number
    }
}
