package com.example.battleroyal.game.rule;

/**
 * How big the world is for a given number of players.
 *
 * <p>The world is a torus of rooms: walk off the east edge and you come in at the west
 * one. That gives it a geography a player can learn, and a pursuer can follow, while
 * still having no edge to get lost against. Its size is what sets how long people
 * wander before they meet; see {@link GameConstants#ROOMS_PER_OTHER_PLAYER}.
 */
public final class WorldSize {

    /** Columns by rows. Columns are never fewer than rows. */
    public record Grid(int columns, int rows) {
        public int area() {
            return columns * rows;
        }
    }

    private WorldSize() {
    }

    /**
     * The smallest grid in the sequence 3x3, 4x3, 4x4, 5x4, 5x5, ... holding at least
     * {@link GameConstants#ROOMS_PER_OTHER_PLAYER} rooms for every other player.
     *
     * <p>Growing one side at a time keeps every step a superset of the last, so a grid
     * that grows only adds rooms and one that shrinks only removes them.
     */
    public static Grid forPopulation(int players) {
        int wanted = Math.max(GameConstants.MIN_WORLD_SIDE * GameConstants.MIN_WORLD_SIDE,
                GameConstants.ROOMS_PER_OTHER_PLAYER * (players - 1));
        for (int side = GameConstants.MIN_WORLD_SIDE; ; side++) {
            if (side * side >= wanted) {
                return new Grid(side, side);
            }
            if ((side + 1) * side >= wanted) {
                return new Grid(side + 1, side);
            }
        }
    }
}
