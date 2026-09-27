package com.example.battleroyal.ws;

import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.loop.RoomBroadcaster;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Serializes one filtered snapshot per player and writes it to their socket.
 *
 * <p>Called from the game loop thread only, which is why sends are not synchronized
 * here; the sessions themselves are wrapped in a concurrent decorator so a slow reader
 * buffers instead of blocking the tick.
 */
@Component
public class WebSocketSnapshotBroadcaster implements RoomBroadcaster {

    private static final Logger log =
            LoggerFactory.getLogger(WebSocketSnapshotBroadcaster.class);

    private final SessionRegistry sessions;
    private final SnapshotFilter filter;
    private final ObjectMapper mapper;

    public WebSocketSnapshotBroadcaster(SessionRegistry sessions, SnapshotFilter filter,
                                        ObjectMapper mapper) {
        this.sessions = sessions;
        this.filter = filter;
        this.mapper = mapper;
    }

    @Override
    public void broadcast(Room room, long tick) {
        for (Player viewer : room.players()) {
            WebSocketSession socket = sessions.socketOf(viewer.id());
            if (socket == null || !socket.isOpen()) {
                continue;
            }
            try {
                Snapshot snapshot = filter.forViewer(room, viewer, tick);
                socket.sendMessage(new TextMessage(mapper.writeValueAsString(snapshot)));
            } catch (Exception e) {
                // A failed send must not abort the tick for everyone else in the room.
                log.debug("Snapshot to {} failed: {}", viewer.id(), e.toString());
            }
        }
    }
}
