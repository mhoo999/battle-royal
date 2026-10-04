package com.example.battleroyal.game.map;

import java.util.List;
import java.util.Random;

/**
 * The authored room layouts. Rooms pick one of these at random on creation.
 *
 * <p>Symbols: {@code #} wall, {@code .} floor, {@code +} door, {@code C} cabinet,
 * {@code b} bush, {@code *} item spawn (on floor). Each room must have exactly 2
 * cabinets and 4 item spawns, at least one bush region, and doors only at wall
 * midpoints, and no item spawn within reach of a door. {@link MapTemplate#parse}
 * enforces all of that plus full reachability,
 * so editing a layout incorrectly fails at class-load time rather than in play.
 */
public final class MapTemplates {

    /** Open room with a solid core. Long sightlines down the middle rows. */
    public static final MapTemplate CROSSROADS = MapTemplate.parse("CROSSROADS",
            "#######+#######",
            "#.............#",
            "#..bbb....*...#",
            "#..bbb........#",
            "#..bbb...C....#",
            "#.............#",
            "#.....###.....#",
            "+....*###*....+",
            "#.....###.....#",
            "#.............#",
            "#....C...bbb..#",
            "#........bbb..#",
            "#...*....bbb..#",
            "#.............#",
            "#######+#######");

    /** Cabinets tucked into wall alcoves; a wide bush across the lower half. */
    public static final MapTemplate ALCOVES = MapTemplate.parse("ALCOVES",
            "#######+#######",
            "#..*.......*..#",
            "#.##.......##.#",
            "#.#C..bbb..C#.#",
            "#.##..bbb..##.#",
            "#.....bbb.....#",
            "#.............#",
            "+.............+",
            "#.............#",
            "#..##.....##..#",
            "#.....bbb.....#",
            "#..*..bbb..*..#",
            "#.....bbb.....#",
            "#.............#",
            "#######+#######");

    /** Two offset blocks break every straight line except the door corridor. */
    public static final MapTemplate PILLARS = MapTemplate.parse("PILLARS",
            "#######+#######",
            "#.............#",
            "#..bbb...*....#",
            "#..bbb........#",
            "#..bbb..###...#",
            "#.......###...#",
            "#....C..###..*#",
            "+.............+",
            "#*..###.......#",
            "#...###..bbb..#",
            "#...###..bbb..#",
            "#........bbb..#",
            "#....*......C.#",
            "#.............#",
            "#######+#######");

    /** A wall down the middle of each half, with the door corridors left open. */
    public static final MapTemplate CHAMBERS = MapTemplate.parse("CHAMBERS",
            "#######+#######",
            "#.............#",
            "#.bbb..#..*...#",
            "#.bbb..#......#",
            "#.bbb..#...C..#",
            "#......#......#",
            "#......*......#",
            "+.............+",
            "#......*......#",
            "#..C...#......#",
            "#......#..bbb.#",
            "#...*..#..bbb.#",
            "#......#..bbb.#",
            "#.............#",
            "#######+#######");

    /** Filled corners and a bush running up the centre line. */
    public static final MapTemplate ARENA = MapTemplate.parse("ARENA",
            "#######+#######",
            "#..##.....##..#",
            "#.##..bbb..##.#",
            "#.#...bbb..C#.#",
            "#.....bbb.....#",
            "#......*......#",
            "#...*.........#",
            "+.............+",
            "#.............#",
            "#......*......#",
            "#.....bbb.....#",
            "#.#C..bbb...#.#",
            "#.##..bbb..##.#",
            "#..##*....##..#",
            "#######+#######");

    /** Two bushes side by side, one tile apart, so concealment does not bleed across. */
    public static final MapTemplate GALLERY = MapTemplate.parse("GALLERY",
            "#######+#######",
            "#.............#",
            "#.###.....###.#",
            "#...bbb.bbb...#",
            "#...bbb.bbb...#",
            "#...bbb.bbb...#",
            "#.............#",
            "+......*......+",
            "#.............#",
            "#..*...C...*..#",
            "#.###.....###.#",
            "#.............#",
            "#.....C.......#",
            "#...*.........#",
            "#######+#######");

    /** An off-centre pocket you have to walk around, reachable only from the east. */
    public static final MapTemplate HOOK = MapTemplate.parse("HOOK",
            "#######+#######",
            "#....*........#",
            "#...*.....bbb.#",
            "#.........bbb.#",
            "#..####...bbb.#",
            "#..#..........#",
            "#..#...C......#",
            "+..#..........+",
            "#..#..........#",
            "#..####....*..#",
            "#.bbb.........#",
            "#.bbb....C....#",
            "#.bbb.........#",
            "#.......*.....#",
            "#######+#######");

    /** Scattered two-by-two blocks; almost no straight line crosses the room. */
    public static final MapTemplate QUARRY = MapTemplate.parse("QUARRY",
            "#######+#######",
            "#..bbb....##..#",
            "#..bbb...*##..#",
            "#..bbb........#",
            "#.....##......#",
            "#..C..##...*..#",
            "#.........C...#",
            "+.............+",
            "#.............#",
            "#..*...##.....#",
            "#......##.....#",
            "#........bbb..#",
            "#..##....bbb..#",
            "#..##.*..bbb..#",
            "#######+#######");

