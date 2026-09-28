package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.ActionA;
import com.example.battleroyal.game.core.ActionB;
import com.example.battleroyal.game.core.Command;
import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.map.MapTemplates;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pickup, swap, spawn and respawn. CROSSROADS spawn points are (10,2), (5,7), (9,7) and
 * (4,12); (2,1) is plain floor well away from any door.
 */
class ItemRulesTest {

    private static final Pos SPAWN = new Pos(10, 2);
    private static final Pos FLOOR = new Pos(2, 1);

    private static Room room() {
        return new Room("room-1", MapTemplates.CROSSROADS.map());
    }

    private static Player put(Room room, Pos pos) {
        Player player = new Player("p", "p", pos, GameConstants.MAX_HP);
        room.add(player);
        return player;
    }

    private static Item item(String id, ItemKind kind) {
        return new Item(id, kind, GameConstants.PISTOL_MAGAZINE);
    }

    private static void pressB(Room room, Player player, long tick) {
        RoomSimulator.apply(room, new Command.ActionB(player.id()), tick);
    }

    /** Presses B and stands still until the loot completes. */
    private static void loot(Room room, Player player, long tick) {
        pressB(room, player, tick);
        RoomSimulator.tick(room, tick + GameConstants.LOOT_TICKS);
    }

    private static Supplier<String> ids() {
        AtomicInteger next = new AtomicInteger();
        return () -> "i-" + next.incrementAndGet();
    }

    // --- Pickup and swap ------------------------------------------------------

    @Test
    void bOverAnItemWithEmptyHandsPicksItUpAndScoresOnce() {
        Room room = room();
        Player player = put(room, FLOOR);
        Item knife = item("i-1", ItemKind.KNIFE);
        room.placeItem(FLOOR, knife);
        assertEquals(ActionB.PICKUP, ActionResolver.actionB(room, player));

        loot(room, player, 0);

        assertSame(knife, player.heldItem());
        assertNull(room.itemAt(FLOOR));
        assertEquals(GameConstants.SCORE_ITEM_PICKUP, player.score());
    }

    @Test
    void bOverAnItemWhileHoldingOneSwapsAndLeavesTheOldItemBehind() {
        Room room = room();
        Player player = put(room, FLOOR);
        Item pistol = item("i-1", ItemKind.PISTOL);
        pistol.spendAmmo();
        player.hold(pistol);
        Item knife = item("i-2", ItemKind.KNIFE);
        room.placeItem(FLOOR, knife);
        assertEquals(ActionB.SWAP, ActionResolver.actionB(room, player));

        loot(room, player, 0);

        assertSame(knife, player.heldItem());
        assertSame(pistol, room.itemAt(FLOOR), "never destroyed");
        assertEquals(GameConstants.PISTOL_MAGAZINE - 1, pistol.ammo(),
                "the dropped pistol keeps its rounds");
    }

    @Test
    void retakingTheSameItemPaysNothing() {
        Room room = room();
        Player player = put(room, FLOOR);
        room.placeItem(FLOOR, item("i-1", ItemKind.KNIFE));
        player.hold(item("i-2", ItemKind.SPOON));

        loot(room, player, 0);     // take the knife, drop the spoon
        loot(room, player, 20);    // take the spoon back
        loot(room, player, 40);    // and the knife again

        assertEquals(2 * GameConstants.SCORE_ITEM_PICKUP, player.score(),
                "once per item instance, however often it changes hands");
    }

    // --- Loot time ------------------------------------------------------------

    @Test
    void lootingTakesTimeAndTheItemArrivesWhenItIsUp() {
        Room room = room();
        Player player = put(room, FLOOR);
        Item knife = item("i-1", ItemKind.KNIFE);
        room.placeItem(FLOOR, knife);

        pressB(room, player, 0);
        assertTrue(player.looting());
        RoomSimulator.tick(room, GameConstants.LOOT_TICKS - 1);
        assertFalse(player.hasItem(), "not yet");
        assertSame(knife, room.itemAt(FLOOR), "still on the floor for anyone to grab");

        RoomSimulator.tick(room, GameConstants.LOOT_TICKS);
        assertSame(knife, player.heldItem());
        assertFalse(player.looting());
    }

