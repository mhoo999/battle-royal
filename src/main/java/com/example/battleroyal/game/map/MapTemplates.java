package com.example.battleroyal.game.map;

import java.util.List;
import java.util.Random;

/**
 * The authored room layouts. Rooms pick one of these at random on creation.
 *
 * <p>Symbols: {@code #} wall, {@code .} floor, {@code +} door, {@code C} cabinet,
 * {@code b} bush, {@code *} item spawn (on floor). Each room must have exactly 2
 * cabinets and 4 item spawns, at least one bush region, and doors only at wall
 * midpoints. {@link MapTemplate#parse} enforces all of that plus full reachability,
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
            "#..##..*..##..#",
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
            "#......*......#",
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
     * Rooms are regenerated whenever the halo discards and rebuilds them, so a player
     * walking any distance sees a lot of rooms. Three layouts read as one repeating
     * pair; eight is enough that a repeat feels like coincidence.
     */
    public static final List<MapTemplate> ALL = List.of(
            CROSSROADS, ALCOVES, PILLARS, CHAMBERS, ARENA, GALLERY, HOOK, QUARRY);

    private MapTemplates() {
    }

    public static MapTemplate random(Random random) {
        return ALL.get(random.nextInt(ALL.size()));
    }
}