    /**
     * Rows of desks, a teacher's podium, potted plants at the back: cover everywhere,
     * cover nowhere, and nothing but the aisle down the middle row.
     */
    public static final MapTemplate CLASSROOM = MapTemplate.parse("CLASSROOM",
            "#######+#######",
            "#..*.......C..#",
            "#..#####......#",
            "#...........*.#",
            "#.#.#.#.#.#.#.#",
            "#.............#",
            "#.#.#.#.#.#.#.#",
            "+.............+",
            "#.#.#.#.#.#.#.#",
            "#.............#",
            "#.#.#.#*#.#.#.#",
            "#.............#",
            "#bb.......*.bb#",
            "#bb...C.....bb#",
            "#######+#######");

    /**
     * The island clinic: bed bays behind partitions on either side, a ward down the
     * middle with planters in it.
     */
    public static final MapTemplate CLINIC = MapTemplate.parse("CLINIC",
            "#######+#######",
            "#...#....*#...#",
            "#.*.#.....#.C.#",
            "#...#..b..#...#",
            "##.##.bbb.##.##",
            "#......b......#",
            "#.............#",
            "+.............+",
            "#.............#",
            "#......b......#",
            "##.##.bbb.##.##",
            "#...#..b..#...#",
            "#.C.#.....#.*.#",
            "#...#.*...#...#",
            "#######+#######");

    /**
     * A round tower with one way in from the south and a cabinet inside it: the safest
     * room on the island to hide in, and the easiest to be trapped in.
     */
    public static final MapTemplate LIGHTHOUSE = MapTemplate.parse("LIGHTHOUSE",
            "#######+#######",
            "#.............#",
            "#..*.......bb.#",
            "#....#####.bb.#",
            "#...##...##...#",
            "#...#..C..#...#",
            "#...#.....#..*#",
            "+...#.....#...+",
            "#...##...##...#",
            "#....##.##....#",
            "#.............#",
            "#.bb.......*..#",
            "#.bb..C.......#",
            "#....*........#",
            "#######+#######");

    /**
     * Woods: two big winding thickets and lone trees. Long ways round, and a lot of
     * bush to lose someone in.
     */
    public static final MapTemplate FOREST = MapTemplate.parse("FOREST",
            "#######+#######",
            "#...#....#..*.#",
            "#.bbb.#.......#",
            "#.bbbb...#....#",
            "#..bbbb....#..#",
            "#.C.bbb.#.....#",
            "#......#....#.#",
            "+..#..........+",
            "#.#....#....C.#",
            "#.....#.bbb...#",
            "#..*...bbbb...#",
            "#.#...bbbb..#.#",
            "#..#..bbb..*..#",
            "#.#..*.....#..#",
            "#######+#######");

    /**
     * Four village houses round a crossroads, each with one doorway onto the middle
     * lane. Inside a house you see who comes in, and nothing else.
     */
    public static final MapTemplate VILLAGE = MapTemplate.parse("VILLAGE",
            "#######+#######",
            "#bb...........#",
            "#.#####.#####.#",
            "#.#C..#.#..*#.#",
            "#.#.........#.#",
            "#.#...#.#...#.#",
            "#.#####.#####.#",
            "+.............+",
            "#.#####.#####.#",
            "#.#...#.#.*.#.#",
            "#.#.........#.#",
            "#.#*..#.#..C#.#",
            "#.#####.#####.#",
            "#.........*.bb#",
            "#######+#######");

    /**
     * Open sand with two beached boats and dune grass: long sightlines, and a gun
     * carries the whole room.
     */
    public static final MapTemplate BEACH = MapTemplate.parse("BEACH",
            "#######+#######",
            "#.............#",
            "#..##......*..#",
            "#..##.........#",
            "#.......bbbb..#",
            "#.C.....bbbb..#",
            "#.............#",
            "+.............+",
            "#.............#",
            "#..bbbb.......#",
            "#..bbbb.....C.#",
            "#.........##..#",
            "#.*.......##..#",
            "#.....*.....*.#",
            "#######+#######");

    /**
     * Even the smallest world has nine rooms. Three layouts read as one repeating pair;
     * with eight, and no room sharing a layout with its neighbours, a repeat feels like
     * coincidence. The six after QUARRY (V2.2) are places on the film's island.
     */
    public static final List<MapTemplate> ALL = List.of(
            CROSSROADS, ALCOVES, PILLARS, CHAMBERS, ARENA, GALLERY, HOOK, QUARRY,
            CLASSROOM, CLINIC, LIGHTHOUSE, FOREST, VILLAGE, BEACH);

    private MapTemplates() {
    }

    public static MapTemplate random(Random random) {
        return ALL.get(random.nextInt(ALL.size()));
    }
}
