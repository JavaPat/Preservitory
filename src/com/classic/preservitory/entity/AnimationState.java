package com.classic.preservitory.entity;

/**
 * The three mutually-exclusive visual states an entity can be in.
 *
 * Priority (highest to lowest): ATTACK → WALK → IDLE.
 * {@link AnimationController} sets this field on the entity every frame
 * before any rendering takes place.
 */
public enum AnimationState {
    IDLE,
    WALK,
    ATTACK
}
