package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.GridMap;
import com.example.battleroyal.game.core.Pos;

import java.util.function.Predicate;

/**
 * Resolves a MOVE command. Pure: no Spring, no mutation, no clock.
 *
 * <p>A direction input always sets facing, and moves one tile only if that tile is
 * free. Blocked input still turns the player and still spends the cooldown, which is
 * what makes retreating expose your back to an attacker.
 */
public final class MovementRules {

    private MovementRules() {
    }

    /**
     * @param facing where the player ends up looking (always the input direction)
     * @param pos    where the player ends up (unchanged when blocked)
     * @param moved  whether the position changed
     */
    public record Outcome(Direction facing, Pos pos, boolean moved) {
    }

    /**
     * @param occupied whether another living player holds a tile; two players never
     *                 share one
     */
    public static Outcome resolve(GridMap map, Pos from, Direction input,
                                  Predicate<Pos> occupied) {
        Pos target = from.step(input);
        boolean canEnter = map.walkable(target) && !occupied.test(target);
        return new Outcome(input, canEnter ? target : from, canEnter);
    }

    /** Cooldowns are inclusive: an action is allowed on the tick it becomes ready. */
    public static boolean ready(long nowTick, long readyAtTick) {
        return nowTick >= readyAtTick;
    }

    public static long nextReadyTick(long nowTick, int cooldownTicks) {
        return nowTick + cooldownTicks;
    }
}
