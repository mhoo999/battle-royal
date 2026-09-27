package com.example.battleroyal.game.loop;

import com.example.battleroyal.game.core.Room;

/**
 * How the loop pushes state out. Implemented over WebSocket in {@code ws}.
 *
 * <p>Declared here so {@code game.loop} never imports a transport type. The
 * implementation is responsible for building one filtered view per viewer.
 */
public interface RoomBroadcaster {

    void broadcast(Room room, long tick);
}
