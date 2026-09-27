package com.example.battleroyal.game.core;

/**
 * The three V1 items. A player carries exactly one, so picking anything up is
 * always a trade.
 *
 * <p>Only identity lives here. Range, damage, cooldown and magazine size are in
 * {@code game.rule.GameConstants}.
 */
public enum ItemKind {
    KNIFE(false),
    PISTOL(true),
    MEDKIT(false);

    private final boolean usesAmmo;

    ItemKind(boolean usesAmmo) {
        this.usesAmmo = usesAmmo;
    }

    public boolean usesAmmo() {
        return usesAmmo;
    }
}
