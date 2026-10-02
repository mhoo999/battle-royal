package com.example.battleroyal.game.rule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSizeTest {

    private static WorldSize.Grid grid(int columns, int rows) {
        return new WorldSize.Grid(columns, rows);
    }

    @Test
    void oneOrTwoPlayersGetThreeByThree() {
        assertEquals(grid(3, 3), WorldSize.forPopulation(0));
        assertEquals(grid(3, 3), WorldSize.forPopulation(1));
        assertEquals(grid(3, 3), WorldSize.forPopulation(2));
    }

    @Test
    void theWorldGrowsAboutSevenRoomsForEveryOtherPlayer() {
        assertEquals(grid(4, 4), WorldSize.forPopulation(3));
        assertEquals(grid(5, 5), WorldSize.forPopulation(4));
        assertEquals(grid(6, 5), WorldSize.forPopulation(5));
        assertEquals(grid(6, 6), WorldSize.forPopulation(6));
    }

    /**
     * The registry relies on this: a resize either keeps every room of the old grid or
     * drops only rooms outside the new one, never both.
     */
    @Test
    void eachSizeContainsTheOneBefore() {
        WorldSize.Grid previous = WorldSize.forPopulation(0);
        for (int players = 1; players <= 200; players++) {
            WorldSize.Grid next = WorldSize.forPopulation(players);
            assertTrue(next.columns() >= previous.columns() && next.rows() >= previous.rows(),
                    previous + " -> " + next);
            assertTrue(next.columns() >= next.rows());
            previous = next;
        }
    }

    @Test
    void thereIsAlwaysAnEmptyRoomForTheNextLogin() {
        for (int players = 0; players <= 200; players++) {
            assertTrue(WorldSize.forPopulation(players + 1).area() > players);
        }
    }
}
