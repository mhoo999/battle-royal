package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.map.MapTemplates;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Survival and room-entry score. */
class ScoreRulesTest {

    private static final int INTERVAL = GameConstants.SURVIVAL_INTERVAL_TICKS;
    private static final int CAP = GameConstants.ROOM_SCORE_RATE_CAP_TICKS;

    private static Player player() {
        return new Player("p", "p", new Pos(1, 1), GameConstants.MAX_HP);
    }

    private static void live(Player player, int ticks) {
        for (int i = 0; i < ticks; i++) {
            ScoreRules.accrueSurvival(player);
        }
    }

    // --- Survival -------------------------------------------------------------

    @Test
    void tenSecondsAlivePaysOnePoint() {
        Player player = player();

        live(player, INTERVAL - 1);
        assertEquals(0, player.score());

        assertTrue(ScoreRules.accrueSurvival(player));
        assertEquals(GameConstants.SCORE_SURVIVAL, player.score());

        live(player, INTERVAL);
        assertEquals(2 * GameConstants.SCORE_SURVIVAL, player.score());
    }

    @Test
    void timeInACabinetNeitherCountsNorResetsTheCount() {
        Player player = player();
        live(player, INTERVAL - 10);

        player.setInCabinet(true);
        live(player, 10 * INTERVAL);
        assertEquals(0, player.score(), "hiding must not pay");

        player.setInCabinet(false);
        live(player, 10);
        assertEquals(GameConstants.SCORE_SURVIVAL, player.score(),
                "the count picks up where it left off");
    }

    @Test
    void theRoomSimulatorPaysSurvivalAndMarksTheRoomForBroadcast() {
        Room room = new Room("room-1", MapTemplates.CROSSROADS.map());
        Player player = player();
        room.add(player);

        for (long tick = 1; tick <= INTERVAL; tick++) {
            room.clearDirty();
            RoomSimulator.tick(room, tick);
        }

        assertEquals(GameConstants.SCORE_SURVIVAL, player.score());
        assertTrue(room.dirty(), "the new score has to reach the client");
    }

    // --- Room entry -----------------------------------------------------------

    @Test
    void aFirstVisitPaysTen() {
        Player player = player();

        assertTrue(ScoreRules.enterRoom(player, "room-2", 0));
        assertEquals(GameConstants.SCORE_ROOM_ENTER, player.score());
    }

    @Test
    void goingBackToARoomPaysNothing() {
        Player player = player();
        ScoreRules.enterRoom(player, "room-2", 0);

        assertFalse(ScoreRules.enterRoom(player, "room-2", 10 * CAP));
        assertEquals(GameConstants.SCORE_ROOM_ENTER, player.score());
    }

    @Test
    void newRoomsPayAtMostOncePerThirtySeconds() {
        Player player = player();
        ScoreRules.enterRoom(player, "room-2", 0);

        assertFalse(ScoreRules.enterRoom(player, "room-3", CAP - 1));
        assertTrue(ScoreRules.enterRoom(player, "room-4", CAP));
        assertEquals(2 * GameConstants.SCORE_ROOM_ENTER, player.score());
    }

    @Test
    void aFirstVisitInsideTheCapIsSpentNotSaved() {
        Player player = player();
        ScoreRules.enterRoom(player, "room-2", 0);
        ScoreRules.enterRoom(player, "room-3", 1);

        assertFalse(ScoreRules.enterRoom(player, "room-3", 2 * CAP));
        assertEquals(GameConstants.SCORE_ROOM_ENTER, player.score());
    }
}
