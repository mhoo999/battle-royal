package com.example.battleroyal.game.core;

/**
 * What the B button currently does, derived from the player's surroundings.
 *
 * <p>Resolved server-side so the client cannot disagree about which interaction is
 * available. Bushes and cabinets never appear here: you walk into them, you do not
 * press B.
 */
public enum ActionB {
    PICKUP,
    SWAP,
    DOOR
}
