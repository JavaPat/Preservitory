package com.classic.preservitory.ui.screens;

import com.classic.preservitory.system.audio.MusicManager;
import com.classic.preservitory.ui.framework.components.MusicToggleButton;
import com.classic.preservitory.ui.framework.components.UIButton;
import com.classic.preservitory.ui.framework.components.UITextField;
import com.classic.preservitory.ui.framework.assets.AssetManager;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.function.BiConsumer;

public class LoginScreen {

    // -----------------------------------------------------------------------
    //  Box dimensions
    // -----------------------------------------------------------------------

    private static final int BOX_W       = 320;
    private static final int BOX_H       = 310;
    private static final int LOGO_H      = 70;
    private static final int LOGO_MARGIN = 12;
    private static final int PAD         = 24;

    /** Vertical flow constants
     HEADER_H    — pixels reserved at the top of the box for title/subtitle/separator

     LABEL_SPACE — gap above each field where UITextField draws its label text

     FIELD_H     — height of each text field

     FIELD_GAP   — vertical gap between two fields

     BUTTON_GAP  — extra breathing room between the last field and the submit button

     SMALL_GAP   — gap between the submit button and the toggle button */

    private static final int HEADER_H    = 100;
    private static final int LABEL_SPACE = 16;
    private static final int FIELD_H     = 30;
    private static final int SUBMIT_H    = 34;
    private static final int TOGGLE_H    = 26;
    private static final int FIELD_GAP   = 8;
    private static final int BUTTON_GAP  = 6;  // gap between password field and login button
    private static final int TOGGLE_MARGIN = 12; // gap between box bottom and register button

    // -----------------------------------------------------------------------
    //  Shared colour
    // -----------------------------------------------------------------------

    private static final Color TEXT_COLOR = new Color(220, 200, 120);

    // -----------------------------------------------------------------------
    //  Components
    // -----------------------------------------------------------------------

    private final UITextField      usernameField;
    private final UITextField      passwordField;
    private final UIButton         submitButton;
    private final UIButton         toggleButton;
    private final MusicToggleButton musicButton;

    // -----------------------------------------------------------------------
    //  State
    // -----------------------------------------------------------------------

    private boolean registerMode  = false;
    private String  statusMessage = "Login or register to start playing.";

    // -----------------------------------------------------------------------
    //  Callbacks
    // -----------------------------------------------------------------------

    private final BiConsumer<String, String> onLogin;
    private final BiConsumer<String, String> onRegister;

    // -----------------------------------------------------------------------
    //  Precomputed positions
    // -----------------------------------------------------------------------

    private int screenW, screenH;
    private int boxX, boxY;
    private int logoY;
    private int submitX, submitY, submitW;
    private int toggleBtnY;

    // -----------------------------------------------------------------------
    //  Constructor
    // -----------------------------------------------------------------------

    public LoginScreen(int screenW, int screenH,
                       BiConsumer<String, String> onLogin,
                       BiConsumer<String, String> onRegister,
                       MusicManager musicManager) {
        this.screenW    = screenW;
        this.screenH    = screenH;
        this.onLogin    = onLogin;
        this.onRegister = onRegister;
        usernameField = new UITextField(0, 0, 0, FIELD_H, "Username", false, 24);
        passwordField = new UITextField(0, 0, 0, FIELD_H, "Password", true, 32);
        submitButton  = new UIButton(0, 0, 0, SUBMIT_H, "Login", this::submit);
        toggleButton  = new UIButton(0, 0, 0, TOGGLE_H, "Create a new account instead", this::toggleMode);

        int btnSize = 32;
        musicButton = new MusicToggleButton(0, 0, btnSize, musicManager);
        layout(screenW, screenH);

        usernameField.setFocused(true);
    }

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    public void render(Graphics2D g, int screenW, int screenH) {
        layout(screenW, screenH);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawBackground(g);
        drawLogo(g);
        drawBox(g);
        drawHeader(g);

        usernameField.render(g);
        passwordField.render(g);
        drawSubmitButton(g);
        toggleButton.render(g);
        musicButton.render(g);

        drawStatus(g);
    }

