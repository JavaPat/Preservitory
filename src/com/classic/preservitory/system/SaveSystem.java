package com.classic.preservitory.system;

import com.classic.preservitory.entity.Player;
import com.classic.preservitory.quest.Quest;
import com.classic.preservitory.quest.QuestSystem;
import com.classic.preservitory.util.Constants;

import java.io.*;
import java.util.Properties;

/**
 * Saves and loads player progress to/from a properties file in the user's
 * home directory (~/preservitory_save.properties).
 *
 * Persisted data:
 *   - Player position
 *   - Skill levels and XP
 *   - Quest state
 */
public class SaveSystem {

    private static final String SAVE_FILE =
            System.getProperty("user.home") + "/preservitory_save.properties";

    // -----------------------------------------------------------------------
    //  Save
    // -----------------------------------------------------------------------

    public static void save(Player player, QuestSystem questSystem) {
        Properties properties = new Properties();

        // Position
        properties.setProperty("player.x", String.valueOf(player.getX()));
        properties.setProperty("player.y", String.valueOf(player.getY()));

        // Skills
        player.getSkillSystem().getAllSkills().forEach((name, skill) -> {
            properties.setProperty("skill." + name + ".level", String.valueOf(skill.getLevel()));
            properties.setProperty("skill." + name + ".xp",    String.valueOf(skill.getXp()));
        });

        // Quest
        Quest q = questSystem.getGettingStarted();
        properties.setProperty("quest.gettingStarted.state",       q.getState().name());
        properties.setProperty("quest.gettingStarted.logsChopped", String.valueOf(q.getLogsChopped()));

        try (FileOutputStream out = new FileOutputStream(SAVE_FILE)) {
            properties.store(out, Constants.GAME_NAME + " Save File");
        } catch (IOException e) {
            System.err.println("[SaveSystem] Save failed: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    //  Load
    // -----------------------------------------------------------------------

    /**
     * Load a save file into {@code player} and {@code questSystem}.
     *
     * @return true if a save file was found and loaded, false otherwise.
     */
    public static boolean load(Player player, QuestSystem questSystem) {
        File file = new File(SAVE_FILE);
        if (!file.exists()) return false;

        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            System.err.println("[SaveSystem] Load failed: " + e.getMessage());
            return false;
        }

        // Position
        player.setX(Double.parseDouble(p.getProperty("player.x", String.valueOf(player.getX()))));
        player.setY(Double.parseDouble(p.getProperty("player.y", String.valueOf(player.getY()))));

        // Skills
        player.getSkillSystem().getAllSkills().forEach((name, skill) -> {
            int level = Integer.parseInt(p.getProperty("skill." + name + ".level", "1"));
            int xp    = Integer.parseInt(p.getProperty("skill." + name + ".xp",    "0"));
            skill.resetTo(level, xp);
        });

        // Quest
        try {
            String stateStr    = p.getProperty("quest.gettingStarted.state", "NOT_STARTED");
            int    logsChopped = Integer.parseInt(p.getProperty("quest.gettingStarted.logsChopped", "0"));
            questSystem.getGettingStarted().setState(Quest.State.valueOf(stateStr));
            questSystem.getGettingStarted().setLogsChopped(logsChopped);
        } catch (IllegalArgumentException ignored) {
            // Corrupt save — leave quest at default state
        }

        return true;
    }
}
