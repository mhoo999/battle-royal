package com.example.battleroyal.web;

import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.loop.DeathListener;
import com.example.battleroyal.game.rule.GameConstants;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Guest sessions. No password, no account, no persistence.
 *
 * <p>The point is server-side identity: the client is handed an opaque token and never
 * tells us who it is. A WebSocket connection presents the token and the server resolves
 * the player, so there is no code path where a client asserts a player id.
 *
 * <p>Nicknames may repeat. They are a label on a scoreboard row, not a login.
 *
 * <p>A token lives as long as the life it was issued for. It survives a dropped socket,
 * which is what lets a reconnect inside the grace period find the same player, and is
 * retired on death, so a reconnect after that cannot bring the player back.
 */
@Service
public class GuestSessionService implements DeathListener {

    public record GuestSession(String token, String playerId, String nickname) {
    }

    /** Thrown for a nickname that is blank or the wrong length after trimming. */
    public static class InvalidNicknameException extends RuntimeException {
        public InvalidNicknameException(String message) {
            super(message);
        }
    }

    private final Map<String, GuestSession> byToken = new ConcurrentHashMap<>();
    private final AtomicLong playerSequence = new AtomicLong();

    public GuestSession issue(String rawNickname) {
        String nickname = rawNickname == null ? "" : rawNickname.trim();
        if (nickname.length() < GameConstants.NICKNAME_MIN_LENGTH
                || nickname.length() > GameConstants.NICKNAME_MAX_LENGTH) {
            throw new InvalidNicknameException("Nickname must be "
                    + GameConstants.NICKNAME_MIN_LENGTH + " to "
                    + GameConstants.NICKNAME_MAX_LENGTH + " characters after trimming");
        }

        GuestSession session = new GuestSession(
                UUID.randomUUID().toString(),
                "p-" + playerSequence.incrementAndGet(),
                nickname);
        byToken.put(session.token(), session);
        return session;
    }

    public GuestSession resolve(String token) {
        return token == null ? null : byToken.get(token);
    }

    @Override
    public void onDeath(GameEvent.Died died) {
        byToken.values().removeIf(session -> session.playerId().equals(died.playerId()));
    }
}
