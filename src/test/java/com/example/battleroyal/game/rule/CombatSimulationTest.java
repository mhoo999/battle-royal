package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.ActionA;
import com.example.battleroyal.game.core.Command;
import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.map.MapTemplates;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pressing A: knife, pistol, reload, medkit, and what a death leaves behind. */
class CombatSimulationTest {

    private static final int FULL = GameConstants.MAX_HP;

    private static Room room() {
        return new Room("room-1", MapTemplates.CROSSROADS.map());
    }

    private static Player put(Room room, String id, Pos pos, Direction facing, ItemKind kind) {
        Player player = new Player(id, id, pos, FULL);
        player.face(facing);
        if (kind != null) {
            player.hold(new Item("i-" + id, kind, GameConstants.PISTOL_MAGAZINE));
        }
        room.add(player);
        return player;
    }

    private static void pressA(Room room, Player player, long tick) {
        RoomSimulator.apply(room, new Command.ActionA(player.id()), tick);
    }

    // --- Pistol -------------------------------------------------------------

    @Test
    void firingSpendsARoundWoundsTheTargetAndTellsTheRoom() {
        Room room = room();
        Player shooter = put(room, "s", new Pos(1, 1), Direction.RIGHT, ItemKind.PISTOL);
        Player target = put(room, "t", new Pos(4, 1), Direction.LEFT, null);
        room.drainEvents();

        pressA(room, shooter, 0);

        assertEquals(GameConstants.PISTOL_MAGAZINE - 1, shooter.heldItem().ammo());
        assertEquals(FULL - GameConstants.PISTOL_DAMAGE, target.hp());
        assertEquals(GameConstants.SCORE_HIT, shooter.score());

        List<GameEvent> events = room.drainEvents();
        GameEvent.Shot shot = assertInstanceOf(GameEvent.Shot.class, events.get(0));
        assertEquals(new Pos(1, 1), shot.path().getFirst());
        assertEquals(new GameEvent.Hit("s"), events.get(1));
        assertEquals(2, events.size());
    }

    @Test
    void aMissStillLeavesATrailButNoHit() {
        Room room = room();
        Player shooter = put(room, "s", new Pos(1, 1), Direction.RIGHT, ItemKind.PISTOL);

        pressA(room, shooter, 0);

        List<GameEvent> events = room.drainEvents();
        assertEquals(1, events.size());
        assertInstanceOf(GameEvent.Shot.class, events.getFirst());
        assertEquals(0, shooter.score());
    }

    @Test
    void aSecondShotInsideTheCooldownIsRejected() {
        Room room = room();
        Player shooter = put(room, "s", new Pos(1, 1), Direction.RIGHT, ItemKind.PISTOL);

        pressA(room, shooter, 0);
        pressA(room, shooter, GameConstants.PISTOL_COOLDOWN_TICKS - 1);
        assertEquals(GameConstants.PISTOL_MAGAZINE - 1, shooter.heldItem().ammo());

        pressA(room, shooter, GameConstants.PISTOL_COOLDOWN_TICKS);
        assertEquals(GameConstants.PISTOL_MAGAZINE - 2, shooter.heldItem().ammo(),
                "allowed on the tick the cooldown clears");
    }

    @Test
    void aAtZeroAmmoReloadsAndTheMagazineFillsOnlyWhenTheReloadFinishes() {
        Room room = room();
        Player shooter = put(room, "s", new Pos(1, 1), Direction.RIGHT, ItemKind.PISTOL);
        Item pistol = shooter.heldItem();
        for (int i = 0; i < GameConstants.PISTOL_MAGAZINE; i++) {
            pistol.spendAmmo();
        }
        assertEquals(ActionA.RELOAD, ActionResolver.actionA(shooter));
        room.drainEvents();

        pressA(room, shooter, 100);
        assertTrue(shooter.reloading());
        assertTrue(room.drainEvents().isEmpty(), "a reload fires nothing");

        long done = 100 + GameConstants.PISTOL_RELOAD_TICKS;
        RoomSimulator.tick(room, done - 1);
        assertEquals(0, pistol.ammo(), "still reloading");
        pressA(room, shooter, done - 1);
        assertEquals(0, pistol.ammo(), "A during a reload does nothing");

        RoomSimulator.tick(room, done);
        assertEquals(GameConstants.PISTOL_MAGAZINE, pistol.ammo());
        assertFalse(shooter.reloading());
    }