    @Test
    void steppingOffTheTileStartsTheLootOver() {
        Room room = room();
        Player player = put(room, FLOOR);
        room.placeItem(FLOOR, item("i-1", ItemKind.KNIFE));

        pressB(room, player, 0);
        RoomSimulator.apply(room, new Command.Move("p", Direction.DOWN), 1);
        RoomSimulator.apply(room, new Command.Move("p", Direction.UP), 5);
        RoomSimulator.tick(room, GameConstants.LOOT_TICKS);

        assertEquals(FLOOR, player.pos(), "back on the item");
        assertFalse(player.hasItem(), "moving away abandoned the loot");
        assertFalse(player.looting());

        loot(room, player, 20);
        assertTrue(player.hasItem(), "a fresh B starts it again");
    }

    @Test
    void turningOnTheSpotDoesNotInterruptTheLoot() {
        Room room = room();
        // (1,1) has a wall above it, so UP only turns.
        Player player = put(room, new Pos(1, 1));
        room.placeItem(new Pos(1, 1), item("i-1", ItemKind.KNIFE));

        pressB(room, player, 0);
        RoomSimulator.apply(room, new Command.Move("p", Direction.UP), 2);
        RoomSimulator.tick(room, GameConstants.LOOT_TICKS);

        assertEquals(Direction.UP, player.facing());
        assertTrue(player.hasItem());
    }

    @Test
    void lettingGoOfBAbandonsTheLoot() {
        Room room = room();
        Player player = put(room, FLOOR);
        Item knife = item("i-1", ItemKind.KNIFE);
        room.placeItem(FLOOR, knife);

        pressB(room, player, 0);
        RoomSimulator.apply(room, new Command.ReleaseB("p"), 5);
        RoomSimulator.tick(room, GameConstants.LOOT_TICKS);

        assertFalse(player.hasItem());
        assertFalse(player.looting());
        assertSame(knife, room.itemAt(FLOOR));
    }

    @Test
    void pressingBAgainDoesNotRestartTheClock() {
        Room room = room();
        Player player = put(room, FLOOR);
        room.placeItem(FLOOR, item("i-1", ItemKind.KNIFE));

        pressB(room, player, 0);
        pressB(room, player, 5);
        RoomSimulator.tick(room, GameConstants.LOOT_TICKS);

        assertTrue(player.hasItem());
    }

    @Test
    void anItemSnatchedFirstIsNotConjuredIntoTheSlowerHand() {
        Room room = room();
        Player slow = put(room, FLOOR);
        Item knife = item("i-1", ItemKind.KNIFE);
        room.placeItem(FLOOR, knife);

        pressB(room, slow, 0);
        room.takeItem(FLOOR);   // someone else got there
        room.placeItem(FLOOR, item("i-2", ItemKind.SPOON));
        RoomSimulator.tick(room, GameConstants.LOOT_TICKS);

        assertFalse(slow.hasItem(), "the loot was for the knife, not whatever lies there now");
    }

    @Test
    void anItemBesideYouIsNotInReach() {
        Room room = room();
        Player player = put(room, FLOOR);
        room.placeItem(FLOOR.step(Direction.RIGHT), item("i-1", ItemKind.KNIFE));

        assertNull(ActionResolver.actionB(room, player));
    }

    // --- Pan and spoon ------------------------------------------------------------

    @Test
    void aPanIsAWeakClub() {
        Room room = room();
        Player attacker = put(room, new Pos(1, 1));
        attacker.face(Direction.RIGHT);
        attacker.hold(item("i-1", ItemKind.PAN));
        Player target = new Player("t", "t", new Pos(2, 1), GameConstants.MAX_HP);
        room.add(target);
        assertEquals(ActionA.ATTACK, ActionResolver.actionA(attacker));

        RoomSimulator.apply(room, new Command.ActionA("p"), 0);

        assertEquals(GameConstants.MAX_HP - GameConstants.PAN_DAMAGE, target.hp());
        assertEquals(GameConstants.PAN_COOLDOWN_TICKS, attacker.nextActionTick());
    }

    @Test
    void aSpoonDoesNothing() {
        Player player = put(room(), FLOOR);
        player.hold(item("i-1", ItemKind.SPOON));

        assertNull(ActionResolver.actionA(player));
    }

    // --- Spawning -----------------------------------------------------------------

    @Test
    void aPrimedRoomRollsEverySpawnOnItsFirstTick() {
        Room room = room();
        ItemSpawns.prime(room);

        ItemSpawns.tick(room, 0, new Random(1), ids());

        for (Pos spawn : room.map().itemSpawns()) {
            assertTrue(room.itemAt(spawn) != null || room.spawnRollPending(spawn),
                    "each spawn either holds an item or is waiting to roll again: " + spawn);
        }
    }

