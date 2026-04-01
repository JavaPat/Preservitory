package com.classic.preservitory.system;

import com.classic.preservitory.entity.Skill;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns and manages all skills for a single character.
 * Skills are looked up by lowercase name, e.g. "woodcutting".
 *
 * Skills are stored in insertion order (LinkedHashMap) so the UI
 * always renders them in the same sequence.
 *
 * To add a new skill: call registerSkill(new Skill("Name")) in the constructor.
 */
public class SkillSystem {

    private final Map<String, Skill> skills = new LinkedHashMap<>();

    public SkillSystem() {
        // Combat skills — must match server Skill enum names (lowercase lookup)
        registerSkill(new Skill("Attack"));
        registerSkill(new Skill("Strength"));
        registerSkill(new Skill("Defence"));
        registerSkill(new Skill("Hitpoints"));
        registerSkill(new Skill("Magic"));
        registerSkill(new Skill("Range"));
        registerSkill(new Skill("Prayer"));
        // Gathering skills
        registerSkill(new Skill("Woodcutting"));
        registerSkill(new Skill("Mining"));
        registerSkill(new Skill("Fishing"));
        // Artisan skills
        registerSkill(new Skill("Cooking"));
        registerSkill(new Skill("Crafting"));
        registerSkill(new Skill("Fletching"));
        registerSkill(new Skill("Smithing"));
    }

    private void registerSkill(Skill skill) {
        skills.put(skill.getName().toLowerCase(), skill);
    }

    /** Returns the Skill object for the given name, or null if not found. */
    public Skill getSkill(String skillName) {
        return skills.get(skillName.toLowerCase());
    }

    public void applySnapshot(String skillName, int level, int xp) {
        Skill skill = skills.get(skillName.toLowerCase());
        if (skill != null) {
            skill.resetTo(level, xp);
        }
    }

    /** Read-only view of every registered skill in registration order. */
    public Map<String, Skill> getAllSkills() {
        return Collections.unmodifiableMap(skills);
    }
}
