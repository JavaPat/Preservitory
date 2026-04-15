package com.classic.preservitory.client.world;

/** Lightweight DTO parsed from the server's {@code ENEMIES} message. */
public class EnemyData {

    public final int     x;
    public final int     y;
    public final int     hp;
    public final int     maxHp;
    public final String  direction;
    public final boolean moving;
    public final boolean attacking;

    public EnemyData(int x, int y, int hp, int maxHp, String direction, boolean moving, boolean attacking) {
        this.x         = x;
        this.y         = y;
        this.hp        = hp;
        this.maxHp     = maxHp;
        this.direction = direction;
        this.moving    = moving;
        this.attacking = attacking;
    }
}
