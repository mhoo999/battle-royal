package com.example.battleroyal.web;

import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.loop.DeathListener;
import com.example.battleroyal.game.rule.GameConstants;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
 * retired on death, so a reconnect after that cannot bring the player back.
 */
@Service
public class GameSessionService implements DeathListener {

    /**
     * @param accountId null for a guest
     * @param loadout   what the player carries in, slot by slot (nulls for empty slots);
     *                  empty for a guest, who always starts with nothing
     */
    public record GameSession(String token, String playerId, String nickname, Long accountId,
                              List<Item> loadout) {
    }

    /** Thrown for a nickname {@link NicknamePolicy} refuses. The message is shown to the player. */
    public static class InvalidNicknameException extends RuntimeException {
        public InvalidNicknameException(String message) {
            super(message);
        }
    }

    private final Map<String, GameSession> byToken = new ConcurrentHashMap<>();
    private final AtomicLong playerSequence = new AtomicLong();

    /** A guest trial. Whatever the name, it plays unranked. */
    public GameSession issueGuest(String rawNickname) {
        String nickname = rawNickname == null ? "" : rawNickname.trim();
        while (nickname.startsWith(GameConstants.UNRANKED_PREFIX)) {
            nickname = nickname.substring(GameConstants.UNRANKED_PREFIX.length()).trim();
        }
        NicknamePolicy.require(nickname);
        return issue(GameConstants.UNRANKED_PREFIX + nickname, null, List.of());
    }

    /**
     * A signed-in account setting out from the hideout, under the nickname it chose and
     * carrying what it picked from the stash.
     */
    public GameSession issueForAccount(long accountId, String nickname, List<Item> loadout) {
        return issue(nickname, accountId, loadout);
    }

    public GameSession resolve(String token) {
        return token == null ? null : byToken.get(token);
    }

    @Override
    public void onDeath(GameEvent.Died died) {
        byToken.values().removeIf(session -> session.playerId().equals(died.playerId()));
    }

    private GameSession issue(String nickname, Long accountId, List<Item> loadout) {
        GameSession session = new GameSession(
                UUID.randomUUID().toString(),
                "p-" + playerSequence.incrementAndGet(),
                nickname,
                accountId,
                Collections.unmodifiableList(new ArrayList<>(loadout)));
        byToken.put(session.token(), session);
        return session;
    }
}
