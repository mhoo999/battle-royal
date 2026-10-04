package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.ItemKind;

import java.util.List;

/**
 * What things are worth (D11): the trader's buying price for each item is its value,
 * which is also what the ranking counts. A first draft (Q12), to be tuned by play.
 *
 * <p>A gun is worth its own price plus whatever is loaded in it, each round valued as
 * it would be in a bundle. The trader sells guns empty and ammunition apart, only real
 * gear, never junk, at three times what it pays, so buying and selling straight back
 * always loses money.
 */
public final class ItemValues {

    private ItemValues() {
    }

    /** One line of the trader's stock: always in stock, always full. */
    public record Offer(ItemKind kind, int ammo, int price) {
    }

    static final int PISTOL_BASE = 90;
    static final int CROSSBOW_BASE = 60;
    static final int PER_ROUND = 10;
    static final int MARKUP = 3;

    /** What the trader pays for this item. */
    public static int sellPrice(ItemKind kind, int ammo) {
        return switch (kind) {
            case PISTOL -> PISTOL_BASE + PER_ROUND * ammo;
            case CROSSBOW -> CROSSBOW_BASE + PER_ROUND * ammo;
            case ROUNDS, BOLTS -> PER_ROUND * ammo;
            case KNIFE -> 40;
            case BAT -> 50;
            case MEDKIT -> 60;
            case PAN -> 15;
            case DOLL, RECORDER, REGISTER -> 10;
            case SPOON, CUP -> 5;
            case SMALL_BAG -> 50;
            case BIG_BAG -> 150;
        };
    }

    /** What the trader sells, cheapest first. */
    public static final List<Offer> STOCK = List.of(
            offer(ItemKind.BOLTS, GameConstants.CROSSBOW_BOLTS),
            offer(ItemKind.KNIFE, 0),
            offer(ItemKind.SMALL_BAG, 0),
            offer(ItemKind.BAT, 0),
            offer(ItemKind.MEDKIT, 0),
            offer(ItemKind.ROUNDS, GameConstants.PISTOL_MAGAZINE),
            offer(ItemKind.CROSSBOW, 0),
            offer(ItemKind.PISTOL, 0),
            offer(ItemKind.BIG_BAG, 0));

    private static Offer offer(ItemKind kind, int ammo) {
        return new Offer(kind, ammo, MARKUP * sellPrice(kind, ammo));
    }

    public static Offer offerFor(ItemKind kind) {
        return STOCK.stream().filter(offer -> offer.kind() == kind).findFirst().orElse(null);
    }
}
