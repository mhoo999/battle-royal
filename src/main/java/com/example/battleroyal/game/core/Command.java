package com.example.battleroyal.game.core;

/**
 * Everything a client is allowed to ask for. Intent only.
 *
 * <p>There is deliberately no command carrying a position, hp, damage or inventory.
 * The server decides all of that; see CLAUDE.md on server authority. Adding such a
 * command would be the single most damaging change possible to this codebase.
 */
public sealed interface Command {

    String playerId();

    /** Face this direction, and step one tile if that tile is free. */
    record Move(String playerId, Direction dir) implements Command {
    }

    /** Use the held item: attack, fire, reload when empty, or heal. */
    record ActionA(String playerId) implements Command {
    }

    /** Context action: pick up, swap, take a door, hide, or leave a cabinet. */
    record ActionB(String playerId) implements Command {
    }
}
