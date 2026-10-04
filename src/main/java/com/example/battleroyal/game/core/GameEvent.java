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
    /**
     * To a player taken off the island because the season ended. What they carried is
     * gone with the wipe; nobody else is told, they simply leave the room.
     */
    record Ejected(String playerId) implements GameEvent {
    }

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
    /** @param byGuard shot by an outpost guard (V2.2); then there is no killer or weapon */
    record Died(String playerId, String nickname, int score, int kills, long survivedTicks,
                String killerNickname, ItemKind weapon, boolean byGuard, int soldiersDowned)
            implements GameEvent {

        public Died(String playerId, String nickname, int score, int kills, long survivedTicks,
                    String killerNickname, ItemKind weapon) {
            this(playerId, nickname, score, kills, survivedTicks, killerNickname, weapon, false, 0);
        }
    }

    /**
     * To the player who got out, with what they got out with. Like {@link Died} it ends
     * a life and is its record; unlike a death nobody else is told, and the player
     * simply leaves the room.
     *
     * @param carried what was in the inventory, in slot order, empty slots left out,
     *                then the bag if one was worn
     */
    record Extracted(String playerId, String nickname, int score, int kills,
                     long survivedTicks, List<Item> carried, int marksReached,
                     int soldiersDowned) implements GameEvent {

        public Extracted {
            carried = List.copyOf(carried);
        }

        public Extracted(String playerId, String nickname, int score, int kills,
                         long survivedTicks, List<Item> carried) {
            this(playerId, nickname, score, kills, survivedTicks, carried, 0, 0);
        }

        public Extracted(String playerId, String nickname, int score, int kills,
                         long survivedTicks, List<Item> carried, int marksReached) {
            this(playerId, nickname, score, kills, survivedTicks, carried, marksReached, 0);
        }
    }

    /**
     * That somebody fell, and where, for the people who could see them fall. Without it
     * a death in front of you was only a body missing from the next snapshot.
     *
     * <p>Witnesses are worked out at the moment of death, before the body leaves its
     * cabinet, because the dead are invisible to every later question. Nobody else is
     * told: a death in a bush or a cabinet gives away nothing more than it did before.
     * Who it was is not said either.
     */
    record Fell(Pos at, List<String> witnessIds) implements GameEvent {

        public Fell {
            witnessIds = List.copyOf(witnessIds);
        }
    }
}
