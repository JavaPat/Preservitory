package com.classic.preservitory.ui.shops;

import com.classic.preservitory.client.definitions.ItemDefinitionManager;
import com.classic.preservitory.ui.framework.assets.AssetManager;
import com.classic.preservitory.util.Constants;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OSRS-style shop overlay window.
 *
 * Separation of concerns:
 *   ShopWindow renders and handles input — it contains NO parsing or business logic.
 *   All data comes in as a {@link Shop} object.
 *   Network calls are delegated to the onBuy / onClose callbacks supplied by GamePanel.
 *
 * Layout (top to bottom inside the window):
 *   HEADER   — shop name + hint text + divider line
 *   GRID     — COLS × ROWS_VISIBLE slots (scrollable)
 *   SCROLL   — prev/next page buttons + page indicator (hidden when not needed)
 *
 * Scrolling:
 *   Mouse wheel or click the arrow buttons to page through items.
 *   Each "page" advances one full row at a time.
 *   Items beyond the visible area are simply not rendered.
 *
 * Future extension points (add to ShopItem / drawSlot only):
 *   - Item icons     : drawSlot reads ShopItem.iconKey → AssetManager.getImage()
 *   - Hover tooltip  : store hovered ShopItem, draw tooltip overlay in render()
 *   - Buy X / All    : right-click menu; ShopWindow fires onBuyQuantity(item, qty)
 */
public class ShopWindow {

    // -----------------------------------------------------------------------
    //  Layout constants
    // -----------------------------------------------------------------------

    private static final int WIN_W       = 370;
    private static final int HEADER_H    = 46;   // title + hint + separator
    private static final int COLS        = 4;
    private static final int ROWS_VISIBLE = 3;
    private static final int SLOT_W      = 64;
    private static final int SLOT_H      = 56;
    private static final int SLOT_GAP    = 6;
    private static final int SCROLL_H    = 26;   // height of the scroll controls row
    private static final int PAD_BOTTOM  = 8;
    private static final int CLOSE_SZ    = 20;

    // Derived
    private static final int GRID_W  = COLS * SLOT_W + (COLS - 1) * SLOT_GAP;
    private static final int GRID_H  = ROWS_VISIBLE * SLOT_H + (ROWS_VISIBLE - 1) * SLOT_GAP;
    private static final int WIN_H   = HEADER_H + GRID_H + SCROLL_H + PAD_BOTTOM;
    private static final int ITEMS_PER_PAGE = COLS * ROWS_VISIBLE;

    // -----------------------------------------------------------------------
    //  Computed positions (constant after construction)
    // -----------------------------------------------------------------------

    private final int winX, winY;
    private final int closeX, closeY;
    private final int gridX, gridY;
    private final int scrollY;          // top of scroll controls row

    // -----------------------------------------------------------------------
    //  State
    // -----------------------------------------------------------------------

    private Shop    shop         = null;
    private boolean open         = false;
    private boolean closeHovered = false;
    private int     hoverSlot    = -1;   // index into full item list; -1 = none
    private int     scrollOffset = 0;    // first visible row index

    // Scroll button hit areas (set once per render cycle when scroll is needed)
    private int prevBtnX, nextBtnX, scrollBtnY, scrollBtnW, scrollBtnH;

    // -----------------------------------------------------------------------
    //  Callbacks
    // -----------------------------------------------------------------------

    private final Consumer<Integer> onBuy;    // receives itemId
    private final Runnable          onClose;

    // -----------------------------------------------------------------------
    //  Constructor
    // -----------------------------------------------------------------------

