package com.example.battleroyal.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps a player to their open socket, so the game loop can find someone to send to.
 *
 * <p>Concurrent because the WebSocket threads register and unregister while the loop
 * thread reads. Sessions stored here are already wrapped for concurrent sending by
 * {@code GameWebSocketHandler}.
 */
@Component
public class SessionRegistry {

    private final Map<String, WebSocketSession> byPlayerId = new ConcurrentHashMap<>();

    public void register(String playerId, WebSocketSession session) {
        byPlayerId.put(playerId, session);
    }

    /** Removes the mapping only if it still points at this session. */
    public void unregister(String playerId, WebSocketSession session) {
        byPlayerId.remove(playerId, session);
    }

    public WebSocketSession socketOf(String playerId) {
        return byPlayerId.get(playerId);
    }

    public int openCount() {
        return byPlayerId.size();
    }
}
