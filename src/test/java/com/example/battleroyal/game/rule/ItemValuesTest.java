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
    void buyingAndSellingStraightBackAlwaysLoses() {
        for (ItemValues.Offer offer : ItemValues.STOCK) {
            assertTrue(offer.price() > ItemValues.sellPrice(offer.kind(), offer.ammo()),
                    offer.kind() + " is no money machine");
        }
    }

    @Test
    void theTraderSellsGearFullyLoadedAndNoJunk() {
        assertEquals(GameConstants.PISTOL_MAGAZINE, ItemValues.offerFor(ItemKind.PISTOL).ammo());
        assertEquals(GameConstants.CROSSBOW_BOLTS, ItemValues.offerFor(ItemKind.CROSSBOW).ammo());
        assertNull(ItemValues.offerFor(ItemKind.CUP));
        assertNull(ItemValues.offerFor(ItemKind.PAN));
    }
}