    public ShopWindow(Consumer<Integer> onBuy, Runnable onClose) {
        this.onBuy   = onBuy;
        this.onClose = onClose;

        // Centre horizontally; centre vertically in the space above the chatbox (88 px).
        winX   = (Constants.VIEWPORT_W - WIN_W) / 2;
        winY   = Math.max(4, (Constants.VIEWPORT_H - 88 - WIN_H) / 2);
        closeX = winX + WIN_W - CLOSE_SZ - 12;
        closeY = winY + 6;
        gridX  = winX + (WIN_W - GRID_W) / 2;
        gridY  = winY + HEADER_H;
        scrollY = gridY + GRID_H + 4;
    }

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    /** Open the window with fresh server data. */
    public void open(Shop shop) {
        this.shop         = shop;
        this.open         = true;
        this.scrollOffset = 0;
        this.hoverSlot    = -1;
        this.closeHovered = false;
    }

    /** Close without firing onClose (server already notified, e.g. on disconnect). */
    public void hide() {
        open = false;
    }

    /** Close and fire onClose so the caller can send SHOP_CLOSE to the server. */
    public void close() {
        open = false;
        if (onClose != null) onClose.run();
    }

    public boolean isOpen() { return open; }

    public boolean canSell(int itemId) {
        return open && shop != null && shop.canSell(itemId);
    }

    public Map<Integer, Integer> getSellPrices() {
        if (shop == null) return Collections.emptyMap();
        return shop.getSellPrices();
    }

