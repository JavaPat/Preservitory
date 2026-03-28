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
        loadImage("login_bg", "sprites/login_screen/background.png");
        loadImage("logo", "sprites/login_screen/logo.png");
        loadImage("login_box", "sprites/login_screen/box.png");
        loadImage("login_button", "sprites/login_screen/button.png");
        loadImage("mute", "sprites/login_screen/mute.png");
        loadImage("unmute", "sprites/login_screen/unmute.png");
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
