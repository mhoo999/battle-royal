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

    /** Use the held item: attack, fire, or heal. */
    record ActionA(String playerId) implements Command {
    }

    /** Context action: open or close a crate, or take a door. */
    record ActionB(String playerId) implements Command {
    }

    /** B let go. Abandons a loot in progress; looting lasts only while B is held. */
    record ReleaseB(String playerId) implements Command {
    }

    /** Make this inventory slot the one A uses. */
    record Equip(String playerId, int slot) implements Command {
    }

    /**
     * From the open crate into an inventory slot. An occupied slot trades places: its
     * item goes into the crate where the taken one was.
     */
    record Take(String playerId, int crateIndex, int slot) implements Command {
    }

    /** From an inventory slot into the open crate. */
    record Put(String playerId, int slot) implements Command {
    }

    /** Close the open crate without moving. */
    record CloseCrate(String playerId) implements Command {
    }
}
