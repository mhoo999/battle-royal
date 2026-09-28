package com.example.battleroyal.game.core;

/**
 * The V1 items. A player carries exactly one, so picking anything up is always a
 * trade.
 *
 * <p>Pan and Spoon are the junk that makes a real weapon a lucky find: the pan is a
 * poor club, the spoon does nothing at all but still fills the one slot.
 *
 * <p>Only identity lives here. Range, damage, cooldown, magazine size and spawn weight
 * are in {@code game.rule.GameConstants}.
 */
public enum ItemKind {
    KNIFE(false),
    PISTOL(true),
    MEDKIT(false),
    PAN(false),
    SPOON(false);

    private final boolean usesAmmo;

    ItemKind(boolean usesAmmo) {
        this.usesAmmo = usesAmmo;
    }

    public boolean usesAmmo() {
        return usesAmmo;
    }
}
