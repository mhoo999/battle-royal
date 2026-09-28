package com.example.battleroyal.game.core;

import java.util.HashSet;
import java.util.Set;

/**
 * A player in the world. Mutable, and mutated only by the game loop thread.
 *
 * <p>Nothing here is sent to other clients as-is. {@code hp}, the held item and its
 * ammo are private state; see docs/NETWORK_PROTOCOL.md for what leaves the server.
 *
 * <p>Bush concealment is derived from position rather than stored, so it cannot drift
 * out of sync with where the player actually is. Only the cabinet is a stored flag,
 * because a cabinet is entered with an explicit action.
 */
public final class Player {

    private final String id;
    private final String nickname;

    private Pos pos;
    private Direction facing = Direction.UP;
    private int hp;
    private boolean alive = true;
    private Item heldItem;
    private boolean inCabinet;

    private long nextMoveTick;
    private long nextActionTick;
    private long nextCabinetToggleTick;

    private Direction bufferedMove;
    private long bufferedMoveExpiresTick;

    private String lootItemId;
    private Pos lootPos;
    private long lootDoneTick;

    private Item reloadingItem;
    private long reloadDoneTick;

    private final Set<String> pickedItemIds = new HashSet<>();
    private final Set<String> visitedRoomIds = new HashSet<>();
    private long nextRoomScoreTick;
    private int survivalTicks;

    private final long joinedTick;
    private int score;
    private int kills;

    public Player(String id, String nickname, Pos pos, int maxHp) {
        this(id, nickname, pos, maxHp, 0);
    }

    public Player(String id, String nickname, Pos pos, int maxHp, long joinedTick) {
        this.id = id;
        this.nickname = nickname;
        this.pos = pos;
        this.hp = maxHp;
        this.joinedTick = joinedTick;
    }

    public String id() {
        return id;
    }

    public String nickname() {
        return nickname;
    }

    public Pos pos() {
        return pos;
    }

    public void moveTo(Pos pos) {
        this.pos = pos;
    }

    public Direction facing() {
        return facing;
    }

    public void face(Direction facing) {
        this.facing = facing;
    }

    public int hp() {
        return hp;
    }

    public boolean alive() {
        return alive;
    }

    /** @return true if this damage killed the player */
    public boolean takeDamage(int amount) {
        hp = Math.max(0, hp - amount);
        if (hp == 0) {
            alive = false;
        }
        return !alive;
    }

    public void heal(int amount, int maxHp) {
        hp = Math.min(maxHp, hp + amount);
    }

    public Item heldItem() {
        return heldItem;
    }

    public boolean hasItem() {
        return heldItem != null;
    }

    public void hold(Item item) {
        this.heldItem = item;
    }

    /**
     * Hands the held item back to the caller, which is responsible for placing it.
     *
     * <p>A reload in progress goes with it. Leaving it running meant an empty pistol
     * dropped mid-reload and taken back before the reload was due came back full.
     */
    public Item releaseItem() {
        Item released = heldItem;
        heldItem = null;
        reloadingItem = null;
        return released;
    }

    public boolean inCabinet() {
        return inCabinet;
    }

    public void setInCabinet(boolean inCabinet) {
        this.inCabinet = inCabinet;
    }

    /** Resolves concealment against the terrain the player is standing on. */
    public Concealment concealment(GridMap map) {
        if (inCabinet) {
            return Concealment.CABINET;
        }
        return map.isBush(pos) ? Concealment.BUSH : Concealment.NONE;
    }

    public long nextMoveTick() {
        return nextMoveTick;
    }

    public void setNextMoveTick(long tick) {
        this.nextMoveTick = tick;
    }

    /**
     * Remembers a move that arrived while the cooldown was still running, so a held
     * direction resumes the instant the cooldown clears instead of being dropped.
     * A single slot: a newer input replaces the pending one rather than queueing.
     */
    public void bufferMove(Direction dir, long expiresTick) {
        this.bufferedMove = dir;
        this.bufferedMoveExpiresTick = expiresTick;
    }

