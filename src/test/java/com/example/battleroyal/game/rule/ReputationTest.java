package com.example.battleroyal.game.rule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReputationTest {

    @Test
    void harderErrandsAddMoreStanding() {
        assertTrue(Reputation.forErrand(Quests.Difficulty.EASY) > 0);
        assertTrue(Reputation.forErrand(Quests.Difficulty.NORMAL)
                > Reputation.forErrand(Quests.Difficulty.EASY));
        assertTrue(Reputation.forErrand(Quests.Difficulty.HARD)
                > Reputation.forErrand(Quests.Difficulty.NORMAL));
    }

    @Test
    void levelsStartAtOneAndRiseAtEachThreshold() {
        assertEquals(0, Reputation.LEVEL_AT.getFirst(), "nobody starts below level 1");
        assertEquals(1, Reputation.level(0));
        for (int level = 1; level < Reputation.LEVEL_AT.size(); level++) {
            int at = Reputation.LEVEL_AT.get(level);
            assertEquals(level, Reputation.level(at - 1));
            assertEquals(level + 1, Reputation.level(at));
            assertEquals(at, Reputation.nextAt(at - 1));
        }
    }

    @Test
    void theTopLevelHasNoNext() {
        int top = Reputation.LEVEL_AT.getLast();
        assertEquals(Reputation.LEVEL_AT.size(), Reputation.level(top + 1000));
        assertNull(Reputation.nextAt(top));
    }
}
