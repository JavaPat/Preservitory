package com.classic.preservitory.ui;

import com.classic.preservitory.entity.Player;
import com.classic.preservitory.entity.Skill;
import com.classic.preservitory.item.Item;
import com.classic.preservitory.quest.Quest;
import com.classic.preservitory.util.Constants;

import java.awt.*;
import java.util.List;

/**
 * Draws and handles input for the fixed right-side panel.
 *
 * === Layout (all Y values are screen-absolute) ===
 *   0  –  82 : Stats bar — HP progress bar + current activity label
 *   82 – 110 : Tab buttons — [INVENTORY] [SKILLS]
 *  110 – 520 : Tab content (inventory grid OR skills/combat stats)
 *  520 – 600 : Footer — quest status + keyboard shortcuts
 *
 * === Tab system ===
 *   Only one tab is visible at a time.  Clicking a tab button switches to it.
 *
 * === Inventory tab ===
 *   4 × 5 grid of item slots.
 *   Hovered slot is highlighted gold.
 *   A tooltip shows the item name above the slot on hover.
 *   Stack counts are shown in yellow in the bottom-left corner.
 *
 * === Skills tab ===
 *   Trained skills (Woodcutting, Mining) with XP progress bar.
 *   Combat stats (Attack, Strength, Defence, Hitpoints) below.
 *
 * === Input ===
 *   handleClick(screenX, screenY) — call when a click lands anywhere on the panel.
 *   handleMouseMove(screenX, screenY, player) — call each mouseMoved event.
 */
public class RightPanel {

    // -----------------------------------------------------------------------
    //  Tab enum
    // -----------------------------------------------------------------------

    public enum Tab { INVENTORY, SKILLS }

    // -----------------------------------------------------------------------
    //  Inventory grid constants
    // -----------------------------------------------------------------------

    private static final int INV_COLS  = 4;
    private static final int INV_ROWS  = 5;
    private static final int SLOT_SIZE = 44;
    private static final int SLOT_GAP  = 3;
    private static final int SLOT_STEP = SLOT_SIZE + SLOT_GAP;  // 47 px per slot

    // -----------------------------------------------------------------------
    //  Panel vertical regions (screen-absolute Y)
    // -----------------------------------------------------------------------

    private static final int STATS_Y    = 0;
    private static final int STATS_H    = 82;
    private static final int TAB_Y      = STATS_H;           //  82
    private static final int TAB_H      = 28;
    private static final int CONTENT_Y  = TAB_Y + TAB_H;     // 110
    private static final int CONTENT_H  = 410;               // → 520
    private static final int FOOTER_Y   = CONTENT_Y + CONTENT_H; // 520

    // -----------------------------------------------------------------------
    //  State
    // -----------------------------------------------------------------------

    private Tab activeTab  = Tab.INVENTORY;
    private int hoverSlot  = -1;   // inventory slot index under mouse, -1 = none

    // -----------------------------------------------------------------------
    //  Public API — input
    // -----------------------------------------------------------------------

    /**
     * Handle a click anywhere on the right panel.
     * Returns true to indicate the click was consumed (GamePanel should not
     * treat it as a world click).
     */
    public boolean handleClick(int sx, int sy) {
        int px  = Constants.PANEL_X;
        int pw  = Constants.PANEL_W;

        // Must be inside the panel bounds
        if (sx < px || sx >= px + pw) return false;

        // Tab bar — switch active tab
        if (sy >= TAB_Y && sy < CONTENT_Y) {
            int mid = px + pw / 2;
            activeTab = (sx < mid) ? Tab.INVENTORY : Tab.SKILLS;
            return true;
        }

        return true; // consume all other panel clicks
    }

