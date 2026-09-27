package com.example.battleroyal.game.core;

/**
 * What the B button currently does, derived from the player's surroundings.
 *
 * <p>Resolved server-side so the client cannot disagree about which interaction is
 * available. Bushes never appear here: you walk into a bush, you do not press B.
 */
public enum ActionB {
    PICKUP,
    SWAP,
    DOOR,
    HIDE,
    UNHIDE
}