    @Test
    void aPressLandingOnTheTickTheReloadEndsFiresRatherThanReloadingAgain() {
        Room room = room();
        Player shooter = put(room, "s", new Pos(1, 1), Direction.RIGHT, ItemKind.PISTOL);
        for (int i = 0; i < GameConstants.PISTOL_MAGAZINE; i++) {
            shooter.heldItem().spendAmmo();
        }
        pressA(room, shooter, 0);

        // Commands are applied before tick(), so this press sees the reload first.
        pressA(room, shooter, GameConstants.PISTOL_RELOAD_TICKS);

        assertEquals(GameConstants.PISTOL_MAGAZINE - 1, shooter.heldItem().ammo());
    }

    @Test
    void aSwingThatMissesIsStillSeen() {
        Room room = room();
        Player attacker = put(room, "a", new Pos(1, 1), Direction.UP, ItemKind.PAN);

        pressA(room, attacker, 0);

        // Swinging at the wall above: nothing hit, but the room sees the swing.
        assertEquals(List.of(new GameEvent.Swing(new Pos(1, 1), new Pos(1, 0))),
                room.drainEvents());
    }

    @Test
    void anEmptyPistolSwappedAwayMidReloadAndTakenBackIsStillEmpty() {
        // Reported: drop an empty pistol, pick it up again, and it had six rounds.
        Room room = room();
        Player player = put(room, "p", new Pos(2, 1), Direction.RIGHT, ItemKind.PISTOL);
        Item pistol = player.heldItem();
        for (int i = 0; i < GameConstants.PISTOL_MAGAZINE; i++) {
            pistol.spendAmmo();
        }
        room.placeItem(new Pos(2, 1), new Item("i-spoon", ItemKind.SPOON, 0));

        pressA(room, player, 0);                       // A at zero ammo: reload starts
        RoomSimulator.apply(room, new Command.ActionB("p"), 1);   // swap for the spoon
        RoomSimulator.tick(room, 1 + GameConstants.LOOT_TICKS);
        assertSame(pistol, room.itemAt(new Pos(2, 1)));
        RoomSimulator.apply(room, new Command.ActionB("p"), 12);  // and back again
        RoomSimulator.tick(room, 12 + GameConstants.LOOT_TICKS);
        assertSame(pistol, player.heldItem());
        assertTrue(12 + GameConstants.LOOT_TICKS < GameConstants.PISTOL_RELOAD_TICKS,
                "retaken while the abandoned reload would still have been running");

        RoomSimulator.tick(room, GameConstants.PISTOL_RELOAD_TICKS + 10);

        assertEquals(0, pistol.ammo(), "dropping it abandoned the reload");
        assertEquals(ActionA.RELOAD, ActionResolver.actionA(player));
        pressA(room, player, GameConstants.PISTOL_RELOAD_TICKS + 11);
        assertTrue(player.reloading(), "and A is free to start a fresh one");
    }

    @Test
    void aPistolDroppedMidReloadIsNotRefilled() {
        Room room = room();
        Player shooter = put(room, "s", new Pos(1, 1), Direction.RIGHT, ItemKind.PISTOL);
        Item pistol = shooter.heldItem();
        for (int i = 0; i < GameConstants.PISTOL_MAGAZINE; i++) {
            pistol.spendAmmo();
        }
        pressA(room, shooter, 0);
        shooter.releaseItem();

        RoomSimulator.tick(room, GameConstants.PISTOL_RELOAD_TICKS);

        assertEquals(0, pistol.ammo());
        assertFalse(shooter.reloading());
    }

    // --- Knife --------------------------------------------------------------

