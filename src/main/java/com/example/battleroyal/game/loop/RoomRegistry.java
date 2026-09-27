package com.example.battleroyal.game.loop;

import com.example.battleroyal.game.core.Command;
import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.GridMap;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.core.TileType;
import com.example.battleroyal.game.map.MapTemplates;
import com.example.battleroyal.game.rule.GameConstants;
import com.example.battleroyal.game.rule.RoomSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the live world: which rooms exist, how their doors connect, who is in them, and
 * what is waiting to be applied.
 *
 * <p>This is the single place where other threads hand work to the game loop. The
 * three queues are concurrent; everything else here is touched by the loop thread
 * alone, so there are no locks anywhere in the simulation.
 *
 * <p>The world is deliberately small: {@link GameConstants#ROOMS_PER_PLAYER} rooms per
 * player and no more. An unbounded world was tried first and failed badly. Rooms laid
 * out on an infinite coordinate grid meant finding another player was a random walk in
 * two dimensions, which is null-recurrent: simulated over three thousand pairs, a third
 * never met at all and the slowest quarter took upwards of thirty minutes. Capping the
 * world means doors eventually have to lead back into it, and people collide.
 */
@Component
public class RoomRegistry {

    private static final Logger log = LoggerFactory.getLogger(RoomRegistry.class);

    /** A player asking to enter the world. Resolved on the next tick. */
    public record JoinRequest(String playerId, String nickname) {
    }

    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final Map<String, String> roomOfPlayer = new HashMap<>();

    private final Queue<Command> commands = new ConcurrentLinkedQueue<>();
    private final Queue<JoinRequest> joins = new ConcurrentLinkedQueue<>();
    private final Queue<String> leaves = new ConcurrentLinkedQueue<>();

    private final AtomicLong roomSequence = new AtomicLong();
    private final Random random;

    public RoomRegistry() {
        this(new Random());
    }

    /** Seeded constructor so tests can pin room layout and destination choice. */
    public RoomRegistry(Random random) {
        this.random = random;
    }

    // --- Called from any thread -------------------------------------------

    public void submit(Command command) {
        commands.add(command);
    }

    public void requestJoin(String playerId, String nickname) {
        joins.add(new JoinRequest(playerId, nickname));
    }

    public void requestLeave(String playerId) {
        leaves.add(playerId);
    }

    // --- Called from the loop thread only ---------------------------------

    /**
     * Applies queued departures and arrivals. Departures run first so that a reconnect
     * landing in the same tick does not collide with the old session.
     */
    public void processPending(long nowTick) {
        String leaving;
        while ((leaving = leaves.poll()) != null) {
            removePlayer(leaving);
        }
        JoinRequest joining;
        while ((joining = joins.poll()) != null) {
            addPlayer(joining);
        }
    }

    public void applyCommands(long nowTick) {
        Command command;
        while ((command = commands.poll()) != null) {
            Room room = roomOf(command.playerId());
            if (room == null) {
                continue;
            }
            RoomSimulator.DoorTransit transit = RoomSimulator.apply(room, command, nowTick);
            if (transit != null) {
                moveThroughDoor(room, transit, nowTick);
            }
        }
    }

    public void tickRooms(long nowTick) {
        for (Room room : rooms.values()) {
            RoomSimulator.tick(room, nowTick);
        }
    }

    /**
     * Discards rooms nobody can reach any more: empty, and not next door to anyone.
     *
     * <p>Keeping the immediate neighbours is what makes turning around safe. Computed
     * outward from occupied rooms rather than by asking rooms to keep each other alive,
     * which would let a chain of empty rooms pin itself in memory forever.
     */
    public void collectRooms() {
        Set<Room> keep = new LinkedHashSet<>();
        for (Room room : rooms.values()) {
            if (room.isEmpty()) {
                continue;
            }
            keep.add(room);
            keep.addAll(room.neighbours());
        }

        List<Room> discarded = rooms.values().stream()
                .filter(room -> !keep.contains(room))
                .toList();
        for (Room room : discarded) {
            room.unlinkAll();
            rooms.remove(room.id());
        }
    }

    /**
     * Keeps the world a single connected piece.
     *
     * <p>A capped world only forces encounters if you can actually walk the whole of
     * it. Two things break that on their own: a fresh login opens a room with no doors
     * linked to anything, and discarding rooms can cut a chain in half. Rather than
     * patching each case where a link is chosen, the invariant is restored here, once
     * per tick, after everything else has had its way with the graph.
     */
    public void connectIslands() {
        List<Set<Room>> islands = components();
        if (islands.size() <= 1) {
            return;
        }
        Set<Room> mainland = islands.getFirst();
        for (Set<Room> island : islands.subList(1, islands.size())) {
            if (!bridge(mainland, island)) {
                continue;
            }
            mainland.addAll(island);
        }
    }

    /**
     * Groups the world into connected pieces.
     *
     * <p>Ordering is insertion-based throughout. {@link Room} does not override
     * {@code hashCode}, so a plain {@code HashSet} would iterate in identity-hash order
     * and give a different answer on every JVM run; the encounter tests measured
     * anything between ten and sixteen door transits for identical code before this was
     * pinned down.
     */
    private List<Set<Room>> components() {
        Set<Room> seen = new LinkedHashSet<>();
        List<Set<Room>> islands = new ArrayList<>();
        for (Room start : rooms.values()) {
            if (seen.contains(start)) {
                continue;
            }
            Set<Room> island = new LinkedHashSet<>();
            Deque<Room> frontier = new ArrayDeque<>();
            frontier.add(start);
            island.add(start);
            while (!frontier.isEmpty()) {
                Room room = frontier.removeFirst();
                for (Room neighbour : room.neighbours()) {
                    if (island.add(neighbour)) {
                        frontier.addLast(neighbour);
                    }
                }
            }
            seen.addAll(island);
            islands.add(island);
        }
        return islands;
    }

    /**
     * Joins two groups of rooms through a pair of unused doors.
     *
     * <p>Empty rooms are preferred as the anchor. Bridging straight onto an occupied
     * room would put every fresh login one door from somebody, which turns the first
     * door of the game into a guaranteed fight rather than a gamble.
     */
    private boolean bridge(Set<Room> left, Set<Room> right) {
        List<Room> anchors = new ArrayList<>(left);
        anchors.removeIf(room -> room.freeDoors().isEmpty());
        if (anchors.isEmpty()) {
            return false;
        }
        List<Room> quiet = anchors.stream().filter(Room::isEmpty).toList();
        Room from = pick(quiet.isEmpty() ? anchors : quiet);

        List<Room> targets = new ArrayList<>(right);
        targets.removeIf(room -> room.freeDoors().isEmpty());
        if (targets.isEmpty()) {
            return false;
        }
        List<Room> quietTargets = targets.stream().filter(Room::isEmpty).toList();
        Room to = pick(quietTargets.isEmpty() ? targets : quietTargets);

        linkPreferringFacingDoors(from, to);
        return true;
    }

    /**
     * Joins two rooms through opposite walls when it can.
     *
     * <p>A bridge that grabs whichever doors happen to be free also spends the facing
     * ones, and every later transit through that wall then has to arrive somewhere
     * sideways. Pairing them up here keeps ordinary doors behaving like doors.
     */
    private void linkPreferringFacingDoors(Room from, Room to) {
        List<Direction> facingPairs = from.freeDoors().stream()
                .filter(side -> to.doorIsFree(side.opposite()))
                .toList();
        if (!facingPairs.isEmpty()) {
            Direction side = pick(facingPairs);
            from.link(side, to, side.opposite());
            return;
        }
        from.link(pick(new ArrayList<>(from.freeDoors())), to,
                pick(new ArrayList<>(to.freeDoors())));
    }

    public Collection<Room> rooms() {
        return rooms.values();
    }

    public Room roomOf(String playerId) {
        String roomId = roomOfPlayer.get(playerId);
        return roomId == null ? null : rooms.get(roomId);
    }

    public Player player(String playerId) {
        Room room = roomOf(playerId);
        return room == null ? null : room.player(playerId);
    }

    public int roomCount() {
        return rooms.size();
    }

    /** Rooms allowed to exist right now, given how many people are playing. */
    public int roomCap() {
        return Math.max(GameConstants.MIN_ROOMS,
                roomOfPlayer.size() * GameConstants.ROOMS_PER_PLAYER);
    }

    // --- Arrivals and departures ------------------------------------------

    /**
     * A fresh login always starts alone. Nobody should be dropped into a fight before
     * they have seen the screen; encounters are for door transits.
     */
    private void addPlayer(JoinRequest request) {
        if (roomOfPlayer.containsKey(request.playerId())) {
            return;
        }
        Room room = emptyRoom();
        Pos spawn = pickSpawn(room);
        if (spawn == null) {
            log.warn("No free tile in {} for {}", room.id(), request.playerId());
            return;
        }

        Player player = new Player(request.playerId(), request.nickname(),
                spawn, GameConstants.MAX_HP);
        place(player, room);
        log.info("{} joined {} ({} rooms, cap {})",
                request.playerId(), room.id(), rooms.size(), roomCap());
    }

    private void removePlayer(String playerId) {
        String roomId = roomOfPlayer.remove(playerId);
        if (roomId == null) {
            return;
        }
        Room room = rooms.get(roomId);
        if (room != null) {
            room.remove(playerId);
        }
    }

    private Room emptyRoom() {
        for (Room room : rooms.values()) {
            if (room.isEmpty()) {
                return room;
            }
        }
        return createRoom();
    }

    // --- Doors ------------------------------------------------------------

    private void moveThroughDoor(Room from, RoomSimulator.DoorTransit transit, long nowTick) {
        Player player = from.player(transit.playerId());
        if (player == null) {
            return;
        }
        Direction side = transit.side();

        Room target = from.linkedRoom(side);
        if (target == null) {
            target = openDoor(from, side);
        }

        Pos entry = pickEntry(target, sideFacing(target, from));
        if (entry == null) {
            return;
        }

        from.remove(player.id());
        player.moveTo(entry);
        player.face(side);
        player.clearBufferedMove();
        player.setNextMoveTick(nowTick + GameConstants.MOVE_COOLDOWN_TICKS);
        place(player, target);
    }

    /**
     * Decides where a door leads the first time it is opened, and wires it up.
     *
     * <p>Under the cap the world grows, which is what makes exploring worth anything.
     * At the cap it has to fold back on itself, and that is what guarantees people run
     * into each other.
     */
    private Room openDoor(Room from, Direction side) {
        // Walking out of the east wall should put you at the next room's west wall, so
        // rooms whose facing door is still free come first. Anything else and the way
        // back would be a different door from the one you arrived at.
        List<Room> facing = rooms.values().stream()
                .filter(room -> room != from && room.doorIsFree(side.opposite()))
                .toList();
        List<Room> anyDoor = rooms.values().stream()
                .filter(room -> room != from && !room.freeDoors().isEmpty())
                .toList();
        List<Room> candidates = facing.isEmpty() ? anyDoor : facing;

        // Sometimes a door simply opens onto someone. Left purely to the cap, running
        // into another player took about half a minute of walking.
        //
        // This branch deliberately ignores the facing-door preference and considers
        // every occupied room. Restricting it was tried and pushed the worst measured
        // search from ten door transits to sixteen, around eighty seconds of finding
        // nobody. Occasionally arriving at the wall you left by is barely noticeable in
        // procedurally generated rooms; an empty-feeling world is not.
        if (random.nextInt(100) < GameConstants.ENCOUNTER_BIAS_PERCENT) {
            List<Room> occupied = anyDoor.stream()
                    .filter(room -> !room.isEmpty())
                    .toList();
            if (!occupied.isEmpty()) {
                return attach(from, side, pick(occupied));
            }
        }

        if (rooms.size() < roomCap()) {
            return attach(from, side, createRoom());
        }
        if (!candidates.isEmpty()) {
            return attach(from, side, pick(candidates));
        }

        // Every doorway in the world is spoken for. Open a one-way passage rather than
        // growing past the cap; the cap is the reason wandering finds anyone at all.
        //
        // Only to a room that already has a door aimed back here. Chaining one-way links
        // otherwise strands the player: there is no way home and, with no facing door to
        // arrive at, they materialise in the middle of the room.
        List<Room> canReturn = from.neighbours().stream()
                .distinct()
                .filter(room -> sideFacing(room, from) != null)
                .toList();
        if (!canReturn.isEmpty()) {
            Room target = pick(canReturn);
            from.linkOneWay(side, target);
            return target;
        }
        return attach(from, side, createRoom());
    }

    /**
     * Links {@code from} to {@code target}, preferring the door facing the way the
     * player was walking so that leaving east arrives at a west wall.
     */
    private Room attach(Room from, Direction side, Room target) {
        Direction facing = side.opposite();
        if (target.doorIsFree(facing) && target.map().doorAt(facing) != null) {
            from.link(side, target, facing);
            return target;
        }
        List<Direction> free = new ArrayList<>(target.freeDoors());
        if (!free.isEmpty()) {
            from.link(side, target, pick(free));
            return target;
        }
        // Callers only offer targets with a spare door, so this is belt and braces: a
        // one-way link is safe here only because the target already leads back.
        if (sideFacing(target, from) != null) {
            from.linkOneWay(side, target);
            return target;
        }
        return attach(from, side, createRoom());
    }

    /** Which of {@code room}'s doors leads back to {@code neighbour}. */
    private Direction sideFacing(Room room, Room neighbour) {
        for (Direction side : Direction.values()) {
            if (room.linkedRoom(side) == neighbour) {
                return side;
            }
        }
        return null;
    }

    private Room createRoom() {
        String id = "room-" + roomSequence.incrementAndGet();
        Room room = new Room(id, MapTemplates.random(random).map());
        rooms.put(id, room);
        return room;
    }

    private void place(Player player, Room room) {
        room.add(player);
        roomOfPlayer.put(player.id(), room.id());
    }

    // --- Placement --------------------------------------------------------

    /**
     * A free plain-floor tile. Doors and bushes are excluded: starting on a door is
     * confusing, and starting already concealed hides the new arrival from everyone.
     */
    private Pos pickSpawn(Room room) {
        List<Pos> candidates = new ArrayList<>();
        GridMap map = room.map();
        for (int y = 0; y < GridMap.SIZE; y++) {
            for (int x = 0; x < GridMap.SIZE; x++) {
                Pos p = new Pos(x, y);
                if (map.tileAt(p) == TileType.FLOOR && !room.occupied(p)) {
                    candidates.add(p);
                }
            }
        }
        return candidates.isEmpty() ? null : pick(candidates);
    }

    /**
     * Just inside the door you came through.
     *
     * <p>Deterministic on purpose. Scattering arrivals across the room would stop a
     * pursuer appearing where their quarry did a moment earlier, and a chase that
     * teleports both parties somewhere unrelated does not read as a chase. The cost is
     * that camping a doorway gives the first shot, which is tempered by there being
     * four doors and only one of you.
     */
    private Pos pickEntry(Room room, Direction arrivalSide) {
        Pos door = arrivalSide == null ? null : room.map().doorAt(arrivalSide);
        if (door == null) {
            return pickSpawn(room);
        }
        Pos inside = door.step(arrivalSide.opposite());
        if (room.map().walkable(inside) && !room.occupied(inside)) {
            return inside;
        }
        // Somebody is already standing there; take any free tile beside the doorway.
        for (Direction dir : Direction.values()) {
            Pos beside = door.step(dir);
            if (room.map().walkable(beside)
                    && room.map().tileAt(beside) != TileType.DOOR
                    && !room.occupied(beside)) {
                return beside;
            }
        }
        return pickSpawn(room);
    }

    private <T> T pick(List<T> candidates) {
        return candidates.get(random.nextInt(candidates.size()));
    }
}
