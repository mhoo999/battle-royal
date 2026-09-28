package com.example.battleroyal.game.core;

import java.util.List;

/**
 * Something that happened once, as opposed to state. Snapshots say where things are;
 * events say that a shot was fired or somebody died.
 *
 * <p>Each event decides its own audience, and the audience is part of the rule: a
 * {@link Hit} going to anyone but the attacker would tell them who is hiding where.
 * See docs/NETWORK_PROTOCOL.md.
 */
public sealed interface GameEvent {

    /**
     * A pistol shot, for everyone in the room to draw. The path starts on the shooter's
     * own tile, which is what gives away a bush camper the moment they fire.
     */
    record Shot(List<Pos> path) implements GameEvent {

        public Shot {
            path = List.copyOf(path);
        }
    }

    /**
     * A knife or pan swung at the tile in front, for everyone in the room to draw,
     * hit or miss. Like a shot it starts on the attacker's own tile, so swinging from
     * a bush gives you away too.
     */
    record Swing(Pos from, Pos to) implements GameEvent {
    }

    /**
     * To the attacker alone, and only the fact of it. Deliberately carries nothing
     * about the target: not who, not how badly, not whether they died.
     */
    record Hit(String attackerId) implements GameEvent {
    }

    /**
     * To the player who died: the numbers for their result screen, and who killed them
     * with what. The killer's identity and weapon are hidden state while you live;
     * they are shown once you no longer can act on them in that life.
     *
     * <p>Also the record of the life that ended, which is what gets saved as a result.
     *
     * @param nickname       the player who died, for the result record
     * @param killerNickname null when nobody killed them (the disconnect grace ran out)
     * @param weapon         null likewise
     */
    record Died(String playerId, String nickname, int score, int kills, long survivedTicks,
                String killerNickname, ItemKind weapon) implements GameEvent {
    }
}
