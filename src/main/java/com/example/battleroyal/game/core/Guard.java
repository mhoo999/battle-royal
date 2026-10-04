package com.example.battleroyal.game.core;

/**
 * A soldier standing guard at an outpost (V2.2 PvE). Not a {@link Player}: guards are
 * not people in the world, so they never count towards the population, an empty room,
 * a result or a kill. They stand on their post, turn, and shoot what they see.
 *
 * <p>Mutable, and mutated only by the game loop thread. Like a player's, a guard's
 * health is never sent to anyone.
 */
public final class Guard {

    private final String id;
    private final Pos post;
    private final Direction firstFacing;
    private final int maxHp;

    private Direction facing;
    private int hp;
    private int seenTicks;
    private long nextShotTick;
    private long nextTurnTick;

    public Guard(String id, Pos post, Direction facing, int maxHp) {
        this.id = id;
        this.post = post;
        this.firstFacing = facing;
        this.maxHp = maxHp;
        revive(0);
    }

    public String id() {
        return id;
    }

    public Pos pos() {
        return post;
    }

    public Direction facing() {
        return facing;
    }

    public void face(Direction facing) {
        this.facing = facing;
    }

    public boolean alive() {
        return hp > 0;
    }

    /** @return true if this damage brought the guard down */
    public boolean takeDamage(int amount) {
        if (!alive()) {
            return false;
        }
        hp = Math.max(0, hp - amount);
        return !alive();
    }

    /** Back on the post, whole, facing the way it first did. */
    public void revive(long nowTick) {
        this.hp = maxHp;
        this.facing = firstFacing;
        this.seenTicks = 0;
        this.nextShotTick = nowTick;
        this.nextTurnTick = nowTick;
    }

    /** How long, in ticks, the guard has had someone in its sights without a break. */
    public int seenTicks() {
        return seenTicks;
    }

    public void sawSomeone() {
        seenTicks++;
    }

    public void lostSight() {
        seenTicks = 0;
    }

    public long nextShotTick() {
        return nextShotTick;
    }

    public void setNextShotTick(long tick) {
        this.nextShotTick = tick;
    }

    public long nextTurnTick() {
        return nextTurnTick;
    }

    public void setNextTurnTick(long tick) {
        this.nextTurnTick = tick;
    }
}
