package com.example.battleroyal.game.loop;

import com.example.battleroyal.game.core.GameEvent;

/**
 * Told once whenever a life on the island ends, after that tick's broadcast: a death,
 * or a way out through an exit. Declared here so the loop does not import what listens:
 * the sessions retire the token, the result is saved, and the hideout settles what the
 * player carried.
 *
 * <p>Called on the game loop thread. An implementation must return quickly and hand
 * anything slow, such as a database write, to another thread.
 */
public interface DepartureListener {

    void onDeath(GameEvent.Died died);

    default void onExtracted(GameEvent.Extracted extracted) {
    }
}