    /**
     * Update hover state; call on every mouseMoved event.
     * Only the inventory grid tracks per-slot hover; the skills tab doesn't need it.
     */
    public void handleMouseMove(int sx, int sy) {
        hoverSlot = -1;
        if (activeTab != Tab.INVENTORY) return;
        if (sy < CONTENT_Y || sy >= FOOTER_Y) return;
        if (sx < Constants.PANEL_X) return;

        int gridX = Constants.PANEL_X + (Constants.PANEL_W - INV_COLS * SLOT_STEP) / 2;
        int gridY = CONTENT_Y + 20;

        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                int slotX = gridX + col * SLOT_STEP;
                int slotY = gridY + row * SLOT_STEP;
                if (sx >= slotX && sx < slotX + SLOT_SIZE
                 && sy >= slotY && sy < slotY + SLOT_SIZE) {
                    hoverSlot = row * INV_COLS + col;
                    return;
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Rendering entry point
    // -----------------------------------------------------------------------

    /**
     * Draw the entire right panel.
     *
     * @param g               graphics context (screen space — no camera transform)
     * @param player          the local player (HP, inventory, skills)
     * @param quest           the main quest (for footer status)
     * @param isChopping      true while WoodcuttingSystem is active
     * @param isMining        true while MiningSystem is active
     * @param isInCombat      true while CombatSystem is active
     * @param combatTarget    name of the current combat enemy, or null
     */
    public void render(Graphics2D g, Player player, Quest quest,
                       boolean isChopping, boolean isMining,
                       boolean isInCombat, String combatTarget) {
        int px = Constants.PANEL_X;
        int pw = Constants.PANEL_W;

        // ---- Panel background ----
        // Dark layered fill for a weathered-stone look
        g.setColor(new Color(20, 16, 12));
        g.fillRect(px, 0, pw, Constants.SCREEN_HEIGHT);

        // Inner bevelled border
        g.setColor(new Color(65, 52, 30));
        g.drawLine(px + 2, 2, px + pw - 3, 2);          // top
        g.drawLine(px + 2, 2, px + 2, Constants.SCREEN_HEIGHT - 3);   // left
        g.setColor(new Color(30, 24, 14));
        g.drawLine(px + pw - 3, 2, px + pw - 3, Constants.SCREEN_HEIGHT - 3); // right

        // ---- Sections ----
        drawStatsSection (g, px, pw, player, isChopping, isMining, isInCombat, combatTarget);
        drawTabBar        (g, px, pw);
        drawContentDivider(g, px, pw);

        if (activeTab == Tab.INVENTORY) {
            drawInventoryTab(g, px, pw, player);
        } else {
            drawSkillsTab(g, px, pw, player);
        }

        drawFooter(g, px, pw, quest);
    }

    // -----------------------------------------------------------------------
    //  Stats section  (Y 0–82)
    // -----------------------------------------------------------------------

    private void drawStatsSection(Graphics2D g, int px, int pw, Player player,
                                   boolean isChopping, boolean isMining,
                                   boolean isInCombat, String combatTarget) {
        int bx = px + 8;
        int bw = pw - 16;

        // Section title
        g.setFont(new Font("Monospaced", Font.BOLD, 11));
        drawOutlined(g, "STATS", px + pw / 2 - 18, 15,
                new Color(220, 200, 120), new Color(0, 0, 0, 180));

        // HP fraction and bar colour
        float hpFrac  = (float) player.getHp() / player.getMaxHp();
        Color hpColor = hpFrac > 0.5f  ? new Color( 55, 175,  55)
                      : hpFrac > 0.25f ? new Color(215, 145,  25)
                      :                   new Color(195,  35,  35);

        // HP label
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g.setColor(new Color(200, 200, 200));
        g.drawString("HP:  " + player.getHp() + " / " + player.getMaxHp(), bx, 33);

        // HP bar
        g.setColor(new Color(40, 0, 0));
        g.fillRect(bx, 37, bw, 9);
        g.setColor(hpColor);
        g.fillRect(bx, 37, (int)(bw * hpFrac), 9);
        g.setColor(new Color(70, 70, 70));
        g.drawRect(bx, 37, bw, 9);

        // Activity status text
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        String status;
        Color  statusColor;
        if (isInCombat && combatTarget != null) {
            status      = "Fighting: " + combatTarget;
            statusColor = new Color(220, 95, 95);
        } else if (isChopping) {
            status      = "Chopping tree...";
            statusColor = new Color(180, 205, 100);
        } else if (isMining) {
            status      = "Mining rock...";
            statusColor = new Color(130, 155, 220);
        } else {
            status      = "Idle";
            statusColor = new Color(120, 120, 120);
        }
        g.setColor(statusColor);
        g.drawString(status, bx, 62);

        // Bottom divider
        g.setColor(new Color(80, 65, 35));
        g.drawLine(px + 4, STATS_H - 2, px + pw - 4, STATS_H - 2);
    }

    // -----------------------------------------------------------------------
    //  Tab bar  (Y 82–110)
    // -----------------------------------------------------------------------

    private void drawTabBar(Graphics2D g, int px, int pw) {
        int mid = px + pw / 2;

        drawTabButton(g, px + 3,   TAB_Y, mid - px - 4,      TAB_H, "INV",    activeTab == Tab.INVENTORY);
        drawTabButton(g, mid + 1,  TAB_Y, px + pw - mid - 3, TAB_H, "SKILLS", activeTab == Tab.SKILLS);

        // Centre divider between the two tabs
        g.setColor(new Color(55, 44, 22));
        g.drawLine(mid, TAB_Y, mid, TAB_Y + TAB_H);
    }

    private void drawTabButton(Graphics2D g, int x, int y, int w, int h,
                                String label, boolean active) {
        // Background
        g.setColor(active ? new Color(52, 44, 26) : new Color(28, 22, 14));
        g.fillRect(x, y, w, h);

        // Active top-highlight line
        if (active) {
            g.setColor(new Color(200, 170, 70));
            g.drawLine(x, y, x + w - 1, y);
        }

        // Border
        g.setColor(active ? new Color(100, 80, 38) : new Color(55, 44, 22));
        g.drawRect(x, y, w, h);

        // Label
        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();
        int lw = fm.stringWidth(label);
        g.setColor(active ? new Color(220, 200, 110) : new Color(130, 115, 65));
        g.drawString(label, x + (w - lw) / 2, y + h / 2 + 4);
    }

    private void drawContentDivider(Graphics2D g, int px, int pw) {
        g.setColor(new Color(90, 72, 38));
        g.drawLine(px + 3, CONTENT_Y, px + pw - 3, CONTENT_Y);
        g.setColor(new Color(35, 28, 14));
        g.drawLine(px + 3, CONTENT_Y + 1, px + pw - 3, CONTENT_Y + 1);
    }

    // -----------------------------------------------------------------------
    //  Inventory tab  (Y 110–520)
    // -----------------------------------------------------------------------

    private void drawInventoryTab(Graphics2D g, int px, int pw, Player player) {
        int bx = px + 8;
        int contentStartY = CONTENT_Y + 6;

        // Header
        int invCount = player.getInventory().getSlots().size();
        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        drawOutlined(g, "INVENTORY   " + invCount + " / 20",
                bx, contentStartY + 12,
                new Color(200, 185, 100), new Color(0, 0, 0, 160));

        // Inventory grid
        int gridX = px + (pw - INV_COLS * SLOT_STEP) / 2;
        int gridY = contentStartY + 18;

        List<Item> slots = player.getInventory().getSlots();

        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                int idx    = row * INV_COLS + col;
                int slotX  = gridX + col * SLOT_STEP;
                int slotY  = gridY + row * SLOT_STEP;
                Item item  = idx < slots.size() ? slots.get(idx) : null;
                boolean hov = (idx == hoverSlot);
                drawSlot(g, slotX, slotY, item, hov);
            }
        }

        // Tooltip for hovered slot
        if (hoverSlot >= 0 && hoverSlot < slots.size()) {
            Item item = slots.get(hoverSlot);
            if (item != null) {
                int tipCol = hoverSlot % INV_COLS;
                int tipRow = hoverSlot / INV_COLS;
                int tipX   = gridX + tipCol * SLOT_STEP;
                int tipY   = gridY + tipRow * SLOT_STEP - 17;
                drawTooltip(g, tipX, tipY, item.getName(), px, pw);
            }
        }
    }

