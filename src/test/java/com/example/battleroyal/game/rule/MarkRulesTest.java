package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.Exit;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.map.MapTemplates;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Errand marks (V2.2): standing on one reaches it; a mark in another room does not. */
class MarkRulesTest {

    @Test
    void standingOnAMarkReachesItAndItLeavesTheCompass() {
        Room room = new Room("room-1", MapTemplates.CROSSROADS.map());
        Player player = new Player("p", "p", new Pos(2, 1), GameConstants.MAX_HP);
        room.add(player);
        Exit here = new Exit("room-1", new Pos(3, 1), 0, 0);
        Exit elsewhere = new Exit("room-2", new Pos(3, 1), 1, 0);
        player.setMarks(List.of(here, elsewhere));

        RoomSimulator.tick(room, 0);
        assertEquals(0, player.marksReached(), "beside it is not on it");

        player.moveTo(new Pos(3, 1));
        RoomSimulator.tick(room, 1);

        assertEquals(1, player.marksReached());
        assertEquals(List.of(elsewhere), player.marks(), "the same tile in another room is not it");
        assertTrue(room.dirty());
    }
}
