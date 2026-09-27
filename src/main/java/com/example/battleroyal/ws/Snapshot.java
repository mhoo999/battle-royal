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
            Concealment concealment,
            int score,
            int kills,
            ActionA actionA,
            ActionB actionB
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

    public record FloorItem(
            String id,
            int x,
            int y,
            ItemKind kind
    ) {
    }
}
