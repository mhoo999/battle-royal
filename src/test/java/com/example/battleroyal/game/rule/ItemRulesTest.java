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
 * Crates, the inventory and the loot roll. CROSSROADS spawn points are (10,2), (5,7), (9,7) and
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

    // --- Opening a crate -----------------------------------------------------

    private static void take(Room room, Player player, int crateIndex, int slot, long tick) {
        RoomSimulator.apply(room, new Command.Take(player.id(), crateIndex, slot), tick);
    }

    private static void putBack(Room room, Player player, int slot, long tick) {
        RoomSimulator.apply(room, new Command.Put(player.id(), slot), tick);
    }

    @Test
    void holdingBOverACrateOpensItAndBAgainClosesIt() {
        Room room = room();
        Player player = put(room, FLOOR);
        room.placeItem(FLOOR, item("i-1", ItemKind.KNIFE));
        assertEquals(ActionB.OPEN, ActionResolver.actionB(room, player));

        loot(room, player, 0);

        assertEquals(FLOOR, player.openCrateAt());
        assertEquals(ActionB.CLOSE, ActionResolver.actionB(room, player));

        pressB(room, player, 20);
        assertNull(player.openCrateAt());
    }

    @Test
    void takingAnItemPutsItInTheChosenSlotAndScoresOnce() {
        Room room = room();
        Player player = put(room, FLOOR);
        Item knife = item("i-1", ItemKind.KNIFE);
        room.placeItem(FLOOR, knife);
        loot(room, player, 0);

        take(room, player, 0, 1, 20);

        assertSame(knife, player.slot(1));
        assertNull(room.crateAt(FLOOR), "an emptied crate leaves the floor");
        assertNull(player.openCrateAt());
        assertEquals(GameConstants.SCORE_ITEM_PICKUP, player.score());
    }

    @Test
    void takingIntoAnOccupiedSlotTradesPlacesAndDestroysNothing() {
        Room room = room();
        Player player = put(room, FLOOR);
        Item pistol = item("i-1", ItemKind.PISTOL);
        pistol.spendAmmo();
        player.hold(pistol);
        Item knife = item("i-2", ItemKind.KNIFE);
        room.placeItem(FLOOR, knife);
        loot(room, player, 0);

        take(room, player, 0, player.equipped(), 20);

        assertSame(knife, player.heldItem());
        assertSame(pistol, room.crateAt(FLOOR).get(0), "never destroyed");
        assertEquals(GameConstants.PISTOL_MAGAZINE - 1, pistol.ammo(),
                "the pistol keeps its rounds in the crate");
    }

    @Test
    void retakingTheSameItemPaysNothing() {
        Room room = room();
        Player player = put(room, FLOOR);
        room.placeItem(FLOOR, item("i-1", ItemKind.KNIFE));
        player.hold(item("i-2", ItemKind.SPOON));
        loot(room, player, 0);

        take(room, player, 0, 0, 20);   // the knife in, the spoon out
        take(room, player, 0, 0, 21);   // the spoon back
        take(room, player, 0, 0, 22);   // and the knife again

        assertEquals(2 * GameConstants.SCORE_ITEM_PICKUP, player.score(),
                "once per item instance, however often it changes hands");
    }

    @Test
    void puttingAnItemBackMovesItIntoTheCrate() {
        Room room = room();
        Player player = put(room, FLOOR);
        room.placeItem(FLOOR, item("i-1", ItemKind.KNIFE));
        Item spoon = item("i-2", ItemKind.SPOON);
        player.setSlot(2, spoon);
        loot(room, player, 0);

        putBack(room, player, 2, 20);

        assertNull(player.slot(2));
        assertEquals(2, room.crateAt(FLOOR).size());
        assertSame(spoon, room.crateAt(FLOOR).get(1));
    }

    @Test
    void aFullCrateTakesNothingMore() {
        Room room = room();
        Player player = put(room, FLOOR);
        for (int i = 0; i < GameConstants.CRATE_CAPACITY; i++) {
            room.placeItem(FLOOR, item("i-" + i, ItemKind.CUP));
        }
        Item spoon = item("i-spoon", ItemKind.SPOON);
        player.setSlot(0, spoon);
        loot(room, player, 0);

        putBack(room, player, 0, 20);

        assertSame(spoon, player.slot(0), "refused, so still carried");
        assertEquals(GameConstants.CRATE_CAPACITY, room.crateAt(FLOOR).size());
    }

    @Test
    void nothingMovesWithoutAnOpenCrate() {
        Room room = room();
        Player player = put(room, FLOOR);
        Item knife = item("i-1", ItemKind.KNIFE);
        room.placeItem(FLOOR, knife);

        take(room, player, 0, 0, 0);

        assertFalse(player.hasItem(), "a crate has to be opened before it can be emptied");
        assertSame(knife, room.crateAt(FLOOR).get(0));
    }

    @Test
    void equipChoosesWhatAUses() {
        Room room = room();
        Player player = put(room, FLOOR);
        Item knife = item("i-1", ItemKind.KNIFE);
        Item pistol = item("i-2", ItemKind.PISTOL);
        player.setSlot(0, knife);
        player.setSlot(1, pistol);

        RoomSimulator.apply(room, new Command.Equip("p", 1), 0);
        assertSame(pistol, player.heldItem());
        assertEquals(ActionA.FIRE, ActionResolver.actionA(player));

        RoomSimulator.apply(room, new Command.Equip("p", 7), 1);
        assertEquals(1, player.equipped(), "a slot that does not exist is ignored");
    }

    // --- Loot time ------------------------------------------------------------

    @Test
    void openingTakesTimeAndTheCrateOpensWhenItIsUp() {
        Room room = room();
        Player player = put(room, FLOOR);
        room.placeItem(FLOOR, item("i-1", ItemKind.KNIFE));

        pressB(room, player, 0);
        assertTrue(player.looting());
        RoomSimulator.tick(room, GameConstants.LOOT_TICKS - 1);
        assertNull(player.openCrateAt(), "not yet");

        RoomSimulator.tick(room, GameConstants.LOOT_TICKS);
        assertEquals(FLOOR, player.openCrateAt());
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

        assertEquals(FLOOR, player.pos(), "back on the crate");
        assertNull(player.openCrateAt(), "moving away abandoned the loot");
        assertFalse(player.looting());

        loot(room, player, 20);
        assertEquals(FLOOR, player.openCrateAt(), "a fresh B starts it again");
    }

    @Test
    void steppingOffAnOpenCrateShutsIt() {
        Room room = room();
        Player player = put(room, FLOOR);
        Item knife = item("i-1", ItemKind.KNIFE);
        room.placeItem(FLOOR, knife);
        loot(room, player, 0);

        RoomSimulator.apply(room, new Command.Move("p", Direction.DOWN), 20);
        take(room, player, 0, 0, 30);

        assertNull(player.openCrateAt());
        assertFalse(player.hasItem());
        assertSame(knife, room.crateAt(FLOOR).get(0));
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
        assertEquals(new Pos(1, 1), player.openCrateAt());
    }

    @Test
    void lettingGoOfBAbandonsTheLoot() {
        Room room = room();
        Player player = put(room, FLOOR);
        room.placeItem(FLOOR, item("i-1", ItemKind.KNIFE));

        pressB(room, player, 0);
        RoomSimulator.apply(room, new Command.ReleaseB("p"), 5);
        RoomSimulator.tick(room, GameConstants.LOOT_TICKS);

        assertNull(player.openCrateAt());
        assertFalse(player.looting());
    }

    @Test
    void pressingBAgainDoesNotRestartTheClock() {
        Room room = room();
        Player player = put(room, FLOOR);
        room.placeItem(FLOOR, item("i-1", ItemKind.KNIFE));

        pressB(room, player, 0);
        pressB(room, player, 5);
        RoomSimulator.tick(room, GameConstants.LOOT_TICKS);

        assertEquals(FLOOR, player.openCrateAt());
    }

    @Test
    void aCrateEmptiedFirstIsNotOpenedForTheSlowerHand() {
        Room room = room();
        Player slow = put(room, FLOOR);
        room.placeItem(FLOOR, item("i-1", ItemKind.KNIFE));

        pressB(room, slow, 0);
        room.removeCrate(FLOOR);   // someone else got there
        room.placeItem(FLOOR, item("i-2", ItemKind.SPOON));
        RoomSimulator.tick(room, GameConstants.LOOT_TICKS);

        assertNull(slow.openCrateAt(), "the loot was for that crate, not whatever lies here now");
    }

    @Test
    void aCrateBesideYouIsNotInReach() {
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
    void junkIsSwungForAtLeastWhatAFistDoes() {
        // There is no way to drop an item, so junk that hit softer than bare hands would
        // leave its holder worse off than empty-handed.
        for (ItemKind junk : new ItemKind[] {ItemKind.SPOON, ItemKind.DOLL, ItemKind.CUP,
                ItemKind.RECORDER, ItemKind.REGISTER}) {
            Player player = put(room(), FLOOR);
            player.hold(item("i-1", junk));
            assertEquals(ActionA.ATTACK, ActionResolver.actionA(player), junk.toString());
            assertTrue(Weapons.strikeOf(junk).damage() >= GameConstants.FIST_DAMAGE,
                    junk + " hits at least as hard as a fist");
        }
    }

    @Test
    void aCupHitsOneHarderThanAFist() {
        Room room = room();
        Player attacker = put(room, FLOOR);
        attacker.face(Direction.RIGHT);
        attacker.hold(item("i-1", ItemKind.CUP));
        Player target = new Player("t", "t", FLOOR.step(Direction.RIGHT), GameConstants.MAX_HP);
        room.add(target);

        RoomSimulator.apply(room, new Command.ActionA("p"), 0);

        assertEquals(GameConstants.MAX_HP - GameConstants.FIST_DAMAGE - 1, target.hp());
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
            assertTrue(room.crates().size() <= 1, "one crate or none, seed " + seed);
            for (Pos at : room.crates().keySet()) {
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
        assertEquals(1, room.crates().size());
        assertEquals(1, room.crateAt(last).size(), "a crate of one item");
        assertEquals(ItemKind.PISTOL, room.crateAt(last).get(0).kind());
        assertEquals(GameConstants.PISTOL_MAGAZINE, room.crateAt(last).get(0).ammo());
    }

    @Test
    void theLootTileVariesBetweenRoomsOfTheSameLayout() {
        Set<Pos> used = new HashSet<>();
        for (int seed = 0; seed < 200; seed++) {
            Room room = room();
            ItemSpawns.prime(room);
            ItemSpawns.tick(room, new Random(seed), ids());
            used.addAll(room.crates().keySet());
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
        assertTrue(room.crates().isEmpty(), "not yet");

        ticks(room, 1, alwaysLast());
        assertEquals(1, room.crates().size());
    }

    @Test
    void waitingInsideARoomNeverRestocksIt() {
        Room room = room();
        put(room, FLOOR);
        ItemSpawns.prime(room);
        ItemSpawns.tick(room, alwaysEmpty(), ids());

        ticks(room, 10 * GameConstants.LOOT_REGROW_TICKS, alwaysLast());

        assertTrue(room.crates().isEmpty(), "camping gains nothing");
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
        assertTrue(room.crates().isEmpty(), "the visit did not count");

        ticks(room, 1, alwaysLast());
        assertEquals(1, room.crates().size(), "the time before the visit did");
    }

    @Test
    void aCrateLyingAroundHoldsRegrowthBackSoARoomNeverStacksLoot() {
        Room room = room();
        ItemSpawns.prime(room);
        ItemSpawns.tick(room, alwaysLast(), ids());

        ticks(room, 5 * GameConstants.LOOT_REGROW_TICKS, alwaysLast());

        assertEquals(1, room.crates().size());
    }

    @Test
    void takingTheItemDoesNotRestockTheRoomWhileYouAreInIt() {
        Room room = room();
        ItemSpawns.prime(room);
        ItemSpawns.tick(room, alwaysLast(), ids());
        Pos spawn = room.map().itemSpawns().getLast();
        Player player = put(room, spawn);

        loot(room, player, 100);
        take(room, player, 0, 0, 120);
        ticks(room, 2 * GameConstants.LOOT_REGROW_TICKS, alwaysLast());

        assertEquals(ItemKind.PISTOL, player.heldItem().kind());
        assertTrue(room.crates().isEmpty());
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

        Map<ItemKind, Integer> expected = new HashMap<>(Map.ofEntries(
                Map.entry(ItemKind.SPOON, GameConstants.LOOT_WEIGHT_SPOON),
                Map.entry(ItemKind.DOLL, GameConstants.LOOT_WEIGHT_DOLL),
                Map.entry(ItemKind.CUP, GameConstants.LOOT_WEIGHT_CUP),
                Map.entry(ItemKind.RECORDER, GameConstants.LOOT_WEIGHT_RECORDER),
                Map.entry(ItemKind.REGISTER, GameConstants.LOOT_WEIGHT_REGISTER),
                Map.entry(ItemKind.PAN, GameConstants.LOOT_WEIGHT_PAN),
                Map.entry(ItemKind.MEDKIT, GameConstants.LOOT_WEIGHT_MEDKIT),
                Map.entry(ItemKind.KNIFE, GameConstants.LOOT_WEIGHT_KNIFE),
                Map.entry(ItemKind.BAT, GameConstants.LOOT_WEIGHT_BAT),
                Map.entry(ItemKind.CROSSBOW, GameConstants.LOOT_WEIGHT_CROSSBOW),
                Map.entry(ItemKind.PISTOL, GameConstants.LOOT_WEIGHT_PISTOL),
                Map.entry(ItemKind.ROUNDS, GameConstants.LOOT_WEIGHT_ROUNDS),
                Map.entry(ItemKind.BOLTS, GameConstants.LOOT_WEIGHT_BOLTS),
                Map.entry(ItemKind.SMALL_BAG, GameConstants.LOOT_WEIGHT_SMALL_BAG),
                Map.entry(ItemKind.BIG_BAG, GameConstants.LOOT_WEIGHT_BIG_BAG)));
        assertEquals(ItemKind.values().length, expected.size(), "every item can be found");
        assertEquals(GameConstants.LOOT_WEIGHT_NOTHING / 2.0, percent(nothing, rolls), 1.0);
        for (Map.Entry<ItemKind, Integer> entry : expected.entrySet()) {
            assertNotNull(counts.get(entry.getKey()), entry.getKey() + " never rolled");
            assertEquals(entry.getValue() / 2.0, percent(counts.get(entry.getKey()), rolls), 0.5,
                    entry.getKey().toString());
        }
        assertEquals(200, ItemSpawns.TOTAL_WEIGHT, "weights are halves of a percent");
    }

    private static double percent(int count, int total) {
        return 100.0 * count / total;
    }
}