    public void handleClick(int x, int y) {
        if (usernameField.contains(x, y)) {
            usernameField.setFocused(true);
            passwordField.setFocused(false);
        } else if (passwordField.contains(x, y)) {
            usernameField.setFocused(false);
            passwordField.setFocused(true);
        }
        submitButton.handleClick(x, y);
        toggleButton.handleClick(x, y);
        musicButton.handleClick(x, y);
    }

    public void handleMouseMove(int x, int y) {
        submitButton.handleMouseMove(x, y);
        toggleButton.handleMouseMove(x, y);
        musicButton.handleMouseMove(x, y);
    }

    public void handleKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_TAB, KeyEvent.VK_ENTER -> {
                if (usernameField.isFocused()) {
                    usernameField.setFocused(false);
                    passwordField.setFocused(true);
                } else {
                    submit();
                }
            }
            default -> {
                usernameField.handleKey(keyCode);
                passwordField.handleKey(keyCode);
            }
        }
    }

    public void handleChar(char c) {
        usernameField.handleChar(c);
        passwordField.handleChar(c);
    }

    public void setStatus(String message) {
        this.statusMessage = message;
    }

    public void reset() {
        usernameField.clear();
        passwordField.clear();
        usernameField.setFocused(true);
        passwordField.setFocused(false);
        registerMode  = false;
        statusMessage = "Login or register to start playing.";
        submitButton.setText("Login");
        toggleButton.setText("Create a new account instead");
    }

    // -----------------------------------------------------------------------
    //  Drawing
    // -----------------------------------------------------------------------

    private void layout(int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;

        boxX = (screenW - BOX_W) / 2;
        boxY = (screenH - BOX_H) / 2 + 20;
        logoY = boxY - LOGO_H - LOGO_MARGIN;

        int fieldX = boxX + PAD;
        int fieldW = BOX_W - PAD * 2;
        int y = boxY + HEADER_H;

        usernameField.setBounds(fieldX, y + LABEL_SPACE - 12, fieldW, FIELD_H);
        y += LABEL_SPACE + FIELD_H + FIELD_GAP;

        passwordField.setBounds(fieldX, y + LABEL_SPACE - 10, fieldW, FIELD_H);
        y += LABEL_SPACE + FIELD_H + BUTTON_GAP;

        submitX = fieldX;
        submitY = y;
        submitW = fieldW;
        submitButton.setBounds(submitX, submitY, submitW, SUBMIT_H);

        toggleBtnY = boxY + BOX_H + TOGGLE_MARGIN;
        toggleButton.setBounds(fieldX, toggleBtnY, fieldW, TOGGLE_H);

        musicButton.setBounds(screenW - 42, screenH - 42, 32, 32);
    }

    private void drawBackground(Graphics2D g) {
        BufferedImage bg = AssetManager.getImage("login_bg");
        if (bg != null) {
            g.drawImage(bg, 0, 0, screenW, screenH, null);
        } else {
            g.setColor(new Color(5, 10, 18));
            g.fillRect(0, 0, screenW, screenH);
        }
    }

    private void drawLogo(Graphics2D g) {
        BufferedImage logo = AssetManager.getImage("logo");
        if (logo != null) {
            double scale = Math.min((double) BOX_W / logo.getWidth(),
                                    (double) LOGO_H / logo.getHeight());
            int dw = (int) (logo.getWidth()  * scale);
            int dh = (int) (logo.getHeight() * scale);
            int dx = boxX + (BOX_W - dw) / 2;
            int dy = logoY + (LOGO_H - dh) / 2;
            g.drawImage(logo, dx, dy, dw, dh, null);
        } else {
            g.setFont(new Font("Serif", Font.BOLD, 34));
            FontMetrics fm = g.getFontMetrics();
            String title = "Preservitory";
            int tx = boxX + (BOX_W - fm.stringWidth(title)) / 2;
            int ty = logoY + LOGO_H - 8;
            g.setColor(new Color(30, 30, 30));
            g.drawString(title, tx + 2, ty + 2);
            g.setColor(TEXT_COLOR);
            g.drawString(title, tx, ty);
        }
    }

    private void drawBox(Graphics2D g) {
        BufferedImage box = AssetManager.getImage("login_box");
        if (box != null) {
            g.drawImage(box, boxX, boxY, BOX_W, BOX_H, null);
        } else {
            g.setColor(new Color(18, 25, 35, 245));
            g.fillRoundRect(boxX, boxY, BOX_W, BOX_H, 14, 14);
            g.setColor(new Color(80, 130, 170));
            g.drawRoundRect(boxX, boxY, BOX_W, BOX_H, 14, 14);
        }
    }

    /** Draws the title, subtitle, and separator — all contained within HEADER_H pixels. */
    private void drawHeader(Graphics2D g) {
        g.setColor(TEXT_COLOR);

        g.setFont(new Font("Arial", Font.BOLD, 16));
        FontMetrics titleFm = g.getFontMetrics();
        String title = registerMode ? "Create Account" : "Account Login";
        int titleX = boxX + (BOX_W - titleFm.stringWidth(title)) / 2;
        g.drawString(title, titleX, boxY + 22);

        g.setFont(new Font("Arial", Font.PLAIN, 11));
        FontMetrics subFm = g.getFontMetrics();
        String sub = "Enter your credentials to continue.";
        int subX = boxX + (BOX_W - subFm.stringWidth(sub)) / 2;
        g.drawString(sub, subX, boxY + 52);

        // Separator sits just before HEADER_H
        g.drawLine(boxX + PAD, boxY + HEADER_H - 38, boxX + BOX_W - PAD, boxY + HEADER_H - 38);
    }

    private void drawSubmitButton(Graphics2D g) {
        BufferedImage img = AssetManager.getImage("login_button");
        if (img != null) {
            g.drawImage(img, submitX, submitY, submitW, SUBMIT_H, null);
        } else {
            submitButton.render(g); // fallback to plain UIButton
            return;
        }
        // Draw the label centered over the image
        g.setFont(new Font("Arial", Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();
        String label = registerMode ? "Register" : "Login";
        int textX = submitX + (submitW - fm.stringWidth(label)) / 2;
        int textY = submitY + (SUBMIT_H + fm.getAscent() - fm.getDescent()) / 2;
        g.setColor(new Color(0, 0, 0, 160));
        g.drawString(label, textX + 1, textY + 1);
        g.setColor(TEXT_COLOR);
        g.drawString(label, textX, textY);
    }

    /** Draws the status message centered between the register button and the screen bottom. */
    private void drawStatus(Graphics2D g) {
        if (statusMessage == null || statusMessage.isEmpty()) return;
        g.setFont(new Font("Arial", Font.PLAIN, 11));
        g.setColor(TEXT_COLOR);
        FontMetrics fm = g.getFontMetrics();
        int areaTop    = toggleBtnY + TOGGLE_H;
        int statusY    = areaTop + (screenH - areaTop) / 2;
        int textX      = boxX + (BOX_W - fm.stringWidth(statusMessage)) / 2;
        g.drawString(statusMessage, textX, statusY);
    }

    // -----------------------------------------------------------------------
    //  Actions
    // -----------------------------------------------------------------------

    private void submit() {
        String username = usernameField.getText().trim().toLowerCase(java.util.Locale.ROOT);
        String password = passwordField.getText();

        if (username.isEmpty()) { statusMessage = "Enter a username."; return; }
        if (password.isEmpty()) { statusMessage = "Enter a password."; return; }

        if (registerMode) {
            statusMessage = "Registering...";
            onRegister.accept(username, password);
        } else {
            statusMessage = "Logging in...";
            onLogin.accept(username, password);
        }
    }

    private void toggleMode() {
        registerMode = !registerMode;
        submitButton.setText(registerMode ? "Register" : "Login");
        toggleButton.setText(registerMode ? "Use existing account instead"
                                          : "Create a new account instead");
        statusMessage = registerMode ? "Create a new account."
                                     : "Login with your existing account.";
    }
}
