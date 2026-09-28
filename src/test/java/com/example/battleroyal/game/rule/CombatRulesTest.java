package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.map.MapTemplates;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Hit resolution on CROSSROADS: bush at x3-5 y2-4, cabinet at (9,4), a solid core at
 * x6-8 y6-8, open floor along row 1.
 */
class CombatRulesTest {

    private static Room room() {
        return new Room("room-1", MapTemplates.CROSSROADS.map());
    }

    private static Player put(Room room, String id, Pos pos) {
        Player player = new Player(id, id, pos, GameConstants.MAX_HP);
        room.add(player);
        return player;
    }

    @Test
    void aTargetAtExactlyMaxRangeIsHit() {
        Room room = room();
        Player target = put(room, "t", new Pos(1 + GameConstants.PISTOL_RANGE, 1));

        CombatRules.Trace trace = CombatRules.trace(room, new Pos(1, 1), Direction.RIGHT,
                GameConstants.PISTOL_RANGE);

        assertSame(target, trace.victim());
        assertEquals(GameConstants.PISTOL_RANGE + 1, trace.path().size(),
                "the shooter's tile plus every tile out to the target");
    }

    @Test
    void aTargetOneTileBeyondRangeIsMissed() {
        Room room = room();
        put(room, "t", new Pos(2 + GameConstants.PISTOL_RANGE, 1));

        CombatRules.Trace trace = CombatRules.trace(room, new Pos(1, 1), Direction.RIGHT,
                GameConstants.PISTOL_RANGE);

        assertFalse(trace.hit());
        assertEquals(GameConstants.PISTOL_RANGE + 1, trace.path().size());
    }

    @Test
    void thePathStartsOnTheShootersOwnTile() {
        CombatRules.Trace trace = CombatRules.trace(room(), new Pos(1, 1), Direction.RIGHT, 3);

        assertEquals(List.of(new Pos(1, 1), new Pos(2, 1), new Pos(3, 1), new Pos(4, 1)),
                trace.path());
    }

    @Test
    void aWallStopsTheShot() {
        Room room = room();
        put(room, "t", new Pos(10, 7));

        // (5,7) is floor, (6,7) is the solid core, the target stands behind it.
        CombatRules.Trace trace = CombatRules.trace(room, new Pos(4, 7), Direction.RIGHT,
                GameConstants.PISTOL_RANGE);

        assertFalse(trace.hit());
        assertEquals(List.of(new Pos(4, 7), new Pos(5, 7)), trace.path(),
                "blocking terrain is not part of the path");
    }

    @Test
    void anEmptyCabinetStopsTheShotAndProtectsWhoeverIsBehindIt() {
        Room room = room();
        put(room, "behind", new Pos(9, 5));

        CombatRules.Trace trace = CombatRules.trace(room, new Pos(9, 1), Direction.DOWN,
                GameConstants.PISTOL_RANGE);

        assertFalse(trace.hit());
        assertEquals(List.of(new Pos(9, 1), new Pos(9, 2), new Pos(9, 3)), trace.path());
    }

    @Test
    void aCabinetStopsTheShotButWoundsItsOccupant() {
        Room room = room();
        Player inside = put(room, "inside", new Pos(9, 4));
        inside.setInCabinet(true);
        put(room, "behind", new Pos(9, 5));

        CombatRules.Trace trace = CombatRules.trace(room, new Pos(9, 1), Direction.DOWN,
                GameConstants.PISTOL_RANGE);

        assertSame(inside, trace.victim(), "attacked blind, but attacked");
    }

    @Test
    void aBushConcealsButDoesNotCover() {
        Room room = room();
        Player hiding = put(room, "hiding", new Pos(4, 3));

        CombatRules.Trace trace = CombatRules.trace(room, new Pos(1, 3), Direction.RIGHT,
                GameConstants.PISTOL_RANGE);

        assertSame(hiding, trace.victim());
        assertEquals(new Pos(4, 3), trace.path().getLast(),
                "decided: the path ends on the victim, even one in a bush");
    }

    @Test
    void anEmptyBushLetsTheShotThrough() {
        Room room = room();
        Player beyond = put(room, "beyond", new Pos(8, 3));

        CombatRules.Trace trace = CombatRules.trace(room, new Pos(1, 3), Direction.RIGHT,
                GameConstants.PISTOL_RANGE);

        assertSame(beyond, trace.victim());
    }

    @Test
    void theFirstPlayerInLineTakesTheShot() {
        Room room = room();
        Player near = put(room, "near", new Pos(3, 1));
        put(room, "far", new Pos(5, 1));

        CombatRules.Trace trace = CombatRules.trace(room, new Pos(1, 1), Direction.RIGHT,
                GameConstants.PISTOL_RANGE);

        assertSame(near, trace.victim());
    }

    @Test
    void theDeadDoNotStopBullets() {
        Room room = room();
        Player corpse = put(room, "corpse", new Pos(3, 1));
        corpse.takeDamage(GameConstants.MAX_HP);
        Player behind = put(room, "behind", new Pos(5, 1));

        CombatRules.Trace trace = CombatRules.trace(room, new Pos(1, 1), Direction.RIGHT,
                GameConstants.PISTOL_RANGE);

        assertSame(behind, trace.victim());
    }

    @Test
    void aKnifeReachesOneTileAndNoFurther() {
        Room room = room();
        put(room, "t", new Pos(3, 1));

        assertNull(CombatRules.trace(room, new Pos(1, 1), Direction.RIGHT,
                GameConstants.KNIFE_RANGE).victim());
        assertFalse(CombatRules.trace(room, new Pos(2, 1), Direction.LEFT,
                GameConstants.KNIFE_RANGE).hit(), "facing matters");
    }
}
