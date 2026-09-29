package com.example.battleroyal.game.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The static terrain of one room: 15x15, wall border, doors at wall midpoints.
 *
 * <p>Immutable and shared between rooms built from the same template. Everything
 * that changes during play (players, items, cabinet occupancy) lives in the room,
 * not here.
 *
 * <p>Bush tiles are grouped into regions at parse time. Concealment works per
 * region rather than per adjacent tile, so {@link #sameBush} is the single source
 * of truth for "are these two players hiding in the same bush".
 */
public final class GridMap {

    public static final int SIZE = 15;
    public static final int NO_BUSH = -1;

    private final TileType[][] tiles;
    private final int[][] bushRegion;
    private final int bushRegionCount;
    private final List<Pos> cabinets;
    private final List<Pos> itemSpawns;
    private final Map<Direction, Pos> doors;

    /**
     * Build from already-parsed terrain. Callers are expected to come through
     * {@code MapTemplate.parse}, which is what validates the layout; this
     * constructor trusts its arguments and checks nothing.
     */
    public GridMap(TileType[][] tiles, int[][] bushRegion, int bushRegionCount,
                   List<Pos> cabinets, List<Pos> itemSpawns, Map<Direction, Pos> doors) {
        this.tiles = tiles;
        this.bushRegion = bushRegion;
        this.bushRegionCount = bushRegionCount;
        this.cabinets = List.copyOf(cabinets);
        this.itemSpawns = List.copyOf(itemSpawns);
        // Not Map.copyOf: immutable maps iterate in an order salted per JVM run, and
        // door order decides which free door a new link takes. That made the world
        // graph, and every encounter measurement, differ from one test run to the next.
        this.doors = Collections.unmodifiableMap(new EnumMap<>(doors));
    }

    public boolean inBounds(Pos p) {
        return p.x() >= 0 && p.x() < SIZE && p.y() >= 0 && p.y() < SIZE;
    }

    public TileType tileAt(Pos p) {
        if (!inBounds(p)) {
            throw new IndexOutOfBoundsException("Out of map: " + p);
        }
        return tiles[p.y()][p.x()];
    }

    /** Whether a MOVE may end here. False for walls and cabinets. */
    public boolean walkable(Pos p) {
        return inBounds(p) && tiles[p.y()][p.x()].walkable();
    }

    /** Whether a raycast stops at this tile. True for walls and cabinets. */
    public boolean blocksRaycast(Pos p) {
        return !inBounds(p) || tiles[p.y()][p.x()].blocksRaycast();
    }

    /** Bush region id, or {@link #NO_BUSH} when this tile is not a bush. */
    public int bushRegionAt(Pos p) {
        return inBounds(p) ? bushRegion[p.y()][p.x()] : NO_BUSH;
    }

    public boolean isBush(Pos p) {
        return bushRegionAt(p) != NO_BUSH;
    }

    /** True only when both positions are bush tiles of the same region. */
    public boolean sameBush(Pos a, Pos b) {
        int ra = bushRegionAt(a);
        return ra != NO_BUSH && ra == bushRegionAt(b);
    }

    public int bushRegionCount() {
        return bushRegionCount;
    }

    public List<Pos> cabinets() {
        return cabinets;
    }

    public List<Pos> itemSpawns() {
        return itemSpawns;
    }

    public Map<Direction, Pos> doors() {
        return doors;
    }

    public Pos doorAt(Direction side) {
        return doors.get(side);
    }

    /**
     * Terrain as 15 rows of tile symbols, for the client to render.
     *
     * <p>Item spawn markers are not included: spawns are dynamic and travel in the
     * snapshot's item list, so a spawn tile appears here as plain floor.
     */
    public List<String> terrainRows() {
        return Arrays.stream(tiles)
                .map(row -> {
                    StringBuilder sb = new StringBuilder(SIZE);
                    for (TileType tile : row) {
                        sb.append(tile.symbol());
                    }
                    return sb.toString();
                })
                .toList();
    }
}