    public boolean containsPoint(int mx, int my) {
        return open && mx >= winX && mx < winX + WIN_W && my >= winY && my < winY + WIN_H;
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    public void render(Graphics2D g) {
        if (!open || shop == null) return;

        drawBackground(g);
        drawHeader(g);
        drawCloseButton(g);
        drawGrid(g);
        if (needsScroll()) drawScrollControls(g);
    }

    private void drawBackground(Graphics2D g) {
        BufferedImage bg = AssetManager.getImage("shop/window");
        if (bg != null) {
            g.drawImage(bg, winX, winY, WIN_W, WIN_H, null);
        } else {
            g.setColor(new Color(15, 11, 6, 248));
            g.fillRoundRect(winX, winY, WIN_W, WIN_H, 8, 8);
            g.setColor(new Color(100, 80, 40));
            g.drawRoundRect(winX, winY, WIN_W, WIN_H, 8, 8);
            g.setColor(new Color(55, 44, 22));
            g.drawLine(winX + 6, winY + HEADER_H - 3, winX + WIN_W - 6, winY + HEADER_H - 3);
        }
    }

    private void drawHeader(Graphics2D g) {
        String title = shop.name;
        g.setFont(new Font("Arial", Font.BOLD, 13));
        FontMetrics fm = g.getFontMetrics();
        int tx = winX + (WIN_W - fm.stringWidth(title)) / 2;
        g.setColor(new Color(0, 0, 0, 160));
        g.drawString(title, tx + 1, winY + 22 + 1);
        g.setColor(new Color(220, 200, 120));
        g.drawString(title, tx, winY + 22);

        g.setFont(new Font("Arial", Font.PLAIN, 9));
        fm = g.getFontMetrics();
        String hint = "Click item to buy  |  Click inventory item to sell";
        g.setColor(new Color(105, 165, 105));
        g.drawString(hint, winX + (WIN_W - fm.stringWidth(hint)) / 2, winY + 37);
    }

    private void drawCloseButton(Graphics2D g) {
        String key = closeHovered ? "shop/close_button_hover" : "shop/close_button";
        BufferedImage img = AssetManager.getImage(key);
        if (img != null) {
            g.drawImage(img, closeX, closeY, CLOSE_SZ, CLOSE_SZ, null);
        } else {
            g.setColor(closeHovered ? new Color(200, 55, 55) : new Color(110, 30, 30));
            g.fillRoundRect(closeX, closeY, CLOSE_SZ, CLOSE_SZ, 4, 4);
            g.setColor(new Color(190, 160, 110));
            g.drawRoundRect(closeX, closeY, CLOSE_SZ, CLOSE_SZ, 4, 4);
            g.setFont(new Font("Arial", Font.BOLD, 10));
            g.setColor(Color.WHITE);
            g.drawString("X", closeX + 6, closeY + 14);
        }
    }

    private void drawGrid(Graphics2D g) {
        List<ShopItem> items = shop.getStockItems();
        if (items.isEmpty()) {
            g.setFont(new Font("Arial", Font.PLAIN, 11));
            g.setColor(new Color(135, 125, 80));
            String msg = "Nothing for sale.";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(msg, winX + (WIN_W - fm.stringWidth(msg)) / 2, gridY + GRID_H / 2);
            return;
        }

        int firstItem = scrollOffset * COLS;
        int lastItem  = Math.min(firstItem + ITEMS_PER_PAGE, items.size());

        for (int i = firstItem; i < lastItem; i++) {
            int localIdx = i - firstItem;
            int col = localIdx % COLS;
            int row = localIdx / COLS;
            int sx  = gridX + col * (SLOT_W + SLOT_GAP);
            int sy  = gridY + row * (SLOT_H + SLOT_GAP);
            drawSlot(g, sx, sy, items.get(i), i == hoverSlot);
        }
    }

    private void drawSlot(Graphics2D g, int x, int y, ShopItem item, boolean hovered) {
        // Slot background sprite
        BufferedImage slotImg = AssetManager.getImage("shop/grid_button");
        if (slotImg != null) {
            g.drawImage(slotImg, x, y, SLOT_W, SLOT_H, null);
        } else {
            g.setColor(hovered ? new Color(62, 52, 26) : new Color(30, 24, 12));
            g.fillRect(x, y, SLOT_W, SLOT_H);
            g.setColor(hovered ? new Color(200, 170, 70) : new Color(72, 58, 30));
            g.drawRect(x, y, SLOT_W, SLOT_H);
        }

        // Hover tint + border highlight
        if (hovered) {
            g.setColor(new Color(255, 220, 80, 36));
            g.fillRect(x + 1, y + 1, SLOT_W - 2, SLOT_H - 2);
            g.setColor(new Color(220, 185, 70));
            g.drawRect(x, y, SLOT_W, SLOT_H);
        }

        int midX = x + SLOT_W / 2;
        int midY = y + SLOT_H / 2;

        // Placeholder icon area (replace with item icon when ready)
        // Future: if (item.iconKey != null) g.drawImage(AssetManager.getImage(item.iconKey), ...)
        g.setColor(new Color(60, 50, 25, 80));
        g.fillRoundRect(x + 8, y + 6, SLOT_W - 16, SLOT_H - 28, 4, 4);

        // Buy price
        g.setFont(new Font("Arial", Font.PLAIN, 8));
        FontMetrics fm = g.getFontMetrics();
        String priceStr = item.price + "c";
        g.setColor(new Color(150, 215, 150));
        g.drawString(priceStr, midX - fm.stringWidth(priceStr) / 2, y + SLOT_H - 5);
    }

    private void drawScrollControls(Graphics2D g) {
        int totalRows   = (int) Math.ceil((double) shop.getStockItems().size() / COLS);
        int currentPage = scrollOffset + 1;
        int totalPages  = (int) Math.ceil((double) totalRows / ROWS_VISIBLE);

        // Button geometry
        int btnW = 28;
        int btnH = 18;
        int centerX  = winX + WIN_W / 2;
        scrollBtnW = btnW;
        scrollBtnH = btnH;
        scrollBtnY = scrollY + (SCROLL_H - btnH) / 2;
        prevBtnX   = centerX - btnW - 30;
        nextBtnX   = centerX + 30;

        boolean canPrev = scrollOffset > 0;
        boolean canNext = (scrollOffset + ROWS_VISIBLE) < totalRows;

        // Previous button
        drawScrollBtn(g, prevBtnX, scrollBtnY, btnW, btnH, "\u25C4", canPrev);

        // Page indicator
        g.setFont(new Font("Arial", Font.PLAIN, 9));
        FontMetrics fm = g.getFontMetrics();
        String pageLabel = currentPage + " / " + totalPages;
        g.setColor(new Color(160, 148, 100));
        g.drawString(pageLabel, centerX - fm.stringWidth(pageLabel) / 2, scrollBtnY + btnH - 4);

        // Next button
        drawScrollBtn(g, nextBtnX, scrollBtnY, btnW, btnH, "\u25BA", canNext);
    }

    private void drawScrollBtn(Graphics2D g, int x, int y, int w, int h,
                                String arrow, boolean active) {
        g.setColor(active ? new Color(55, 46, 24) : new Color(28, 24, 14));
        g.fillRoundRect(x, y, w, h, 4, 4);
        g.setColor(active ? new Color(140, 115, 55) : new Color(55, 50, 35));
        g.drawRoundRect(x, y, w, h, 4, 4);

        g.setFont(new Font("Arial", Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(active ? new Color(210, 185, 110) : new Color(80, 72, 48));
        g.drawString(arrow, x + (w - fm.stringWidth(arrow)) / 2, y + h - 4);
    }

    // -----------------------------------------------------------------------
    //  Input
    // -----------------------------------------------------------------------

    public void handleClick(int mx, int my) {
        if (!open || shop == null) return;

        // Close button
        if (mx >= closeX && mx < closeX + CLOSE_SZ
         && my >= closeY && my < closeY + CLOSE_SZ) {
            close();
            return;
        }

        // Scroll buttons
        if (needsScroll()) {
            if (mx >= prevBtnX && mx < prevBtnX + scrollBtnW
             && my >= scrollBtnY && my < scrollBtnY + scrollBtnH) {
                scroll(-1);
                return;
            }
            if (mx >= nextBtnX && mx < nextBtnX + scrollBtnW
             && my >= scrollBtnY && my < scrollBtnY + scrollBtnH) {
                scroll(1);
                return;
            }
        }

        // Item slot
        int idx = slotIndexAt(mx, my);
        if (idx >= 0 && idx < shop.getStockItems().size() && onBuy != null) {
            onBuy.accept(shop.getStockItems().get(idx).itemId);  // fires integer itemId
        }
    }

    public void handleMouseMove(int mx, int my) {
        if (!open || shop == null) return;
        hoverSlot    = -1;
        closeHovered = mx >= closeX && mx < closeX + CLOSE_SZ
                    && my >= closeY && my < closeY + CLOSE_SZ;
        if (!closeHovered) hoverSlot = slotIndexAt(mx, my);
    }

    /** Called by GamePanel's MouseWheelListener. direction: +1 = down, -1 = up. */
    public void handleScroll(int direction) {
        scroll(direction);
    }

    public String getHoveredItemName() {
        if (!open || shop == null || hoverSlot < 0 || hoverSlot >= shop.getStockItems().size()) {
            return null;
        }
        return ItemDefinitionManager.get(shop.getStockItems().get(hoverSlot).itemId).name;
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    private boolean needsScroll() {
        return shop != null && shop.getStockItems().size() > ITEMS_PER_PAGE;
    }

    /** Advance the scroll offset by one row in the given direction, clamped to valid range. */
    private void scroll(int direction) {
        if (shop == null) return;
        int totalRows = (int) Math.ceil((double) shop.getStockItems().size() / COLS);
        int maxOffset = Math.max(0, totalRows - ROWS_VISIBLE);
        scrollOffset = Math.max(0, Math.min(scrollOffset + direction, maxOffset));
        hoverSlot    = -1;
    }

    /**
     * Returns the absolute item index (into shop.getStockItems()) at screen position,
     * or -1 if no slot is at that position.
     */
    private int slotIndexAt(int mx, int my) {
        if (shop == null) return -1;
        for (int localIdx = 0; localIdx < ITEMS_PER_PAGE; localIdx++) {
            int col = localIdx % COLS;
            int row = localIdx / COLS;
            int sx  = gridX + col * (SLOT_W + SLOT_GAP);
            int sy  = gridY + row * (SLOT_H + SLOT_GAP);
            if (mx >= sx && mx < sx + SLOT_W && my >= sy && my < sy + SLOT_H) {
                return scrollOffset * COLS + localIdx;
            }
        }
        return -1;
    }
}
