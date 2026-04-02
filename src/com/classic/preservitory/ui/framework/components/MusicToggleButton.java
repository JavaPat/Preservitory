package com.classic.preservitory.ui.framework.components;

import com.classic.preservitory.system.audio.MusicManager;
import com.classic.preservitory.ui.framework.UIComponent;
import com.classic.preservitory.ui.framework.assets.AssetManager;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Image-based toggle button that mutes/unmutes background music.
 *
 * Renders the "mute" icon when music is ON (click to mute),
 * and the "unmute" icon when music is OFF (click to unmute).
 * A semi-transparent tint is drawn over the icon when hovered.
 */
public class MusicToggleButton extends UIComponent {

    private final MusicManager music;

    public MusicToggleButton(int x, int y, int size, MusicManager music) {
        super(x, y, size, size);
        this.music = music;
    }

    @Override
    public void render(Graphics2D g) {
        if (!visible) return;

        String key = music.isEnabled() ? "mute" : "unmute";
        BufferedImage icon = AssetManager.getImage(key);

        if (icon != null) {
            g.drawImage(icon, x, y, width, height, null);
        } else {
            // Fallback: plain coloured square with "M"
            g.setColor(music.isEnabled() ? new Color(60, 120, 60) : new Color(120, 60, 60));
            g.fillRect(x, y, width, height);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 11));
            g.drawString("M", x + width / 2 - 4, y + height / 2 + 4);
        }

        if (hovered) {
            g.setColor(new Color(255, 255, 255, 50));
            g.fillRect(x, y, width, height);
        }
    }

    @Override
    public void handleClick(int mouseX, int mouseY) {
        if (!visible) return;
        if (contains(mouseX, mouseY)) {
            music.setEnabled(!music.isEnabled());
        }
    }
}
