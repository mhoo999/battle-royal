package com.example.battleroyal.web;

import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.web.GameSessionService.GameSession;
import com.example.battleroyal.web.GameSessionService.InvalidNicknameException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameSessionServiceTest {

    private static GameEvent.Died died(String playerId) {
        return new GameEvent.Died(playerId, "me", 0, 0, 0, null, null);
    }

    @Test
    void aTokenOutlivesNothingButItsLife() {
        GameSessionService sessions = new GameSessionService();
        GameSession mine = sessions.issueGuest("me");
        GameSession theirs = sessions.issueGuest("them");

        sessions.onDeath(died(mine.playerId()));

        assertNull(sessions.resolve(mine.token()), "a dead player cannot reconnect");
        assertNotNull(sessions.resolve(theirs.token()), "nobody else is touched");
    }

    @Test
    void gettingOutEndsTheTokenToo() {
        GameSessionService sessions = new GameSessionService();
        GameSession mine = sessions.issueGuest("me");

        sessions.onExtracted(new GameEvent.Extracted(mine.playerId(), "~me", 0, 0, 0,
                java.util.List.of()));

        assertNull(sessions.resolve(mine.token()), "out is out: no way back onto the island");
    }

    @Test
    void aGuestAlwaysPlaysUnranked() {
        GameSessionService sessions = new GameSessionService();

        assertEquals("~kang", sessions.issueGuest("  kang ").nickname());
        assertEquals("~kang", sessions.issueGuest("~kang").nickname(),
                "a name that already carries the prefix is not prefixed twice");
        assertEquals("~kang", sessions.issueGuest("~~ kang").nickname());
    }

    @Test
    void aGuestNameStillHasALength() {
        GameSessionService sessions = new GameSessionService();

        assertThrows(InvalidNicknameException.class, () -> sessions.issueGuest("~"));
        assertThrows(InvalidNicknameException.class, () -> sessions.issueGuest(null));
        assertThrows(InvalidNicknameException.class,
                () -> sessions.issueGuest("thirteen-char"));
    }

    @Test
    void anAccountPlaysUnderItsOwnNickname() {
        GameSessionService sessions = new GameSessionService();

        GameSession session = sessions.issueForAccount(7, "kang", java.util.List.of());

        assertEquals("kang", session.nickname());
        assertEquals(7L, session.accountId());
        assertNull(sessions.issueGuest("lee").accountId());
    }
}
