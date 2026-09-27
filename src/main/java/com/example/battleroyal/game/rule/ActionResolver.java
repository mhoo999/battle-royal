package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.ActionA;
import com.example.battleroyal.game.core.ActionB;
import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;

import java.util.Map;

/**
 * Works out what A and B mean for a player right now.
 *
 * <p>The server resolves this and ships the answer in the snapshot so the client never
 * has to reimplement the rules. If the two disagreed, a player would press a button
 * labelled one thing and get another.
 */
public final class ActionResolver {

    private ActionResolver() {
    }

    /** A follows the held item. An empty pistol turns A into a reload. */
    public static ActionA actionA(Player player) {
        if (!player.hasItem()) {
            return null;
        }
        return switch (player.heldItem().kind()) {
            case KNIFE -> player.inCabinet() ? null : ActionA.ATTACK;
            case PISTOL -> {
                if (player.inCabinet()) {
                    // A cabinet is for surviving, not shooting.
                    yield null;
                }
                yield player.heldItem().hasAmmo() ? ActionA.FIRE : ActionA.RELOAD;
            }
            // Healing is the one action a cabinet allows.
            case MEDKIT -> ActionA.HEAL;
        };
    }

    /**
     * B follows the surroundings, most specific first.
     *
     * <p>Pickups and swaps arrive with Step 5; cabinets with Step 6. Bushes never
     * appear here, because you walk into a bush rather than pressing anything.
     */
    public static ActionB actionB(Room room, Player player) {
        if (player.inCabinet()) {
            return ActionB.UNHIDE;
        }
        if (doorSideFor(room, player) != null) {
            return ActionB.DOOR;
        }
        return null;
    }

    /**
     * Which wall's door the player can currently use, or null if none is in reach.
     *
     * <p>Standing on the door counts, and so does standing beside it: requiring the
     * exact tile would mean fighting the movement cooldown to line up before every
     * room change.
     */
    public static Direction doorSideFor(Room room, Player player) {
        Pos pos = player.pos();
        for (Map.Entry<Direction, Pos> door : room.map().doors().entrySet()) {
            if (adjacentOrSame(pos, door.getValue())) {
                return door.getKey();
            }
        }
        return null;
    }

    private static boolean adjacentOrSame(Pos a, Pos b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) <= 1;
    }
}
