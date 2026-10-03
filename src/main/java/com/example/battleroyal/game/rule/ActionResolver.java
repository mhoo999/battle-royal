package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.ActionA;
import com.example.battleroyal.game.core.ActionB;
import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.ItemKind;
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

    /**
     * A follows the held item, and empty hands punch. A gun in hand always has a round:
     * the last shot uses it up. A medkit does nothing at full health, so it is not
     * offered then and cannot be wasted.
     */
    public static ActionA actionA(Player player) {
        if (player.hasItem() && player.heldItem().kind() == ItemKind.MEDKIT) {
            // Healing is the one action a cabinet allows.
            return player.hp() < GameConstants.MAX_HP ? ActionA.HEAL : null;
        }
        if (player.inCabinet()) {
            // A cabinet is for surviving, not fighting.
            return null;
        }
        if (!player.hasItem()) {
            return ActionA.ATTACK;
        }
        return switch (player.heldItem().kind()) {
            case PISTOL, CROSSBOW -> ActionA.FIRE;
            // Everything else is swung, junk included.
            default -> ActionA.ATTACK;
        };
    }

    /**
     * B follows the surroundings, most specific first.
     *
     * <p>Cabinets and bushes never appear here: you walk into them and out again rather
     * than pressing anything, and a cabinet occupant has nothing to press B for. An item
     * is picked up from the tile you stand on, not beside it.
     */
    public static ActionB actionB(Room room, Player player) {
        if (player.inCabinet()) {
            return null;
        }
        if (doorSideFor(room, player) != null) {
            return ActionB.DOOR;
        }
        if (room.itemAt(player.pos()) != null) {
            return player.hasItem() ? ActionB.SWAP : ActionB.PICKUP;
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
        return doorSideAt(room, player.pos());
    }

    /** Which wall's door is in reach from this tile, or null if none is. */
    public static Direction doorSideAt(Room room, Pos pos) {
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
