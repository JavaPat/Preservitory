package com.classic.preservitory.ui.panels;

import java.util.Collections;
import java.util.List;

/**
 * Central registry of all known prayers.
 *
 * This is the single source of truth for prayer metadata on the client.
 * Adding a new prayer means adding one entry here — no other class needs editing.
 *
 * Ordering matters: prayers are displayed in the grid in list order (row-major).
 */
final class PrayerDefinitionRegistry {

    static final List<PrayerDefinition> PRAYERS = Collections.unmodifiableList(List.of(
        new PrayerDefinition("thick_skin",           "Thick Skin",           1,  "+5% Defence bonus"),
        new PrayerDefinition("burst_of_strength",    "Burst of Strength",    4,  "+5% Strength bonus"),
        new PrayerDefinition("clarity_of_thought",   "Clarity of Thought",   7,  "+5% Attack bonus"),
        new PrayerDefinition("rock_skin",            "Rock Skin",           10,  "+10% Defence bonus"),
        new PrayerDefinition("superhuman_strength",  "Superhuman Strength", 13,  "+10% Strength bonus"),
        new PrayerDefinition("improved_reflexes",    "Improved Reflexes",   16,  "+10% Attack bonus"),
        new PrayerDefinition("rapid_restore",        "Rapid Restore",       19,  "2x restore rate"),
        new PrayerDefinition("rapid_heal",           "Rapid Heal",          22,  "2x natural heal rate"),
        new PrayerDefinition("protect_item",         "Protect Item",        25,  "Keep extra item on death"),
        new PrayerDefinition("steel_skin",           "Steel Skin",          28,  "+15% Defence bonus"),
        new PrayerDefinition("ultimate_strength",    "Ultimate Strength",   31,  "+15% Strength bonus"),
        new PrayerDefinition("incredible_reflexes",  "Incredible Reflexes", 34,  "+15% Attack bonus"),
        new PrayerDefinition("protect_from_magic",   "Protect from Magic",  37,  "Reduces magic damage"),
        new PrayerDefinition("protect_from_missiles","Protect from Missiles",40, "Reduces ranged damage"),
        new PrayerDefinition("protect_from_melee",   "Protect from Melee",  43,  "Reduces melee damage")
    ));

    private PrayerDefinitionRegistry() {}
}
