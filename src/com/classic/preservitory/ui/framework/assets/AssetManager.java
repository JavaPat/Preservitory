package com.classic.preservitory.ui.framework.assets;

import com.classic.preservitory.cache.CacheLoader;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

public class AssetManager {

    private static final Map<String, BufferedImage> images = new HashMap<>();

    public static void load() {
        //loading and login screen.
        loadImage("login_bg", "sprites/login_screen/background.png");
        loadImage("logo", "sprites/login_screen/logo.png");
        loadImage("login_box", "sprites/login_screen/box.png");
        loadImage("login_button", "sprites/login_screen/button.png");
        loadImage("mute", "sprites/login_screen/mute.png");
        loadImage("unmute", "sprites/login_screen/unmute.png");
        // Tab bar icons (24x24 PNG with transparency)
        loadImage("tab_combat",    "sprites/inventory/combat_tab.png");
        loadImage("tab_inventory", "sprites/inventory/inventory_tab.png");
        loadImage("tab_skills",    "sprites/inventory/skills_tab.png");
        loadImage("tab_equipment", "sprites/inventory/equipment_tab.png");
        loadImage("tab_quests",    "sprites/inventory/quest_tab.png");

        //shop window.
        loadImage("shop_window", "sprites/shop/window.png");
        loadImage("close", "sprites/shop/close_button.png");
        loadImage("close_hover", "sprites/shop/close_button_hover.png");
        loadImage("grid_slot", "sprites/shop/grid_button.png");

        //tree's
        loadImage("tree", "objects/trees/tree.png");
        loadImage("oak_tree", "objects/trees/oak.png");
        loadImage("willow_tree", "objects/trees/willow.png");
        loadImage("maple_tree", "objects/trees/maple.png");
        loadImage("yew_tree", "objects/trees/yew.png");
        //rocks
        loadImage("tin_rock", "objects/rocks/tin_rocks.png");
        loadImage("copper_rock", "objects/rocks/copper_rocks.png");
        loadImage("iron_rock", "objects/rocks/iron_rocks.png");
        loadImage("gold_rock", "objects/rocks/gold_rocks.png");
        loadImage("mithril_rock", "objects/rocks/mithril_rocks.png");
        loadImage("adamant_rock", "objects/rocks/adamant_rocks.png");
        loadImage("runite_rock", "objects/rocks/runite_rocks.png");
    }

    private static void loadImage(String key, String path) {
        try {
            byte[] data = CacheLoader.getFile(path);
            if (data == null) return;

            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            images.put(key, img);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static BufferedImage getImage(String key) {
        return images.get(key);
    }
}
