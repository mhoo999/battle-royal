package com.example.battleroyal.game.map;

import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.GridMap;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.TileType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapTemplateTest {

    /** A known-good layout, kept as source rows so the mutations below stay readable. */
    private static final String[] VALID = {
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
            "#######+#######",
    };

    private static String[] mutate(int row, String replacement) {
        String[] rows = VALID.clone();
        rows[row] = replacement;
        return rows;
    }

    // --- The shipped templates --------------------------------------------

    @Test
    void everyShippedTemplateHasTheRequiredFurniture() {
        for (MapTemplate template : MapTemplates.ALL) {
            GridMap map = template.map();
            assertEquals(2, map.cabinets().size(), template.name() + " cabinets");
            assertEquals(4, map.itemSpawns().size(), template.name() + " item spawns");
            assertEquals(2, map.bushRegionCount(), template.name() + " bush regions");
        }
    }

    @Test
    void noTwoTemplatesAreTheSameLayout() {
        Map<String, String> byTerrain = new HashMap<>();
        for (MapTemplate template : MapTemplates.ALL) {
            String terrain = String.join("\n", template.map().terrainRows());
            String clash = byTerrain.put(terrain, template.name());
            assertNull(clash,
                    template.name() + " is a duplicate of " + clash
                            + "; hand-authored layouts make copy-paste easy to miss");
        }
        assertEquals(MapTemplates.ALL.size(), byTerrain.size());
    }

    @Test
    void thereAreEnoughLayoutsThatWalkingAroundDoesNotFeelLikeOneRoom() {
        assertTrue(MapTemplates.ALL.size() >= 6,
                "the halo discards and regenerates rooms constantly, so a small set "
                        + "reads as the same two rooms repeating");
    }

    @Test
    void everyShippedTemplateHasFourDoorsOnWallMidpoints() {
        for (MapTemplate template : MapTemplates.ALL) {
            GridMap map = template.map();
            String who = template.name();
            assertEquals(4, map.doors().size(), who);
            assertEquals(new Pos(7, 0), map.doorAt(Direction.UP), who);
            assertEquals(new Pos(7, 14), map.doorAt(Direction.DOWN), who);
            assertEquals(new Pos(0, 7), map.doorAt(Direction.LEFT), who);
            assertEquals(new Pos(14, 7), map.doorAt(Direction.RIGHT), who);
        }
    }

    @Test
    void cabinetsBlockBulletsAndAreNotWalkable() {
        for (MapTemplate template : MapTemplates.ALL) {
            GridMap map = template.map();
            for (Pos cabinet : map.cabinets()) {
                assertTrue(map.blocksRaycast(cabinet),
                        template.name() + ": cabinet must stop a shot at " + cabinet);
                assertFalse(map.walkable(cabinet),
                        template.name() + ": cabinet is entered with B, not walked into");
            }
        }
    }

    @Test
    void bushesConcealButDoNotStopBullets() {
        for (MapTemplate template : MapTemplates.ALL) {
            GridMap map = template.map();
            for (int y = 0; y < GridMap.SIZE; y++) {
                for (int x = 0; x < GridMap.SIZE; x++) {
                    Pos p = new Pos(x, y);
                    if (map.tileAt(p) != TileType.BUSH) {
                        continue;
                    }
                    assertFalse(map.blocksRaycast(p),
                            template.name() + ": bush must not stop a shot at " + p);
                    assertTrue(map.walkable(p),
                            template.name() + ": bush is walked into at " + p);
                }
            }
        }
    }

    // --- Bush regions -----------------------------------------------------

    @Test
    void bushRegionsAreSeparateSoConcealmentDoesNotLeakBetweenThem() {
        GridMap map = MapTemplates.CROSSROADS.map();

        Pos inFirst = new Pos(3, 2);
        Pos alsoFirst = new Pos(5, 4);
        Pos inSecond = new Pos(9, 10);

        assertTrue(map.sameBush(inFirst, alsoFirst), "corners of one patch");
        assertFalse(map.sameBush(inFirst, inSecond), "two different patches");
    }

    @Test
    void openFloorNeverCountsAsSharedConcealment() {
        GridMap map = MapTemplates.CROSSROADS.map();
        Pos floor = new Pos(1, 1);
        Pos adjacentFloor = new Pos(2, 1);

        assertEquals(GridMap.NO_BUSH, map.bushRegionAt(floor));
        assertFalse(map.sameBush(floor, adjacentFloor),
                "two open tiles must not be treated as hiding together");
        assertFalse(map.sameBush(floor, new Pos(3, 2)));
    }

    // --- Structural validation --------------------------------------------

    @Test
    void theBaselineRowsUsedByTheseTestsAreThemselvesValid() {
        assertEquals("VALID", MapTemplate.parse("VALID", VALID).name());
    }

    @Test
    void rejectsWrongRowCount() {
        String[] shortMap = Arrays.copyOf(VALID, 14);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MapTemplate.parse("SHORT", shortMap));
        assertTrue(e.getMessage().contains("15 rows"), e.getMessage());
    }

    @Test
    void rejectsWrongRowLength() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MapTemplate.parse("NARROW", mutate(1, "#...........#")));
        assertTrue(e.getMessage().contains("row 1"), e.getMessage());
    }

    @Test
    void rejectsUnknownSymbol() {
        assertThrows(IllegalArgumentException.class,
                () -> MapTemplate.parse("ODD", mutate(1, "#......X......#")));
    }

    @Test
    void rejectsDoorThatIsNotOnAWallMidpoint() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MapTemplate.parse("SKEW", mutate(0, "#####+.+#######")));
        assertTrue(e.getMessage().contains("wall midpoint"), e.getMessage());
    }

    @Test
    void rejectsWrongCabinetCount() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MapTemplate.parse("ONECAB", mutate(4, "#..bbb........#")));
        assertTrue(e.getMessage().contains("cabinets"), e.getMessage());
    }

    @Test
    void rejectsWrongItemSpawnCount() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MapTemplate.parse("NOSPAWN", mutate(2, "#..bbb........#")));
        assertTrue(e.getMessage().contains("item spawns"), e.getMessage());
    }

    @Test
    void rejectsCabinetTouchingBushBecauseTwoConcealmentRulesWouldOverlap() {
        // row 4 normally reads "#..bbb...C....#"; here the cabinet sits against the bush
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MapTemplate.parse("TOUCHING", mutate(4, "#..bbbC.......#")));
        assertTrue(e.getMessage().contains("touches bush"), e.getMessage());
    }

    @Test
    void rejectsUnreachableFloorPocket() {
        String[] rows = VALID.clone();
        rows[12] = "##..*....bbb..#";
        rows[13] = "#.#...........#";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MapTemplate.parse("SEALED", rows));
        assertTrue(e.getMessage().contains("unreachable"), e.getMessage());
    }

    @Test
    void rejectsCabinetSealedBehindWalls() {
        String[] rows = VALID.clone();
        rows[3] = "#..bbb..###...#";
        rows[4] = "#..bbb..#C#...#";
        rows[5] = "#.......###...#";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MapTemplate.parse("WALLEDIN", rows));
        assertTrue(e.getMessage().contains("walkable neighbour"), e.getMessage());
    }
}
