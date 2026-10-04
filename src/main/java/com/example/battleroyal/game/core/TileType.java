package com.example.battleroyal.game.core;

/**
 * Terrain. The two flags encode the whole of GAME_RULES section 2:
 * BUSH conceals but does not stop bullets, CABINET stops bullets and is entered
 * with B rather than walked into.
 */
public enum TileType {
    FLOOR('.', true, false),
    DOOR('+', true, false),
    BUSH('b', true, false),
    CABINET('C', false, true),
    WALL('#', false, true);

    /** Template character marking a floor tile that also spawns items. */
    public static final char ITEM_SPAWN_SYMBOL = '*';

    /** An outpost guard's post (V2.2): floor, with a guard standing on it. */
    public static final char GUARD_POST_SYMBOL = 'G';

    private final char symbol;
    private final boolean walkable;
    private final boolean blocksRaycast;

    TileType(char symbol, boolean walkable, boolean blocksRaycast) {
        this.symbol = symbol;
        this.walkable = walkable;
        this.blocksRaycast = blocksRaycast;
    }

    public char symbol() {
        return symbol;
    }

    /** Whether a MOVE command may end on this tile. Cabinets are entered with B. */
    public boolean walkable() {
        return walkable;
    }

    public boolean blocksRaycast() {
        return blocksRaycast;
    }

    public static TileType fromSymbol(char c) {
        if (c == ITEM_SPAWN_SYMBOL || c == GUARD_POST_SYMBOL) {
            return FLOOR;
        }
        for (TileType t : values()) {
            if (t.symbol == c) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown map symbol: '" + c + "'");
    }
}
