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
        registerSkill(new Skill("Woodcutting"));
        registerSkill(new Skill("Mining"));
    }

    private void registerSkill(Skill skill) {
        skills.put(skill.getName().toLowerCase(), skill);
    }

    /**
     * Add XP to the named skill.
     * Does nothing if the skill name is not registered.
     */
    public void addXp(String skillName, int amount) {
        Skill skill = skills.get(skillName.toLowerCase());
        if (skill != null) {
            skill.addXp(amount);
        }
    }

    /** Returns the Skill object for the given name, or null if not found. */
    public Skill getSkill(String skillName) {
        return skills.get(skillName.toLowerCase());
    }

    /** Read-only view of every registered skill in registration order. */
    public Map<String, Skill> getAllSkills() {
        return Collections.unmodifiableMap(skills);
    }
}