    @Test
    void aKnifeStrikesTheAdjacentTileWithASwingNotATrail() {
        Room room = room();
        Player attacker = put(room, "a", new Pos(1, 1), Direction.RIGHT, ItemKind.KNIFE);
        Player target = put(room, "t", new Pos(2, 1), Direction.LEFT, null);

        pressA(room, attacker, 0);

        assertEquals(FULL - GameConstants.KNIFE_DAMAGE, target.hp());
        assertEquals(List.of(new GameEvent.Swing(new Pos(1, 1), new Pos(2, 1)),
                        new GameEvent.Hit("a")),
                room.drainEvents(), "a swing at the tile in front, never a shot trail");
        assertEquals(GameConstants.KNIFE_COOLDOWN_TICKS, attacker.nextActionTick());
    }

    @Test
    void aKnifeCanStabIntoACabinetBlind() {
        Room room = room();
        Player attacker = put(room, "a", new Pos(9, 3), Direction.DOWN, ItemKind.KNIFE);
        Player inside = put(room, "in", new Pos(9, 4), Direction.UP, null);
        inside.setInCabinet(true);

        pressA(room, attacker, 0);

        assertEquals(FULL - GameConstants.KNIFE_DAMAGE, inside.hp());
    }

    @Test
    void threeKnifeHitsKill() {
        Room room = room();
        Player attacker = put(room, "a", new Pos(1, 1), Direction.RIGHT, ItemKind.KNIFE);
        Player target = put(room, "t", new Pos(2, 1), Direction.LEFT, null);

        for (int i = 0; i < 3; i++) {
            pressA(room, attacker, (long) i * GameConstants.KNIFE_COOLDOWN_TICKS);
        }

        assertFalse(target.alive());
        assertEquals(1, attacker.kills());
    }

    // --- Death ---------------------------------------------------------------

    @Test
    void fourPistolHitsKillAndPayForEachHitPlusTheKill() {
        Room room = room();
        Player shooter = put(room, "s", new Pos(1, 1), Direction.RIGHT, ItemKind.PISTOL);
        Player target = new Player("t", "t", new Pos(4, 1), FULL, 40);
        room.add(target);

        for (int i = 0; i < 4; i++) {
            pressA(room, shooter, 100 + (long) i * GameConstants.PISTOL_COOLDOWN_TICKS);
        }
        long killTick = 100 + 3L * GameConstants.PISTOL_COOLDOWN_TICKS;

        assertFalse(target.alive());
        assertEquals(0, target.hp());
        assertEquals(1, shooter.kills());
        assertEquals(4 * GameConstants.SCORE_HIT + GameConstants.SCORE_KILL, shooter.score());

        GameEvent.Died died = room.drainEvents().stream()
                .filter(GameEvent.Died.class::isInstance)
                .map(GameEvent.Died.class::cast)
                .findFirst().orElseThrow();
        assertEquals("t", died.playerId());
        assertEquals(killTick - 40, died.survivedTicks());
    }

    @Test
    void theDeadCannotAct() {
        Room room = room();
        Player shooter = put(room, "s", new Pos(1, 1), Direction.RIGHT, ItemKind.PISTOL);
        shooter.takeDamage(FULL);

        pressA(room, shooter, 0);

        assertEquals(GameConstants.PISTOL_MAGAZINE, shooter.heldItem().ammo());
    }

    @Test
    void aDeadPlayersItemFallsWhereTheyDied() {
        Room room = room();
        Player attacker = put(room, "a", new Pos(1, 1), Direction.RIGHT, ItemKind.KNIFE);
        Player target = put(room, "t", new Pos(2, 1), Direction.LEFT, ItemKind.PISTOL);
        Item carried = target.heldItem();
        target.takeDamage(FULL - 1);

        pressA(room, attacker, 0);

        assertNull(target.heldItem());
        assertSame(carried, room.itemAt(new Pos(2, 1)), "the same instance, ammo intact");
    }

