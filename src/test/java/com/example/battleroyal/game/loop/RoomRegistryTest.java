package com.example.battleroyal.game.loop;

import com.example.battleroyal.game.core.Command;
import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.rule.GameConstants;
import com.example.battleroyal.game.rule.WorldSize;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomRegistryTest {

    private static RoomRegistry registry(long seed) {
        return new RoomRegistry(new Random(seed));
    }

    private static RoomRegistry withPlayers(String... ids) {
        return withPlayers(1, ids);
    }

    private static RoomRegistry withPlayers(long seed, String... ids) {
        RoomRegistry registry = registry(seed);
        for (String id : ids) {
            registry.requestJoin(id, id);
        }
        registry.processPending(0);
        settle(registry);
        return registry;
    }

    /**
     * The housekeeping the game loop runs after every tick. Tests that skip it drift
     * from the live server.
     */
    private static void settle(RoomRegistry registry) {
        registry.reapDead();
        registry.fitWorld();
    }

    private static Set<Room> reachableFrom(Room start) {
        Set<Room> seen = new HashSet<>();
        Deque<Room> frontier = new ArrayDeque<>();
        seen.add(start);
        frontier.add(start);
        while (!frontier.isEmpty()) {
            for (Room neighbour : frontier.removeFirst().neighbours()) {
                if (seen.add(neighbour)) {
                    frontier.addLast(neighbour);
                }
            }
        }
        return seen;
    }

    /** Walks a player onto the given wall's door and through it. */
    private static void takeDoor(RoomRegistry registry, String playerId, Direction side,
                                 long tick) {
        Room room = registry.roomOf(playerId);
        registry.player(playerId).moveTo(room.map().doorAt(side));
        registry.submit(new Command.ActionB(playerId));
        registry.applyCommands(tick);
    }

    private static final Direction[] LOOP =
            {Direction.UP, Direction.RIGHT, Direction.DOWN, Direction.LEFT};

    // --- Arrival ----------------------------------------------------------

    @Test
    void theFirstPlayerOpensTheWorld() {
        RoomRegistry registry = withPlayers("a");

        assertNotNull(registry.roomOf("a"));
        assertEquals(9, registry.roomCount());
    }

    @Test
    void aFreshLoginAlwaysStartsAlone() {
        RoomRegistry registry = withPlayers("a", "b", "c");

        for (Room room : registry.rooms()) {
            assertTrue(room.playerCount() <= 1,
                    "nobody should be dropped into a fight before seeing the screen");
        }
    }

    // --- Disconnect grace -------------------------------------------------

    private static final int GRACE = GameConstants.DISCONNECT_GRACE_TICKS;

    @Test
    void aDroppedPlayerStaysInTheWorldForTheGracePeriodThenDies() {
        RoomRegistry registry = withPlayers("a");
        Room room = registry.roomOf("a");
        room.drainEvents();

        registry.requestDisconnect("a");
        registry.processPending(0);
        registry.tickRooms(GRACE - 1);
        assertTrue(registry.player("a").alive(), "still inside the grace period");
        assertTrue(room.drainEvents().isEmpty());

        registry.tickRooms(GRACE);
        assertFalse(registry.player("a").alive());
        GameEvent.Died died = room.drainEvents().stream()
                .filter(GameEvent.Died.class::isInstance)
                .map(GameEvent.Died.class::cast)
                .findFirst().orElseThrow();
        assertEquals("a", died.playerId());
        assertNull(died.killerNickname(), "nobody gets the credit");

        settle(registry);
        assertNull(registry.roomOf("a"));
    }

    @Test
    void reconnectingInsideTheGracePeriodPicksUpWhereTheyWere() {
        RoomRegistry registry = withPlayers("a");
        Room room = registry.roomOf("a");
        Player before = registry.player("a");
        var pos = before.pos();

        registry.requestDisconnect("a");
        registry.processPending(0);
        registry.requestJoin("a", "a");
        room.clearDirty();
        registry.processPending(GRACE - 1);

        assertSame(before, registry.player("a"), "the same player, not a fresh one");
        assertSame(room, registry.roomOf("a"));
        assertEquals(pos, before.pos());
        assertFalse(before.disconnected());
        assertTrue(room.dirty(), "the new socket needs a snapshot straight away");

        registry.tickRooms(10L * GRACE);
        assertTrue(before.alive());
    }

    @Test
    void aDropAndAReconnectInTheSameTickLeaveThePlayerConnected() {
        RoomRegistry registry = withPlayers("a");

        registry.requestDisconnect("a");
        registry.requestJoin("a", "a");
        registry.processPending(0);

        assertFalse(registry.player("a").disconnected());
    }

    @Test
    void aSecondDropDoesNotRestartTheClock() {
        RoomRegistry registry = withPlayers("a");
        registry.requestDisconnect("a");
        registry.processPending(0);
        registry.requestDisconnect("a");
        registry.processPending(GRACE - 1);

        registry.tickRooms(GRACE);

        assertFalse(registry.player("a").alive());
    }

    // --- Room-entry score ------------------------------------------------

    @Test
    void theStartingRoomPaysNothingButTheFirstDoorPaysTen() {
        RoomRegistry registry = withPlayers("a");
        Room start = registry.roomOf("a");
        assertEquals(0, registry.player("a").score());

        takeDoor(registry, "a", Direction.RIGHT, 0);
        assertNotSame(start, registry.roomOf("a"));
        assertEquals(GameConstants.SCORE_ROOM_ENTER, registry.player("a").score());
    }

    @Test
    void walkingBackToTheStartingRoomPaysNothing() {
        RoomRegistry registry = withPlayers("a");
        Room start = registry.roomOf("a");
        takeDoor(registry, "a", Direction.RIGHT, 0);
        settle(registry);

        Room here = registry.roomOf("a");
        Direction back = null;
        for (Direction side : Direction.values()) {
            if (here.linkedRoom(side) == start) {
                back = side;
            }
        }
        takeDoor(registry, "a", back, 10L * GameConstants.ROOM_SCORE_RATE_CAP_TICKS);

        assertSame(start, registry.roomOf("a"));
        assertEquals(GameConstants.SCORE_ROOM_ENTER, registry.player("a").score());
    }

    // --- World size -------------------------------------------------------

    @Test
    void theWorldGrowsWithHowManyPeopleArePlaying() {
        assertEquals(new WorldSize.Grid(3, 3), withPlayers("a").grid());
        assertEquals(new WorldSize.Grid(3, 3), withPlayers("a", "b").grid());
        assertEquals(new WorldSize.Grid(4, 4), withPlayers("a", "b", "c").grid());
        assertEquals(new WorldSize.Grid(6, 5),
                withPlayers("a", "b", "c", "d", "e").grid());
        assertEquals(9, withPlayers("a").roomCount());
    }

    @Test
    void aLonePlayerStillGetsAWorldWorthWalkingAround() {
        RoomRegistry registry = withPlayers("a");
        for (Room room : registry.rooms()) {
            assertEquals(4, room.neighbours().stream().distinct().count(),
                    "east and west leading to the same room reads as the game being "
                            + "broken, which is why no side is shorter than three");
        }
    }

    @Test
    void growingLeavesEveryoneWhereTheyWere() {
        RoomRegistry registry = withPlayers("a", "b");
        Room room = registry.roomOf("a");
        var pos = registry.player("a").pos();

        registry.requestJoin("c", "c");
        registry.processPending(10);
        settle(registry);

        assertEquals(new WorldSize.Grid(4, 4), registry.grid());
        assertSame(room, registry.roomOf("a"));
        assertEquals(pos, registry.player("a").pos());
    }

    @Test
    void oneLeavingPlayerDoesNotShrinkAWorldTheNextJoinWouldRegrow() {
        RoomRegistry registry = withPlayers("a", "b", "c");

        registry.requestLeave("c");
        registry.processPending(10);
        settle(registry);

        assertEquals(new WorldSize.Grid(4, 4), registry.grid(),
                "a world that flips size on every login re-rolls its edge rooms");
    }

    @Test
    void theWorldShrinksOnceTheRoomsItDropsAreEmpty() {
        RoomRegistry registry = withPlayers("a", "b", "c");
        registry.requestLeave("b");
        registry.requestLeave("c");
        registry.processPending(10);
        settle(registry);

        // A may be standing in a room the smaller world has no place for. It shrinks
        // only once they walk back in; nobody is ever moved by a resize.
        Random walk = new Random(3);
        for (int i = 0; i < 40 && registry.roomCount() > 9; i++) {
            assertTrue(registry.rooms().contains(registry.roomOf("a")));
            takeDoor(registry, "a", LOOP[walk.nextInt(LOOP.length)], 20L + i * 10L);
            settle(registry);
        }

        assertEquals(new WorldSize.Grid(3, 3), registry.grid());
        assertTrue(registry.rooms().contains(registry.roomOf("a")));
        for (Room room : registry.rooms()) {
            for (Room neighbour : room.neighbours()) {
                assertTrue(registry.rooms().contains(neighbour),
                        "a door into a dropped room would lead nowhere");
            }
        }
    }

    @Test
    void wanderingNeverResizesTheWorld() {
        RoomRegistry registry = withPlayers("a", "b");
        int rooms = registry.roomCount();

        Random walk = new Random(9);
        for (int i = 0; i < 40; i++) {
            takeDoor(registry, "a", LOOP[walk.nextInt(LOOP.length)], i * 10L);
            settle(registry);
            assertEquals(rooms, registry.roomCount());
        }
    }

    // --- Geography --------------------------------------------------------

    @Test
    void everyDoorLeadsToTheFacingWall() {
        RoomRegistry registry = withPlayers("a", "b", "c");

        for (Room room : registry.rooms()) {
            for (Direction side : Direction.values()) {
                Room next = room.linkedRoom(side);
                assertNotNull(next, room.id() + " " + side + " leads nowhere");
                assertSame(room, next.linkedRoom(side.opposite()),
                        "leaving by the top wall has to land you at the bottom one");
            }
        }
    }

    @Test
    void walkingOffTheEdgeOfTheWorldComesBackRound() {
        RoomRegistry registry = withPlayers("a");
        Room start = registry.roomOf("a");

        for (int i = 0; i < registry.grid().columns(); i++) {
            takeDoor(registry, "a", Direction.RIGHT, i * 10L);
            settle(registry);
        }
        assertSame(start, registry.roomOf("a"), "east all the way round");

        for (int i = 0; i < registry.grid().rows(); i++) {
            takeDoor(registry, "a", Direction.UP, 100 + i * 10L);
            settle(registry);
        }
        assertSame(start, registry.roomOf("a"), "north all the way round");
    }

    @Test
    void goingRoundASquareReturnsToWhereYouStarted() {
        RoomRegistry registry = withPlayers("a");
        Room start = registry.roomOf("a");

        for (int i = 0; i < LOOP.length; i++) {
            takeDoor(registry, "a", LOOP[i], i * 10L);
            settle(registry);
        }

        assertSame(start, registry.roomOf("a"), "a map you can learn");
    }

    @Test
    void neighboursNeverShareALayout() {
        for (long seed = 0; seed < 20; seed++) {
            RoomRegistry registry = withPlayers(seed, "a", "b", "c");
            for (Room room : registry.rooms()) {
                for (Room neighbour : room.neighbours()) {
                    assertNotSame(room.map(), neighbour.map(),
                            "seed " + seed + ": a twin next door looks like walking back "
                                    + "into the room you left");
                }
            }
        }
    }

    @Test
    void aFreshLoginStartsWithNobodyNextDoor() {
        for (long seed = 0; seed < 20; seed++) {
            RoomRegistry registry = withPlayers(seed, "a", "b");
            assertFalse(registry.roomOf("a").neighbours().contains(registry.roomOf("b")),
                    "seed " + seed + ": the first door of the game should be a gamble");
        }
    }

    /**
     * The design target: meeting somebody after four or five doors, with time to loot
     * on the way. Both players wander, one door at a time in no fixed order, which is
     * how the numbers in {@link GameConstants#ROOMS_PER_OTHER_PLAYER} were simulated.
     */
    @Test
    void twoWanderersMeetAfterAFewDoors() {
        int trials = 200;
        int total = 0;
        for (long seed = 0; seed < trials; seed++) {
            RoomRegistry registry = withPlayers(seed, "a", "b");
            Random walk = new Random(seed * 31 + 7);

            int doors = 0;
            long tick = 0;
            while (doors < 200 && registry.roomOf("a") != registry.roomOf("b")) {
                String who = walk.nextBoolean() ? "a" : "b";
                takeDoor(registry, who, LOOP[walk.nextInt(LOOP.length)], tick);
                settle(registry);
                tick += 10;
                if (who.equals("a")) {
                    doors++;
                }
            }
            assertSame(registry.roomOf("b"), registry.roomOf("a"),
                    "seed " + seed + ": still apart after " + doors + " doors");
            total += doors;
        }

        double average = (double) total / trials;
        // Measured 5.3 average, 36 worst. A little above the free simulation's 4.4,
        // because a fresh login starts with nobody next door.
        assertTrue(average >= 3.0,
                "averaged " + average + " doors: too soon to have looted anything");
        assertTrue(average <= 6.0,
                "averaged " + average + " doors: wandering should find company");
    }

    @Test
    void theWorldIsAlwaysOneConnectedPiece() {
        RoomRegistry registry = withPlayers("a", "b", "c");

        Random walk = new Random(11);
        for (int i = 0; i < 40; i++) {
            takeDoor(registry, "a", LOOP[walk.nextInt(LOOP.length)], i * 10L);
            settle(registry);
            assertEquals(registry.roomCount(), reachableFrom(registry.roomOf("a")).size(),
                    "every room must stay walkable from every other");
        }
    }

    @Test
    void aFreshLoginIsWiredIntoTheWorldRatherThanLeftOnAnIsland() {
        RoomRegistry registry = withPlayers("a");
        for (int i = 0; i < 4; i++) {
            takeDoor(registry, "a", LOOP[i], i * 10L);
            settle(registry);
        }

        registry.requestJoin("b", "b");
        registry.processPending(100);
        settle(registry);

        assertTrue(reachableFrom(registry.roomOf("b")).contains(registry.roomOf("a")),
                "a new room with no links would strand its occupant permanently");
    }

    // --- Doors ------------------------------------------------------------

    @Test
    void takingADoorMovesThePlayerToAnotherRoom() {
        RoomRegistry registry = withPlayers("a");
        Room before = registry.roomOf("a");

        takeDoor(registry, "a", Direction.RIGHT, 10);

        assertNotEquals(before, registry.roomOf("a"));
        assertNull(before.player("a"));
        assertTrue(before.isEmpty());
    }

    @Test
    void walkingBackReturnsToTheSameRoom() {
        RoomRegistry registry = withPlayers("a");
        Room origin = registry.roomOf("a");

        takeDoor(registry, "a", Direction.RIGHT, 10);
        settle(registry);
        takeDoor(registry, "a", Direction.LEFT, 20);

        assertSame(origin, registry.roomOf("a"),
                "a door that led somewhere new on the way back would read as the world "
                        + "reshuffling behind you");
    }

    @Test
    void aDoorKeepsLeadingWhereItLedTheFirstTime() {
        RoomRegistry registry = withPlayers("a");
        Room origin = registry.roomOf("a");

        takeDoor(registry, "a", Direction.RIGHT, 10);
        Room firstDestination = registry.roomOf("a");
        takeDoor(registry, "a", Direction.LEFT, 20);
        settle(registry);
        takeDoor(registry, "a", Direction.RIGHT, 30);

        assertSame(firstDestination, registry.roomOf("a"));
        assertSame(origin, firstDestination.linkedRoom(Direction.LEFT));
    }

    @Test
    void arrivalIsJustInsideTheDoorYouCameThrough() {
        RoomRegistry registry = withPlayers("a");

        takeDoor(registry, "a", Direction.RIGHT, 10);

        Room room = registry.roomOf("a");
        Player player = registry.player("a");
        assertEquals(room.map().doorAt(Direction.LEFT).step(Direction.RIGHT), player.pos(),
                "a pursuer has to appear where their quarry did, or a chase stops "
                        + "reading as a chase");
        assertEquals(Direction.RIGHT, player.facing(), "facing into the room");
    }

    @Test
    void aPursuerFollowsThroughTheSameDoor() {
        RoomRegistry registry = withPlayers("a", "b");
        // Put both in one room, then let A flee and B follow.
        Room shared = registry.roomOf("a");
        Player chaser = registry.player("b");
        registry.roomOf("b").remove("b");
        shared.add(chaser);
        registry.requestLeave("nobody");

        takeDoor(registry, "a", Direction.RIGHT, 10);
        Room fledTo = registry.roomOf("a");

        assertNotSame(shared, fledTo);
        assertSame(fledTo, shared.linkedRoom(Direction.RIGHT),
                "the door the quarry used has to stay pointing at where they went");
    }

    @Test
    void aHeldMoveDoesNotFollowThePlayerIntoTheNextRoom() {
        RoomRegistry registry = withPlayers("a");
        registry.player("a").bufferMove(Direction.UP, 1000);

        takeDoor(registry, "a", Direction.RIGHT, 10);

        assertNull(registry.player("a").takeBufferedMove(11),
                "input aimed at the old room must not shove you around the new one");
    }

    // --- Cleanup ----------------------------------------------------------

    @Test
    void theRoomYouJustLeftStaysReachable() {
        RoomRegistry registry = withPlayers("a");
        Room origin = registry.roomOf("a");

        takeDoor(registry, "a", Direction.RIGHT, 10);
        settle(registry);

        assertTrue(registry.rooms().contains(origin));
    }

    @Test
    void anEmptyWorldKeepsItsSmallestShape() {
        RoomRegistry registry = withPlayers("a", "b", "c");

        registry.requestLeave("a");
        registry.requestLeave("b");
        registry.requestLeave("c");
        registry.processPending(30);
        settle(registry);

        assertEquals(new WorldSize.Grid(3, 3), registry.grid(),
                "nine rooms are cheap, and their loot keeps growing for the next visitor");
    }

    @Test
    void aDepartingPlayerIsRemovedFromTheirRoom() {
        RoomRegistry registry = withPlayers("a", "b");
        Room room = registry.roomOf("a");

        registry.requestLeave("a");
        registry.processPending(30);

        assertNull(registry.roomOf("a"));
        assertNull(room.player("a"));
        assertNotNull(registry.roomOf("b"));
    }

    @Test
    void commandsFromAPlayerWhoHasLeftAreHarmless() {
        RoomRegistry registry = withPlayers("a");
        registry.requestLeave("a");
        registry.processPending(30);

        registry.submit(new Command.Move("a", Direction.RIGHT));
        registry.applyCommands(31);

        assertNull(registry.roomOf("a"));
    }

    @Test
    void theDeadAreTakenOutOfTheWorld() {
        RoomRegistry registry = withPlayers("a", "b");
        Player a = registry.player("a");

        a.takeDamage(GameConstants.MAX_HP);
        settle(registry);

        assertNull(registry.player("a"));
        assertNull(registry.roomOf("a"));
        assertNotNull(registry.player("b"), "the living stay");
    }

    @Test
    void aJoinRemembersWhenThePlayerArrived() {
        RoomRegistry registry = registry(1);
        registry.requestJoin("a", "a");

        registry.processPending(42);

        assertEquals(42, registry.player("a").joinedTick());
    }

    @Test
    void aNewRoomIsStockedBeforeItsFirstBroadcast() {
        RoomRegistry registry = registry(1);
        registry.requestJoin("a", "a");
        registry.processPending(0);

        registry.tickRooms(0);

        Room room = registry.roomOf("a");
        assertFalse(room.lootRollPending(), "rolled on the first tick");
    }

    @Test
    void itemRollsAreReproducibleForAGivenSeed() {
        // Most rooms roll nothing, and two bare rooms prove nothing, so try several seeds.
        boolean sawLoot = false;
        for (int seed = 0; seed < 20; seed++) {
            String stock = stockOf(new RoomRegistry(new Random(3), new Random(seed)));
            assertEquals(stock, stockOf(new RoomRegistry(new Random(3), new Random(seed))));
            sawLoot |= !stock.equals("[]");
        }
        assertTrue(sawLoot, "at least one seed stocked the room");
    }

    private static String stockOf(RoomRegistry registry) {
        registry.requestJoin("a", "a");
        registry.processPending(0);
        registry.tickRooms(0);
        return registry.roomOf("a").floorItems().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue().id() + ":" + e.getValue().kind())
                .sorted()
                .toList()
                .toString();
    }
}
