package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.Command;
import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.map.MapTemplates;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoomSimulatorTest {

    private static final Pos OPEN = new Pos(1, 1);

    private static Room room() {
        return new Room("room-1", MapTemplates.CROSSROADS.map());
    }

    private static Player join(Room room, Pos pos) {
        Player player = new Player("p1", "p1", pos, GameConstants.MAX_HP);
        room.add(player);
        return player;
    }

    @Test
    void aReadyMoveIsAppliedImmediately() {
        Room room = room();
        Player player = join(room, OPEN);

        RoomSimulator.apply(room, new Command.Move("p1", Direction.RIGHT), 0);

        assertEquals(new Pos(2, 1), player.pos());
        assertEquals(GameConstants.MOVE_COOLDOWN_TICKS, player.nextMoveTick());
    }

    @Test
    void aMoveDuringCooldownIsHeldAndFiresTheTickItClears() {
        Room room = room();
        Player player = join(room, OPEN);

        RoomSimulator.apply(room, new Command.Move("p1", Direction.RIGHT), 0);
        assertEquals(new Pos(2, 1), player.pos());

        // Arrives one tick into a three tick cooldown.
        RoomSimulator.apply(room, new Command.Move("p1", Direction.RIGHT), 1);
        assertEquals(new Pos(2, 1), player.pos(), "must not jump the cooldown");

        RoomSimulator.tick(room, 2);
        assertEquals(new Pos(2, 1), player.pos(), "still cooling down");

        RoomSimulator.tick(room, GameConstants.MOVE_COOLDOWN_TICKS);
        assertEquals(new Pos(3, 1), player.pos(),
                "the held input should resume the moment the cooldown clears");
    }

    @Test
    void onlyTheMostRecentHeldMoveSurvives() {
        Room room = room();
        Player player = join(room, OPEN);

        RoomSimulator.apply(room, new Command.Move("p1", Direction.RIGHT), 0);
        RoomSimulator.apply(room, new Command.Move("p1", Direction.RIGHT), 1);
        RoomSimulator.apply(room, new Command.Move("p1", Direction.DOWN), 1);

        RoomSimulator.tick(room, GameConstants.MOVE_COOLDOWN_TICKS);

        assertEquals(new Pos(2, 2), player.pos(), "the later input wins");
        assertEquals(Direction.DOWN, player.facing());

        // And nothing else is queued behind it.
        RoomSimulator.tick(room, GameConstants.MOVE_COOLDOWN_TICKS * 2);
        assertEquals(new Pos(2, 2), player.pos(),
                "one slot only, so releasing a key stops the player");
    }

    @Test
    void aStaleHeldMoveIsDiscardedRatherThanFiringLate() {
        Room room = room();
        Player player = join(room, OPEN);

        RoomSimulator.apply(room, new Command.Move("p1", Direction.RIGHT), 0);
        RoomSimulator.apply(room, new Command.Move("p1", Direction.RIGHT), 1);

        RoomSimulator.tick(room, 1 + GameConstants.MOVE_BUFFER_TICKS + 1);

        assertEquals(new Pos(2, 1), player.pos(),
                "an input the player has long since abandoned must not move them");
    }

    // --- Doors ------------------------------------------------------------

    @Test
    void pressingBBesideADoorRequestsTransitThroughThatWall() {
        Room room = room();
        Pos door = room.map().doorAt(Direction.LEFT);
        Player player = join(room, door.step(Direction.RIGHT));

        RoomSimulator.DoorTransit transit =
                RoomSimulator.apply(room, new Command.ActionB("p1"), 0);

        assertNotNull(transit);
        assertEquals(Direction.LEFT, transit.side());
        assertEquals("p1", transit.playerId());
    }

    @Test
    void pressingBStandingOnTheDoorAlsoWorks() {
        Room room = room();
        Player player = join(room, room.map().doorAt(Direction.UP));

        RoomSimulator.DoorTransit transit =
                RoomSimulator.apply(room, new Command.ActionB("p1"), 0);

        assertNotNull(transit);
        assertEquals(Direction.UP, transit.side());
    }

    @Test
    void pressingBAwayFromAnyDoorDoesNothing() {
        Room room = room();
        join(room, OPEN);

        assertNull(RoomSimulator.apply(room, new Command.ActionB("p1"), 0));
    }

    @Test
    void commandsFromAnUnknownOrDeadPlayerAreDropped() {
        Room room = room();
        Player player = join(room, OPEN);

        assertNull(RoomSimulator.apply(room, new Command.Move("ghost", Direction.RIGHT), 0));

        player.takeDamage(GameConstants.MAX_HP);
        RoomSimulator.apply(room, new Command.Move("p1", Direction.RIGHT), 0);
        assertEquals(OPEN, player.pos(), "the dead do not move");
    }
}
