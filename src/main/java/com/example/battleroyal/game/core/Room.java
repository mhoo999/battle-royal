package com.example.battleroyal.game.core;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
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
 * <p>Rooms are never written to the database. They exist only while somebody is in
 * them or next door.
 *
 * <p>Doors are links to other rooms rather than positions on a grid. A link is made
 * the first time someone opens that door and is remembered in both directions
 * afterwards, so walking back takes you where you came from and a pursuer can follow.
 */
public final class Room {

    private final String id;
    private final GridMap map;
    private final Map<String, Player> players = new LinkedHashMap<>();
    private final Map<Pos, Item> floorItems = new HashMap<>();
    private final Map<Direction, Room> links = new EnumMap<>(Direction.class);
    private final List<GameEvent> events = new ArrayList<>();

    private boolean dirty = true;

    public Room(String id, GridMap map) {
        this.id = id;
        this.map = map;
    }

    public String id() {
        return id;
    }

    // --- Doors ------------------------------------------------------------

    /** The room behind this wall's door, or null if that door has never been opened. */
    public Room linkedRoom(Direction side) {
        return links.get(side);
    }

    public boolean doorIsFree(Direction side) {
        return !links.containsKey(side);
    }

    /**
     * Joins two rooms through a pair of doors, both ways at once. A one-way link would
     * mean walking back put you somewhere new, which reads as the world reshuffling
     * behind you.
     *
     * <p>The two sides need not be opposites. Rooms form a graph, not a grid, so when
     * the natural facing door is already spoken for any free door will do.
     */
    public void link(Direction side, Room other, Direction otherSide) {
        links.put(side, other);
        other.links.put(otherSide, this);
    }

    /**
     * Points a door at a room without claiming a door on the other side.
     *
     * <p>For a world that has run out of free doorways. The return trip still works,
     * because the target already has a door aimed back here; it is simply not the
     * mirror of the one you walked through. Growing the world instead would make the
     * room cap meaningless, and the cap is what makes players find each other.
     */
    public void linkOneWay(Direction side, Room other) {
        links.put(side, other);
    }

    public java.util.Set<Direction> freeDoors() {
        java.util.Set<Direction> free = new java.util.LinkedHashSet<>();
        for (Direction side : map.doors().keySet()) {
            if (!links.containsKey(side)) {
                free.add(side);
            }
        }
        return free;
    }

    /** Drops every link into and out of this room, for when it is discarded. */
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

    /** Whether a living player stands here. Cabinet occupants hold their own tile. */
    public boolean occupied(Pos pos) {
        for (Player p : players.values()) {
            if (p.alive() && p.pos().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    public Player livingPlayerAt(Pos pos) {
        for (Player p : players.values()) {
            if (p.alive() && p.pos().equals(pos)) {
                return p;
            }
        }
        return null;
    }

    // --- Floor items ------------------------------------------------------

    public Map<Pos, Item> floorItems() {
        return floorItems;
    }

    public Item itemAt(Pos pos) {
        return floorItems.get(pos);
    }

    public void placeItem(Pos pos, Item item) {
        floorItems.put(pos, item);
        dirty = true;
    }

    public Item takeItem(Pos pos) {
        Item taken = floorItems.remove(pos);
        if (taken != null) {
            dirty = true;
        }
        return taken;
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
