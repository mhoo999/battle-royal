package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.core.Player;

/**
 * Bags (V2.1): worn in the bag slot, a bag adds inventory slots. Changing to a smaller
 * bag, or taking one off, is refused while the slots it would take away hold anything,
 * so a bag change never destroys or spills an item.
 */
public final class Bags {

    private Bags() {
    }

    /** Slots this kind adds when worn; 0 for anything that is not a bag. */
    public static int extraSlots(ItemKind kind) {
        return switch (kind) {
            case SMALL_BAG -> GameConstants.SMALL_BAG_SLOTS;
            case BIG_BAG -> GameConstants.BIG_BAG_SLOTS;
            default -> 0;
        };
    }

    public static boolean isBag(ItemKind kind) {
        return extraSlots(kind) > 0;
    }

    /** Inventory slots with this bag worn, or none (null). */
    public static int slotsWith(Item bag) {
        return Player.INVENTORY_SLOTS + (bag == null ? 0 : extraSlots(bag.kind()));
    }

    /** Whether the player can wear this bag (null: take theirs off) without losing a slot in use. */
    public static boolean canWear(Player player, Item bag) {
        return (bag == null || isBag(bag.kind())) && player.emptyFrom(slotsWith(bag));
    }

    /** Wears the bag and hands back the one that was worn. Check {@link #canWear} first. */
    public static Item wear(Player player, Item bag) {
        return player.wearBag(bag, slotsWith(bag));
    }
}
