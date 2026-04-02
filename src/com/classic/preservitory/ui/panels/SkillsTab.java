package com.classic.preservitory.ui.panels;

import com.classic.preservitory.entity.Player;
import com.classic.preservitory.entity.Skill;
import com.classic.preservitory.util.Constants;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Renders the Skills tab content: trained skills, combat stats, and attack style buttons.
 * Also owns scroll state for the scrollable skills list.
 *
 * Display only — reads Player skill data, never modifies it.
 * Combat style selection fires a callback; the actual style change is applied by
 * Scroll state is owned here; combat style buttons live in CombatTab.
 */
class SkillsTab implements Tab {

    private static final List<String> SKILL_ORDER = Arrays.asList(
            "attack",
            "strength",
            "defence",
            "hitpoints"
    );

    // -----------------------------------------------------------------------
    //  Layout
    // -----------------------------------------------------------------------

    private static final int CONTENT_TOP    = RightPanel.CONTENT_Y;
    private static final int CONTENT_BOTTOM = Constants.SCREEN_HEIGHT - GamePanel.LOGOUT_BTN_H - 2 - 6;
    private static final int CONTENT_H      = CONTENT_BOTTOM - CONTENT_TOP;
    private static final int SCROLL_STEP = 20;

    // -----------------------------------------------------------------------
    //  State
    // -----------------------------------------------------------------------

    private int scrollOffset = 0;
    private int maxScroll    = 0;

    // -----------------------------------------------------------------------
    //  Configuration
    // -----------------------------------------------------------------------

    void resetScroll() {
        scrollOffset = 0;
    }

    /**
     * Scroll up (direction = -1) or down (direction = +1).
     */
    void handleMouseWheel(int direction) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + direction * SCROLL_STEP));
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    void render(Graphics2D g, Player player, int px, int pw) {
        Graphics2D contentG = (Graphics2D) g.create();
        contentG.setClip(px, CONTENT_TOP, pw, CONTENT_H);
        contentG.translate(0, -scrollOffset);

        int x  = px + 8;
        int bw = pw - 16;
        int y  = CONTENT_TOP + 10;
        List<Skill> skills = getOrderedSkills(player);

        contentG.setFont(new Font("Arial", Font.BOLD, 10));
        drawOutlined(contentG, "SKILLS", px + pw / 2 - 16, y + 2,
                new Color(200, 185, 100), new Color(0, 0, 0, 160));
        y += 14;

        for (Skill skill : skills) {
            y = drawSkillRow(contentG, x, y, bw, skill);
        }

        int contentHeight = y - CONTENT_TOP;
        maxScroll = Math.max(0, contentHeight - CONTENT_H);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        contentG.dispose();

        drawSkillsScrollbar(g, px, pw, contentHeight);
    }

    // -----------------------------------------------------------------------
    //  Private draw helpers
    // -----------------------------------------------------------------------

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

    private void drawSkillsScrollbar(Graphics2D g, int px, int pw, int contentHeight) {
        if (maxScroll <= 0) return;

        int trackX = px + pw - 5;
        g.setColor(new Color(30, 24, 14));
        g.fillRect(trackX, CONTENT_TOP, 4, CONTENT_H);

        int thumbH = Math.max(18, CONTENT_H * CONTENT_H / contentHeight);
        float frac = (float) scrollOffset / maxScroll;
        int thumbY = CONTENT_TOP + (int)((CONTENT_H - thumbH) * frac);

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
