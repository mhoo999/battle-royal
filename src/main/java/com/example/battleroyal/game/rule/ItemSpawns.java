package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Stocking a new room. Pure apart from the random source and id supplier, which the
 * caller owns so that a seeded test gets the same items every run.
 *
 * <p>A room rolls when it is built and holds at most one item: more often than not,
 * none. It rolls again after spending {@link GameConstants#LOOT_REGROW_TICKS} in all with
 * nobody in it and nothing on its floor. Loot is found by moving on: waiting in a room
 * never restocks it, but rooms you left behind do. Rebuilding alone would not be enough,
 * because a small world keeps nearly every room alive as someone's neighbour.
 */
public final class ItemSpawns {

    private ItemSpawns() {
    }

    /**
     * Makes a freshly built room's roll due at once, so the first {@link #tick} after
     * creation stocks it. That tick runs before the room is first broadcast, so nobody
     * ever sees it bare.
     */
    public static void prime(Room room) {
        room.scheduleLootRoll();
    }

    /**
     * Called once per tick. Rolls a primed room, or one that has spent long enough
     * empty and bare. Anyone in the room, or anything on its floor, pauses the clock
     * rather than resetting it: a room somebody keeps passing through still regrows,
     * one somebody camps in never does.
     */
    public static void tick(Room room, Random random, Supplier<String> ids) {
        if (!room.takeLootRoll()) {
            if (!room.isEmpty() || !room.floorItems().isEmpty()) {
                return;
            }
            if (room.advanceLootRegrow() < GameConstants.LOOT_REGROW_TICKS) {
                return;
            }
        }
        room.resetLootRegrow();
        roll(room, random, ids);
    }

    /**
     * Nothing, or one item on one of the map's spawn tiles. The tile is chosen per roll,
     * so the same layout does not always hide its loot in the same place.
     */
    private static void roll(Room room, Random random, Supplier<String> ids) {
        ItemKind kind = pick(random);
        if (kind == null) {
            return;
        }
        List<Pos> spawns = room.map().itemSpawns();
        Pos spawn = spawns.get(random.nextInt(spawns.size()));
        room.placeItem(spawn, new Item(ids.get(), kind, Weapons.startingAmmo(kind)));
    }

    /** One row of the loot table. A null kind is a roll that comes up empty. */
    private record Weight(ItemKind kind, int weight) {
    }

    private static final List<Weight> TABLE = List.of(
            new Weight(null, GameConstants.LOOT_WEIGHT_NOTHING),
            new Weight(ItemKind.SPOON, GameConstants.LOOT_WEIGHT_SPOON),
            new Weight(ItemKind.DOLL, GameConstants.LOOT_WEIGHT_DOLL),
            new Weight(ItemKind.CUP, GameConstants.LOOT_WEIGHT_CUP),
            new Weight(ItemKind.RECORDER, GameConstants.LOOT_WEIGHT_RECORDER),
            new Weight(ItemKind.REGISTER, GameConstants.LOOT_WEIGHT_REGISTER),
            new Weight(ItemKind.PAN, GameConstants.LOOT_WEIGHT_PAN),
            new Weight(ItemKind.MEDKIT, GameConstants.LOOT_WEIGHT_MEDKIT),
            new Weight(ItemKind.KNIFE, GameConstants.LOOT_WEIGHT_KNIFE),
            new Weight(ItemKind.BAT, GameConstants.LOOT_WEIGHT_BAT),
            new Weight(ItemKind.CROSSBOW, GameConstants.LOOT_WEIGHT_CROSSBOW),
            new Weight(ItemKind.PISTOL, GameConstants.LOOT_WEIGHT_PISTOL));

    static final int TOTAL_WEIGHT = TABLE.stream().mapToInt(Weight::weight).sum();

    /** @return the rolled kind, or null when the roll came up empty */
    static ItemKind pick(Random random) {
        int roll = random.nextInt(TOTAL_WEIGHT);
        for (Weight entry : TABLE) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry.kind();
            }
        }
        throw new IllegalStateException("unreachable: roll below total weight");
    }
}
