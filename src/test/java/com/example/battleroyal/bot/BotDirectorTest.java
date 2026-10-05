package com.example.battleroyal.bot;

import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.loop.RoomRegistry;
import com.example.battleroyal.game.rule.GameConstants;
import com.example.battleroyal.game.rule.WorldSize;
import com.example.battleroyal.web.GameSessionService;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who keeps people company, and when (docs/GAME_RULES.md §10c). */
class BotDirectorTest {

    private static final int TARGET = 4;
    /** Long enough for every arrival gap to pass. */
    private static final int A_MINUTE = 60 * GameConstants.TICKS_PER_SECOND;

    private final RoomRegistry registry = new RoomRegistry(new Random(3));
    private final GameSessionService sessions = new GameSessionService();
    private long tick;
    private int mostBots;

    private BotDirector director(int target) {
        return new BotDirector(target, sessions, new Random(5));
    }

    /** The loop's order: arrivals, the drivers, commands, rooms, the dead, the size. */
    private void run(BotDirector director, int ticks) {
        run(director, ticks, () -> false);
    }

    /** As above, stopping early once {@code done} holds. Checks the invariants every tick. */
    private void run(BotDirector director, int ticks, java.util.function.BooleanSupplier done) {
        for (int i = 0; i < ticks && !done.getAsBoolean(); i++, tick++) {
            registry.processPending(tick);
            director.drive(registry, tick);
            registry.applyCommands(tick);
            registry.tickRooms(tick);
            registry.reapDead();
            registry.fitWorld();
            mostBots = Math.max(mostBots, registry.bots().size());
            assertTrue(registry.people() + registry.bots().size() <= TARGET || registry.people() >= TARGET,
                    "never more than the target together");
            if (registry.people() == 1) {
                assertEquals(WorldSize.forPopulation(1), registry.grid(),
                        "bots do not grow the island");
            }
        }
    }

    @Test
    void withNobodyOnTheIslandThereAreNoBots() {
        BotDirector director = director(TARGET);

        run(director, A_MINUTE);

        assertEquals(0, registry.bots().size());
    }

    @Test
    void onePersonGetsCompanyUpToTheTarget() {
        BotDirector director = director(TARGET);
        registry.requestJoin(sessions.nextPlayerId(), "~kang");

        run(director, 2 * A_MINUTE);

        assertEquals(TARGET - 1, mostBots, "three bots beside one person");
        for (String id : registry.bots()) {
            Player bot = registry.player(id);
            assertTrue(id.startsWith("p-"), "a bot's id is a player's id: " + id);
            assertTrue(bot.nickname().startsWith(GameConstants.UNRANKED_PREFIX),
                    "named like a guest: " + bot.nickname());
        }
    }

    @Test
    void theWorldStaysSizedForPeople() {
        BotDirector director = director(TARGET);
        registry.requestJoin(sessions.nextPlayerId(), "~kang");

        // run() checks the size every tick while one person is out.
        run(director, A_MINUTE, () -> registry.bots().size() == TARGET - 1);

        assertEquals(TARGET - 1, registry.bots().size());
    }

    @Test
    void whenTheLastPersonLeavesTheBotsGoToo() {
        BotDirector director = director(TARGET);
        String person = sessions.nextPlayerId();
        registry.requestJoin(person, "~kang");
        run(director, A_MINUTE, () -> !registry.bots().isEmpty());
        assertTrue(registry.bots().size() > 0);

        registry.requestLeave(person);
        run(director, 3);

        assertEquals(0, registry.bots().size());
        assertEquals(0, director.running());
    }

    @Test
    void offUnlessATargetIsSet() {
        BotDirector director = director(0);
        registry.requestJoin(sessions.nextPlayerId(), "~kang");

        run(director, 2 * A_MINUTE);

        assertEquals(0, registry.bots().size());
    }
}
