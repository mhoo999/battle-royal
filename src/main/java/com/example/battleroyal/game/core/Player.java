package com.example.battleroyal.game.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A player in the world. Mutable, and mutated only by the game loop thread.
 *
 * <p>Nothing here is sent to other clients as-is. {@code hp}, the inventory and its
 * ammo are private state; see docs/NETWORK_PROTOCOL.md for what leaves the server.
 *
 * <p>The inventory is {@link #INVENTORY_SLOTS} slots, one of them equipped: A uses
 * whatever is in the equipped slot, and an empty equipped slot is bare hands. A bag worn
 * in the separate bag slot adds slots; how many is the caller's to say, like magazine
 * size on {@link Item}.
 *
 * <p>Bush concealment is derived from position rather than stored, so it cannot drift
 * out of sync with where the player actually is. Only the cabinet is a stored flag,
 * because a cabinet is entered with an explicit action.
 */
public final class Player {

    /** Slots without a bag. */
    public static final int INVENTORY_SLOTS = 3;

    /** The bag slot's index in TAKE and PUT. Not an inventory slot: A never uses it. */
    public static final int BAG_SLOT = -1;

    private final String id;
    private final String nickname;

    private Pos pos;
    private Direction facing = Direction.UP;
    private int hp;
    private boolean alive = true;
    private Item[] slots = new Item[INVENTORY_SLOTS];
    private Item bag;
    private int equipped;
    private Pos openCrateAt;
    private boolean inCabinet;

    private long nextMoveTick;
    private long nextActionTick;
    private long nextCabinetToggleTick;

    private Direction bufferedMove;
    private long bufferedMoveExpiresTick;

    private List<Exit> exits = List.of();
    private List<Exit> marks = List.of();
    private int marksReached;
    private int soldiersDowned;
    private Pos extractPos;
    private long extractDoneTick;
    private boolean extracted;
    private boolean ejected;

    private String lootItemId;
    private Pos lootPos;
    private long lootDoneTick;

    private final Set<String> pickedItemIds = new HashSet<>();
    private final Set<String> visitedRoomIds = new HashSet<>();
    private long nextRoomScoreTick;
    private int survivalTicks;

    private static final long CONNECTED = -1;
    private long disconnectedSinceTick = CONNECTED;

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

    /** The equipped item, which is what A uses; null is bare hands. */
    public Item heldItem() {
        return slots[equipped];
    }

    public boolean hasItem() {
        return heldItem() != null;
    }

    /** Puts an item in the equipped slot, replacing whatever was there. */
    public void hold(Item item) {
        slots[equipped] = item;
    }

    /**
     * Takes the equipped item out of the inventory, for a used-up medkit or gun. The
     * slot stays equipped and empty: bare hands until something else is chosen.
     */
    public Item releaseItem() {
        Item released = slots[equipped];
        slots[equipped] = null;
        return released;
    }

    public Item slot(int index) {
        return slots[index];
    }

    /** Puts an item (or nothing) in a slot and hands back what was there. */
    public Item setSlot(int index, Item item) {
        Item previous = slots[index];
        slots[index] = item;
        return previous;
    }

    /** The slots in order, empty ones as null. A copy. */
    public List<Item> inventory() {
        return Arrays.asList(slots.clone());
    }

    /** How many inventory slots there are now: the base, plus what the bag adds. */
    public int slotCount() {
        return slots.length;
    }

    /** The bag worn, or null. */
    public Item bag() {
        return bag;
    }

    /** True when every slot from {@code index} on is empty: what a smaller bag would lose. */
    public boolean emptyFrom(int index) {
        for (int i = index; i < slots.length; i++) {
            if (slots[i] != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Puts on a bag, or takes it off with null, leaving {@code slotCount} slots in all,
     * and hands back the bag that was worn. Slots past the new count must already be
     * empty: nothing is ever destroyed by changing bags.
     */
    public Item wearBag(Item bag, int slotCount) {
        if (!emptyFrom(slotCount)) {
            throw new IllegalStateException("Slots past " + slotCount + " still hold items");
        }
        slots = Arrays.copyOf(slots, slotCount);
        if (equipped >= slotCount) {
            equipped = 0;
        }
        Item worn = this.bag;
        this.bag = bag;
        return worn;
    }

    public int equipped() {
        return equipped;
    }

    public void equip(int index) {
        this.equipped = index;
    }

    /**
     * Empties every slot and the bag slot and hands back what was in them, slot order
     * then the bag, for a death or an extraction.
     */
    public List<Item> dropAll() {
        List<Item> carried = new ArrayList<>();
        for (Item item : slots) {
            if (item != null) {
                carried.add(item);
            }
        }
        if (bag != null) {
            carried.add(bag);
        }
        slots = new Item[INVENTORY_SLOTS];
        bag = null;
        equipped = 0;
        return carried;
    }

    /** The tile of the crate this player has open, or null. */
    public Pos openCrateAt() {
        return openCrateAt;
    }

    public void openCrate(Pos at) {
        this.openCrateAt = at;
    }

    public void closeCrate() {
        this.openCrateAt = null;
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
     * Starts opening the crate on this tile. It opens only if the player is still on
     * the same tile, and the same crate still lies there, when time is up.
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

    // --- Errand marks (V2.2) ------------------------------------------------

    /**
     * Places the trader marked for this player's errand still to be reached, compass
     * bearings included; private like the exits. Reached ones leave the list.
     */
    public List<Exit> marks() {
        return marks;
    }

    public void setMarks(List<Exit> marks) {
        this.marks = List.copyOf(marks);
    }

    /** Standing on this mark: it is reached and leaves the list. */
    public void reachMark(Exit mark) {
        List<Exit> left = new ArrayList<>(marks);
        if (left.remove(mark)) {
            marks = List.copyOf(left);
            marksReached++;
        }
    }

    public int marksReached() {
        return marksReached;
    }

    /** Outpost soldiers this player brought down this trip: for errands, never for score. */
    public int soldiersDowned() {
        return soldiersDowned;
    }

    public void downedSoldier() {
        soldiersDowned++;
    }

    // --- Exits ------------------------------------------------------------

    /** This player's exits, compass bearings included. Empty for a player given none. */
    public List<Exit> exits() {
        return exits;
    }

    public void setExits(List<Exit> exits) {
        this.exits = List.copyOf(exits);
    }

    /**
     * Starts holding B on an exit. Like a loot it is pinned to the tile: it completes
     * only if the player is still standing there when time is up.
     */
    public void startExtract(long doneTick) {
        this.extractPos = pos;
        this.extractDoneTick = doneTick;
    }

    public boolean extracting() {
        return extractPos != null;
    }

    /** Meaningful only while {@link #extracting()}. */
    public long extractDoneTick() {
        return extractDoneTick;
    }

    public void cancelExtract() {
        this.extractPos = null;
    }

    /** Whether an extraction under way is due now, on the tile it started on. */
    public boolean extractDue(long nowTick) {
        return extractPos != null && nowTick >= extractDoneTick && pos.equals(extractPos);
    }

    /**
     * Off the island. Still alive, but out of the game: no command reaches them and the
     * registry takes them out of the world after this tick's broadcast.
     */
    public boolean extracted() {
        return extracted;
    }

    public void markExtracted() {
        this.extracted = true;
        this.extractPos = null;
    }

    /** Taken off the island because the season ended (D12); gone like an extraction. */
    public void markEjected() {
        this.ejected = true;
        this.extractPos = null;
    }

    /** Still in play: alive, not gone through an exit, not sent home by a season's end. */
    public boolean active() {
        return alive && !extracted && !ejected;
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

    /**
     * The socket dropped. The player stays in the world, still standing where they
     * were and still able to be hit, until they reconnect or the grace period ends.
     * A second drop while already disconnected keeps the original start.
     */
    public void markDisconnected(long nowTick) {
        if (disconnectedSinceTick == CONNECTED) {
            disconnectedSinceTick = nowTick;
        }
    }

    public void markConnected() {
        disconnectedSinceTick = CONNECTED;
    }

    public boolean disconnected() {
        return disconnectedSinceTick != CONNECTED;
    }

    /** Meaningful only while {@link #disconnected()}. */
    public long disconnectedSinceTick() {
        return disconnectedSinceTick;
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
