package com.example.battleroyal.game.loop;

/**
 * Something that acts in the world on the loop thread, once a tick, before the tick's
 * commands are applied — the server-run players (the bot package) are one.
 *
 * <p>It may read rooms freely, being on the loop thread, but it changes the world only
 * the way a player does: through {@link RoomRegistry#submit} and the join and leave
 * requests. Its commands are applied the same tick.
 */
public interface TickDriver {

    void drive(RoomRegistry registry, long tick);
}
