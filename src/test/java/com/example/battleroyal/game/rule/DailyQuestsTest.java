package com.example.battleroyal.game.rule;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The day's three dailies: fixed for a day and an account, varied across days. */
class DailyQuestsTest {

    @Test
    void aDayIsTheSameEveryTimeItIsAskedAndDaysDiffer() {
        LocalDate day = LocalDate.of(2026, 10, 5);
        assertEquals(DailyQuests.of(7, day), DailyQuests.of(7, day));

        Set<String> thirds = new HashSet<>();
        Set<Object> junk = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            var dailies = DailyQuests.of(7, day.plusDays(i));
            assertEquals(DailyQuests.PER_DAY, dailies.size());
            thirds.add(dailies.get(2).title());
            junk.addAll(dailies.get(0).deliver().keySet());
        }
        assertTrue(thirds.size() > 1, "the normal one changes from day to day");
        assertTrue(junk.size() > 1, "so does the junk asked for");
    }
}
