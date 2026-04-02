package com.classic.preservitory.ui.screens;

import com.classic.preservitory.cache.CacheLoader;
import com.classic.preservitory.util.Constants;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

/**
 * Full-screen loading screen shown during cache download and extraction.
 *
 * Images are loaded lazily on each paint once the cache files exist on disk.
 * Falls back to plain colour/text until then.
 */
public class LoadingScreen extends JPanel {

    private static final Color TEXT_COLOR = new Color(220, 200, 120);
    private static final Color BAR_BG     = new Color(20, 20, 20);
    private static final Color BAR_FILL   = new Color(60, 120, 60);
    private static final Color BAR_BORDER = new Color(80, 80, 80);

    private static final int BAR_W = 400;
    private static final int BAR_H = 18;

    private int    percent = 0;
    private String status  = "Checking cache...";

    private BufferedImage background;
    private BufferedImage logo;

    public LoadingScreen() {
        setPreferredSize(new Dimension(Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT));
        setBackground(Color.BLACK);
    }

    public void setProgress(int percent, String status) {
        this.percent = Math.min(100, Math.max(0, percent));
        this.status  = status;
        repaint();
    }

    // -----------------------------------------------------------------------
    //  Paint
    // -----------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        // Try to pick up images as soon as the cache lands on disk
        if (background == null) background = loadFromCache("sprites/login_screen/background.png");
        if (logo == null) logo = loadFromCache("sprites/login_screen/logo.png");

        int w = getWidth();
        int h = getHeight();

        drawBackground(g2, w, h);
        drawLogo(g2, w, h);
        drawProgressBar(g2, w, h);
        drawStatus(g2, w, h);
    }

    // -----------------------------------------------------------------------
    //  Sections
    // -----------------------------------------------------------------------

    private void drawBackground(Graphics2D g, int w, int h) {
        if (background != null) {
            g.drawImage(background, 0, 0, w, h, null);
        } else {
            g.setColor(new Color(5, 10, 18));
            g.fillRect(0, 0, w, h);
        }
    }

    private void drawLogo(Graphics2D g, int w, int h) {
        if (logo != null) {
            int maxW = w / 2;
            int maxH = h / 3;
            double scale = Math.min((double) maxW / logo.getWidth(),
                                    (double) maxH / logo.getHeight());
            int dw = (int) (logo.getWidth()  * scale);
            int dh = (int) (logo.getHeight() * scale);
            int dx = (w - dw) / 2;
            int dy = h / 2 - dh - 30;
            g.drawImage(logo, dx, dy, dw, dh, null);
        } else {
            g.setFont(new Font("Serif", Font.BOLD, 42));
            FontMetrics fm = g.getFontMetrics();
            String title = Constants.GAME_NAME;
            int tx = (w - fm.stringWidth(title)) / 2;
            int ty = h / 2 - 50;
            g.setColor(new Color(30, 30, 30));
            g.drawString(title, tx + 2, ty + 2);
            g.setColor(TEXT_COLOR);
            g.drawString(title, tx, ty);
        }
    }

    private void drawProgressBar(Graphics2D g, int w, int h) {
        int barX = (w - BAR_W) / 2;
        int barY = h / 2 + 20;

        g.setColor(BAR_BG);
        g.fillRect(barX, barY, BAR_W, BAR_H);

        if (percent > 0) {
            g.setColor(BAR_FILL);
            g.fillRect(barX, barY, BAR_W * percent / 100, BAR_H);
        }

        g.setColor(BAR_BORDER);
        g.drawRect(barX, barY, BAR_W, BAR_H);

        g.setFont(new Font("Arial", Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        String pct = percent + "%";
        g.setColor(TEXT_COLOR);
        g.drawString(pct, (w - fm.stringWidth(pct)) / 2, barY + BAR_H - 3);
    }

    private void drawStatus(Graphics2D g, int w, int h) {
        if (status == null || status.isEmpty()) return;
        g.setFont(new Font("Arial", Font.PLAIN, 11));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(TEXT_COLOR);
        g.drawString(status, (w - fm.stringWidth(status)) / 2, h / 2 + 20 + BAR_H + 18);
    }

    // -----------------------------------------------------------------------
    //  Image loading
    // -----------------------------------------------------------------------

    private static BufferedImage loadFromCache(String path) {
        try {
            byte[] data = CacheLoader.getFile(path);
            if (data == null) return null;
            return ImageIO.read(new ByteArrayInputStream(data));
        } catch (Exception e) {
            return null;
        }
    }
}