    /** Draw a single inventory slot at (x, y). */
    private void drawSlot(Graphics2D g, int x, int y, Item item, boolean hovered) {
        // Slot background
        Color bg;
        if (hovered) {
            bg = (item != null) ? new Color(90, 75, 38, 230) : new Color(60, 50, 28, 220);
        } else {
            bg = (item != null) ? new Color(45, 38, 22, 220) : new Color(22, 18, 12, 210);
        }
        g.setColor(bg);
        g.fillRect(x, y, SLOT_SIZE, SLOT_SIZE);

        // Outer border
        g.setColor(hovered ? new Color(210, 180, 70, 240) : new Color(72, 60, 30, 200));
        g.drawRect(x, y, SLOT_SIZE, SLOT_SIZE);

        // Inner bevel (depth effect)
        g.setColor(hovered ? new Color(255, 225, 100, 80) : new Color(38, 32, 16, 140));
        g.drawRect(x + 1, y + 1, SLOT_SIZE - 2, SLOT_SIZE - 2);

        if (item == null) return;

        // Item icon — rounded coloured rectangle
        Color ic  = iconColorFor(item.getName());
        int   pad = 7;
        int   iw  = SLOT_SIZE - pad * 2;
        int   ih  = SLOT_SIZE - pad * 2 - 4;
        g.setColor(ic);
        g.fillRoundRect(x + pad, y + pad, iw, ih, 5, 5);
        g.setColor(ic.brighter().brighter());
        g.drawLine(x + pad + 2, y + pad + 2, x + pad + iw / 3, y + pad + 2);  // highlight edge
        g.setColor(ic.darker().darker());
        g.drawRoundRect(x + pad, y + pad, iw, ih, 5, 5);

        // Stack count (bottom-left, yellow)
        if (item.isStackable() && item.getCount() > 1) {
            String cnt = item.getCount() >= 10_000 ? (item.getCount() / 1000) + "k"
                       : item.getCount() >= 1000   ? (item.getCount() / 1000) + "k"
                       :                              String.valueOf(item.getCount());
            g.setFont(new Font("Monospaced", Font.BOLD, 9));
            // Shadow
            g.setColor(new Color(0, 0, 0, 210));
            g.drawString(cnt, x + 3 + 1, y + SLOT_SIZE - 3 + 1);
            // Text
            g.setColor(new Color(255, 230, 0));
            g.drawString(cnt, x + 3, y + SLOT_SIZE - 3);
        }
    }

