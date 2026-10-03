package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.ItemKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemValuesTest {

    @Test
    void everythingIsWorthSomething() {
        for (ItemKind kind : ItemKind.values()) {
            assertTrue(ItemValues.sellPrice(kind, 1) > 0, kind + " sells for something");
        }
    }

    @Test
    void aGunIsWorthLessWithEveryShotFired() {
        assertTrue(ItemValues.sellPrice(ItemKind.PISTOL, 1)
                < ItemValues.sellPrice(ItemKind.PISTOL, GameConstants.PISTOL_MAGAZINE));
        assertTrue(ItemValues.sellPrice(ItemKind.CROSSBOW, 1)
                < ItemValues.sellPrice(ItemKind.CROSSBOW, GameConstants.CROSSBOW_BOLTS));
    }

    @Test
    void aLoadedGunIsWorthTheGunPlusItsRounds() {
        assertEquals(ItemValues.sellPrice(ItemKind.PISTOL, 0)
                        + ItemValues.sellPrice(ItemKind.ROUNDS, GameConstants.PISTOL_MAGAZINE),
                ItemValues.sellPrice(ItemKind.PISTOL, GameConstants.PISTOL_MAGAZINE),
                "loading a gun neither makes nor loses value");
        assertTrue(ItemValues.sellPrice(ItemKind.PISTOL, 0) > 0, "an empty gun is still worth keeping");
    }

    @Test
    void theStockIsListedCheapestFirst() {
        for (int i = 1; i < ItemValues.STOCK.size(); i++) {
            assertTrue(ItemValues.STOCK.get(i - 1).price() <= ItemValues.STOCK.get(i).price());
        }
    }

    @Test
    void buyingAndSellingStraightBackAlwaysLoses() {
        for (ItemValues.Offer offer : ItemValues.STOCK) {
            assertTrue(offer.price() > ItemValues.sellPrice(offer.kind(), offer.ammo()),
                    offer.kind() + " is no money machine");
        }
    }

    @Test
    void theTraderSellsGunsEmptyAmmunitionApartAndNoJunk() {
        assertEquals(0, ItemValues.offerFor(ItemKind.PISTOL).ammo());
        assertEquals(0, ItemValues.offerFor(ItemKind.CROSSBOW).ammo());
        assertEquals(GameConstants.PISTOL_MAGAZINE, ItemValues.offerFor(ItemKind.ROUNDS).ammo());
        assertEquals(GameConstants.CROSSBOW_BOLTS, ItemValues.offerFor(ItemKind.BOLTS).ammo());
        assertNull(ItemValues.offerFor(ItemKind.CUP));
        assertNull(ItemValues.offerFor(ItemKind.PAN));
    }
}