    /** Returns the pending move and clears it, or null if there is none or it aged out. */
    public Direction takeBufferedMove(long nowTick) {
        if (bufferedMove == null) {
            return null;
        }
        Direction pending = bufferedMove;
        bufferedMove = null;
        // A stale input firing seconds later would move the player somewhere they no
        // longer intended.
        return nowTick <= bufferedMoveExpiresTick ? pending : null;
    }

    public void clearBufferedMove() {
        this.bufferedMove = null;
    }

    public long nextActionTick() {
        return nextActionTick;
    }

    public void setNextActionTick(long tick) {
        this.nextActionTick = tick;
    }

    public long nextCabinetToggleTick() {
        return nextCabinetToggleTick;
    }

    public void setNextCabinetToggleTick(long tick) {
        this.nextCabinetToggleTick = tick;
    }

    /**
     * Starts reloading the held item. The magazine is refilled only when the reload
     * finishes, and only if the same item is still in hand, so dropping a pistol
     * halfway through does not leave a full one on the floor.
     */
    public void startReload(long doneTick) {
        this.reloadingItem = heldItem;
        this.reloadDoneTick = doneTick;
    }

    public boolean reloading() {
        return reloadingItem != null;
    }

    /**
     * Returns the item whose reload is due and clears the reload, or null if none is
     * due yet. An item that has left the player's hand is abandoned, not returned.
     */
    public Item takeFinishedReload(long nowTick) {
        if (reloadingItem == null || nowTick < reloadDoneTick) {
            return null;
        }
        Item finished = reloadingItem == heldItem ? reloadingItem : null;
        reloadingItem = null;
        return finished;
    }

    /**
     * Starts taking the item on this tile. It lands in hand only if the player is
     * still on the same tile, and the same item still lies there, when time is up.
     */
    public void startLoot(String itemId, long doneTick) {
        this.lootItemId = itemId;
        this.lootPos = pos;
        this.lootDoneTick = doneTick;
    }

    public boolean looting() {
        return lootItemId != null;
    }

    /** The tick the current loot completes on. Meaningful only while looting. */
    public long lootDoneTick() {
        return lootDoneTick;
    }

    public void cancelLoot() {
        this.lootItemId = null;
        this.lootPos = null;
    }

    /**
     * Returns the id of the item whose loot is due and clears the loot, or null if none
     * is due. A loot abandoned by stepping off the tile is dropped, not returned.
     */
    public String takeFinishedLoot(long nowTick) {
        if (lootItemId == null || nowTick < lootDoneTick) {
            return null;
        }
        String itemId = pos.equals(lootPos) ? lootItemId : null;
        cancelLoot();
        return itemId;
    }

    /**
     * Records that this player has held this item instance.
     *
     * @return true the first time only, which is when a pickup pays out; dropping and
     *         re-taking the same item earns nothing
     */
    public boolean firstPickup(String itemId) {
        return pickedItemIds.add(itemId);
    }

    /**
     * Records that this player has been in this room.
     *
     * @return true the first time only
     */
    public boolean visitRoom(String roomId) {
        return visitedRoomIds.add(roomId);
    }

    public long nextRoomScoreTick() {
        return nextRoomScoreTick;
    }

    public void setNextRoomScoreTick(long tick) {
        this.nextRoomScoreTick = tick;
    }

    /** Ticks counted towards the next survival point. */
    public int survivalTicks() {
        return survivalTicks;
    }

    public void setSurvivalTicks(int ticks) {
        this.survivalTicks = ticks;
    }

    public long joinedTick() {
        return joinedTick;
    }

    public int score() {
        return score;
    }

    public void addScore(int points) {
        this.score += points;
    }

    public int kills() {
        return kills;
    }

    public void addKill() {
        this.kills++;
    }
}
