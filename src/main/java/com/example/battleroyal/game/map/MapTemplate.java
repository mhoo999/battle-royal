package com.example.battleroyal.game.map;

import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.GridMap;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.TileType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses a hand-authored room layout into a {@link GridMap}, validating it hard.
 *
 * <p>Rooms are authored rather than generated. Generation can produce unreachable
 * pockets and sealed doors, and cannot be tested. The trade-off is that a typo in a
 * template must fail loudly instead of shipping a broken room, so every structural
 * rule in GAME_RULES section 2 is asserted here.
 */
public final class MapTemplate {

    private static final int MID = GridMap.SIZE / 2;
    private static final int REQUIRED_CABINETS = 2;
    private static final int REQUIRED_ITEM_SPAWNS = 4;

    private final String name;
    private final GridMap map;

    private MapTemplate(String name, GridMap map) {
        this.name = name;
        this.map = map;
    }

    public String name() {
        return name;
    }

    public GridMap map() {
        return map;
    }

    public static MapTemplate parse(String name, String... rows) {
        require(rows.length == GridMap.SIZE,
                name + ": expected " + GridMap.SIZE + " rows, got " + rows.length);

        TileType[][] tiles = new TileType[GridMap.SIZE][GridMap.SIZE];
        List<Pos> cabinets = new ArrayList<>();
        List<Pos> itemSpawns = new ArrayList<>();
        Map<Direction, Pos> doors = new EnumMap<>(Direction.class);

        for (int y = 0; y < GridMap.SIZE; y++) {
            String row = rows[y];
            require(row.length() == GridMap.SIZE,
                    name + ": row " + y + " has length " + row.length()
                            + ", expected " + GridMap.SIZE);
            for (int x = 0; x < GridMap.SIZE; x++) {
                char c = row.charAt(x);
                TileType tile = TileType.fromSymbol(c);
                tiles[y][x] = tile;
                Pos pos = new Pos(x, y);
                if (c == TileType.ITEM_SPAWN_SYMBOL) {
                    itemSpawns.add(pos);
                } else if (tile == TileType.CABINET) {
                    cabinets.add(pos);
                } else if (tile == TileType.DOOR) {
                    doors.put(doorSide(name, pos), pos);
                }
            }
        }

        int[][] bushRegion = labelBushRegions(tiles);
        GridMap map = new GridMap(tiles, bushRegion, countRegions(bushRegion),
                cabinets, itemSpawns, doors);

        validateCounts(name, map);
        validateCabinets(name, map);
        validateReachability(name, map);

        return new MapTemplate(name, map);
    }

    /** Doors sit at the midpoint of a wall; the wall they sit on names the side. */
    private static Direction doorSide(String name, Pos p) {
        if (p.y() == 0 && p.x() == MID) {
            return Direction.UP;
        }
        if (p.y() == GridMap.SIZE - 1 && p.x() == MID) {
            return Direction.DOWN;
        }
        if (p.x() == 0 && p.y() == MID) {
            return Direction.LEFT;
        }
        if (p.x() == GridMap.SIZE - 1 && p.y() == MID) {
            return Direction.RIGHT;
        }
        throw new IllegalArgumentException(
                name + ": door at " + p + " is not on a wall midpoint");
    }

    private static void validateCounts(String name, GridMap map) {
        require(map.cabinets().size() == REQUIRED_CABINETS,
                name + ": expected " + REQUIRED_CABINETS + " cabinets, got "
                        + map.cabinets().size());
        require(map.itemSpawns().size() == REQUIRED_ITEM_SPAWNS,
                name + ": expected " + REQUIRED_ITEM_SPAWNS + " item spawns, got "
                        + map.itemSpawns().size());
        require(!map.doors().isEmpty(), name + ": room has no doors");
        require(map.bushRegionCount() >= 1, name + ": room has no bush");
    }

    /**
     * A cabinet must be enterable, and must not touch a bush. Letting the two
     * concealment rules share an edge makes it ambiguous which one applies.
     */
    private static void validateCabinets(String name, GridMap map) {
        for (Pos cabinet : map.cabinets()) {
            boolean enterable = false;
            for (Direction dir : Direction.values()) {
                Pos n = cabinet.step(dir);
                require(!map.isBush(n),
                        name + ": cabinet at " + cabinet + " touches bush at " + n);
                if (map.walkable(n)) {
                    enterable = true;
                }
            }
            require(enterable,
                    name + ": cabinet at " + cabinet + " has no walkable neighbour");
        }
    }

    /**
     * Every walkable tile must be reachable from every other, and every cabinet must
     * border reachable ground. Otherwise a player can spawn somewhere with no way
     * out, or an item can spawn where nobody can collect it.
     */
    private static void validateReachability(String name, GridMap map) {
        Pos start = map.itemSpawns().getFirst();
        Set<Pos> reached = flood(map, start);

        for (int y = 0; y < GridMap.SIZE; y++) {
            for (int x = 0; x < GridMap.SIZE; x++) {
                Pos p = new Pos(x, y);
                if (map.walkable(p)) {
                    require(reached.contains(p),
                            name + ": " + map.tileAt(p) + " at " + p
                                    + " is unreachable from " + start);
                }
            }
        }

        for (Pos cabinet : map.cabinets()) {
            boolean bordered = false;
            for (Direction dir : Direction.values()) {
                if (reached.contains(cabinet.step(dir))) {
                    bordered = true;
                    break;
                }
            }
            require(bordered, name + ": cabinet at " + cabinet + " is unreachable");
        }
    }

    private static Set<Pos> flood(GridMap map, Pos start) {
        Set<Pos> seen = new HashSet<>();
        Deque<Pos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            Pos p = queue.removeFirst();
            for (Direction dir : Direction.values()) {
                Pos n = p.step(dir);
                if (map.walkable(n) && seen.add(n)) {
                    queue.addLast(n);
                }
            }
        }
        return seen;
    }

    /** Flood-fills connected BUSH tiles so each bush patch gets its own id. */
    private static int[][] labelBushRegions(TileType[][] tiles) {
        int[][] region = new int[GridMap.SIZE][GridMap.SIZE];
        for (int[] row : region) {
            Arrays.fill(row, GridMap.NO_BUSH);
        }
        int next = 0;
        for (int y = 0; y < GridMap.SIZE; y++) {
            for (int x = 0; x < GridMap.SIZE; x++) {
                if (tiles[y][x] != TileType.BUSH || region[y][x] != GridMap.NO_BUSH) {
                    continue;
                }
                Deque<Pos> queue = new ArrayDeque<>();
                region[y][x] = next;
                queue.add(new Pos(x, y));
                while (!queue.isEmpty()) {
                    Pos p = queue.removeFirst();
                    for (Direction dir : Direction.values()) {
                        Pos n = p.step(dir);
                        if (n.x() < 0 || n.x() >= GridMap.SIZE
                                || n.y() < 0 || n.y() >= GridMap.SIZE) {
                            continue;
                        }
                        if (tiles[n.y()][n.x()] == TileType.BUSH
                                && region[n.y()][n.x()] == GridMap.NO_BUSH) {
                            region[n.y()][n.x()] = next;
                            queue.addLast(n);
                        }
                    }
                }
                next++;
            }
        }
        return region;
    }

    private static int countRegions(int[][] region) {
        int max = GridMap.NO_BUSH;
        for (int[] row : region) {
            for (int v : row) {
                max = Math.max(max, v);
            }
        }
        return max + 1;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
