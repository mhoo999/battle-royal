package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.Command;
import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.core.Guard;
import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.map.MapTemplate;
import com.example.battleroyal.game.map.MapTemplates;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Outpost guards (V2.2): they turn, they need a moment to react, they see by the
 * players' rules, they fall to players' weapons, and they stand again later.
 */
class GuardRulesTest {

    /** A guard at (3,2) facing down its column, with a bush across that column at (3,4). */
    private static final MapTemplate POST = MapTemplate.parse("TEST_POST",
            "#######+#######",
            "#.............#",
            "#..G..........#",
            "#.............#",
            "#..bbb........#",
            "#..bbb....C...#",
            "#.............#",
            "+.............+",
            "#.....*.......#",
            "#.............#",
            "#....C....*...#",
            "#.............#",
            "#..*......*...#",
            "#.............#",
            "#######+#######");

    private static Room room() {
        return new Room("r", POST.map(), GameConstants.GUARD_HP);
    }

    private static Player put(Room room, String id, Pos pos) {
        Player player = new Player(id, id, pos, GameConstants.MAX_HP);
        room.add(player);
        return player;
    }

    private static List<GameEvent> run(Room room, long from, long to) {
        for (long tick = from; tick < to; tick++) {
            RoomSimulator.tick(room, tick);
        }
        return room.drainEvents();
    }

    @Test
    void someoneWhoStaysInTheLineIsShot() {
        Room room = room();
        Player standing = put(room, "p", new Pos(3, 7));

        List<GameEvent> events = run(room, 0, GameConstants.GUARD_REACTION_TICKS + 1);

        assertEquals(GameConstants.MAX_HP - GameConstants.GUARD_DAMAGE, standing.hp());
        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.Shot shot
                && shot.path().getFirst().equals(new Pos(3, 2))), "the shot starts at the guard");
    }

    @Test
    void aDashAcrossTheLineGetsThrough() {
        Room room = room();
        Player runner = put(room, "p", new Pos(3, 7));

        run(room, 0, GameConstants.GUARD_REACTION_TICKS - 2);
        runner.moveTo(new Pos(4, 7));
        run(room, GameConstants.GUARD_REACTION_TICKS - 2, GameConstants.GUARD_REACTION_TICKS * 3);

        assertEquals(GameConstants.MAX_HP, runner.hp());
    }

    @Test
    void aBushHidesYouFromAGuardAsFromAPlayer() {
        Room room = room();
        Player hiding = put(room, "p", new Pos(3, 4));

        run(room, 0, GameConstants.GUARD_TURN_TICKS - 1);

        assertEquals(GameConstants.MAX_HP, hiding.hp());
    }

    @Test
    void aGuardTurnsAQuarterClockwiseEveryFewSeconds() {
        Room room = room();
        put(room, "p", new Pos(12, 12));
        Guard guard = room.guards().getFirst();

        run(room, 0, GameConstants.GUARD_TURN_TICKS + 1);

        assertEquals(Direction.LEFT, guard.facing(), "down, then left");
    }

    @Test
    void aGuardShotDownDropsRoundsAndCountsForNothing() {
        Room room = room();
        // Beside the guard's line, facing along row 2 at it.
        Player shooter = put(room, "p", new Pos(6, 2));
        shooter.face(Direction.LEFT);
        shooter.hold(new Item("i-1", ItemKind.PISTOL, GameConstants.PISTOL_MAGAZINE));

        // 60 health against 25 a shot: three shots.
        RoomSimulator.apply(room, new Command.ActionA("p"), 100);
        RoomSimulator.apply(room, new Command.ActionA("p"), 200);
        assertTrue(room.guards().getFirst().alive(), "two are not enough");
        RoomSimulator.apply(room, new Command.ActionA("p"), 300);

        Guard guard = room.guards().getFirst();
        assertFalse(guard.alive());
        assertEquals(0, shooter.kills(), "a guard is not a player");
        assertEquals(1, shooter.soldiersDowned(), "but it counts for a soldier errand");
        assertEquals(ItemKind.ROUNDS, room.crateAt(new Pos(3, 2)).get(0).kind());
        assertTrue(room.drainEvents().stream().anyMatch(e -> e instanceof GameEvent.Fell));
        assertNull(room.livingGuardAt(new Pos(3, 2)));
    }

    @Test
    void aGuardStandsAgainAfterTheOutpostSitsEmpty() {
        Room room = room();
        Guard guard = room.guards().getFirst();
        guard.takeDamage(GameConstants.GUARD_HP);
        Player visitor = put(room, "p", new Pos(12, 12));

        run(room, 0, GameConstants.GUARD_RESPAWN_TICKS + 10);
        assertFalse(guard.alive(), "not while someone is here");

        room.remove(visitor.id());
        run(room, 0, GameConstants.GUARD_RESPAWN_TICKS);

        assertTrue(guard.alive());
        assertEquals(Direction.DOWN, guard.facing(), "back as it first stood");
    }

    @Test
    void aDeathByAGuardSaysSoAndCreditsNobody() {
        Room room = room();
        Player standing = put(room, "p", new Pos(3, 7));
        standing.takeDamage(GameConstants.MAX_HP - GameConstants.GUARD_DAMAGE);

        List<GameEvent> events = run(room, 0, GameConstants.GUARD_REACTION_TICKS + 1);

        GameEvent.Died died = events.stream().filter(GameEvent.Died.class::isInstance)
                .map(GameEvent.Died.class::cast).findFirst().orElseThrow();
        assertTrue(died.byGuard());
        assertNull(died.killerNickname());
    }

    @Test
    void theOutpostLayoutHasTwoGuardsAndMilitaryCrates() {
        Room outpost = new Room("o", MapTemplates.OUTPOST.map(), GameConstants.GUARD_HP);
        assertEquals(2, outpost.guards().size());
        assertTrue(outpost.map().isOutpost());
        assertFalse(MapTemplates.ALL.contains(MapTemplates.OUTPOST), "placed on its own roll");

        java.util.Random random = new java.util.Random(3);
        for (int i = 0; i < 200; i++) {
            assertTrue(ItemSpawns.pickMilitary(random) != null, "never an empty roll");
        }
        assertEquals(100, ItemSpawns.MILITARY_TOTAL, "weights are percentages");
    }
}
