package com.classic.preservitory.client.definitions;

/**
 * Compile-time constants for all item IDs (mirrors the server-side ItemIds).
 * Each constant matches the {@code "id"} field in {@code cache/items/*.json}.
 */
public final class ItemIds {

    private ItemIds() {}

    public static final int
            COINS = 1,
            LOGS = 2,
            ORE = 3,
            COPPER_ORE = 4,

            BRONZE_AXE = 100,
            BRONZE_PICKAXE = 102,
            BRONZE_SHIELD = 103,
            BRONZE_SWORD = 104,
            BRONZE_HELMET = 105,
            IRON_SWORD = 106,
            IRON_HELMET = 107,
            STEEL_SWORD = 108,

            OAK_BOW = 200,
            MAGIC_STAFF = 201,

            RAW_LOBSTER = 300,
            COOKED_LOBSTER = 301,
            BURNT_LOBSTER = 302,
            RARE_LOBSTER = 303;
}
