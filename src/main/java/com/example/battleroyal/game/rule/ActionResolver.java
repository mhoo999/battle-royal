package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.ActionA;
import com.example.battleroyal.game.core.ActionB;
import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.Exit;
import com.example.battleroyal.game.core.Item;
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
     * A follows the held item, and empty hands punch. A gun fires while it has a round;
     * empty, it loads from a matching bundle in the inventory, and with none it does
     * nothing until one turns up. A medkit does nothing at full health, so it is not
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
            case PISTOL, CROSSBOW -> {
                if (player.heldItem().hasAmmo()) {
                    yield ActionA.FIRE;
                }
                yield ammunitionSlot(player) >= 0 ? ActionA.RELOAD : null;
            }
            // Everything else is swung, junk and ammunition included.
            default -> ActionA.ATTACK;
        };
    }

    /** The first slot holding what loads the gun in hand, or -1. */
    public static int ammunitionSlot(Player player) {
        ItemKind wanted = Weapons.ammunitionFor(player.heldItem().kind());
        for (int slot = 0; slot < player.slotCount(); slot++) {
            Item item = player.slot(slot);
            if (item != null && item.kind() == wanted && item.hasAmmo()) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * B follows the surroundings, most specific first.
     *
     * <p>Cabinets and bushes never appear here: you walk into them and out again rather
     * than pressing anything, and a cabinet occupant has nothing to press B for. A crate
     * is opened from the tile you stand on, not beside it.
     */
    public static ActionB actionB(Room room, Player player) {
        if (player.inCabinet()) {
            return null;
        }
        if (exitUnderfoot(room, player) != null) {
            return ActionB.EXTRACT;
        }
        if (doorSideFor(room, player) != null) {
            return ActionB.DOOR;
        }
        if (room.crateAt(player.pos()) != null) {
            return player.pos().equals(player.openCrateAt()) ? ActionB.CLOSE : ActionB.OPEN;
        }
        return null;
    }

    /**
     * The player's own exit on the tile they stand on, or null. Somebody else's exit
     * underfoot is plain floor: it does not exist for anyone but its owner.
     */
    public static Exit exitUnderfoot(Room room, Player player) {
        for (Exit exit : player.exits()) {
            if (exit.in(room) && exit.at().equals(player.pos())) {
                return exit;
            }
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
