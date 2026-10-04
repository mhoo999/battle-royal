package com.example.battleroyal.web;

import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.loop.DepartureListener;
import com.example.battleroyal.game.rule.GameConstants;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Game sessions: the token a WebSocket presents to say which player it is.
 *
 * <p>The point is server-side identity: the client is handed an opaque token and never
 * tells us who it is. A WebSocket connection presents the token and the server resolves
 * the player, so there is no code path where a client asserts a player id.
 *
 * <p>Two ways in. A signed-in account plays under its own nickname, which is unique
 * among accounts. A guest plays a trial under any name, always given the
 * {@link GameConstants#UNRANKED_PREFIX} so it never reaches the ranking.
 *
 * <p>A token lives as long as the life it was issued for. It survives a dropped socket,
 * which is what lets a reconnect inside the grace period find the same player, and is
 * retired on death or extraction, so a reconnect after that cannot bring the player back.
 */
@Service
public class GameSessionService implements DepartureListener {

    /**
     * @param accountId null for a guest
     * @param loadout   what the player carries in, slot by slot (nulls for empty slots);
     *                  empty for a guest, who always starts with nothing
     * @param bag       the bag worn in, or null
     */
    public record GameSession(String token, String playerId, String nickname, Long accountId,
                              List<Item> loadout, Item bag) {
    }

    /** Thrown for a nickname {@link NicknamePolicy} refuses. The message is shown to the player. */
    public static class InvalidNicknameException extends RuntimeException {
        public InvalidNicknameException(String message) {
            super(message);
        }
    }

    private final Map<String, GameSession> byToken = new ConcurrentHashMap<>();
    /** Players whose socket has attached at least once: from then on they are in the game. */
    private final Set<String> attached = ConcurrentHashMap.newKeySet();
    private final AtomicLong playerSequence = new AtomicLong();

    /** A guest trial. Whatever the name, it plays unranked. */
    public GameSession issueGuest(String rawNickname) {
        String nickname = rawNickname == null ? "" : rawNickname.trim();
        while (nickname.startsWith(GameConstants.UNRANKED_PREFIX)) {
            nickname = nickname.substring(GameConstants.UNRANKED_PREFIX.length()).trim();
        }
        NicknamePolicy.require(nickname);
        return issue(GameConstants.UNRANKED_PREFIX + nickname, null, List.of(), null);
    }

    /**
     * A signed-in account setting out from the hideout, under the nickname it chose and
     * carrying what it picked from the stash.
     */
    public GameSession issueForAccount(long accountId, String nickname, List<Item> loadout) {
        return issueForAccount(accountId, nickname, loadout, null);
    }

    public GameSession issueForAccount(long accountId, String nickname, List<Item> loadout,
                                       Item bag) {
        return issue(nickname, accountId, loadout, bag);
    }

    public GameSession resolve(String token) {
        return token == null ? null : byToken.get(token);
    }

    /**
     * Resolves a socket's token and records that the player has attached. Atomic with
     * {@link #retireIfNeverAttached}: a session is either claimed by its socket or
     * retired, never both, so a sortie refunded as abandoned cannot also walk in.
     */
    public synchronized GameSession attach(String token) {
        GameSession session = resolve(token);
        if (session != null) {
            attached.add(session.playerId());
        }
        return session;
    }

    /**
     * Retires a session whose socket never attached, so its token can no longer be used.
     *
     * @return false, changing nothing, if the player has attached
     */
    public synchronized boolean retireIfNeverAttached(String playerId) {
        if (attached.contains(playerId)) {
            return false;
        }
        retire(playerId);
        return true;
    }

    @Override
    public void onDeath(GameEvent.Died died) {
        retire(died.playerId());
    }

    /** Out is as final as dead: the token cannot bring the player back onto the island. */
    @Override
    public void onExtracted(GameEvent.Extracted extracted) {
        retire(extracted.playerId());
    }

    /** Sent home by the season's end: the token cannot bring them back either. */
    @Override
    public void onEjected(GameEvent.Ejected ejected) {
        retire(ejected.playerId());
    }

    private synchronized void retire(String playerId) {
        byToken.values().removeIf(session -> session.playerId().equals(playerId));
        attached.remove(playerId);
    }

    private GameSession issue(String nickname, Long accountId, List<Item> loadout, Item bag) {
        GameSession session = new GameSession(
                UUID.randomUUID().toString(),
                "p-" + playerSequence.incrementAndGet(),
                nickname,
                accountId,
                Collections.unmodifiableList(new ArrayList<>(loadout)),
                bag);
        byToken.put(session.token(), session);
        return session;
    }
}
