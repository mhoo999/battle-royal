package com.example.battleroyal.game.core;

/**
 * One of a player's own ways off the island: a floor tile in some room, and where that
 * room lies from the room the player is in now, for the compass.
 *
 * <p>Private to its owner, like hp. Nobody else is ever told where your exits are, so
 * nobody can wait at one (docs/V2_PLAN.md, D9).
 *
 * @param dx rooms east (negative: west) along the shorter way round the world
 * @param dy rooms south (negative: north), likewise
 */
public record Exit(String roomId, Pos at, int dx, int dy) {

    public boolean in(Room room) {
        return roomId.equals(room.id());
    }

    public Exit pointedFrom(int dx, int dy) {
        return new Exit(roomId, at, dx, dy);
    }
}