    @Test
    void aDropNeverOverwritesAnItemAlreadyOnTheFloor() {
        Room room = room();
        Player attacker = put(room, "a", new Pos(1, 1), Direction.RIGHT, ItemKind.KNIFE);
        Player target = put(room, "t", new Pos(2, 1), Direction.LEFT, ItemKind.PISTOL);
        Item carried = target.heldItem();
        Item lying = new Item("i-floor", ItemKind.MEDKIT, 0);
        room.placeItem(new Pos(2, 1), lying);
        target.takeDamage(FULL - 1);

        pressA(room, attacker, 0);

        assertSame(lying, room.itemAt(new Pos(2, 1)));
        long placed = Arrays.stream(Direction.values())
                .map(d -> room.itemAt(new Pos(2, 1).step(d)))
                .filter(item -> item == carried)
                .count();
        assertEquals(1, placed, "the carried item lands on a neighbouring tile");
    }

    @Test
    void anItemDroppedAtADoorLandsWhereBCanStillPickItUp() {
        Room room = room();
        // (0,7) is CROSSROADS' west door; (1,7) is the tile just inside it.
        Player attacker = put(room, "a", new Pos(2, 7), Direction.LEFT, ItemKind.KNIFE);
        Player target = put(room, "t", new Pos(1, 7), Direction.RIGHT, ItemKind.PISTOL);
        Item carried = target.heldItem();
        target.takeDamage(FULL - 1);

        pressA(room, attacker, 0);

        Pos landed = room.floorItems().entrySet().stream()
                .filter(e -> e.getValue() == carried)
                .map(java.util.Map.Entry::getKey)
                .findFirst().orElseThrow();
        assertNull(ActionResolver.doorSideAt(room, landed),
                "B would take the door instead of the item at " + landed);
    }

    @Test
    void aCabinetOccupantKilledBlindDropsBesideTheCabinet() {
        Room room = room();
        Player attacker = put(room, "a", new Pos(9, 3), Direction.DOWN, ItemKind.KNIFE);
        Player inside = put(room, "in", new Pos(9, 4), Direction.UP, ItemKind.MEDKIT);
        inside.setInCabinet(true);
        inside.takeDamage(FULL - 1);

        pressA(room, attacker, 0);

        assertFalse(inside.alive());
        assertFalse(inside.inCabinet());
        assertNull(room.itemAt(new Pos(9, 4)), "a cabinet tile holds no floor item");
        assertEquals(1, room.floorItems().size());
    }

    // --- Medkit ----------------------------------------------------------------

    @Test
    void aMedkitHealsCappedAtMaxAndIsUsedUp() {
        Room room = room();
        Player player = put(room, "p", new Pos(1, 1), Direction.RIGHT, ItemKind.MEDKIT);
        player.takeDamage(30);

        pressA(room, player, 0);

        assertEquals(FULL, player.hp(), "never above max");
        assertFalse(player.hasItem());
        assertEquals(GameConstants.MEDKIT_COOLDOWN_TICKS, player.nextActionTick());
    }

    @Test
    void healingIsTheOneThingACabinetAllows() {
        Room room = room();
        Player healer = put(room, "h", new Pos(9, 4), Direction.UP, ItemKind.MEDKIT);
        healer.setInCabinet(true);
        healer.takeDamage(60);
        Player gunner = put(room, "g", new Pos(5, 10), Direction.UP, ItemKind.PISTOL);
        gunner.setInCabinet(true);

        pressA(room, healer, 0);
        pressA(room, gunner, 0);

        assertEquals(FULL - 60 + GameConstants.MEDKIT_HEAL, healer.hp());
        assertEquals(GameConstants.PISTOL_MAGAZINE, gunner.heldItem().ammo(),
                "no shooting from a cabinet");
    }

    @Test
    void anEmptyHandDoesNothing() {
        Room room = room();
        Player player = put(room, "p", new Pos(1, 1), Direction.RIGHT, null);

        pressA(room, player, 0);

        assertTrue(room.drainEvents().isEmpty());
        assertEquals(0, player.nextActionTick());
    }
}
