package com.example.battleroyal.ws;

import com.example.battleroyal.game.core.ActionA;
import com.example.battleroyal.game.core.ActionB;
import com.example.battleroyal.game.core.Concealment;
import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.ItemKind;

import java.util.List;

/**
 * One player's view of a room, as it goes over the wire.
 *
 * <p>The split between {@link Self} and {@link Other} is the whole of the information
 * asymmetry rule, expressed as types. {@code Other} has no field for hp, item, ammo or
 * cooldown, so no amount of careless serialization can leak them; adding one would be
 * a visible change to this file.
 *
 * <p>See docs/NETWORK_PROTOCOL.md.
 */
public record Snapshot(
        String type,
        long tick,
        String roomId,
        List<String> terrain,
        Self self,
        List<Other> players,
        List<FloorItem> items
) {

    public static final String TYPE = "SNAPSHOT";

    public static Snapshot of(long tick, String roomId, List<String> terrain,
                             Self self, List<Other> players, List<FloorItem> items) {
        return new Snapshot(TYPE, tick, roomId, terrain, self, players, items);
    }

    /** Everything the owning player is allowed to know about themselves. */
    public record Self(
            String id,
            String nickname,
            int x,
            int y,
            Direction direction,
            int hp,
            ItemKind item,
            Integer ammo,
            List<Slot> inventory,
            int equipped,
            List<Slot> crate,
            Concealment concealment,
            Integer lootMsLeft,
            List<Bearing> exits,
            Integer extractMsLeft,
            int score,
            int kills,
            ActionA actionA,
            ActionB actionB
    ) {
    }

    /**
     * One inventory slot or crate entry, as its owner sees it. An empty inventory slot
     * is a null in the list. Only ever inside {@link Self}: what a player carries, and
     * what is in a crate they opened, is nobody else's business.
     */
    public record Slot(
            ItemKind kind,
            Integer ammo
    ) {
    }

    /**
     * The compass needle for one of the viewer's own exits: how many rooms east and
     * south it lies, the shorter way round, and its tile once the viewer is in its room.
     * Only ever inside {@link Self}; nobody learns where anyone else's exits are (D9).
     *
     * @param x null unless the exit is in this room
     */
    public record Bearing(
            int dx,
            int dy,
            Integer x,
            Integer y
    ) {
    }

    /**
     * Everything anyone else is allowed to know about a player: where they are and
     * which way they face. Nothing more, ever.
     */
    public record Other(
            String id,
            int x,
            int y,
            Direction direction,
            boolean alive
    ) {
    }

    /**
     * That a crate lies here, never what is in it. You learn that by opening it, which is
     * the point of looting; a kind on the wire would be one devtools panel away even if
     * the client never drew it. The id is the crate's.
     */
    public record FloorItem(
            String id,
            int x,
            int y
    ) {
    }
}
