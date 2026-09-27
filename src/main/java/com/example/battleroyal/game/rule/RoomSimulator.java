package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.ActionB;
import com.example.battleroyal.game.core.Command;
import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Room;

/**
 * Applies commands and per-tick timers to one room. Pure with respect to the outside
 * world: no Spring, no clock, no I/O. The tick number is always passed in, so a test
 * can replay an exact sequence and get an identical result.
 *
 * <p>Lives in {@code game.rule} rather than {@code game.core} because it orchestrates
 * rules; {@code core} must not depend on {@code rule}.
 *
 * <p>Every command is validated here, never on the client. A command that fails
 * validation is dropped silently.
 *
 * <p>Changing rooms is the one thing this class cannot finish on its own, since it
 * only ever sees a single room. It reports the intent and lets the registry, which
 * owns the world grid, carry it out.
 */
public final class RoomSimulator {

    private RoomSimulator() {
    }

    /** A player wants to leave through the door on this side of the room. */
    public record DoorTransit(String playerId, Direction side) {
    }

    /**
     * @return a transit request when the command was a door interaction, else null
     */
    public static DoorTransit apply(Room room, Command command, long nowTick) {
        Player player = room.player(command.playerId());
        if (player == null || !player.alive()) {
            return null;
        }
        switch (command) {
            case Command.Move move -> move(room, player, move.dir(), nowTick);
            case Command.ActionA ignored -> {
                // Attack, fire, reload and heal arrive in Step 4.
            }
            case Command.ActionB ignored -> {
                return actionB(room, player);
            }
        }
        return null;
    }

    /**
     * Per-tick work that is not driven by a command: currently just releasing a move
     * that was held back by the cooldown.
     */
    public static void tick(Room room, long nowTick) {
        for (Player player : room.players()) {
            if (!player.alive() || player.inCabinet()) {
                continue;
            }
            if (!MovementRules.ready(nowTick, player.nextMoveTick())) {
                continue;
            }
            Direction pending = player.takeBufferedMove(nowTick);
            if (pending != null) {
                step(room, player, pending, nowTick);
            }
        }
    }

    private static void move(Room room, Player player, Direction dir, long nowTick) {
        if (player.inCabinet()) {
            return;
        }
        if (!MovementRules.ready(nowTick, player.nextMoveTick())) {
            // Hold it rather than discard it. Dropping mid-cooldown input is what made
            // a held direction stutter whenever the client drifted out of phase.
            player.bufferMove(dir, nowTick + GameConstants.MOVE_BUFFER_TICKS);
            return;
        }
        step(room, player, dir, nowTick);
    }

    private static void step(Room room, Player player, Direction dir, long nowTick) {
        MovementRules.Outcome outcome =
                MovementRules.resolve(room.map(), player.pos(), dir, room::occupied);

        boolean turned = player.facing() != outcome.facing();
        player.face(outcome.facing());
        if (outcome.moved()) {
            player.moveTo(outcome.pos());
        }
        // A blocked input still spends the cooldown; that is what makes walls and other
        // players cost you time rather than being free to probe.
        player.setNextMoveTick(
                MovementRules.nextReadyTick(nowTick, GameConstants.MOVE_COOLDOWN_TICKS));

        if (outcome.moved() || turned) {
            room.markDirty();
        }
    }

    private static DoorTransit actionB(Room room, Player player) {
        ActionB action = ActionResolver.actionB(room, player);
        if (action != ActionB.DOOR) {
            // Pickups, swaps and cabinets arrive in Steps 5 and 6.
            return null;
        }
        Direction side = ActionResolver.doorSideFor(room, player);
        return side == null ? null : new DoorTransit(player.id(), side);
    }
}
