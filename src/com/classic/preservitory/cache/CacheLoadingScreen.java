package com.classic.preservitory.cache;

import com.classic.preservitory.util.Constants;

import javax.swing.*;
import java.awt.*;

public class CacheLoadingScreen extends JPanel {

    private int    percent = 0;
    private String status  = "Checking cache...";

    public CacheLoadingScreen() {
        setPreferredSize(new Dimension(Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT));
        setBackground(Color.BLACK);
    }

    public void setProgress(int percent, String status) {
        this.percent = Math.min(100, Math.max(0, percent));
        this.status  = status;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Title
        g2.setFont(new Font("Serif", Font.BOLD, 42));
        FontMetrics titleFm = g2.getFontMetrics();
        g2.setColor(new Color(30, 30, 30));
        g2.drawString(Constants.GAME_NAME, (w - titleFm.stringWidth(Constants.GAME_NAME)) / 2 + 2, h / 2 - 48);
        g2.setColor(new Color(210, 175, 90));
        g2.drawString(Constants.GAME_NAME, (w - titleFm.stringWidth(Constants.GAME_NAME)) / 2, h / 2 - 50);

        // Progress bar
        int barW = 420;
        int barH = 22;
        int barX = (w - barW) / 2;
        int barY = h / 2 + 10;

        g2.setColor(new Color(30, 30, 30));
        g2.fillRect(barX - 1, barY - 1, barW + 2, barH + 2);

        g2.setColor(new Color(50, 110, 50));
        g2.fillRect(barX, barY, barW * percent / 100, barH);

        g2.setColor(new Color(80, 80, 80));
        g2.drawRect(barX, barY, barW, barH);

        // Percent label centered in bar
        g2.setFont(new Font("Arial", Font.BOLD, 13));
        FontMetrics pFm = g2.getFontMetrics();
        String pctStr = percent + "%";
        g2.setColor(Color.WHITE);
        g2.drawString(pctStr, (w - pFm.stringWidth(pctStr)) / 2, barY + barH - 5);

        // Status text below bar
        g2.setFont(new Font("Arial", Font.PLAIN, 12));
        FontMetrics sFm = g2.getFontMetrics();
        g2.setColor(new Color(160, 160, 160));
        g2.drawString(status, (w - sFm.stringWidth(status)) / 2, barY + barH + 20);
    }
}
