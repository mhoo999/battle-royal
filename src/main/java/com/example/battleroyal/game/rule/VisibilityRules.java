package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.GridMap;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;

/**
 * Decides whether one player can see another. Pure, and the only place this question
 * is answered.
 *
 * <p>An information leak is both the easiest bug to test for and the most damaging to
 * ship, which is why this lives in one small class instead of being spread through
 * serialization code.
 *
 * <p>The rules, from docs/GAME_RULES.md section 6:
 *
 * <pre>
 *   target in a cabinet                  -> never visible
 *   target in a bush, viewer same bush   -> visible
 *   target in a bush, anyone else        -> not visible
 *   otherwise                            -> visible
 * </pre>
 *
 * <p>Concealment does not restrict what the concealed player sees. Both a bush and a
 * cabinet let you look out.
 */
public final class VisibilityRules {

    private VisibilityRules() {
    }

    public static boolean canSee(GridMap map, Player viewer, Player target) {
        if (viewer.id().equals(target.id())) {
            return true;
        }
        if (!target.alive()) {
            return false;
        }
        return visibleAt(map, viewer.pos(), target.pos(), target.inCabinet());
    }

    /**
     * Position-level form, so tests and the raycast resolver can ask the question
     * without constructing players.
     *
     * <p>A viewer inside a cabinet stands on a CABINET tile, which is never a bush, so
     * the same-bush test correctly denies them sight of anyone hiding in foliage. No
     * special case is needed for that.
     */
    public static boolean visibleAt(GridMap map, Pos viewerPos, Pos targetPos,
                                    boolean targetInCabinet) {
        if (targetInCabinet) {
            return false;
        }
        if (map.isBush(targetPos)) {
            return map.sameBush(viewerPos, targetPos);
        }
        return true;
    }
}
