package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.Command;
import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.map.MapTemplates;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bags (V2.1): worn in the bag slot they add inventory slots, and a change of bag never
 * loses an item. (2,1) on CROSSROADS is plain floor away from any door.
 */
class BagRulesTest {

    private static final Pos FLOOR = new Pos(2, 1);

    private static Room room() {
        return new Room("room-1", MapTemplates.CROSSROADS.map());
    }

    private static Player put(Room room) {
        Player player = new Player("p", "p", FLOOR, GameConstants.MAX_HP);
        room.add(player);
        return player;
    }

    private static Item item(String id, ItemKind kind) {
        return new Item(id, kind, 0);
    }

    /** Holds B on the crate underfoot until it opens. */
    private static void open(Room room, Player player) {
        RoomSimulator.apply(room, new Command.ActionB(player.id()), 0);
        RoomSimulator.tick(room, GameConstants.LOOT_TICKS);
    }

    private static void take(Room room, Player player, int crateIndex, int slot) {
        RoomSimulator.apply(room, new Command.Take(player.id(), crateIndex, slot), 20);
    }

    private static void putBack(Room room, Player player, int slot) {
        RoomSimulator.apply(room, new Command.Put(player.id(), slot), 20);
    }

    @Test
    void wearingABagFromACrateAddsItsSlots() {
        Room room = room();
        Player player = put(room);
        Item bag = item("i-bag", ItemKind.BIG_BAG);
        room.placeItem(FLOOR, bag);
        open(room, player);

        take(room, player, 0, Player.BAG_SLOT);

        assertSame(bag, player.bag());
        assertEquals(Player.INVENTORY_SLOTS + GameConstants.BIG_BAG_SLOTS, player.slotCount());
        assertTrue(room.crates().isEmpty(), "the crate held only the bag");
    }

    @Test
    void onlyABagGoesInTheBagSlot() {
        Room room = room();
        Player player = put(room);
        Item knife = item("i-knife", ItemKind.KNIFE);
        room.placeItem(FLOOR, knife);
        open(room, player);

        take(room, player, 0, Player.BAG_SLOT);

        assertNull(player.bag());
        assertSame(knife, room.crateAt(FLOOR).get(0));
    }

    @Test
    void theNewSlotsHoldItemsAndAUsesThem() {
        Room room = room();
        Player player = put(room);
        Bags.wear(player, item("i-bag", ItemKind.SMALL_BAG));
        Item knife = item("i-knife", ItemKind.KNIFE);
        room.placeItem(FLOOR, knife);
        open(room, player);

        take(room, player, 0, 4);
        RoomSimulator.apply(room, new Command.Equip(player.id(), 4), 30);

        assertSame(knife, player.heldItem());
    }

    @Test
    void aSmallerBagIsRefusedWhileTheSlotsItWouldLoseHoldItems() {
        Room room = room();
        Player player = put(room);
        Item big = item("i-big", ItemKind.BIG_BAG);
        Bags.wear(player, big);
        Item cup = item("i-cup", ItemKind.CUP);
        player.setSlot(6, cup);
        Item small = item("i-small", ItemKind.SMALL_BAG);
        room.placeItem(FLOOR, small);
        open(room, player);

        take(room, player, 0, Player.BAG_SLOT);

        assertSame(big, player.bag(), "refused: slot 7 is in use");
        assertSame(cup, player.slot(6));

        putBack(room, player, 6);
        take(room, player, 0, Player.BAG_SLOT);

        assertSame(small, player.bag(), "with the slot emptied it goes on");
        assertEquals(Player.INVENTORY_SLOTS + GameConstants.SMALL_BAG_SLOTS, player.slotCount());
        assertEquals(List.of(big, cup), room.crateAt(FLOOR).items(),
                "the old bag went into the crate in its place");
    }

    @Test
    void aBagComesOffIntoACrateOnlyWhenItsSlotsAreEmpty() {
        Room room = room();
        Player player = put(room);
        Item bag = item("i-bag", ItemKind.SMALL_BAG);
        Bags.wear(player, bag);
        Item cup = item("i-cup", ItemKind.CUP);
        player.setSlot(3, cup);
        room.placeItem(FLOOR, item("i-spoon", ItemKind.SPOON));
        open(room, player);

        putBack(room, player, Player.BAG_SLOT);
        assertSame(bag, player.bag(), "slot 4 still holds the cup");

        putBack(room, player, 3);
        putBack(room, player, Player.BAG_SLOT);

        assertNull(player.bag());
        assertEquals(Player.INVENTORY_SLOTS, player.slotCount());
        assertSame(bag, room.crateAt(FLOOR).items().getLast());
    }

    @Test
    void aDeathDropsTheBagWithEverythingInIt() {
        Room room = room();
        Player player = put(room);
        Item bag = item("i-bag", ItemKind.BIG_BAG);
        Bags.wear(player, bag);
        for (int slot = 0; slot < player.slotCount(); slot++) {
            player.setSlot(slot, item("i-" + slot, ItemKind.CUP));
        }

        List<Item> dropped = player.dropAll();

        assertEquals(Player.INVENTORY_SLOTS + GameConstants.BIG_BAG_SLOTS + 1, dropped.size());
        assertSame(bag, dropped.getLast(), "the bag comes last, after the slots");
        assertNull(player.bag());
        assertEquals(Player.INVENTORY_SLOTS, player.slotCount());
    }

    @Test
    void anUnwornBagIsLootAndHitsLikeAFist() {
        assertEquals(GameConstants.FIST_DAMAGE, Weapons.strikeOf(ItemKind.SMALL_BAG).damage());
        assertEquals(50, ItemValues.sellPrice(ItemKind.SMALL_BAG, 0));
        assertEquals(150, ItemValues.offerFor(ItemKind.SMALL_BAG).price());
        assertEquals(450, ItemValues.offerFor(ItemKind.BIG_BAG).price());
    }
}
