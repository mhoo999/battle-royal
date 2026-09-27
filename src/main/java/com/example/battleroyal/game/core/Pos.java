package com.example.battleroyal.game.core;

/** A tile coordinate. Immutable. */
public record Pos(int x, int y) {

    public Pos step(Direction dir) {
        return new Pos(x + dir.dx(), y + dir.dy());
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
