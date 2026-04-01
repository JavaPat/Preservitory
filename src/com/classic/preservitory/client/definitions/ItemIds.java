package com.classic.preservitory.client.definitions;

/**
 * Compile-time constants for all item IDs (mirrors the server-side ItemIds).
 * Each constant matches the {@code "id"} field in {@code cache/items/*.json}.
 */
public final class ItemIds {

    private ItemIds() {}

    public static final int COINS          = 1;
    public static final int LOGS           = 2;
    public static final int ORE            = 3;
    public static final int BRONZE_AXE     = 100;
    public static final int BRONZE_PICKAXE = 102;
    public static final int LOBSTER        = 103;
    public static final int BRONZE_SWORD   = 104;
    public static final int BRONZE_HELMET  = 105;
    public static final int IRON_SWORD     = 106;
    public static final int IRON_HELMET    = 107;
    public static final int STEEL_SWORD    = 108;
}
