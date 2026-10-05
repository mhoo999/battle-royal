package com.example.battleroyal.game.loop;

import com.example.battleroyal.game.core.Command;
import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.Exit;
import com.example.battleroyal.game.core.GridMap;
import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.core.TileType;
import com.example.battleroyal.game.map.MapTemplate;
import com.example.battleroyal.game.map.MapTemplates;
import com.example.battleroyal.game.rule.ActionResolver;
import com.example.battleroyal.game.rule.Bags;
import com.example.battleroyal.game.rule.GameConstants;
import com.example.battleroyal.game.rule.ItemSpawns;
import com.example.battleroyal.game.rule.RoomSimulator;
import com.example.battleroyal.game.rule.ScoreRules;
import com.example.battleroyal.game.rule.WorldSize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 * <p>Rooms sit on a torus sized to the population ({@link WorldSize}): leave by the east
 * door and you arrive at the west door of the room to the east, and walking off the
 * edge of the world brings you in at the other side. An unbounded world was tried first
 * and failed badly: finding another player was a two-dimensional random walk, and a
 * third of simulated pairs never met at all. A world that wraps and grows with its
 * population keeps the walk short, and fixed geography lets a pursuer follow.
 *
 * <p>Iteration is insertion-ordered throughout. {@link Room} does not override
 * {@code hashCode}, so a plain {@code HashSet} of rooms would iterate in identity-hash
 * order and give a different answer on every JVM run.
 */
@Component
public class RoomRegistry {

    private static final Logger log = LoggerFactory.getLogger(RoomRegistry.class);

    /** A player asking to enter the world. Resolved on the next tick. */
    /**
     * @param loadout what the player carries in, slot by slot; nulls are empty slots
     * @param bag     the bag worn in, or null
     * @param marks   errand marks to place (V2.2), and how many doors from the start
     */
    /**
     * @param bot     a server-run player (the bot package): placed like anyone, but not
     *                counted when the world is sized, and never grows it
     */
    public record JoinRequest(String playerId, String nickname, List<Item> loadout, Item bag,
                              int marks, int markDoors, boolean bot) {
    }

    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final Map<String, String> roomOfPlayer = new HashMap<>();
    /** Server-run players now in the world; the rest of roomOfPlayer are people. */
    private final Set<String> bots = new LinkedHashSet<>();

    /** Rooms by position, {@code cells[row][column]}. */
    private Room[][] cells = new Room[0][0];
    private WorldSize.Grid grid = new WorldSize.Grid(0, 0);

    private final Queue<Command> commands = new ConcurrentLinkedQueue<>();
    private final Queue<JoinRequest> joins = new ConcurrentLinkedQueue<>();
    private final Queue<String> leaves = new ConcurrentLinkedQueue<>();
    private final Queue<String> disconnects = new ConcurrentLinkedQueue<>();
    private final Queue<String> ejects = new ConcurrentLinkedQueue<>();

    private final AtomicLong roomSequence = new AtomicLong();
    private final AtomicLong itemSequence = new AtomicLong();
    private final Random random;
    private final Random itemRandom;
    private final Random exitRandom;
    /** Null in the seeded test constructors: no outposts unless a test asks for them. */
    private Random outpostRandom;

    public RoomRegistry() {
        this(new Random(), new Random(), new Random());
        this.outpostRandom = new Random();
    }

    /**
     * Turns outposts on, rolled from their own source so layouts and spawns stay where
     * the seeded tests expect them. Off by default in the seeded constructors, whose
     * players would otherwise walk into guards a measurement never planned for.
     */
    public RoomRegistry withOutposts(Random random) {
        this.outpostRandom = random;
        return this;
    }

    /**
     * {@code game.outposts=false} leaves outposts out of the running server. CI's socket
     * smoke runs that way: its walkers check the protocol, and a guard shooting one on a
     * random route would make it fail at random.
     */
    @Autowired
    void configureOutposts(@Value("${game.outposts:true}") boolean enabled) {
        this.outpostRandom = enabled ? new Random() : null;
    }

