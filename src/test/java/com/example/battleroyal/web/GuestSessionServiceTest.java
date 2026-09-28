package com.example.battleroyal.web;

import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.web.GuestSessionService.GuestSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GuestSessionServiceTest {

    private static GameEvent.Died died(String playerId) {
        return new GameEvent.Died(playerId, "me", 0, 0, 0, null, null);
    }

    @Test
    void aTokenOutlivesNothingButItsLife() {
        GuestSessionService guests = new GuestSessionService();
        GuestSession mine = guests.issue("me");
        GuestSession theirs = guests.issue("them");

        guests.onDeath(died(mine.playerId()));

        assertNull(guests.resolve(mine.token()), "a dead player cannot reconnect");
        assertNotNull(guests.resolve(theirs.token()), "nobody else is touched");
    }
}
