package com.classic.preservitory.entity;

import java.awt.Graphics;

/**
 * Strategy interface for entity animation.
 *
 * Current implementation for NPCs and enemies: {@link DefaultAttackAnimation} (attack lunge).
 * Walk animation and state selection are handled by {@link AnimationController}, which is
 * the preferred entry point for NPC/enemy animation.
 *
 * This interface remains for extensibility — future sprite-based overrides can implement
 * it and be plugged into {@link AnimationController} without changing entity code.
 *
 * Implementations receive the entity directly so they can read
 * {@link Entity#walkTick}, {@link Entity#isMoving}, and
 * {@link Entity#getDirection()} without casting in common cases.
 */
public interface AnimationStrategy {

    /**
     * Advance animation state for the current frame.
     * Typically increments {@link Entity#walkTick} when moving and resets it when idle.
     * Called once per frame before rendering.
     */
    void update(Entity entity);

    /**
     * Draw the animated representation of the entity at its current world position.
     * Responsible only for the entity body sprite — shadows and overlays are
     * drawn by the entity's own {@code render()} method.
     */
    void render(Entity entity, Graphics g);
}
