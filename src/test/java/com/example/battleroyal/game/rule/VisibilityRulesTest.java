package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.GridMap;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.map.MapTemplates;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The visibility table from docs/GAME_RULES.md section 6, case by case.
 *
 * <p>Fixture positions come from CROSSROADS: bush patch A spans (3..5, 2..4), patch B
 * spans (9..11, 10..12), and the cabinets sit at (9,4) and (5,10).
 */
class VisibilityRulesTest {

    private static final GridMap MAP = MapTemplates.CROSSROADS.map();

    private static final Pos BUSH_A = new Pos(3, 2);
    private static final Pos BUSH_A_FAR_CORNER = new Pos(5, 4);
    private static final Pos BUSH_B = new Pos(9, 10);
    private static final Pos OPEN = new Pos(1, 1);
    private static final Pos ALSO_OPEN = new Pos(2, 1);

    private static Player at(String id, Pos pos) {
        return new Player(id, id, pos, GameConstants.MAX_HP);
    }

    private static Player inCabinet(String id) {
        Player p = at(id, MAP.cabinets().getFirst());
        p.setInCabinet(true);
        return p;
    }

    private static boolean sees(Player viewer, Player target) {
        return VisibilityRules.canSee(MAP, viewer, target);
    }

    // --- The four bush cases ----------------------------------------------

    @Test
    void sameBushSeeEachOther() {
        Player a = at("a", BUSH_A);
        Player b = at("b", BUSH_A_FAR_CORNER);

        assertTrue(sees(a, b));
        assertTrue(sees(b, a));
    }

    @Test
    void differentBushesDoNotSeeEachOther() {
        Player a = at("a", BUSH_A);
        Player b = at("b", BUSH_B);

        assertFalse(sees(a, b), "concealment is per bush region, not global to bushes");
        assertFalse(sees(b, a));
    }

    @Test
    void concealmentIsOneWay() {
        Player hidden = at("hidden", BUSH_A);
        Player outside = at("outside", OPEN);

        assertTrue(sees(hidden, outside), "a bush does not blind the player inside it");
        assertFalse(sees(outside, hidden), "and it does hide them from everyone outside");
    }

    @Test
    void openGroundIsMutuallyVisible() {
        Player a = at("a", OPEN);
        Player b = at("b", ALSO_OPEN);

        assertTrue(sees(a, b));
        assertTrue(sees(b, a));
    }

    // --- Cabinets ---------------------------------------------------------

    @Test
    void cabinetOccupantIsInvisibleToEveryone() {
        Player hider = inCabinet("hider");
        Player outside = at("outside", OPEN);
        Player inBush = at("inBush", BUSH_A);

        assertFalse(sees(outside, hider));
        assertFalse(sees(inBush, hider),
                "being concealed yourself does not let you spot a cabinet occupant");
    }

    @Test
    void cabinetOccupantCanStillSeeOut() {
        Player hider = inCabinet("hider");
        Player outside = at("outside", OPEN);

        assertTrue(sees(hider, outside), "a cabinet is for surviving, not for blinding");
    }

    @Test
    void cabinetOccupantCannotSeeIntoABush() {
        Player hider = inCabinet("hider");
        Player inBush = at("inBush", BUSH_A);

        assertFalse(sees(hider, inBush),
                "a cabinet tile is never a bush tile, so the same-bush test denies it");
    }

    // --- Edges ------------------------------------------------------------

    @Test
    void aPlayerAlwaysSeesThemself() {
        Player hider = inCabinet("hider");

        assertTrue(sees(hider, hider));
    }

    @Test
    void deadPlayersAreNotVisible() {
        Player corpse = at("corpse", OPEN);
        corpse.takeDamage(GameConstants.MAX_HP);
        Player watcher = at("watcher", ALSO_OPEN);

        assertFalse(corpse.alive(), "fixture assumption");
        assertFalse(sees(watcher, corpse));
    }

    @Test
    void positionFormMatchesThePlayerForm() {
        assertFalse(VisibilityRules.visibleAt(MAP, OPEN, BUSH_A, false));
        assertTrue(VisibilityRules.visibleAt(MAP, BUSH_A, BUSH_A_FAR_CORNER, false));
        assertFalse(VisibilityRules.visibleAt(MAP, OPEN, ALSO_OPEN, true),
                "the cabinet flag alone hides a player standing anywhere");
    }
}
