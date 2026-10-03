package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.ItemKind;

import java.util.List;

/**
 * What things are worth (D11): the trader's buying price for each item is its value,
 * which is also what the ranking counts. A first draft (Q12), to be tuned by play.
 *
 * <p>A gun is worth more the more rounds it has left: one shot from empty is mostly a
 * gun-shaped lump. The trader sells only real gear, never junk, at three times what it
 * pays, so buying and selling straight back always loses money.
 */
public final class ItemValues {

    private ItemValues() {
    }

    /** One line of the trader's stock: always in stock, always full. */
    public record Offer(ItemKind kind, int ammo, int price) {
    }

    static final int PISTOL_BASE = 30;
    static final int PISTOL_PER_ROUND = 20;
    static final int CROSSBOW_BASE = 40;
    static final int CROSSBOW_PER_BOLT = 20;
    static final int MARKUP = 3;

    /** What the trader pays for this item. */
    public static int sellPrice(ItemKind kind, int ammo) {
        return switch (kind) {
            case PISTOL -> PISTOL_BASE + PISTOL_PER_ROUND * ammo;
            case CROSSBOW -> CROSSBOW_BASE + CROSSBOW_PER_BOLT * ammo;
            case KNIFE -> 40;
            case BAT -> 50;
            case MEDKIT -> 60;
            case PAN -> 15;
            case DOLL, RECORDER, REGISTER -> 10;
            case SPOON, CUP -> 5;
        };
    }

    /** What the trader sells, cheapest first. */
    public static final List<Offer> STOCK = List.of(
            offer(ItemKind.KNIFE, 0),
            offer(ItemKind.BAT, 0),
            offer(ItemKind.MEDKIT, 0),
            offer(ItemKind.CROSSBOW, GameConstants.CROSSBOW_BOLTS),
            offer(ItemKind.PISTOL, GameConstants.PISTOL_MAGAZINE));

    private static Offer offer(ItemKind kind, int ammo) {
        return new Offer(kind, ammo, MARKUP * sellPrice(kind, ammo));
    }

    public static Offer offerFor(ItemKind kind) {
        return STOCK.stream().filter(offer -> offer.kind() == kind).findFirst().orElse(null);
    }
}