    /** Small tooltip box showing the item name. Clamped to panel bounds. */
    private void drawTooltip(Graphics2D g, int x, int y, String name, int px, int pw) {
        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        FontMetrics fm  = g.getFontMetrics();
        int tw  = fm.stringWidth(name) + 10;
        int th  = 15;
        int tx  = Math.max(px + 3, Math.min(px + pw - tw - 3, x));
        int ty  = Math.max(CONTENT_Y + 4, y);

        g.setColor(new Color(12, 10, 6, 220));
        g.fillRoundRect(tx, ty, tw, th, 4, 4);
        g.setColor(new Color(190, 165, 70));
        g.drawRoundRect(tx, ty, tw, th, 4, 4);
        g.setColor(Color.WHITE);
        g.drawString(name, tx + 5, ty + th - 3);
    }

    // -----------------------------------------------------------------------
    //  Skills tab  (Y 110–520)
    // -----------------------------------------------------------------------

    private void drawSkillsTab(Graphics2D g, int px, int pw, Player player) {
        int x  = px + 8;
        int bw = pw - 16;
        int y  = CONTENT_Y + 10;

        // ---- Trained skills ----
        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        drawOutlined(g, "SKILLS", px + pw / 2 - 16, y + 2,
                new Color(200, 185, 100), new Color(0, 0, 0, 160));
        y += 14;

        for (Skill skill : player.getSkillSystem().getAllSkills().values()) {
            y = drawSkillRow(g, x, y, bw, skill);
        }

        // ---- Divider ----
        y += 4;
        g.setColor(new Color(80, 65, 35));
        g.drawLine(x, y, x + bw, y);
        y += 10;

        // ---- Combat stats ----
        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        drawOutlined(g, "COMBAT", px + pw / 2 - 18, y + 2,
                new Color(200, 185, 100), new Color(0, 0, 0, 160));
        y += 14;

        y = drawStatRow(g, x, y, bw, "Attack",    player.getAttackLevel(),   new Color(210, 90,  70));
        y = drawStatRow(g, x, y, bw, "Strength",  player.getStrengthLevel(), new Color(220, 60,  60));
        y = drawStatRow(g, x, y, bw, "Defence",   player.getDefenceLevel(),  new Color( 70, 130, 210));
            drawStatRow(g, x, y, bw, "Hitpoints", player.getMaxHp(),         new Color( 55, 175,  55));
    }

