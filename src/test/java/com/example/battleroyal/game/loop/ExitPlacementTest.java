package com.example.battleroyal.game.loop;

import com.example.battleroyal.game.core.Command;
import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.Exit;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.core.TileType;
import com.example.battleroyal.game.rule.ActionResolver;
import com.example.battleroyal.game.rule.GameConstants;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a player's exits go (D8) and the compass that points at them (D3). Distances are
 * checked against the door graph itself, not against the registry's own arithmetic.
 */
class ExitPlacementTest {

    private static RoomRegistry withPlayers(int count, long seed) {
        RoomRegistry registry = new RoomRegistry(new Random(seed), new Random(0), new Random(seed));
        for (int i = 0; i < count; i++) {
            registry.requestJoin("p" + i, "p" + i);
        }
        registry.processPending(0);
        registry.fitWorld();
        return registry;
    }

    /** Doors to walk from one room to every other, by breadth-first search. */
    private static Map<Room, Integer> doorsFrom(Room start) {
        Map<Room, Integer> distance = new HashMap<>();
        Deque<Room> frontier = new ArrayDeque<>();
        distance.put(start, 0);
        frontier.add(start);
        while (!frontier.isEmpty()) {
            Room room = frontier.removeFirst();
            for (Room next : room.neighbours()) {
                if (!distance.containsKey(next)) {
                    distance.put(next, distance.get(room) + 1);
                    frontier.addLast(next);
                }
            }
        }
        return distance;
    }

    private static Room roomById(RoomRegistry registry, String id) {
        return registry.rooms().stream().filter(room -> room.id().equals(id)).findFirst().orElseThrow();
    }

    /** Follows the bearing door by door: east or west first, then south or north. */
    private static Room follow(Room from, Exit exit) {
        Room room = from;
        for (int i = 0; i < Math.abs(exit.dx()); i++) {
            room = room.linkedRoom(exit.dx() > 0 ? Direction.RIGHT : Direction.LEFT);
        }
        for (int i = 0; i < Math.abs(exit.dy()); i++) {
            room = room.linkedRoom(exit.dy() > 0 ? Direction.DOWN : Direction.UP);
        }
        return room;
    }

    private static void assertCompassTrue(RoomRegistry registry, String playerId) {
        Room here = registry.roomOf(playerId);
        Map<Room, Integer> doors = doorsFrom(here);
        for (Exit exit : registry.player(playerId).exits()) {
            Room target = roomById(registry, exit.roomId());
            assertEquals(target, follow(here, exit), "the needle leads to the exit");
            assertEquals(doors.get(target), Math.abs(exit.dx()) + Math.abs(exit.dy()),
                    "and the shortest way round");
        }
    }

    @Test
    void twoExitsInRoomsOfTheirOwnOnPlainFloor() {
        for (long seed = 0; seed < 20; seed++) {
            RoomRegistry registry = withPlayers(1, seed);
            Player player = registry.player("p0");
            List<Exit> exits = player.exits();

            assertEquals(GameConstants.EXIT_DISTANCES.size(), exits.size());
            assertNotEquals(exits.get(0).roomId(), exits.get(1).roomId());
            for (Exit exit : exits) {
                Room room = roomById(registry, exit.roomId());
                assertEquals(TileType.FLOOR, room.map().tileAt(exit.at()));
                assertNull(ActionResolver.doorSideAt(room, exit.at()),
                        "B by a door means the door");
                assertNull(room.crateAt(exit.at()));
            }
        }
    }

    @Test
    void aSmallIslandPutsBothAtItsFarthestRoom() {
        RoomRegistry registry = withPlayers(1, 3);
        Map<Room, Integer> doors = doorsFrom(registry.roomOf("p0"));
        int farthest = doors.values().stream().max(Integer::compare).orElseThrow();

        assertEquals(3, registry.grid().rows(), "a lone player gets the smallest island");
        for (Exit exit : registry.player("p0").exits()) {
            assertEquals(Math.min(2, farthest), doors.get(roomById(registry, exit.roomId())));
        }
    }

    @Test
    void aBigIslandPutsThemTwoAndThreeDoorsAway() {
        RoomRegistry registry = withPlayers(6, 5);
        assertTrue(registry.grid().rows() >= 4, "big enough for three doors");

        Map<Room, Integer> doors = doorsFrom(registry.roomOf("p5"));
        List<Exit> exits = registry.player("p5").exits();

        assertEquals(2, doors.get(roomById(registry, exits.get(0).roomId())));
        assertEquals(3, doors.get(roomById(registry, exits.get(1).roomId())));
    }

    @Test
    void theCompassPointsTheShortWayRoundAndTurnsAsYouWalk() {
        RoomRegistry registry = withPlayers(4, 7);
        assertCompassTrue(registry, "p0");

        Room before = registry.roomOf("p0");
        registry.player("p0").moveTo(before.map().doorAt(Direction.RIGHT));
        registry.submit(new Command.ActionB("p0"));
        registry.applyCommands(1);

        assertNotEquals(before, registry.roomOf("p0"));
        assertCompassTrue(registry, "p0");
    }

    @Test
    void aShrinkingIslandNeverStrandsAnExit() {
        RoomRegistry registry = withPlayers(10, 11);
        for (int i = 1; i < 10; i++) {
            registry.requestLeave("p" + i);
        }
        registry.processPending(1);
        for (int i = 0; i < 5; i++) {
            registry.fitWorld();
        }

        Player stayed = registry.player("p0");
        assertEquals(GameConstants.EXIT_DISTANCES.size(), stayed.exits().size());
        for (Exit exit : stayed.exits()) {
            assertTrue(registry.rooms().stream().anyMatch(exit::in), "every exit is on the island");
        }
        assertCompassTrue(registry, "p0");
    }
}