    /**
     * Seeded constructor so tests can pin room layout and spawn choice. Items get a
     * fixed seed of their own.
     */
    public RoomRegistry(Random random) {
        this(random, new Random(0));
    }

    /**
     * Item rolls draw from their own source. Sharing one would shift every layout and
     * spawn choice whenever a spawn table changed, and the encounter measurements in
     * {@link GameConstants#ROOMS_PER_OTHER_PLAYER} would move with it.
     */
    public RoomRegistry(Random random, Random itemRandom) {
        this(random, itemRandom, new Random(1));
    }

    /** Exits roll from a third source, for the same reason items do. */
    RoomRegistry(Random random, Random itemRandom, Random exitRandom) {
        this.random = random;
        this.itemRandom = itemRandom;
        this.exitRandom = exitRandom;
    }

    // --- Called from any thread -------------------------------------------

    public void submit(Command command) {
        commands.add(command);
    }

    public void requestJoin(String playerId, String nickname) {
        requestJoin(playerId, nickname, List.of());
    }

    /**
     * A join carrying gear from the hideout. The loadout only fills a new player; a
     * reconnect inside the grace period keeps whatever the player holds now.
     */
    public void requestJoin(String playerId, String nickname, List<Item> loadout) {
        requestJoin(playerId, nickname, loadout, null);
    }

    public void requestJoin(String playerId, String nickname, List<Item> loadout, Item bag) {
        requestJoin(playerId, nickname, loadout, bag, 0, 0);
    }

    public void requestJoin(String playerId, String nickname, List<Item> loadout, Item bag,
                            int marks, int markDoors) {
        joins.add(new JoinRequest(playerId, nickname, loadout, bag, marks, markDoors, false));
    }

    /** A server-run player arrives empty-handed, like a guest. */
    public void requestBotJoin(String playerId, String nickname) {
        joins.add(new JoinRequest(playerId, nickname, List.of(), null, 0, 0, true));
    }

    public void requestLeave(String playerId) {
        leaves.add(playerId);
    }

    /** Takes a player off the island because the season ended (D12). */
    public void requestEject(String playerId) {
        ejects.add(playerId);
    }

    /**
     * The player's socket dropped. They stay in the world for
     * {@link GameConstants#DISCONNECT_GRACE_TICKS} so that closing the app is not an
     * escape from a losing fight; {@link #requestJoin} with the same id brings them back.
     */
    public void requestDisconnect(String playerId) {
        disconnects.add(playerId);
    }

    // --- Called from the loop thread only ---------------------------------

    /**
     * Applies queued departures and arrivals. Departures and drops run first so that a
     * reconnect landing in the same tick wins over the drop it follows.
     */
    public void processPending(long nowTick) {
        String leaving;
        while ((leaving = leaves.poll()) != null) {
            removePlayer(leaving);
        }
        String dropped;
        while ((dropped = disconnects.poll()) != null) {
            Player player = player(dropped);
            if (player != null && player.alive()) {
                player.markDisconnected(nowTick);
            }
        }
        JoinRequest joining;
        while ((joining = joins.poll()) != null) {
            addPlayer(joining, nowTick);
        }
        String ejected;
        while ((ejected = ejects.poll()) != null) {
            Room room = roomOf(ejected);
            Player player = player(ejected);
            if (room != null && player != null) {
                RoomSimulator.eject(room, player);
            }
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
            ItemSpawns.tick(room, itemRandom, this::nextItemId);
            expireDisconnected(room, nowTick);
        }
    }

    private void expireDisconnected(Room room, long nowTick) {
        for (Player player : room.players()) {
            if (player.active() && player.disconnected()
                    && nowTick - player.disconnectedSinceTick()
                            >= GameConstants.DISCONNECT_GRACE_TICKS) {
                RoomSimulator.abandon(room, player, nowTick);
                log.info("{} did not come back in time", player.id());
            }
        }
    }

