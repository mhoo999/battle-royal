package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Filling item spawn points. Pure apart from the random source and id supplier, which
 * the caller owns so that a seeded test gets the same items every run.
 *
 * <p>A spawn point is a schedule, not a container: it rolls once when its room is
 * built, and again {@link GameConstants#ITEM_RESPAWN_TICKS} after it is emptied or after
 * a roll that came up with nothing.
 */
public final class ItemSpawns {

    private ItemSpawns() {
    }

    /**
     * Makes every spawn point of a freshly built room due at once, so the first
     * {@link #tick} after creation stocks it. That tick runs before the room is first
     * broadcast, so nobody ever sees it bare.
     */
    public static void prime(Room room) {
        for (Pos spawn : room.map().itemSpawns()) {
            room.scheduleSpawnRoll(spawn, Long.MIN_VALUE);
        }
    }

    /** Rolls the spawn points whose timer has run out. */
    public static void tick(Room room, long nowTick, Random random, Supplier<String> ids) {
        for (Pos spawn : room.takeDueSpawnRolls(nowTick)) {
            roll(room, spawn, nowTick, random, ids);
        }
    }

    /**
     * A spawn point that has just lost its item starts counting down. One refilled by
     * a swap or a dropped item does not: something is lying there already.
     */
    public static void onTaken(Room room, Pos from, long nowTick) {
        if (room.map().itemSpawns().contains(from) && room.itemAt(from) == null) {
            room.scheduleSpawnRoll(from, nowTick + GameConstants.ITEM_RESPAWN_TICKS);
        }
    }

    private static void roll(Room room, Pos spawn, long nowTick, Random random,
                             Supplier<String> ids) {
        if (room.itemAt(spawn) != null) {
            // Something was dropped here while the timer ran. It stands in for the roll.
            return;
        }
        ItemKind kind = pick(random);
        if (kind == null) {
            room.scheduleSpawnRoll(spawn, nowTick + GameConstants.ITEM_RESPAWN_TICKS);
            return;
        }
        room.placeItem(spawn, new Item(ids.get(), kind, GameConstants.PISTOL_MAGAZINE));
    }

    /** One row of the spawn table. A null kind is a roll that comes up empty. */
    private record Weight(ItemKind kind, int weight) {
    }

    private static final List<Weight> TABLE = List.of(
            new Weight(null, GameConstants.SPAWN_WEIGHT_NOTHING),
            new Weight(ItemKind.SPOON, GameConstants.SPAWN_WEIGHT_SPOON),
            new Weight(ItemKind.PAN, GameConstants.SPAWN_WEIGHT_PAN),
            new Weight(ItemKind.MEDKIT, GameConstants.SPAWN_WEIGHT_MEDKIT),
            new Weight(ItemKind.KNIFE, GameConstants.SPAWN_WEIGHT_KNIFE),
            new Weight(ItemKind.PISTOL, GameConstants.SPAWN_WEIGHT_PISTOL));

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
