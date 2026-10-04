package com.example.battleroyal.game.core;

import java.util.Collection;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One room: terrain, the players in it, the items on its floor, and a queue of
 * pending commands.
 *
 * <p>Threading contract: every member here is touched by the game loop thread alone,
 * which is why none of these collections are concurrent. Inbound commands cross
 * threads at {@code RoomRegistry}, not here. See docs/ARCHITECTURE.md.
 *
 * <p>Rooms are never written to the database. They live in server memory for as long
 * as the world is big enough to hold them.
 *
 * <p>Doors are links to the neighbouring rooms. The registry lays rooms out on a torus
 * and wires every door, so the east door always leads to the room whose west door
 * leads back, and a pursuer can follow.
 */
public final class Room {

    private final String id;
    private final GridMap map;
    private final Map<String, Player> players = new LinkedHashMap<>();
    private final Map<Pos, Crate> crates = new LinkedHashMap<>();
    private int crateSequence;
    private final Map<Direction, Room> links = new EnumMap<>(Direction.class);
    private final List<GameEvent> events = new ArrayList<>();
    private boolean lootRollPending;
    private int lootRegrowTicks;
    private boolean dirty = true;
    private final List<Guard> guards = new ArrayList<>();
    private int guardRespawnTicks;

    public Room(String id, GridMap map) {
        this(id, map, 0);
    }

    /**
     * @param guardHp an outpost's guards' health; its posts get a guard each, first
     *                facing down into the room
     */
    public Room(String id, GridMap map, int guardHp) {
        this.id = id;
        this.map = map;
        for (int i = 0; i < map.guardPosts().size(); i++) {
            guards.add(new Guard(id + "-g" + (i + 1), map.guardPosts().get(i), Direction.DOWN,
                    guardHp));
        }
    }

    public String id() {
        return id;
    }

    // --- Doors ------------------------------------------------------------

    /** The room behind this wall's door, or null if the room has not been wired up. */
    public Room linkedRoom(Direction side) {
        return links.get(side);
    }

    /** Joins two rooms through a pair of doors, both ways at once. */
    public void link(Direction side, Room other, Direction otherSide) {
        links.put(side, other);
        other.links.put(otherSide, this);
    }

    /** Drops every link into and out of this room, for a resize or a discard. */
    public void unlinkAll() {
        for (Room neighbour : links.values()) {
            neighbour.links.values().removeIf(room -> room == this);
        }
        links.clear();
    }

    public Collection<Room> neighbours() {
        return links.values();
    }

    public GridMap map() {
        return map;
    }

    // --- Players ----------------------------------------------------------

    public Collection<Player> players() {
        return players.values();
    }

    public Player player(String playerId) {
        return players.get(playerId);
    }

    public int playerCount() {
        return players.size();
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public void add(Player player) {
        players.put(player.id(), player);
        dirty = true;
    }

    public Player remove(String playerId) {
        Player removed = players.remove(playerId);
        if (removed != null) {
            dirty = true;
        }
        return removed;
    }

    /** Whether a living player or guard stands here. Cabinet occupants hold their own tile. */
    public boolean occupied(Pos pos) {
        for (Player p : players.values()) {
            if (p.alive() && p.pos().equals(pos)) {
                return true;
            }
        }
        return livingGuardAt(pos) != null;
    }

    /** An outpost's guards, standing or down; empty everywhere else. */
    public List<Guard> guards() {
        return guards;
    }

    public Guard livingGuardAt(Pos pos) {
        for (Guard guard : guards) {
            if (guard.alive() && guard.pos().equals(pos)) {
                return guard;
            }
        }
        return null;
    }

    /** One more tick with a guard down and nobody here; returns the total. */
    public int advanceGuardRespawn() {
        return ++guardRespawnTicks;
    }

    public void resetGuardRespawn() {
        guardRespawnTicks = 0;
    }

    public Player livingPlayerAt(Pos pos) {
        for (Player p : players.values()) {
            if (p.alive() && p.pos().equals(pos)) {
                return p;
            }
        }
        return null;
    }

    // --- Crates ---------------------------------------------------------

    /** Crates on the floor by tile, at most one per tile. */
    public Map<Pos, Crate> crates() {
        return crates;
    }

    public Crate crateAt(Pos pos) {
        return crates.get(pos);
    }

    public void placeCrate(Pos pos, Crate crate) {
        crates.put(pos, crate);
        dirty = true;
    }

    public Crate removeCrate(Pos pos) {
        Crate removed = crates.remove(pos);
        if (removed != null) {
            dirty = true;
        }
        return removed;
    }

    /** Puts an item on the floor: into the crate on that tile, or a new one. */
    public void placeItem(Pos pos, Item item) {
        Crate crate = crates.get(pos);
        if (crate == null) {
            placeCrate(pos, new Crate(newCrateId(), List.of(item)));
        } else {
            crate.add(item);
            dirty = true;
        }
    }

    /** A crate id unique within this room, which is all a loot needs to check. */
    public String newCrateId() {
        return id + "-c" + (++crateSequence);
    }

    // --- Loot roll --------------------------------------------------------

    /** Marks this room as waiting for its first loot roll. */
    public void scheduleLootRoll() {
        lootRollPending = true;
    }

    public boolean lootRollPending() {
        return lootRollPending;
    }

    /** Whether the first loot roll was pending, clearing it. */
    public boolean takeLootRoll() {
        boolean pending = lootRollPending;
        lootRollPending = false;
        return pending;
    }

    /** Counts one more tick spent empty and bare, returning the total so far. */
    public int advanceLootRegrow() {
        return ++lootRegrowTicks;
    }

    public void resetLootRegrow() {
        lootRegrowTicks = 0;
    }

    // --- Events -----------------------------------------------------------

    /** Queues a one-off event for the next broadcast. Events always force one. */
    public void emit(GameEvent event) {
        events.add(event);
    }

    /** Hands over every event emitted since the last drain, in emission order. */
    public List<GameEvent> drainEvents() {
        if (events.isEmpty()) {
            return List.of();
        }
        List<GameEvent> drained = List.copyOf(events);
        events.clear();
        return drained;
    }

    // --- Broadcast gating -------------------------------------------------

    public boolean dirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }
}
