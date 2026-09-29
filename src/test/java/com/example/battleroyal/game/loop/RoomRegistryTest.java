package com.example.battleroyal.game.loop;

import com.example.battleroyal.game.core.Command;
import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.rule.GameConstants;
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
     * from the live server, which is exactly how the world was able to split into
     * islands without a single unit test noticing.
     */
    private static void settle(RoomRegistry registry) {
        registry.reapDead();
        registry.collectRooms();
        registry.connectIslands();
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
        assertEquals(1, registry.roomCount());
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

    // --- The room cap -----------------------------------------------------

    @Test
    void theWorldGrowsWithHowManyPeopleArePlaying() {
        assertEquals(4 * GameConstants.ROOMS_PER_PLAYER,
                withPlayers("a", "b", "c", "d").roomCap());
        assertEquals(6 * GameConstants.ROOMS_PER_PLAYER,
                withPlayers("a", "b", "c", "d", "e", "f").roomCap());
    }

    @Test
    void aLonePlayerStillGetsAWorldWorthWalkingAround() {
        assertEquals(GameConstants.MIN_ROOMS, withPlayers("a").roomCap());
        assertTrue(GameConstants.MIN_ROOMS >= 4,
                "two rooms to bounce between reads as the game being broken");
    }

    @Test
    void wanderingNeverGrowsTheWorldPastTheCap() {
        RoomRegistry registry = withPlayers("a", "b");

        for (int i = 0; i < 40; i++) {
            takeDoor(registry, "a", LOOP[i % LOOP.length], i * 10L);
            settle(registry);
            assertTrue(registry.roomCount() <= registry.roomCap(),
                    "room " + registry.roomCount() + " exceeds cap " + registry.roomCap());
        }
    }

    /**
     * The whole point of the cap. On an unbounded coordinate grid this was a
     * two-dimensional random walk: a third of simulated pairs never met at all.
     *
     * <p>Doors are chosen at random rather than in a fixed rotation. A rigid cycle can
     * close on itself even in a connected world, which is a property of the route and
     * not of the map; a player who kept seeing the same rooms would try a different
     * door.
     */
    @Test
    void twoPlayersWanderingRunIntoEachOtherQuickly() {
        int worstCase = 0;
        int total = 0;
        int trials = 60;

        for (long seed = 0; seed < trials; seed++) {
            RoomRegistry registry = withPlayers(seed, "a", "b");
            Random walk = new Random(seed * 31 + 7);

            int transits = 0;
            while (transits < 40 && registry.roomOf("a") != registry.roomOf("b")) {
                takeDoor(registry, "a", LOOP[walk.nextInt(LOOP.length)], transits * 10L);
                settle(registry);
                transits++;
            }
            assertSame(registry.roomOf("b"), registry.roomOf("a"),
                    "seed " + seed + ": still apart after " + transits + " doors");
            worstCase = Math.max(worstCase, transits);
            total += transits;
        }

        int average = total / trials;
        assertTrue(average <= 3,
                "averaged " + average + " door transits to meet; wandering should "
                        + "converge on company, not merely avoid diverging from it");
        assertTrue(worstCase <= 18,
                "worst case took " + worstCase + " door transits, which is a long walk");
    }

    @Test
    void theWorldIsAlwaysOneConnectedPiece() {
        RoomRegistry registry = withPlayers("a", "b", "c");

        Random walk = new Random(11);
        for (int i = 0; i < 40; i++) {
            takeDoor(registry, "a", LOOP[walk.nextInt(LOOP.length)], i * 10L);
            settle(registry);
            assertEquals(registry.roomCount(), reachableFrom(registry.roomOf("a")).size(),
                    "every room must stay walkable from every other; a capped world is "
                            + "no use if it breaks into islands");
        }
    }

    /**
     * Every passage has to be walkable in both directions. One-way links exist, for a
     * world that has run out of spare doorways, but a door whose destination has no way
     * back strands the player and leaves them arriving in the middle of a room with no
     * doorway to stand beside.
     */
    @Test
    void everyDoorHasAWayBack() {
        RoomRegistry registry = withPlayers("a", "b");
        Random walk = new Random(5);

        for (int i = 0; i < 60; i++) {
            takeDoor(registry, "a", LOOP[walk.nextInt(LOOP.length)], i * 10L);
            settle(registry);

            for (Room room : registry.rooms()) {
                for (Direction side : Direction.values()) {
                    Room target = room.linkedRoom(side);
                    if (target == null) {
                        continue;
                    }
                    boolean leadsBack = false;
                    for (Direction back : Direction.values()) {
                        if (target.linkedRoom(back) == room) {
                            leadsBack = true;
                            break;
                        }
                    }
                    assertTrue(leadsBack,
                            room.id() + " " + side + " -> " + target.id()
                                    + " with no way back");
                }
            }
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
    void emptyRoomsDoNotKeepEachOtherAlive() {
        RoomRegistry registry = withPlayers("a");
        takeDoor(registry, "a", Direction.RIGHT, 10);
        takeDoor(registry, "a", Direction.UP, 20);
        takeDoor(registry, "a", Direction.DOWN, 30);

        registry.requestLeave("a");
        registry.processPending(30);
        settle(registry);

        assertEquals(0, registry.roomCount(),
                "liveness is computed outward from players, so a chain of empty "
                        + "neighbours cannot pin itself in memory");
    }

    @Test
    void discardingARoomAlsoDropsTheDoorsPointingAtIt() {
        RoomRegistry registry = withPlayers("a");

        // Walk far enough that the starting room is no longer anyone's neighbour.
        Room origin = registry.roomOf("a");
        for (int i = 0; i < 6 && registry.rooms().contains(origin); i++) {
            takeDoor(registry, "a", LOOP[i % LOOP.length], i * 10L);
            settle(registry);
        }

        if (registry.rooms().contains(origin)) {
            return; // A small world folded straight back; nothing was discarded.
        }
        for (Room room : registry.rooms()) {
            for (Direction side : Direction.values()) {
                assertNotSame(origin, room.linkedRoom(side),
                        "a link to a discarded room would keep it alive through the "
                                + "back door");
            }
        }
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
