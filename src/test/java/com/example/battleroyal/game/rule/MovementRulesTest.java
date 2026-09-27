package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.GridMap;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.map.MapTemplates;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementRulesTest {

    private static final GridMap MAP = MapTemplates.CROSSROADS.map();

    private static MovementRules.Outcome move(Pos from, Direction dir) {
        return MovementRules.resolve(MAP, from, dir, p -> false);
    }

    @Test
    void movesOneTileOntoOpenFloor() {
        MovementRules.Outcome out = move(new Pos(1, 1), Direction.RIGHT);

        assertTrue(out.moved());
        assertEquals(new Pos(2, 1), out.pos());
        assertEquals(Direction.RIGHT, out.facing());
    }

    @Test
    void movesOneTileAtATimeAndNeverFarther() {
        Pos start = new Pos(1, 1);
        MovementRules.Outcome out = move(start, Direction.DOWN);

        assertEquals(1, Math.abs(out.pos().y() - start.y()));
        assertEquals(start.x(), out.pos().x());
    }

    @Test
    void wallBlocksTheStepButStillTurnsThePlayer() {
        Pos againstWall = new Pos(1, 1);
        MovementRules.Outcome out = move(againstWall, Direction.LEFT);

        assertFalse(out.moved(), "(0,1) is the wall border");
        assertEquals(againstWall, out.pos());
        assertEquals(Direction.LEFT, out.facing(),
                "a blocked input must still cost a turn, which is what exposes your back");
    }

    @Test
    void cabinetBlocksTheStepBecauseItIsEnteredWithB() {
        Pos cabinet = MAP.cabinets().getFirst();
        Pos beside = cabinet.step(Direction.LEFT);
        MovementRules.Outcome out = move(beside, Direction.RIGHT);

        assertFalse(out.moved(), "walking into a cabinet must not enter it");
        assertEquals(beside, out.pos());
        assertEquals(Direction.RIGHT, out.facing());
    }

    @Test
    void anotherPlayerBlocksTheStepSoTwoNeverShareATile() {
        Pos from = new Pos(1, 1);
        Pos taken = new Pos(2, 1);
        Set<Pos> occupied = Set.of(taken);

        MovementRules.Outcome out =
                MovementRules.resolve(MAP, from, Direction.RIGHT, occupied::contains);

        assertFalse(out.moved());
        assertEquals(from, out.pos());
    }

    @Test
    void bushIsWalkedIntoRatherThanEntered() {
        Pos bush = new Pos(3, 2);
        Pos beside = new Pos(2, 2);
        assertTrue(MAP.isBush(bush), "fixture assumption");

        MovementRules.Outcome out = move(beside, Direction.RIGHT);

        assertTrue(out.moved(), "concealment costs a normal step, not a B action");
        assertEquals(bush, out.pos());
    }

    @Test
    void doorTileIsWalkable() {
        Pos door = MAP.doorAt(Direction.LEFT);
        Pos beside = door.step(Direction.RIGHT);

        MovementRules.Outcome out = move(beside, Direction.LEFT);

        assertTrue(out.moved());
        assertEquals(door, out.pos());
    }

    @Test
    void facingAlwaysFollowsTheInputRegardlessOfTheResult() {
        Pos corner = new Pos(1, 1);
        for (Direction dir : Direction.values()) {
            assertEquals(dir, move(corner, dir).facing(), "input " + dir);
        }
    }

    // --- Cooldowns --------------------------------------------------------

    @Test
    void cooldownIsInclusiveOnTheTickItBecomesReady() {
        assertFalse(MovementRules.ready(9, 10), "still cooling down");
        assertTrue(MovementRules.ready(10, 10), "ready exactly on schedule");
        assertTrue(MovementRules.ready(11, 10), "and after");
    }

    @Test
    void moveCooldownIsThreeTicks() {
        long now = 100;
        long next = MovementRules.nextReadyTick(now, GameConstants.MOVE_COOLDOWN_TICKS);

        assertEquals(103, next);
        assertFalse(MovementRules.ready(102, next), "2 ticks is too soon");
        assertTrue(MovementRules.ready(103, next));
        assertEquals(150, GameConstants.MOVE_COOLDOWN_TICKS * GameConstants.TICK_MS,
                "150ms: above mobile RTT since there is no prediction, but not so slow "
                        + "that crossing a room feels like wading");
    }

    @Test
    void theInputBufferOutlivesOneCooldownSoHeldInputNeverSkipsABeat() {
        assertTrue(GameConstants.MOVE_BUFFER_TICKS >= GameConstants.MOVE_COOLDOWN_TICKS,
                "a move held back by the cooldown must still be valid when it clears, "
                        + "otherwise buffering would not fix the stutter it exists for");
    }
}
