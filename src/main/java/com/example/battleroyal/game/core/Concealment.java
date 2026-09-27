package com.example.battleroyal.game.core;

/**
 * How a player is hidden, if at all. The two kinds are deliberately asymmetric:
 * a bush conceals you while letting you move and shoot, a cabinet stops bullets
 * but freezes you.
 */
public enum Concealment {
    /** In the open. */
    NONE,
    /** Standing in a bush: invisible to anyone outside that bush, bullets pass through. */
    BUSH,
    /** Inside a cabinet: invisible, bullets blocked, cannot move or attack. */
    CABINET;

    public boolean hidden() {
        return this != NONE;
    }
}
