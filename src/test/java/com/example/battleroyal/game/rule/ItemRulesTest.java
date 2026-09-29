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
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pickup, swap and the loot roll. CROSSROADS spawn points are (10,2), (5,7), (9,7) and
 * (4,12); (2,1) is plain floor well away from any door.
 */
class ItemRulesTest {

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

    /** Answers every draw with its top value: the table's last row, the last spawn. */
    private static Random alwaysLast() {
        return new Random() {
            @Override
            public int nextInt(int bound) {
                return bound - 1;
            }
        };
    }

    @Test
    void aPrimedRoomRollsOnceOnItsFirstTickAndHoldsAtMostOneItem() {
        for (int seed = 0; seed < 200; seed++) {
            Room room = room();
            ItemSpawns.prime(room);

            ItemSpawns.tick(room, new Random(seed), ids());

            assertFalse(room.lootRollPending(), "rolled, seed " + seed);
            assertTrue(room.floorItems().size() <= 1, "one item or none, seed " + seed);
            for (Pos at : room.floorItems().keySet()) {
                assertTrue(room.map().itemSpawns().contains(at), "on a spawn tile: " + at);
            }
        }
    }

    @Test
    void aHitPutsOneFullPistolOnOneOfTheSpawnTiles() {
        Room room = room();
        ItemSpawns.prime(room);

        ItemSpawns.tick(room, alwaysLast(), ids());

        Pos last = room.map().itemSpawns().getLast();
        assertEquals(1, room.floorItems().size());
        assertEquals(ItemKind.PISTOL, room.itemAt(last).kind());
        assertEquals(GameConstants.PISTOL_MAGAZINE, room.itemAt(last).ammo());
    }

    @Test
    void theLootTileVariesBetweenRoomsOfTheSameLayout() {
        Set<Pos> used = new HashSet<>();
        for (int seed = 0; seed < 200; seed++) {
            Room room = room();
            ItemSpawns.prime(room);
            ItemSpawns.tick(room, new Random(seed), ids());
            used.addAll(room.floorItems().keySet());
        }
        assertEquals(Set.copyOf(room().map().itemSpawns()), used);
    }

    private static Random alwaysEmpty() {
        return new Random() {
            @Override
            public int nextInt(int bound) {
                return 0;   // the first row of the table is "nothing"
            }
        };
    }

    /** Runs the per-tick loot check the given number of times. */
    private static void ticks(Room room, int count, Random random) {
        for (int i = 0; i < count; i++) {
            ItemSpawns.tick(room, random, ids());
        }
    }

    @Test
    void aBareRoomNobodyIsInRollsAgainAfterTheRegrowDelay() {
        Room room = room();
        ItemSpawns.prime(room);
        ItemSpawns.tick(room, alwaysEmpty(), ids());

        ticks(room, GameConstants.LOOT_REGROW_TICKS - 1, alwaysLast());
        assertTrue(room.floorItems().isEmpty(), "not yet");

        ticks(room, 1, alwaysLast());
        assertEquals(1, room.floorItems().size());
    }

    @Test
    void waitingInsideARoomNeverRestocksIt() {
        Room room = room();
        put(room, FLOOR);
        ItemSpawns.prime(room);
        ItemSpawns.tick(room, alwaysEmpty(), ids());

        ticks(room, 10 * GameConstants.LOOT_REGROW_TICKS, alwaysLast());

        assertTrue(room.floorItems().isEmpty(), "camping gains nothing");
    }

    @Test
    void passingThroughPausesTheClockRatherThanResettingIt() {
        Room room = room();
        ItemSpawns.prime(room);
        ItemSpawns.tick(room, alwaysEmpty(), ids());
        int half = GameConstants.LOOT_REGROW_TICKS / 2;

        ticks(room, half, alwaysLast());
        Player visitor = put(room, FLOOR);
        ticks(room, 5 * GameConstants.LOOT_REGROW_TICKS, alwaysLast());
        room.remove(visitor.id());
        ticks(room, GameConstants.LOOT_REGROW_TICKS - half - 1, alwaysLast());
        assertTrue(room.floorItems().isEmpty(), "the visit did not count");

        ticks(room, 1, alwaysLast());
        assertEquals(1, room.floorItems().size(), "the time before the visit did");
    }

    @Test
    void anItemLyingAroundHoldsRegrowthBackSoARoomNeverStacksLoot() {
        Room room = room();
        ItemSpawns.prime(room);
        ItemSpawns.tick(room, alwaysLast(), ids());

        ticks(room, 5 * GameConstants.LOOT_REGROW_TICKS, alwaysLast());

        assertEquals(1, room.floorItems().size());
    }

    @Test
    void takingTheItemDoesNotRestockTheRoomWhileYouAreInIt() {
        Room room = room();
        ItemSpawns.prime(room);
        ItemSpawns.tick(room, alwaysLast(), ids());
        Pos spawn = room.map().itemSpawns().getLast();
        Player player = put(room, spawn);

        loot(room, player, 100);
        ticks(room, 2 * GameConstants.LOOT_REGROW_TICKS, alwaysLast());

        assertEquals(ItemKind.PISTOL, player.heldItem().kind());
        assertTrue(room.floorItems().isEmpty());
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
                ItemKind.SPOON, GameConstants.LOOT_WEIGHT_SPOON,
                ItemKind.PAN, GameConstants.LOOT_WEIGHT_PAN,
                ItemKind.MEDKIT, GameConstants.LOOT_WEIGHT_MEDKIT,
                ItemKind.KNIFE, GameConstants.LOOT_WEIGHT_KNIFE,
                ItemKind.PISTOL, GameConstants.LOOT_WEIGHT_PISTOL));
        assertEquals(GameConstants.LOOT_WEIGHT_NOTHING, percent(nothing, rolls), 1.0);
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
