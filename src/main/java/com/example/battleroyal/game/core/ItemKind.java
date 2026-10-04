package com.example.battleroyal.game.core;

/**
 * The V1 items. A player carries exactly one, so picking anything up is always a
 * trade.
 *
 * <p>Most of what lies around is junk from a school trip: a spoon, a cup, a doll
 * leaking stuffing, a recorder, the class register. Junk hits about as hard as a bare
 * fist, never less, because there is no way to drop an item: junk that could not
 * attack would leave its holder worse off than empty-handed until they found a swap.
 * The pan is the one piece of junk that does real harm. A real weapon is a lucky find.
 *
 * <p>Only identity lives here. Range, damage, cooldown, ammunition and loot weight
 * are in {@code game.rule.GameConstants}.
 */
public enum ItemKind {
    KNIFE(false),
    BAT(false),
    PISTOL(true),
    CROSSBOW(true),
    MEDKIT(false),
    PAN(false),
    SPOON(false),
    CUP(false),
    DOLL(false),
    RECORDER(false),
    REGISTER(false),
    /** Pistol rounds, a bundle; its ammo is how many are in it. Loaded into a pistol by A. */
    ROUNDS(true),
    /** Crossbow bolts, likewise. */
    BOLTS(true),
    /** Worn in the bag slot, it adds inventory slots (V2.1). Anywhere else it is loot. */
    SMALL_BAG(false),
    BIG_BAG(false);

    private final boolean usesAmmo;

    ItemKind(boolean usesAmmo) {
        this.usesAmmo = usesAmmo;
    }

    public boolean usesAmmo() {
        return usesAmmo;
    }
}