    /**
     * Takes the dead, and those who got out, out of the world. Runs after the tick's
     * broadcast, so they have already been sent their last snapshot and their result.
     *
     * <p>Their socket stays open; the client starts a fresh session to play again.
     */
    public void reapDead() {
        for (Room room : rooms.values()) {
            List<String> dead = room.players().stream()
                    .filter(player -> !player.active())
                    .map(Player::id)
                    .toList();
            for (String playerId : dead) {
                removePlayer(playerId);
            }
        }
    }

    // --- World size -------------------------------------------------------

    /**
     * Grows or shrinks the world to suit how many people are in it.
     *
     * <p>Growing happens at once, and on a join before the newcomer is placed, so a
     * fresh login always has room to start alone. Shrinking waits on two things: a
     * single newcomer must not be about to grow it straight back, and the rooms being
     * dropped must be empty. Nobody is ever moved by a resize, though the doors on the
     * edge of the world may lead somewhere new afterwards.
     */
    public void fitWorld() {
        int population = people();
        WorldSize.Grid wanted = WorldSize.forPopulation(population);
        if (wanted.area() > grid.area()) {
            resize(wanted);
        } else if (wanted.area() < grid.area()
                && WorldSize.forPopulation(population + 1).area() < grid.area()
                && outskirtsEmpty(wanted)) {
            resize(wanted);
        }
    }

    public WorldSize.Grid grid() {
        return grid;
    }

    /** People in the world, bots left out: what the world is sized for. */
    public int people() {
        return roomOfPlayer.size() - bots.size();
    }

    public boolean isBot(String playerId) {
        return bots.contains(playerId);
    }

    /** The server-run players now in the world, in the order they came. */
    public List<String> bots() {
        return List.copyOf(bots);
    }

