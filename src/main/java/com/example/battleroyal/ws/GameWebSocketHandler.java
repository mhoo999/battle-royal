package com.example.battleroyal.ws;

import com.example.battleroyal.game.core.Command;
import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.loop.RoomRegistry;
import com.example.battleroyal.web.GuestSessionService;
import com.example.battleroyal.web.GuestSessionService.GuestSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Translates socket traffic into commands. Raw text frames with a small JSON envelope
 * rather than STOMP: there are four inbound message types and the outbound path needs
 * a different payload per recipient, which STOMP's broker model works against.
 *
 * <p>Identity comes from the token presented at handshake, never from the message body.
 * A frame claiming a position, hp or damage is not understood by design.
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private static final String PLAYER_ID = "playerId";
    private static final String TOKEN = "token";

    /** Give up on a client that cannot keep up rather than stalling the game loop. */
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_BYTES = 64 * 1024;

    private final GuestSessionService guests;
    private final RoomRegistry rooms;
    private final SessionRegistry sessions;
    private final ObjectMapper mapper;

    public GameWebSocketHandler(GuestSessionService guests, RoomRegistry rooms,
                                SessionRegistry sessions, ObjectMapper mapper) {
        this.guests = guests;
        this.rooms = rooms;
        this.sessions = sessions;
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String token = tokenOf(session);
        GuestSession guest = guests.resolve(token);
        if (guest == null) {
            log.debug("Rejecting socket with unknown token");
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unknown session token"));
            return;
        }

        WebSocketSession sendSafe = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, SEND_BUFFER_BYTES);
        session.getAttributes().put(PLAYER_ID, guest.playerId());
        session.getAttributes().put(TOKEN, token);

        sessions.register(guest.playerId(), sendSafe);
        rooms.requestJoin(guest.playerId(), guest.nickname());
        log.info("Player {} ({}) connected", guest.playerId(), guest.nickname());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String playerId = (String) session.getAttributes().get(PLAYER_ID);
        if (playerId == null) {
            return;
        }
        Command command = parse(playerId, message.getPayload());
        if (command != null) {
            rooms.submit(command);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String playerId = (String) session.getAttributes().get(PLAYER_ID);
        if (playerId == null) {
            return;
        }
        WebSocketSession stored = sessions.socketOf(playerId);
        sessions.unregister(playerId, stored);
        // Step 7 replaces this with a 15 second grace period so a dropped phone is not
        // an escape from a losing fight.
        rooms.requestLeave(playerId);
        guests.discard((String) session.getAttributes().get(TOKEN));
        log.info("Player {} disconnected ({})", playerId, status);
    }

    /**
     * The entire vocabulary a client may use. Deserializing into this shape is itself a
     * guard: a frame carrying {@code x}, {@code y}, {@code hp} or {@code damage} has
     * those fields discarded, because there is nowhere for them to land.
     */
    private record Frame(String type, String dir) {
    }

    /**
     * Returns null for anything unrecognised. A malformed or unknown frame is dropped
     * silently instead of closing the socket, so a buggy client degrades rather than
     * disconnects.
     */
    private Command parse(String playerId, String payload) {
        try {
            Frame frame = mapper.readValue(payload, Frame.class);
            if (frame == null || frame.type() == null) {
                return null;
            }
            return switch (frame.type()) {
                case "MOVE" -> {
                    Direction dir = direction(frame.dir());
                    yield dir == null ? null : new Command.Move(playerId, dir);
                }
                case "ACTION_A" -> new Command.ActionA(playerId);
                case "ACTION_B" -> new Command.ActionB(playerId);
                case "RELEASE_B" -> new Command.ReleaseB(playerId);
                default -> null;
            };
        } catch (Exception e) {
            log.debug("Dropping unparseable frame from {}", playerId);
            return null;
        }
    }

    private static Direction direction(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Direction.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String tokenOf(WebSocketSession session) {
        String query = session.getUri() == null ? null : session.getUri().getQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && "token".equals(pair.substring(0, eq))) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
