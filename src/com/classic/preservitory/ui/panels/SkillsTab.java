package com.classic.preservitory.ui.panels;

import com.classic.preservitory.entity.Player;
import com.classic.preservitory.entity.Skill;
import com.classic.preservitory.ui.framework.TabRenderer;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Renders the Skills tab: scrollable list of skills with XP bars.
 */
class SkillsTab implements TabRenderer {

    private static final List<String> SKILL_ORDER = Arrays.asList(
            "attack", "strength", "defence", "hitpoints");

    private static final int SCROLL_STEP = 20;

    private Player player      = null;
    private int    scrollOffset = 0;
    private int    maxScroll    = 0;

    void setPlayer(Player p) { this.player = p; }

    void resetScroll() { scrollOffset = 0; }

    @Override
    public void handleMouseWheel(int direction) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + direction * SCROLL_STEP));
    }

    @Override
    public void render(Graphics2D g, int x, int y, int width, int height) {
        if (player == null) return;

        Graphics2D contentG = (Graphics2D) g.create();
        contentG.setClip(x, y, width, height);
        contentG.translate(0, -scrollOffset);

        int bx = x + 8;
        int bw = width - 16;
        int cy = y + 10;
        List<Skill> skills = getOrderedSkills(player);

        contentG.setFont(new Font("Arial", Font.BOLD, 10));
        drawOutlined(contentG, "SKILLS", x + width / 2 - 16, cy + 2,
                new Color(200, 185, 100), new Color(0, 0, 0, 160));
        cy += 14;

        for (Skill skill : skills) {
            cy = drawSkillRow(contentG, bx, cy, bw, skill);
        }

        int contentHeight = cy - y;
        maxScroll = Math.max(0, contentHeight - height);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        contentG.dispose();

        drawScrollbar(g, x, y, width, height, contentHeight);
    }

    private int drawSkillRow(Graphics2D g, int x, int y, int bw, Skill skill) {
        g.setFont(new Font("Arial", Font.BOLD, 10));
        g.setColor(new Color(195, 185, 125));
        g.drawString(skill.getName().toUpperCase(), x, y + 11);

        g.setFont(new Font("Arial", Font.PLAIN, 10));
        g.setColor(Color.WHITE);
        String lvl = "Lv " + skill.getLevel();
        FontMetrics fm = g.getFontMetrics();
        g.drawString(lvl, x + bw - fm.stringWidth(lvl), y + 11);
        y += 14;

        g.setFont(new Font("Arial", Font.PLAIN, 9));
        g.setColor(new Color(130, 125, 90));
        int xpInLevel   = skill.getXp() - skill.xpForCurrentLevel();
        int xpThisLevel = skill.xpForNextLevel() - skill.xpForCurrentLevel();
        g.drawString(xpInLevel + " / " + xpThisLevel + " xp", x, y + 8);
        y += 10;

        g.setColor(new Color(25, 25, 25));
        g.fillRect(x, y, bw, 7);
        g.setColor(new Color(50, 170, 50));
        g.fillRect(x, y, (int)(bw * skill.getProgressFraction()), 7);
        if (skill.getProgressFraction() > 0) {
            g.setColor(new Color(90, 220, 90, 70));
            g.fillRect(x, y, (int)(bw * skill.getProgressFraction()), 3);
        }
        g.setColor(new Color(55, 55, 55));
        g.drawRect(x, y, bw, 7);

        return y + 16;
    }

    private void drawScrollbar(Graphics2D g, int x, int y, int width, int height, int contentHeight) {
        if (maxScroll <= 0) return;

        int trackX = x + width - 5;
        g.setColor(new Color(30, 24, 14));
        g.fillRect(trackX, y, 4, height);

        int thumbH = Math.max(18, height * height / contentHeight);
        float frac = (float) scrollOffset / maxScroll;
        int thumbY = y + (int)((height - thumbH) * frac);

        g.setColor(new Color(110, 88, 42));
        g.fillRect(trackX, thumbY, 4, thumbH);
        g.setColor(new Color(160, 128, 55));
        g.drawRect(trackX, thumbY, 3, thumbH - 1);
    }

    private static void drawOutlined(Graphics2D g, String text, int x, int y,
                                     Color fg, Color shadow) {
        g.setColor(shadow);
        g.drawString(text, x + 1, y + 1);
        g.drawString(text, x - 1, y + 1);
        g.drawString(text, x + 1, y - 1);
        g.drawString(text, x - 1, y - 1);
        g.setColor(fg);
        g.drawString(text, x, y);
    }

    private List<Skill> getOrderedSkills(Player player) {
        List<Skill> skills = new ArrayList<>(player.getSkillSystem().getAllSkills().values());
        skills.sort(Comparator
                .comparingInt((Skill skill) -> skillOrderIndex(skill.getName()))
                .thenComparing(skill -> skill.getName().toLowerCase()));
        return skills;
    }

    private int skillOrderIndex(String skillName) {
        int index = SKILL_ORDER.indexOf(skillName.toLowerCase());
        return index >= 0 ? index : SKILL_ORDER.size();
    }
}
