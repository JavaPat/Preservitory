package com.classic.preservitory.ui.panels;

/**
 * Immutable data record for a single prayer.
 *
 * All prayers are registered in {@link PrayerDefinitionRegistry}.
 * The client never calculates prayer effects — that is the server's job.
 * This class exists purely for UI display (icon, name, tooltip, level gate).
 */
final class PrayerDefinition {

    /** Unique string identifier — matches what the server expects in TOGGLE_PRAYER. */
    final String id;
    /** Display name shown in tooltip. */
    final String name;
    /** Minimum prayer level required to use this prayer. */
    final int levelRequired;
    /** Short description shown in tooltip. */
    final String description;
    /**
     * Sprite key used for the icon.
     * Derived as {@code "prayer_icons/" + id} so sprite packing just needs matching names.
     */
    final String spriteKey;

    PrayerDefinition(String id, String name, int levelRequired, String description) {
        this.id            = id;
        this.name          = name;
        this.levelRequired = levelRequired;
        this.description   = description;
        this.spriteKey     = "prayer_icons/" + id;
    }
}
