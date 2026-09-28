package com.example.battleroyal.ws;

import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.loop.RoomBroadcaster;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

/**
 * Serializes one filtered snapshot per player and writes it to their socket, then sends
 * each event to its audience: a shot to the whole room, a hit to the attacker, a death
 * to the player who died.
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
    public void broadcast(Room room, List<GameEvent> events, long tick) {
        if (room.dirty()) {
            for (Player viewer : room.players()) {
                send(viewer.id(), filter.forViewer(room, viewer, tick));
            }
        }
        for (GameEvent event : events) {
            switch (event) {
                case GameEvent.Shot shot -> {
                    Object message = Outbound.shot(shot);
                    for (Player viewer : room.players()) {
                        send(viewer.id(), message);
                    }
                }
                case GameEvent.Swing swing -> {
                    Object message = Outbound.swing(swing);
                    for (Player viewer : room.players()) {
                        send(viewer.id(), message);
                    }
                }
                case GameEvent.Hit hit -> send(hit.attackerId(), Outbound.hit());
                case GameEvent.Died died -> send(died.playerId(), Outbound.youDied(died));
            }
        }
    }

    private void send(String playerId, Object message) {
        WebSocketSession socket = sessions.socketOf(playerId);
        if (socket == null || !socket.isOpen()) {
            return;
        }
        try {
            socket.sendMessage(new TextMessage(mapper.writeValueAsString(message)));
        } catch (Exception e) {
            // A failed send must not abort the tick for everyone else in the room.
            log.debug("Send to {} failed: {}", playerId, e.toString());
        }
    }
}
