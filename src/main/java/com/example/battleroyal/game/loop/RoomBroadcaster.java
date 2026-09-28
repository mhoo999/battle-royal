package com.example.battleroyal.game.loop;

import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.core.Room;

import java.util.List;

/**
 * How the loop pushes state out. Implemented over WebSocket in {@code ws}.
 *
 * <p>Declared here so {@code game.loop} never imports a transport type. The
 * implementation is responsible for building one filtered view per viewer and for
 * sending each event only to its audience.
 */
public interface RoomBroadcaster {

    /**
     * @param events what happened in this room this tick, already drained from it;
     *               a snapshot is sent only when the room is dirty
     */
    void broadcast(Room room, List<GameEvent> events, long tick);
}