    /** Whether every room outside {@code next} is empty, so it can be dropped. */
    private boolean outskirtsEmpty(WorldSize.Grid next) {
        for (int row = 0; row < grid.rows(); row++) {
            for (int column = 0; column < grid.columns(); column++) {
                if ((row >= next.rows() || column >= next.columns())
                        && !cells[row][column].isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Keeps the rooms that fit, drops the rest, fills the gaps and rewires every door.
     * Sizes only ever step through {@link WorldSize#forPopulation}, so a grid either
     * contains the next one or is contained by it.
     */
    private void resize(WorldSize.Grid next) {
        Room[][] fresh = new Room[next.rows()][next.columns()];
        for (int row = 0; row < grid.rows(); row++) {
            for (int column = 0; column < grid.columns(); column++) {
                Room room = cells[row][column];
                if (row < next.rows() && column < next.columns()) {
                    fresh[row][column] = room;
                } else {
                    room.unlinkAll();
                    rooms.remove(room.id());
                }
            }
        }
        WorldSize.Grid before = grid;
        cells = fresh;
        grid = next;
        for (int row = 0; row < next.rows(); row++) {
            for (int column = 0; column < next.columns(); column++) {
                if (cells[row][column] == null) {
                    cells[row][column] = createRoom(row, column);
                }
            }
        }
        wireDoors();
        // Dropped rooms take their exits with them, and every bearing may change.
        for (Room room : rooms.values()) {
            for (Player player : room.players()) {
                rerollLostExits(player, room);
                dropLostMarks(player);
                pointCompass(player, room);
            }
        }
        log.info("World {}x{} -> {}x{} for {} players", before.columns(), before.rows(),
                next.columns(), next.rows(), roomOfPlayer.size());
    }

    /** East to the next column's west, south to the next row's north, wrapping round. */
    private void wireDoors() {
        for (Room room : rooms.values()) {
            room.unlinkAll();
        }
        for (int row = 0; row < grid.rows(); row++) {
            for (int column = 0; column < grid.columns(); column++) {
                Room room = cells[row][column];
                room.link(Direction.RIGHT, cellAt(row, column + 1), Direction.LEFT);
                room.link(Direction.DOWN, cellAt(row + 1, column), Direction.UP);
            }
        }
    }

    private Room cellAt(int row, int column) {
        return cells[Math.floorMod(row, grid.rows())][Math.floorMod(column, grid.columns())];
    }

    // --- Lookups ----------------------------------------------------------

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

    // --- Arrivals and departures ------------------------------------------

    /**
     * A fresh login always starts alone. Nobody should be dropped into a fight before
     * they have seen the screen; encounters are for door transits.
     */
    private void addPlayer(JoinRequest request, long nowTick) {
        Player existing = player(request.playerId());
        if (existing != null) {
            // A reconnect inside the grace period: pick up exactly where they were. The
            // room is marked so the new socket gets a snapshot straight away.
            existing.markConnected();
            roomOf(request.playerId()).markDirty();
            return;
        }
        // The world is sized for people; a bot fits into it or does not come.
        if (!request.bot()) {
            WorldSize.Grid wanted = WorldSize.forPopulation(people() + 1);
            if (wanted.area() > grid.area()) {
                resize(wanted);
            }
        }
        Room room = startingRoom();
        if (room == null) {
            if (!request.bot()) {
                log.warn("No empty room for {}", request.playerId());
            }
            return;
        }
        Pos spawn = pickSpawn(room);
        if (spawn == null) {
            log.warn("No free tile in {} for {}", room.id(), request.playerId());
            return;
        }

        Player player = new Player(request.playerId(), request.nickname(),
                spawn, GameConstants.MAX_HP, nowTick);
        if (request.bag() != null && Bags.isBag(request.bag().kind())) {
            Bags.wear(player, request.bag());
        }
        List<Item> loadout = request.loadout();
        for (int slot = 0; slot < Math.min(loadout.size(), player.slotCount()); slot++) {
            player.setSlot(slot, loadout.get(slot));
        }
        // Where you start is not somewhere you travelled to.
        player.visitRoom(room.id());
        player.setExits(rollExits(room, List.of()));
        player.setMarks(rollMarks(room, player, request.marks(), request.markDoors()));
        pointCompass(player, room);
        place(player, room);
        if (request.bot()) {
            bots.add(player.id());
        }
        log.info("{} joined {} ({}x{} world)",
                request.playerId(), room.id(), grid.columns(), grid.rows());
    }

    private void removePlayer(String playerId) {
        bots.remove(playerId);
        String roomId = roomOfPlayer.remove(playerId);
        if (roomId == null) {
            return;
        }
        Room room = rooms.get(roomId);
        if (room != null) {
            room.remove(playerId);
        }
    }

    /**
     * An empty room, preferably with nobody next door either, so that the first door of
     * the game is a gamble rather than a guaranteed fight.
     */
    private Room startingRoom() {
        // Nobody starts in an outpost, in front of its guns.
        List<Room> empty = rooms.values().stream()
                .filter(Room::isEmpty)
                .filter(room -> !room.map().isOutpost())
                .toList();
        if (empty.isEmpty()) {
            return null;
        }
        List<Room> quiet = empty.stream()
                .filter(room -> room.neighbours().stream().allMatch(Room::isEmpty))
                .toList();
        return pick(quiet.isEmpty() ? empty : quiet);
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
            return;
        }

        Pos entry = pickEntry(target, side.opposite());
        if (entry == null) {
            return;
        }

        from.remove(player.id());
        player.moveTo(entry);
        player.face(side);
        player.clearBufferedMove();
        // The same coordinates in the next room are a different tile.
        player.cancelLoot();
        player.closeCrate();
        player.cancelExtract();
        player.setNextMoveTick(nowTick + GameConstants.MOVE_COOLDOWN_TICKS);
        place(player, target);
        pointCompass(player, target);
        ScoreRules.enterRoom(player, target.id(), nowTick);
    }

    /**
     * A room for the given cell, with a layout none of its known neighbours already use.
     * Eight layouts on nine rooms must repeat somewhere, but a repeat next door makes
     * walking through it look like walking back into the room you just left.
     */
    private Room createRoom(int row, int column) {
        List<GridMap> nextDoor = new ArrayList<>();
        for (Direction side : Direction.values()) {
            Room neighbour = cellAt(row + side.dy(), column + side.dx());
            if (neighbour != null) {
                nextDoor.add(neighbour.map());
            }
        }
        MapTemplate template;
        if (outpostRandom != null && nextDoor.stream().noneMatch(GridMap::isOutpost)
                && outpostRandom.nextInt(GameConstants.OUTPOST_ONE_IN) == 0) {
            template = MapTemplates.OUTPOST;
        } else {
            List<MapTemplate> unlike = MapTemplates.ALL.stream()
                    .filter(candidate -> !nextDoor.contains(candidate.map()))
                    .toList();
            template = pick(unlike.isEmpty() ? MapTemplates.ALL : unlike);
        }

        String id = "room-" + roomSequence.incrementAndGet();
        Room room = new Room(id, template.map(), GameConstants.GUARD_HP);
        ItemSpawns.prime(room);
        rooms.put(id, room);
        return room;
    }

    private String nextItemId() {
        return "i-" + itemSequence.incrementAndGet();
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
        Pos door = room.map().doorAt(arrivalSide);
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

    // --- Exits ------------------------------------------------------------

    /** A room's place on the grid. */
    private record Cell(int row, int column) {
    }

    private Cell cellOf(Room room) {
        for (int row = 0; row < grid.rows(); row++) {
            for (int column = 0; column < grid.columns(); column++) {
                if (cells[row][column] == room) {
                    return new Cell(row, column);
                }
            }
        }
        throw new IllegalStateException(room.id() + " is not on the grid");
    }

    /** The shorter way from {@code from} to {@code to} on a ring of {@code size}, signed. */
    private static int around(int from, int to, int size) {
        int ahead = Math.floorMod(to - from, size);
        return ahead > size / 2 ? ahead - size : ahead;
    }

    /**
     * A player's exits, one for each of {@link GameConstants#EXIT_DISTANCES}, counted in
     * doors from {@code start}. A distance the world is too small for becomes its
     * farthest. Each exit gets a room of its own when there are enough, and a floor tile
     * that is no door's, bush's, cabinet's or crate's.
     *
     * @param keep exits already decided, whose rooms are not used again
     */
    private List<Exit> rollExits(Room start, List<Exit> keep) {
        Cell origin = cellOf(start);
        int farthest = grid.rows() / 2 + grid.columns() / 2;
        List<Exit> exits = new ArrayList<>(keep);
        for (int i = keep.size(); i < GameConstants.EXIT_DISTANCES.size(); i++) {
            int distance = Math.min(GameConstants.EXIT_DISTANCES.get(i), farthest);
            List<Room> ring = new ArrayList<>();
            List<Room> fallback = new ArrayList<>();
            for (int row = 0; row < grid.rows(); row++) {
                for (int column = 0; column < grid.columns(); column++) {
                    Room room = cells[row][column];
                    boolean taken = exits.stream().anyMatch(exit -> exit.in(room));
                    // Five seconds standing still in front of the guards is no way out.
                    if (room == start || taken || room.map().isOutpost()) {
                        continue;
                    }
                    int away = Math.abs(around(origin.row(), row, grid.rows()))
                            + Math.abs(around(origin.column(), column, grid.columns()));
                    (away == distance ? ring : fallback).add(room);
                }
            }
            List<Room> choice = ring.isEmpty() ? fallback : ring;
            if (choice.isEmpty()) {
                break;
            }
            Room room = choice.get(exitRandom.nextInt(choice.size()));
            Pos at = exitTile(room);
            if (at != null) {
                exits.add(new Exit(room.id(), at, 0, 0));
            }
        }
        return exits;
    }

    private Pos exitTile(Room room) {
        List<Pos> candidates = new ArrayList<>();
        for (int y = 0; y < GridMap.SIZE; y++) {
            for (int x = 0; x < GridMap.SIZE; x++) {
                Pos p = new Pos(x, y);
                if (room.map().tileAt(p) == TileType.FLOOR && room.crateAt(p) == null
                        && ActionResolver.doorSideAt(room, p) == null
                        && !room.map().guardPosts().contains(p)) {
                    candidates.add(p);
                }
            }
        }
        return candidates.isEmpty() ? null : candidates.get(exitRandom.nextInt(candidates.size()));
    }

    /** An exit whose room the world dropped is rolled again from where the player is. */
    private void rerollLostExits(Player player, Room room) {
        List<Exit> kept = player.exits().stream()
                .filter(exit -> rooms.containsKey(exit.roomId()))
                .toList();
        if (kept.size() < player.exits().size()) {
            player.setExits(rollExits(room, kept));
        }
    }

    /**
     * The places an errand marks (V2.2): {@code count} rooms {@code doors} doors from the
     * start (or as far as the island allows), never the start or an outpost, one tile
     * each that is clear like an exit's.
     */
    private List<Exit> rollMarks(Room start, Player player, int count, int doors) {
        List<Exit> marks = new ArrayList<>();
        if (count <= 0) {
            return marks;
        }
        Cell origin = cellOf(start);
        int distance = Math.min(doors, grid.rows() / 2 + grid.columns() / 2);
        for (int i = 0; i < count; i++) {
            List<Room> ring = new ArrayList<>();
            List<Room> fallback = new ArrayList<>();
            for (int row = 0; row < grid.rows(); row++) {
                for (int column = 0; column < grid.columns(); column++) {
                    Room room = cells[row][column];
                    boolean taken = marks.stream().anyMatch(mark -> mark.in(room));
                    if (room == start || taken || room.map().isOutpost()) {
                        continue;
                    }
                    int away = Math.abs(around(origin.row(), row, grid.rows()))
                            + Math.abs(around(origin.column(), column, grid.columns()));
                    (away == distance ? ring : fallback).add(room);
                }
            }
            List<Room> choice = ring.isEmpty() ? fallback : ring;
            if (choice.isEmpty()) {
                break;
            }
            Room room = choice.get(exitRandom.nextInt(choice.size()));
            Pos at = exitTile(room);
            boolean onExit = player.exits().stream()
                    .anyMatch(exit -> exit.in(room) && exit.at().equals(at));
            if (at != null && !onExit) {
                marks.add(new Exit(room.id(), at, 0, 0));
            }
        }
        return marks;
    }

    /** A mark whose room the world dropped is gone; the errand waits for the next trip. */
    private void dropLostMarks(Player player) {
        List<Exit> kept = player.marks().stream()
                .filter(mark -> rooms.containsKey(mark.roomId()))
                .toList();
        if (kept.size() < player.marks().size()) {
            player.setMarks(kept);
        }
    }

    /** Points every exit's bearing from the room the player is in now. */
    private void pointCompass(Player player, Room here) {
        Cell origin = cellOf(here);
        List<Exit> pointed = new ArrayList<>();
        for (Exit exit : player.exits()) {
            Cell target = cellOf(rooms.get(exit.roomId()));
            pointed.add(exit.pointedFrom(
                    around(origin.column(), target.column(), grid.columns()),
                    around(origin.row(), target.row(), grid.rows())));
        }
        player.setExits(pointed);
        List<Exit> marks = new ArrayList<>();
        for (Exit mark : player.marks()) {
            Cell target = cellOf(rooms.get(mark.roomId()));
            marks.add(mark.pointedFrom(
                    around(origin.column(), target.column(), grid.columns()),
                    around(origin.row(), target.row(), grid.rows())));
        }
        player.setMarks(marks);
    }

    private <T> T pick(List<T> candidates) {
        return candidates.get(random.nextInt(candidates.size()));
    }
}