    @Test
    void anEmptyRollTriesAgainAfterTheRespawnDelay() {
        Room room = room();
        room.scheduleSpawnRoll(SPAWN, 0);
        Random alwaysEmpty = new Random() {
            @Override
            public int nextInt(int bound) {
                return 0;   // the first row of the table is "nothing"
            }
        };

        ItemSpawns.tick(room, 0, alwaysEmpty, ids());
        assertNull(room.itemAt(SPAWN));

        ItemSpawns.tick(room, GameConstants.ITEM_RESPAWN_TICKS - 1, new Random(1), ids());
        assertTrue(room.spawnRollPending(SPAWN), "not yet");

        Random alwaysPistol = new Random() {
            @Override
            public int nextInt(int bound) {
                return bound - 1;   // the last row is the pistol
            }
        };
        ItemSpawns.tick(room, GameConstants.ITEM_RESPAWN_TICKS, alwaysPistol, ids());
        assertEquals(ItemKind.PISTOL, room.itemAt(SPAWN).kind());
        assertEquals(GameConstants.PISTOL_MAGAZINE, room.itemAt(SPAWN).ammo());
    }

    @Test
    void takingFromASpawnPointStartsItsRespawnTimer() {
        Room room = room();
        Player player = put(room, SPAWN);
        room.placeItem(SPAWN, item("i-1", ItemKind.KNIFE));

        loot(room, player, 100);

        long taken = 100 + GameConstants.LOOT_TICKS;
        assertTrue(room.spawnRollPending(SPAWN));
        assertTrue(room.takeDueSpawnRolls(taken + GameConstants.ITEM_RESPAWN_TICKS - 1).isEmpty());
        assertEquals(1, room.takeDueSpawnRolls(taken + GameConstants.ITEM_RESPAWN_TICKS).size());
    }

    @Test
    void aSwapOnASpawnPointLeavesItStockedSoNoTimerStarts() {
        Room room = room();
        Player player = put(room, SPAWN);
        player.hold(item("i-1", ItemKind.SPOON));
        room.placeItem(SPAWN, item("i-2", ItemKind.KNIFE));

        loot(room, player, 0);

        assertFalse(room.spawnRollPending(SPAWN));
    }

    @Test
    void aRollDueOnAnOccupiedSpawnKeepsWhatIsLyingThere() {
        Room room = room();
        Item dropped = item("i-1", ItemKind.MEDKIT);
        room.placeItem(SPAWN, dropped);
        room.scheduleSpawnRoll(SPAWN, 0);

        ItemSpawns.tick(room, 0, new Random(1), ids());

        assertSame(dropped, room.itemAt(SPAWN));
    }

    @Test
    void theSpawnTableMatchesTheConfiguredOdds() {
        Random random = new Random(7);
        Map<ItemKind, Integer> counts = new EnumMap<>(ItemKind.class);
        int nothing = 0;
        int rolls = 100_000;
        for (int i = 0; i < rolls; i++) {
            ItemKind kind = ItemSpawns.pick(random);
            if (kind == null) {
                nothing++;
            } else {
                counts.merge(kind, 1, Integer::sum);
            }
        }

        Map<ItemKind, Integer> expected = new HashMap<>(Map.of(
                ItemKind.SPOON, GameConstants.SPAWN_WEIGHT_SPOON,
                ItemKind.PAN, GameConstants.SPAWN_WEIGHT_PAN,
                ItemKind.MEDKIT, GameConstants.SPAWN_WEIGHT_MEDKIT,
                ItemKind.KNIFE, GameConstants.SPAWN_WEIGHT_KNIFE,
                ItemKind.PISTOL, GameConstants.SPAWN_WEIGHT_PISTOL));
        assertEquals(GameConstants.SPAWN_WEIGHT_NOTHING, percent(nothing, rolls), 1.0);
        for (Map.Entry<ItemKind, Integer> entry : expected.entrySet()) {
            assertNotNull(counts.get(entry.getKey()), entry.getKey() + " never rolled");
            assertEquals(entry.getValue(), percent(counts.get(entry.getKey()), rolls), 1.0,
                    entry.getKey().toString());
        }
        assertEquals(100, ItemSpawns.TOTAL_WEIGHT, "weights are percentages");
    }

    private static double percent(int count, int total) {
        return 100.0 * count / total;
    }
}
