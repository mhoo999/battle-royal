package com.example.battleroyal.game.core;

/**
 * What the B button currently does, derived from the player's surroundings.
 *
 * <p>Resolved server-side so the client cannot disagree about which interaction is
 * available. Bushes and cabinets never appear here: you walk into them, you do not
 * press B.
 */
public enum ActionB {
    /** Start opening the crate underfoot; held, like V1's loot. */
    OPEN,
    /** Close the crate this player has open. */
    CLOSE,
    DOOR,
    /** Hold to leave the island through one of this player's own exits. */
    EXTRACT
}
