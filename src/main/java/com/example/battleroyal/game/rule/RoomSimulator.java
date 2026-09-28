package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.ActionA;
import com.example.battleroyal.game.core.ActionB;
import com.example.battleroyal.game.core.Command;
import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

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
            case Command.ActionA ignored -> actionA(room, player, nowTick);
            case Command.ActionB ignored -> {
                return actionB(room, player, nowTick);
            }
            case Command.ReleaseB ignored -> {
                if (player.looting()) {
                    player.cancelLoot();
                    room.markDirty();
                }
            }
        }
        return null;
    }

    /**
     * Per-tick work that is not driven by a command: finishing reloads and releasing a
     * move that was held back by the cooldown.
     */
    public static void tick(Room room, long nowTick) {
        for (Player player : room.players()) {
            if (!player.alive()) {
                continue;
            }
            finishReload(room, player, nowTick);
            finishLoot(room, player, nowTick);
            if (player.inCabinet()) {
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
            // Stepping off the item starts the loot over; turning on the spot does not.
            player.cancelLoot();
        }
        // A blocked input still spends the cooldown; that is what makes walls and other
        // players cost you time rather than being free to probe.
        player.setNextMoveTick(
                MovementRules.nextReadyTick(nowTick, GameConstants.MOVE_COOLDOWN_TICKS));

        if (outcome.moved() || turned) {
            room.markDirty();
        }
    }

    // --- A: the held item ------------------------------------------------

    /**
     * Every A shares one cooldown, set by whatever was just done. The token is
     * recomputed here rather than trusted from the snapshot the client saw, so a stale
     * button label can never unlock an action the rules no longer allow.
     */
    private static void actionA(Room room, Player player, long nowTick) {
        // Commands run before tick(), so a press landing on the very tick a reload
        // completes must see the full magazine rather than start a second reload.
        finishReload(room, player, nowTick);
        ActionA action = ActionResolver.actionA(player);
        if (action == null || !MovementRules.ready(nowTick, player.nextActionTick())) {
            return;
        }
        switch (action) {
            case ATTACK -> {
                if (player.heldItem().kind() == ItemKind.PAN) {
                    strike(room, player, GameConstants.PAN_RANGE, GameConstants.PAN_DAMAGE,
                            false, nowTick);
                    player.setNextActionTick(nowTick + GameConstants.PAN_COOLDOWN_TICKS);
                } else {
                    strike(room, player, GameConstants.KNIFE_RANGE,
                            GameConstants.KNIFE_DAMAGE, false, nowTick);
                    player.setNextActionTick(nowTick + GameConstants.KNIFE_COOLDOWN_TICKS);
                }
            }
            case FIRE -> {
                player.heldItem().spendAmmo();
                strike(room, player, GameConstants.PISTOL_RANGE,
                        GameConstants.PISTOL_DAMAGE, true, nowTick);
                player.setNextActionTick(nowTick + GameConstants.PISTOL_COOLDOWN_TICKS);
            }
            case RELOAD -> {
                long done = nowTick + GameConstants.PISTOL_RELOAD_TICKS;
                player.startReload(done);
                player.setNextActionTick(done);
            }
            case HEAL -> {
                player.heal(GameConstants.MEDKIT_HEAL, GameConstants.MAX_HP);
                player.releaseItem();
                player.setNextActionTick(nowTick + GameConstants.MEDKIT_COOLDOWN_TICKS);
            }
        }
        room.markDirty();
    }

    private static void finishReload(Room room, Player player, long nowTick) {
        Item reloaded = player.takeFinishedReload(nowTick);
        if (reloaded != null) {
            reloaded.refill(GameConstants.PISTOL_MAGAZINE);
            room.markDirty();
        }
    }

    /**
     * Resolves one attack along the attacker's facing. A pistol leaves a trail along
     * the whole shot; a knife or pan leaves a swing on the tile in front.
     */
    private static void strike(Room room, Player attacker, int range, int damage,
                               boolean shot, long nowTick) {
        CombatRules.Trace trace =
                CombatRules.trace(room, attacker.pos(), attacker.facing(), range);
        if (shot) {
            room.emit(new GameEvent.Shot(trace.path()));
        } else {
            room.emit(new GameEvent.Swing(attacker.pos(),
                    attacker.pos().step(attacker.facing())));
        }
        if (!trace.hit()) {
            return;
        }
        Player victim = trace.victim();
        room.emit(new GameEvent.Hit(attacker.id()));
        attacker.addScore(GameConstants.SCORE_HIT);
        if (victim.takeDamage(damage)) {
            attacker.addScore(GameConstants.SCORE_KILL);
            attacker.addKill();
            die(room, victim, attacker, nowTick);
        }
    }

    /**
     * The body stays in the room, marked dead, until the registry reaps it after the
     * tick's broadcast; that way the victim's last snapshot shows them at zero.
     */
    private static void die(Room room, Player victim, Player killer, long nowTick) {
        victim.setInCabinet(false);
        victim.cancelLoot();
        victim.clearBufferedMove();
        Item dropped = victim.releaseItem();
        if (dropped != null) {
            Pos spot = dropSpot(room, victim.pos());
            if (spot != null) {
                room.placeItem(spot, dropped);
            }
        }
        room.emit(new GameEvent.Died(victim.id(), victim.score(), victim.kills(),
                nowTick - victim.joinedTick(),
                killer.nickname(), killer.heldItem() == null ? null : killer.heldItem().kind()));
    }

    /**
     * Where a dead player's item lands: the nearest tile, starting with their own, that
     * is walkable, holds no item already, and is out of a door's reach. B resolves a
     * door before an item, so an item beside a door could never be picked up again.
     * A cabinet occupant's item spills onto the floor next to it.
     */
    private static Pos dropSpot(Room room, Pos at) {
        Set<Pos> seen = new HashSet<>();
        Deque<Pos> frontier = new ArrayDeque<>();
        seen.add(at);
        frontier.add(at);
        while (!frontier.isEmpty()) {
            Pos pos = frontier.removeFirst();
            if (room.map().walkable(pos) && room.itemAt(pos) == null
                    && ActionResolver.doorSideAt(room, pos) == null) {
                return pos;
            }
            for (Direction dir : Direction.values()) {
                Pos next = pos.step(dir);
                if (room.map().walkable(next) && seen.add(next)) {
                    frontier.addLast(next);
                }
            }
        }
        // Only a room with an item on every usable tile ends up here.
        return null;
    }

    // --- B: the surroundings --------------------------------------------

    private static DoorTransit actionB(Room room, Player player, long nowTick) {
        ActionB action = ActionResolver.actionB(room, player);
        if (action == null) {
            return null;
        }
        switch (action) {
            case DOOR -> {
                Direction side = ActionResolver.doorSideFor(room, player);
                return side == null ? null : new DoorTransit(player.id(), side);
            }
            case PICKUP, SWAP -> startLoot(room, player, nowTick);
            // Cabinets arrive in Step 6.
            case HIDE, UNHIDE -> {
            }
        }
        return null;
    }

    /** Pressing B again while already looting does not restart the clock. */
    private static void startLoot(Room room, Player player, long nowTick) {
        if (player.looting()) {
            return;
        }
        player.startLoot(room.itemAt(player.pos()).id(), nowTick + GameConstants.LOOT_TICKS);
        room.markDirty();
    }

    private static void finishLoot(Room room, Player player, long nowTick) {
        if (!player.looting()) {
            return;
        }
        String itemId = player.takeFinishedLoot(nowTick);
        if (player.looting()) {
            return;
        }
        room.markDirty();
        Item lying = room.itemAt(player.pos());
        // Someone else may have taken it, or swapped something else onto the tile.
        if (itemId != null && lying != null && lying.id().equals(itemId)) {
            takeItem(room, player, nowTick);
        }
    }

    /**
     * Picks up the item underfoot. When already holding one, the two trade places: the
     * outgoing item stays on this tile for anyone to take. Never destroyed.
     */
    private static void takeItem(Room room, Player player, long nowTick) {
        Pos here = player.pos();
        Item taken = room.takeItem(here);
        if (player.reloading()) {
            // The reload leaves with the pistol, and so does the A lock it imposed.
            player.setNextActionTick(nowTick);
        }
        Item outgoing = player.releaseItem();
        if (outgoing != null) {
            room.placeItem(here, outgoing);
        }
        player.hold(taken);
        if (player.firstPickup(taken.id())) {
            player.addScore(GameConstants.SCORE_ITEM_PICKUP);
        }
        ItemSpawns.onTaken(room, here, nowTick);
        room.markDirty();
    }
}