    /**
     * Draw one trained-skill row: name + level on one line, XP progress bar below.
     * Returns the Y position below this row (for stacking rows).
     */
    private int drawSkillRow(Graphics2D g, int x, int y, int bw, Skill skill) {
        // Name (left) + level (right) on same line
        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        g.setColor(new Color(195, 185, 125));
        g.drawString(skill.getName().toUpperCase(), x, y + 11);

        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(Color.WHITE);
        String lvl = "Lv " + skill.getLevel();
        FontMetrics fm = g.getFontMetrics();
        g.drawString(lvl, x + bw - fm.stringWidth(lvl), y + 11);

        y += 14;

        // XP fraction text (small, grey)
        g.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g.setColor(new Color(130, 125, 90));
        g.drawString(skill.getXp() + " / " + skill.xpForNextLevel() + " xp", x, y + 8);

        y += 10;

        // Progress bar
        g.setColor(new Color(25, 25, 25));
        g.fillRect(x, y, bw, 7);
        g.setColor(new Color(50, 170, 50));
        g.fillRect(x, y, (int)(bw * skill.getProgressFraction()), 7);
        // Highlight stripe on bar fill
        if (skill.getProgressFraction() > 0) {
            g.setColor(new Color(90, 220, 90, 70));
            g.fillRect(x, y, (int)(bw * skill.getProgressFraction()), 3);
        }
        g.setColor(new Color(55, 55, 55));
        g.drawRect(x, y, bw, 7);

        return y + 16;  // row height: 14 + 10 + 7 + 9 = 40 px total
    }

    /**
     * Draw one combat stat row: coloured bullet + name + value.
     * Returns Y below this row.
     */
    private int drawStatRow(Graphics2D g, int x, int y, int bw,
                             String name, int value, Color bullet) {
        // Coloured dot
        g.setColor(bullet);
        g.fillOval(x + 1, y + 2, 7, 7);
        g.setColor(bullet.darker());
        g.drawOval(x + 1, y + 2, 7, 7);

        // Name
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(new Color(195, 185, 125));
        g.drawString(name, x + 12, y + 10);

        // Value (right-aligned)
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        String val = String.valueOf(value);
        g.drawString(val, x + bw - fm.stringWidth(val), y + 10);

        return y + 15;
    }

    // -----------------------------------------------------------------------
    //  Footer  (Y 520–600) — quest status + hotkeys
    // -----------------------------------------------------------------------

    private void drawFooter(Graphics2D g, int px, int pw, Quest quest) {
        int bx = px + 8;
        int bw = pw - 16;

        // Top border
        g.setColor(new Color(80, 65, 35));
        g.drawLine(px + 3, FOOTER_Y, px + pw - 3, FOOTER_Y);
        g.setColor(new Color(35, 28, 14));
        g.drawLine(px + 3, FOOTER_Y + 1, px + pw - 3, FOOTER_Y + 1);

        int y = FOOTER_Y + 14;

        // Quest status
        if (quest != null && quest.getState() != Quest.State.NOT_STARTED) {
            boolean done = (quest.getState() == Quest.State.COMPLETE);
            Color qColor = done ? new Color(90, 215, 90) : new Color(200, 185, 80);

            g.setFont(new Font("Monospaced", Font.BOLD, 10));
            g.setColor(qColor);
            g.drawString("QUEST: Getting Started", bx, y);
            y += 13;

            g.setFont(new Font("Monospaced", Font.PLAIN, 10));
            g.setColor(done ? new Color(90, 215, 90) : Color.WHITE);
            g.drawString(done ? "[COMPLETE]"
                              : "Logs: " + quest.getLogsChopped() + " / 3", bx, y);
            y += 18;
        }

        // Keyboard shortcut hints
        g.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g.setColor(new Color(95, 90, 65));
        g.drawString("[S]ave  [L]oad  [D]ebug  [M]ute", bx, Constants.SCREEN_HEIGHT - 8);
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    /** Item icon colour keyed by item name. */
    private static Color iconColorFor(String name) {
        switch (name) {
            case "Logs":   return new Color(139,  90,  43);
            case "Ore":    return new Color(160,  88,  65);
            case "Coins":  return new Color(240, 200,  40);
            case "Stone":  return new Color(130, 130, 130);
            case "Candle": return new Color(240, 230, 120);
            case "Rope":   return new Color(160, 130,  80);
            case "Gem":    return new Color( 80, 180, 220);
            default:       return new Color(180, 180,  60);
        }
    }

    /** Outlined string (four 1-px dark offsets + coloured text on top). */
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
}
