package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.Command;
import com.example.battleroyal.game.core.Concealment;
import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.map.MapTemplates;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Walking into and out of a cabinet. CROSSROADS has a cabinet at (9,4) with open floor
 * on all four sides: (9,3) above, (9,5) below, (8,4) left, (10,4) right.
 */
class CabinetRulesTest {

    private static final Pos CABINET = new Pos(9, 4);
    private static final Pos ABOVE = new Pos(9, 3);
    private static final Pos BELOW = new Pos(9, 5);
    private static final Pos LEFT = new Pos(8, 4);
    private static final Pos RIGHT = new Pos(10, 4);
    private static final int TOGGLE = GameConstants.CABINET_TOGGLE_COOLDOWN_TICKS;

    private static Room room() {
        return new Room("room-1", MapTemplates.CROSSROADS.map());
    }

    private static Player put(Room room, String id, Pos pos) {
        Player player = new Player(id, id, pos, GameConstants.MAX_HP);
        room.add(player);
        return player;
    }

    private static void move(Room room, Player player, Direction dir, long tick) {
        RoomSimulator.apply(room, new Command.Move(player.id(), dir), tick);
    }

    /** Hides a player standing above the cabinet, at tick 0. */
    private static Player hiddenFromAbove(Room room) {
        Player player = put(room, "p", ABOVE);
        move(room, player, Direction.DOWN, 0);
        return player;
    }

    @Test
    void walkingIntoAnEmptyCabinetHidesYou() {
        Room room = room();
        Player player = hiddenFromAbove(room);

        assertTrue(player.inCabinet());
        assertEquals(CABINET, player.pos());
        assertEquals(Direction.DOWN, player.facing());
        assertEquals(Concealment.CABINET, player.concealment(room.map()));
        assertNull(ActionResolver.actionB(room, player), "a cabinet needs no button");
    }

    @Test
    void anOccupiedCabinetBlocksLikeAnyPlayer() {
        Room room = room();
        Player inside = hiddenFromAbove(room);
        Player other = put(room, "other", BELOW);

        move(room, other, Direction.UP, 0);

        assertEquals(BELOW, other.pos());
        assertEquals(Direction.UP, other.facing(), "a blocked step still turns you");
        assertFalse(other.inCabinet());
        assertEquals(CABINET, inside.pos());
    }

    @Test
    void leavingInsideTheToggleCooldownIsIgnored() {
        Room room = room();
        Player player = hiddenFromAbove(room);

        move(room, player, Direction.UP, GameConstants.MOVE_COOLDOWN_TICKS);
        assertTrue(player.inCabinet());

        move(room, player, Direction.UP, TOGGLE);
        assertFalse(player.inCabinet());
        assertEquals(ABOVE, player.pos());
        assertEquals(Direction.UP, player.facing());
        assertEquals(Concealment.NONE, player.concealment(room.map()));
    }

    @Test
    void youCanLeaveOutASide() {
        Room room = room();
        Player player = hiddenFromAbove(room);

        move(room, player, Direction.LEFT, TOGGLE);

        assertFalse(player.inCabinet());
        assertEquals(LEFT, player.pos());
        assertEquals(Direction.LEFT, player.facing());
    }

    @Test
    void aCabinetCannotBeWalkedThrough() {
        Room room = room();
        Player player = hiddenFromAbove(room);

        move(room, player, Direction.DOWN, TOGGLE);

        assertTrue(player.inCabinet());
        assertEquals(CABINET, player.pos());
    }

    @Test
    void holdingTheKeyYouWalkedInWithKeepsYouInside() {
        Room room = room();
        Player player = hiddenFromAbove(room);

        // The client repeats a held key every few ticks; the buffer replays it.
        for (long tick = 1; tick <= 40; tick++) {
            if (tick % 3 == 0) {
                move(room, player, Direction.DOWN, tick);
            }
            RoomSimulator.tick(room, tick);
        }

        assertTrue(player.inCabinet());
        assertEquals(Direction.DOWN, player.facing());
    }

    @Test
    void aBlockedSideIsNoWayOutButAnotherIs() {
        Room room = room();
        Player player = hiddenFromAbove(room);
        put(room, "blocker", ABOVE);

        move(room, player, Direction.UP, TOGGLE);
        assertTrue(player.inCabinet());

        move(room, player, Direction.RIGHT, TOGGLE + GameConstants.MOVE_COOLDOWN_TICKS);
        assertEquals(RIGHT, player.pos());
        assertFalse(player.inCabinet());
    }

    @Test
    void steppingStraightBackInWaitsForTheToggleCooldown() {
        Room room = room();
        Player player = hiddenFromAbove(room);
        move(room, player, Direction.UP, TOGGLE);

        long early = TOGGLE + GameConstants.MOVE_COOLDOWN_TICKS;
        move(room, player, Direction.DOWN, early);
        assertEquals(ABOVE, player.pos());

        move(room, player, Direction.DOWN, 2L * TOGGLE);
        assertTrue(player.inCabinet());
    }

    @Test
    void theOccupantIsTheOneABlindStabHits() {
        Room room = room();
        Player inside = hiddenFromAbove(room);
        Player attacker = put(room, "a", LEFT);
        attacker.face(Direction.RIGHT);

        CombatRules.Trace trace = CombatRules.trace(room, LEFT, Direction.RIGHT,
                GameConstants.KNIFE_RANGE);

        assertEquals(inside, trace.victim());
    }
}
