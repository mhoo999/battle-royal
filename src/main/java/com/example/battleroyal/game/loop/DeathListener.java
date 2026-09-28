package com.example.battleroyal.game.loop;

import com.example.battleroyal.game.core.GameEvent;

/**
 * Told once whenever a life ends, after that tick's broadcast. Declared here so the
 * loop does not import what listens: the guest sessions retire the token, and the
 * result is saved.
 *
 * <p>Called on the game loop thread. An implementation must return quickly and hand
 * anything slow, such as a database write, to another thread.
 */
public interface DeathListener {

    void onDeath(GameEvent.Died died);
}
