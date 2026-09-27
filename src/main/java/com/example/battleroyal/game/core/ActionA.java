package com.example.battleroyal.game.core;

/**
 * What the A button currently does, derived from the held item.
 *
 * <p>A token rather than display text: the server decides the semantics, the client
 * decides the words. That keeps wording out of the simulation while still preventing
 * the client from guessing at rules.
 */
public enum ActionA {
    ATTACK,
    FIRE,
    RELOAD,
    HEAL
}
