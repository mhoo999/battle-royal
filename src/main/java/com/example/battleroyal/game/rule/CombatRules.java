package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.GridMap;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.core.TileType;

import java.util.ArrayList;
import java.util.List;

/**
 * Instant hit resolution along a line. Pure: reads the room, mutates nothing.
 *
 * <p>A shot is not an entity. It scans tile by tile from the shooter in the facing
 * direction and stops at the first thing that stops it:
 *
 * <pre>
 *   FLOOR / DOOR / BUSH   keep scanning
 *   player on the tile    hit, stop    (a bush hides you, it does not cover you)
 *   CABINET               stop; whoever is inside is hit
 *   WALL                  stop
 *   out of range          stop
 * </pre>
 *
 * <p>The Knife is the same scan with a range of one, which is what lets it stab into
 * a cabinet blind.
 */
public final class CombatRules {

    private CombatRules() {
    }

    /**
     * @param path   tiles the shot crossed, starting with the shooter's own and ending
     *               with the victim's when somebody in the open or in a bush was hit.
     *               Blocking terrain is not part of the path.
     * @param victim who was hit, or null for a miss
     */
    public record Trace(List<Pos> path, Player victim) {

        public Trace {
            path = List.copyOf(path);
        }

        public boolean hit() {
            return victim != null;
        }
    }

    public static Trace trace(Room room, Pos origin, Direction dir, int range) {
        GridMap map = room.map();
        List<Pos> path = new ArrayList<>();
        path.add(origin);

        Pos cursor = origin;
        for (int step = 0; step < range; step++) {
            cursor = cursor.step(dir);
            if (map.blocksRaycast(cursor)) {
                boolean cabinet = map.inBounds(cursor)
                        && map.tileAt(cursor) == TileType.CABINET;
                return new Trace(path, cabinet ? cabinetOccupant(room, cursor) : null);
            }
            path.add(cursor);
            Player standing = room.livingPlayerAt(cursor);
            if (standing != null) {
                return new Trace(path, standing);
            }
        }
        return new Trace(path, null);
    }

    /** A cabinet's occupant stands on the cabinet tile itself. */
    private static Player cabinetOccupant(Room room, Pos cabinet) {
        Player inside = room.livingPlayerAt(cabinet);
        return inside != null && inside.inCabinet() ? inside : null;
    }
}
