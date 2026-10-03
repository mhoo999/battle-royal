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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pressing A: knife, pistol, medkit, and what a death leaves behind. */
class CombatSimulationTest {

    private static final int FULL = GameConstants.MAX_HP;

    private static Room room() {
        return new Room("room-1", MapTemplates.CROSSROADS.map());
    }

    private static Player put(Room room, String id, Pos pos, Direction facing, ItemKind kind) {
        Player player = new Player(id, id, pos, FULL);
        player.face(facing);
        if (kind != null) {
            player.hold(new Item("i-" + id, kind, Weapons.startingAmmo(kind)));
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
    void theLastRoundIsFiredAndTheEmptyPistolStaysInHand() {
        Room room = room();
        Player shooter = put(room, "s", new Pos(1, 1), Direction.RIGHT, ItemKind.PISTOL);
        for (int i = 0; i < GameConstants.PISTOL_MAGAZINE - 1; i++) {
            shooter.heldItem().spendAmmo();
        }
        assertEquals(ActionA.FIRE, ActionResolver.actionA(shooter));
        room.drainEvents();

        pressA(room, shooter, 0);

        assertInstanceOf(GameEvent.Shot.class, room.drainEvents().getFirst(),
                "the last round is still a shot");
        assertEquals(ItemKind.PISTOL, shooter.heldItem().kind(), "an empty gun is kept");
        assertEquals(0, shooter.heldItem().ammo());
        assertNull(ActionResolver.actionA(shooter), "and does nothing until it is loaded");
    }

    @Test
    void anEmptyPistolLoadsFromABundleInTheInventory() {
        Room room = room();
        Player shooter = put(room, "s", new Pos(1, 1), Direction.RIGHT, ItemKind.PISTOL);
        for (int i = 0; i < GameConstants.PISTOL_MAGAZINE; i++) {
            shooter.heldItem().spendAmmo();
        }
        shooter.setSlot(2, new Item("r", ItemKind.ROUNDS, GameConstants.PISTOL_MAGAZINE + 2));
        assertEquals(ActionA.RELOAD, ActionResolver.actionA(shooter));

        pressA(room, shooter, 0);

        assertEquals(GameConstants.PISTOL_MAGAZINE, shooter.heldItem().ammo(), "full");
        assertEquals(2, shooter.slot(2).ammo(), "what did not fit stays in the bundle");
        assertEquals(ActionA.FIRE, ActionResolver.actionA(shooter));
        pressA(room, shooter, GameConstants.RELOAD_TICKS - 1);
        assertEquals(GameConstants.PISTOL_MAGAZINE, shooter.heldItem().ammo(),
                "loading takes a moment before the first shot");
        pressA(room, shooter, GameConstants.RELOAD_TICKS);
        assertEquals(GameConstants.PISTOL_MAGAZINE - 1, shooter.heldItem().ammo());
    }

    @Test
    void anEmptiedBundleIsGoneAndTheWrongAmmunitionLoadsNothing() {
        Room room = room();
        Player shooter = put(room, "s", new Pos(1, 1), Direction.RIGHT, ItemKind.CROSSBOW);
        for (int i = 0; i < GameConstants.CROSSBOW_BOLTS; i++) {
            shooter.heldItem().spendAmmo();
        }
        shooter.setSlot(1, new Item("r", ItemKind.ROUNDS, 6));
        assertNull(ActionResolver.actionA(shooter), "pistol rounds do not fit a crossbow");

        shooter.setSlot(2, new Item("b", ItemKind.BOLTS, 1));
        pressA(room, shooter, 0);

        assertEquals(1, shooter.heldItem().ammo(), "one bolt was all there was");
        assertNull(shooter.slot(2), "the empty bundle is gone");
        assertEquals(6, shooter.slot(1).ammo(), "the rounds were not touched");
    }

    @Test
    void noLoadingInsideACabinet() {
        Room room = room();
        Player shooter = put(room, "s", new Pos(1, 1), Direction.RIGHT, ItemKind.PISTOL);
        for (int i = 0; i < GameConstants.PISTOL_MAGAZINE; i++) {
            shooter.heldItem().spendAmmo();
        }
        shooter.setSlot(1, new Item("r", ItemKind.ROUNDS, 6));
        shooter.setInCabinet(true);

        assertNull(ActionResolver.actionA(shooter), "a cabinet allows a medkit and nothing else");
    }

    @Test
    void aKillWithTheLastRoundStillNamesThePistol() {
        Room room = room();
        Player shooter = put(room, "s", new Pos(1, 1), Direction.RIGHT, ItemKind.PISTOL);
        for (int i = 0; i < GameConstants.PISTOL_MAGAZINE - 1; i++) {
            shooter.heldItem().spendAmmo();
        }
        Player target = new Player("t", "t", new Pos(4, 1), GameConstants.PISTOL_DAMAGE);
        room.add(target);
        room.drainEvents();

        pressA(room, shooter, 0);

        assertFalse(target.alive());
        GameEvent.Died died = room.drainEvents().stream()
                .filter(GameEvent.Died.class::isInstance)
                .map(GameEvent.Died.class::cast)
                .findFirst().orElseThrow();
        assertEquals(ItemKind.PISTOL, died.weapon());
        assertEquals(0, shooter.heldItem().ammo(), "the empty pistol is still in hand");
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

    // --- Bare hands, bat, crossbow -------------------------------------------

    @Test
    void emptyHandsPunch() {
        Room room = room();
        Player attacker = put(room, "a", new Pos(1, 1), Direction.RIGHT, null);
        Player target = put(room, "t", new Pos(2, 1), Direction.LEFT, null);
        assertEquals(ActionA.ATTACK, ActionResolver.actionA(attacker));

        pressA(room, attacker, 0);

        assertEquals(FULL - GameConstants.FIST_DAMAGE, target.hp());
        assertEquals(List.of(new GameEvent.Swing(new Pos(1, 1), new Pos(2, 1)),
                        new GameEvent.Hit("a")),
                room.drainEvents(), "a punch is a swing like any other");
        assertEquals(GameConstants.FIST_COOLDOWN_TICKS, attacker.nextActionTick());
    }

    @Test
    void noPunchingFromInsideACabinet() {
        Room room = room();
        Player inside = put(room, "in", new Pos(9, 4), Direction.UP, null);
        inside.setInCabinet(true);

        assertNull(ActionResolver.actionA(inside));
    }

    @Test
    void aKillWithBareHandsNamesNoWeapon() {
        Room room = room();
        Player attacker = put(room, "a", new Pos(1, 1), Direction.RIGHT, null);
        Player target = new Player("t", "t", new Pos(2, 1), GameConstants.FIST_DAMAGE);
        room.add(target);

        pressA(room, attacker, 0);

        GameEvent.Died died = room.drainEvents().stream()
                .filter(GameEvent.Died.class::isInstance)
                .map(GameEvent.Died.class::cast)
                .findFirst().orElseThrow();
        assertEquals("a", died.killerNickname());
        assertNull(died.weapon(), "fists: the client says so");
    }

    @Test
    void aBatHitsHarderAndSlowerThanAKnife() {
        Room room = room();
        Player attacker = put(room, "a", new Pos(1, 1), Direction.RIGHT, ItemKind.BAT);
        Player target = put(room, "t", new Pos(2, 1), Direction.LEFT, null);

        pressA(room, attacker, 0);
        pressA(room, attacker, GameConstants.BAT_COOLDOWN_TICKS - 1);

        assertEquals(FULL - GameConstants.BAT_DAMAGE, target.hp(), "the second was too soon");
        assertTrue(GameConstants.BAT_DAMAGE > GameConstants.KNIFE_DAMAGE);
        assertTrue(GameConstants.BAT_COOLDOWN_TICKS > GameConstants.KNIFE_COOLDOWN_TICKS);
    }

    @Test
    void aCrossbowShootsAlongTheLineAndIsKeptWhenEmpty() {
        Room room = room();
        Player shooter = put(room, "s", new Pos(1, 1), Direction.RIGHT, ItemKind.CROSSBOW);
        Player target = put(room, "t", new Pos(4, 1), Direction.LEFT, null);
        assertEquals(GameConstants.CROSSBOW_BOLTS, shooter.heldItem().ammo());
        assertEquals(ActionA.FIRE, ActionResolver.actionA(shooter));
        room.drainEvents();

        pressA(room, shooter, 0);

        assertEquals(FULL - GameConstants.CROSSBOW_DAMAGE, target.hp());
        assertInstanceOf(GameEvent.Shot.class, room.drainEvents().getFirst());

        target.heal(FULL, FULL);
        for (int i = 1; i < GameConstants.CROSSBOW_BOLTS; i++) {
            pressA(room, shooter, (long) i * GameConstants.CROSSBOW_COOLDOWN_TICKS);
            target.heal(FULL, FULL);
        }
        assertEquals(0, shooter.heldItem().ammo(), "no bolts left");
        assertEquals(ItemKind.CROSSBOW, shooter.heldItem().kind(), "but still a crossbow");
    }

    @Test
    void aCrossbowFallsShortOfWhereAPistolReaches() {
        Room room = room();
        Player shooter = put(room, "s", new Pos(1, 1), Direction.RIGHT, ItemKind.CROSSBOW);
        Player far = put(room, "t", new Pos(1 + GameConstants.CROSSBOW_RANGE + 1, 1),
                Direction.LEFT, null);

        pressA(room, shooter, 0);

        assertEquals(FULL, far.hp());
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
        assertEquals("s", died.killerNickname(), "the victim learns who");
        assertEquals(ItemKind.PISTOL, died.weapon(), "and with what");
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
    void aDeadPlayersInventoryFallsWhereTheyDiedInOneCrate() {
        Room room = room();
        Player attacker = put(room, "a", new Pos(1, 1), Direction.RIGHT, ItemKind.KNIFE);
        Player target = put(room, "t", new Pos(2, 1), Direction.LEFT, ItemKind.PISTOL);
        Item carried = target.heldItem();
        Item spare = new Item("i-spare", ItemKind.MEDKIT, 0);
        target.setSlot(2, spare);
        target.takeDamage(FULL - 1);

        pressA(room, attacker, 0);

        assertNull(target.heldItem());
        assertEquals(java.util.List.of(carried, spare), room.crateAt(new Pos(2, 1)).items(),
                "the same instances, ammo intact, all of them: a body is worth searching");
    }

    @Test
    void aDropNeverJoinsACrateAlreadyOnTheFloor() {
        Room room = room();
        Player attacker = put(room, "a", new Pos(1, 1), Direction.RIGHT, ItemKind.KNIFE);
        Player target = put(room, "t", new Pos(2, 1), Direction.LEFT, ItemKind.PISTOL);
        Item carried = target.heldItem();
        Item lying = new Item("i-floor", ItemKind.MEDKIT, 0);
        room.placeItem(new Pos(2, 1), lying);
        target.takeDamage(FULL - 1);

        pressA(room, attacker, 0);

        assertEquals(java.util.List.of(lying), room.crateAt(new Pos(2, 1)).items());
        long placed = Arrays.stream(Direction.values())
                .map(d -> room.crateAt(new Pos(2, 1).step(d)))
                .filter(crate -> crate != null && crate.items().contains(carried))
                .count();
        assertEquals(1, placed, "the body's crate lands on a neighbouring tile");
    }

    @Test
    void aCrateDroppedAtADoorLandsWhereBCanStillOpenIt() {
        Room room = room();
        // (0,7) is CROSSROADS' west door; (1,7) is the tile just inside it.
        Player attacker = put(room, "a", new Pos(2, 7), Direction.LEFT, ItemKind.KNIFE);
        Player target = put(room, "t", new Pos(1, 7), Direction.RIGHT, ItemKind.PISTOL);
        Item carried = target.heldItem();
        target.takeDamage(FULL - 1);

        pressA(room, attacker, 0);

        Pos landed = room.crates().entrySet().stream()
                .filter(e -> e.getValue().items().contains(carried))
                .map(java.util.Map.Entry::getKey)
                .findFirst().orElseThrow();
        assertNull(ActionResolver.doorSideAt(room, landed),
                "B would take the door instead of the crate at " + landed);
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
        assertNull(room.crateAt(new Pos(9, 4)), "a cabinet tile holds no crate");
        assertEquals(1, room.crates().size());
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
    void aMedkitIsNeitherOfferedNorSpentAtFullHealth() {
        Room room = room();
        Player player = put(room, "p", new Pos(1, 1), Direction.UP, ItemKind.MEDKIT);

        pressA(room, player, 0);

        assertNull(ActionResolver.actionA(player));
        assertTrue(player.hasItem(), "a press at full health must not waste it");
        assertEquals(0, player.nextActionTick());
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

    // --- Falling ------------------------------------------------------------

    private static GameEvent.Fell fell(Room room) {
        return room.drainEvents().stream()
                .filter(GameEvent.Fell.class::isInstance)
                .map(GameEvent.Fell.class::cast)
                .findFirst().orElseThrow();
    }

    @Test
    void aDeathInTheOpenIsSeenByEveryoneElseInTheRoom() {
        Room room = room();
        Player killer = put(room, "k", new Pos(7, 1), Direction.RIGHT, null);
        Player victim = put(room, "v", new Pos(8, 1), Direction.LEFT, null);
        put(room, "b", new Pos(12, 12), Direction.UP, null);
        victim.takeDamage(FULL - 1);
        room.drainEvents();

        pressA(room, killer, 0);

        GameEvent.Fell fell = fell(room);
        assertEquals(new Pos(8, 1), fell.at());
        assertEquals(Set.of("k", "b"), Set.copyOf(fell.witnessIds()),
                "the dead do not watch themselves fall");
    }

    @Test
    void aDeathInABushIsSeenOnlyFromInsideIt() {
        Room room = room();
        Player killer = put(room, "k", new Pos(3, 3), Direction.RIGHT, null);
        Player victim = put(room, "v", new Pos(4, 3), Direction.LEFT, null);
        put(room, "b", new Pos(12, 12), Direction.UP, null);
        victim.takeDamage(FULL - 1);
        room.drainEvents();

        pressA(room, killer, 0);

        assertEquals(List.of("k"), fell(room).witnessIds(),
                "outside the bush nobody saw it, so nobody is told");
    }

    @Test
    void aDeathInACabinetIsSeenByNobody() {
        Room room = room();
        Player killer = put(room, "k", new Pos(8, 4), Direction.RIGHT, ItemKind.KNIFE);
        Player victim = put(room, "v", new Pos(9, 4), Direction.LEFT, null);
        victim.setInCabinet(true);
        victim.takeDamage(FULL - 1);
        room.drainEvents();

        pressA(room, killer, 0);

        assertTrue(fell(room).witnessIds().isEmpty(),
                "counted before the body leaves the cabinet, not after");
    }

    @Test
    void aSwingAtNothingWithBareHandsStillSpendsTheCooldown() {
        Room room = room();
        Player player = put(room, "p", new Pos(1, 1), Direction.UP, null);

        pressA(room, player, 0);

        assertEquals(List.of(new GameEvent.Swing(new Pos(1, 1), new Pos(1, 0))),
                room.drainEvents());
        assertEquals(GameConstants.FIST_COOLDOWN_TICKS, player.nextActionTick());
    }
}
