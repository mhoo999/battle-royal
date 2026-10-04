package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.ActionA;
import com.example.battleroyal.game.core.ActionB;
import com.example.battleroyal.game.core.Command;
import com.example.battleroyal.game.core.Crate;
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
import java.util.List;
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
        if (player == null || !player.active()) {
            return null;
        }
        switch (command) {
            case Command.Move move -> move(room, player, move.dir(), nowTick);
            case Command.ActionA ignored -> actionA(room, player, nowTick);
            case Command.ActionB ignored -> {
                return actionB(room, player, nowTick);
            }
            case Command.ReleaseB ignored -> {
                if (player.looting() || player.extracting()) {
                    player.cancelLoot();
                    player.cancelExtract();
                    room.markDirty();
                }
            }
            case Command.Equip equip -> equip(room, player, equip.slot());
            case Command.Take take -> take(room, player, take.crateIndex(), take.slot());
            case Command.Put put -> put(room, player, put.slot());
            case Command.CloseCrate ignored -> closeCrate(room, player);
        }
        return null;
    }

    /**
     * Per-tick work that is not driven by a command: finishing loots and releasing a
     * move that was held back by the cooldown.
     */
    public static void tick(Room room, long nowTick) {
        for (Player player : room.players()) {
            if (!player.active()) {
                continue;
            }
            finishLoot(room, player, nowTick);
            if (finishExtract(room, player, nowTick)) {
                continue;
            }
            if (ScoreRules.accrueSurvival(player)) {
                room.markDirty();
            }
            if (!MovementRules.ready(nowTick, player.nextMoveTick())) {
                continue;
            }
            Direction pending = player.takeBufferedMove(nowTick);
            if (pending != null) {
                step(room, player, pending, nowTick);
            }
        }
        GuardRules.tick(room, nowTick);
    }

    private static void move(Room room, Player player, Direction dir, long nowTick) {
        if (!MovementRules.ready(nowTick, player.nextMoveTick())) {
            // Hold it rather than discard it. Dropping mid-cooldown input is what made
            // a held direction stutter whenever the client drifted out of phase.
            player.bufferMove(dir, nowTick + GameConstants.MOVE_BUFFER_TICKS);
            return;
        }
        step(room, player, dir, nowTick);
    }

    private static void step(Room room, Player player, Direction dir, long nowTick) {
        if (player.inCabinet()) {
            player.setNextMoveTick(
                    MovementRules.nextReadyTick(nowTick, GameConstants.MOVE_COOLDOWN_TICKS));
            leaveCabinet(room, player, dir, nowTick);
            return;
        }
        Pos target = player.pos().step(dir);
        if (room.map().cabinets().contains(target)) {
            player.setNextMoveTick(
                    MovementRules.nextReadyTick(nowTick, GameConstants.MOVE_COOLDOWN_TICKS));
            enterCabinet(room, player, dir, target, nowTick);
            return;
        }
        MovementRules.Outcome outcome =
                MovementRules.resolve(room.map(), player.pos(), dir, room::occupied);

        boolean turned = player.facing() != outcome.facing();
        player.face(outcome.facing());
        if (outcome.moved()) {
            player.moveTo(outcome.pos());
            // Stepping off the crate starts the loot over and shuts it, and stepping off
            // an exit starts the way out over; turning on the spot does none of these.
            player.cancelLoot();
            player.closeCrate();
            player.cancelExtract();
        }
        // A blocked input still spends the cooldown; that is what makes walls and other
        // players cost you time rather than being free to probe.
        player.setNextMoveTick(
                MovementRules.nextReadyTick(nowTick, GameConstants.MOVE_COOLDOWN_TICKS));

        if (outcome.moved() || turned) {
            room.markDirty();
        }
    }

    // --- Cabinets --------------------------------------------------------

    /**
     * Walking into an empty cabinet hides you in it. The occupant stands on the cabinet
     * tile, which is what {@link CombatRules} relies on, and keeps facing the way they
     * walked in. An occupied cabinet blocks like any other player: walking into it is
     * how you find out someone is there.
     */
    private static void enterCabinet(Room room, Player player, Direction dir, Pos cabinet,
                                     long nowTick) {
        boolean turned = player.facing() != dir;
        player.face(dir);
        if (MovementRules.ready(nowTick, player.nextCabinetToggleTick())
                && !room.occupied(cabinet)) {
            player.moveTo(cabinet);
            player.setInCabinet(true);
            player.cancelLoot();
            player.closeCrate();
            player.cancelExtract();
            player.setNextCabinetToggleTick(
                    nowTick + GameConstants.CABINET_TOGGLE_COOLDOWN_TICKS);
            room.markDirty();
        } else if (turned) {
            room.markDirty();
        }
    }

    /**
     * Any direction but straight on walks back out, onto a free tile. Straight on is the
     * way you came in, so refusing it means a cabinet cannot be walked through, and a
     * direction key still held from walking in keeps you inside rather than carrying you
     * out the far side. Facing is left alone while inside so that rule has something to
     * go by; nobody sees an occupant's facing anyway.
     */
    private static void leaveCabinet(Room room, Player player, Direction dir, long nowTick) {
        if (dir == player.facing()
                || !MovementRules.ready(nowTick, player.nextCabinetToggleTick())) {
            return;
        }
        Pos target = player.pos().step(dir);
        if (!room.map().walkable(target) || room.occupied(target)) {
            return;
        }
        player.face(dir);
        player.moveTo(target);
        player.setInCabinet(false);
        player.setNextCabinetToggleTick(nowTick + GameConstants.CABINET_TOGGLE_COOLDOWN_TICKS);
        room.markDirty();
    }

    // --- A: the held item ------------------------------------------------

    /**
     * Every A shares one cooldown, set by whatever was just done. The token is
     * recomputed here rather than trusted from the snapshot the client saw, so a stale
     * button label can never unlock an action the rules no longer allow.
     */
    private static void actionA(Room room, Player player, long nowTick) {
        ActionA action = ActionResolver.actionA(player);
        if (action == null || !MovementRules.ready(nowTick, player.nextActionTick())) {
            return;
        }
        switch (action) {
            case ATTACK -> {
                Weapons.Strike blow = Weapons.strikeOf(heldKind(player));
                strike(room, player, blow, nowTick);
                player.setNextActionTick(nowTick + blow.cooldownTicks());
            }
            case FIRE -> {
                Item gun = player.heldItem();
                Weapons.Strike shot = Weapons.strikeOf(gun.kind());
                gun.spendAmmo();
                // The empty gun stays in hand: it can be loaded again.
                strike(room, player, shot, nowTick);
                player.setNextActionTick(nowTick + shot.cooldownTicks());
            }
            case RELOAD -> {
                reload(player);
                player.setNextActionTick(nowTick + GameConstants.RELOAD_TICKS);
            }
            case HEAL -> {
                player.heal(GameConstants.MEDKIT_HEAL, GameConstants.MAX_HP);
                player.releaseItem();
                player.setNextActionTick(nowTick + GameConstants.MEDKIT_COOLDOWN_TICKS);
            }
        }
        room.markDirty();
    }

    /**
     * Fills the gun in hand from the first matching bundle, as far as the bundle goes.
     * An emptied bundle is gone; what is left of one stays where it was.
     */
    private static void reload(Player player) {
        Item gun = player.heldItem();
        int slot = ActionResolver.ammunitionSlot(player);
        Item bundle = player.slot(slot);
        int moved = Math.min(Weapons.capacity(gun.kind()) - gun.ammo(), bundle.ammo());
        bundle.spendAmmo(moved);
        gun.addAmmo(moved);
        if (!bundle.hasAmmo()) {
            player.setSlot(slot, null);
        }
    }

    /** Bare hands are a null kind, which is how {@link Weapons} tells them apart. */
    private static ItemKind heldKind(Player player) {
        return player.hasItem() ? player.heldItem().kind() : null;
    }

    /**
     * Resolves one attack along the attacker's facing. A gun leaves a trail along the
     * whole shot; anything swung, fists included, leaves a swing on the tile in front.
     */
    private static void strike(Room room, Player attacker, Weapons.Strike blow, long nowTick) {
        CombatRules.Trace trace =
                CombatRules.trace(room, attacker.pos(), attacker.facing(), blow.range());
        if (blow.shot()) {
            room.emit(new GameEvent.Shot(trace.path()));
        } else {
            room.emit(new GameEvent.Swing(attacker.pos(),
                    attacker.pos().step(attacker.facing())));
        }
        if (!trace.hit()) {
            return;
        }
        room.emit(new GameEvent.Hit(attacker.id()));
        if (trace.guard() != null) {
            // No score, no kill: a guard is not a player (V2.2).
            GuardRules.wound(room, trace.guard(), blow.damage());
            return;
        }
        Player victim = trace.victim();
        // A hit knocks you off the way out, whatever it does to your health.
        if (victim.extracting()) {
            victim.cancelExtract();
            room.markDirty();
        }
        attacker.addScore(GameConstants.SCORE_HIT);
        if (victim.takeDamage(blow.damage())) {
            attacker.addScore(GameConstants.SCORE_KILL);
            attacker.addKill();
            die(room, victim, attacker, nowTick);
        }
    }

    /**
     * The season ended (D12): the player leaves the island at once, and what they carry
     * goes with the wipe rather than into a crate. Reaped after this tick's broadcast,
     * like an extraction.
     */
    public static void eject(Room room, Player player) {
        if (!player.active()) {
            return;
        }
        player.cancelLoot();
        player.cancelExtract();
        player.closeCrate();
        player.dropAll();
        player.markEjected();
        room.emit(new GameEvent.Ejected(player.id()));
        room.markDirty();
    }

    /**
     * A disconnected player whose grace period has run out. Dies like anyone else, item
     * dropped and result recorded, but with nobody to credit.
     */
    public static void abandon(Room room, Player player, long nowTick) {
        if (!player.active()) {
            return;
        }
        player.takeDamage(player.hp());
        die(room, player, null, nowTick);
        room.markDirty();
    }

    /**
     * The body stays in the room, marked dead, until the registry reaps it after the
     * tick's broadcast; that way the victim's last snapshot shows them at zero.
     *
     * @param killer null when nobody killed them
     */
    private static void die(Room room, Player victim, Player killer, long nowTick) {
        die(room, victim, killer, false, nowTick);
    }

    /** Shot dead by an outpost guard: no killer to credit, and the news says so. */
    static void dieToGuard(Room room, Player victim, long nowTick) {
        die(room, victim, null, true, nowTick);
    }

    private static void die(Room room, Player victim, Player killer, boolean byGuard,
                            long nowTick) {
        // Before the cabinet flag is cleared: a body pulled out of hiding was not seen.
        List<String> witnesses = room.players().stream()
                .filter(other -> other != victim && other.alive())
                .filter(other -> VisibilityRules.visibleAt(room.map(), other.pos(),
                        victim.pos(), victim.inCabinet()))
                .map(Player::id)
                .toList();
        room.emit(new GameEvent.Fell(victim.pos(), witnesses));

        victim.setInCabinet(false);
        victim.cancelLoot();
        victim.cancelExtract();
        victim.closeCrate();
        victim.clearBufferedMove();
        // Everything they carried, in one crate: a body is worth searching.
        List<Item> dropped = victim.dropAll();
        if (!dropped.isEmpty()) {
            Pos spot = dropSpot(room, victim.pos());
            if (spot != null) {
                room.placeCrate(spot, new Crate(room.newCrateId(), dropped));
            }
        }
        String killerName = killer == null ? null : killer.nickname();
        ItemKind weapon = killer == null || killer.heldItem() == null
                ? null : killer.heldItem().kind();
        room.emit(new GameEvent.Died(victim.id(), victim.nickname(), victim.score(),
                victim.kills(), nowTick - victim.joinedTick(), killerName, weapon, byGuard));
    }

    /**
     * Where a dead player's crate lands: the nearest tile, starting with their own, that
     * is walkable, holds no crate already, and is out of a door's reach. B resolves a
     * door before a crate, so a crate beside a door could never be opened.
     * A cabinet occupant's crate lands on the floor next to it.
     */
    static Pos dropSpot(Room room, Pos at) {
        Set<Pos> seen = new HashSet<>();
        Deque<Pos> frontier = new ArrayDeque<>();
        seen.add(at);
        frontier.add(at);
        while (!frontier.isEmpty()) {
            Pos pos = frontier.removeFirst();
            if (room.map().walkable(pos) && room.crateAt(pos) == null
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
        // Only a room with a crate on every usable tile ends up here.
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
            case OPEN -> startLoot(room, player, nowTick);
            case CLOSE -> closeCrate(room, player);
            case EXTRACT -> startExtract(room, player, nowTick);
        }
        return null;
    }

    // --- Exits ------------------------------------------------------------

    /** Like a loot, pressing B again while already on the way out does not restart it. */
    private static void startExtract(Room room, Player player, long nowTick) {
        if (player.extracting()) {
            return;
        }
        player.closeCrate();
        player.startExtract(nowTick + GameConstants.EXTRACT_TICKS);
        room.markDirty();
    }

    /**
     * Five seconds held without moving or being hit: the player is out, with everything
     * they carry. They stay in the room until the registry takes them away after this
     * tick's broadcast, so their last snapshot and the news reach them first.
     *
     * @return true when the player got out this tick
     */
    private static boolean finishExtract(Room room, Player player, long nowTick) {
        if (!player.extractDue(nowTick)) {
            return false;
        }
        List<Item> carried = player.dropAll();
        player.markExtracted();
        room.emit(new GameEvent.Extracted(player.id(), player.nickname(), player.score(),
                player.kills(), nowTick - player.joinedTick(), carried));
        room.markDirty();
        return true;
    }

    /** Pressing B again while already looting does not restart the clock. */
    private static void startLoot(Room room, Player player, long nowTick) {
        if (player.looting()) {
            return;
        }
        player.startLoot(room.crateAt(player.pos()).id(), nowTick + GameConstants.LOOT_TICKS);
        room.markDirty();
    }

    /** When the time is up the crate opens, and its contents go to this player alone. */
    private static void finishLoot(Room room, Player player, long nowTick) {
        if (!player.looting()) {
            return;
        }
        String crateId = player.takeFinishedLoot(nowTick);
        if (player.looting()) {
            return;
        }
        room.markDirty();
        Crate lying = room.crateAt(player.pos());
        // Someone may have emptied it, or a different crate may lie here now.
        if (crateId != null && lying != null && lying.id().equals(crateId)) {
            player.openCrate(player.pos());
        }
    }

    private static void closeCrate(Room room, Player player) {
        if (player.openCrateAt() != null) {
            player.closeCrate();
            room.markDirty();
        }
    }

    /** The crate this player has open and is standing on, or null. */
    public static Crate openCrate(Room room, Player player) {
        Pos at = player.openCrateAt();
        if (at == null || !at.equals(player.pos()) || player.inCabinet()) {
            return null;
        }
        return room.crateAt(at);
    }

    private static boolean validSlot(Player player, int slot) {
        return slot >= 0 && slot < player.slotCount();
    }

    /** Which slot A uses. Instant: the one A cooldown is what keeps swapping honest. */
    private static void equip(Room room, Player player, int slot) {
        if (!validSlot(player, slot) || player.equipped() == slot) {
            return;
        }
        player.equip(slot);
        room.markDirty();
    }

    /**
     * From the open crate into a slot. An occupied slot trades places with the crate's
     * item, so nothing is ever destroyed. An emptied crate leaves the floor. Into the
     * bag slot only a bag goes, and only when the slots it would take away are empty.
     */
    private static void take(Room room, Player player, int crateIndex, int slot) {
        Crate crate = openCrate(room, player);
        boolean bagSlot = slot == Player.BAG_SLOT;
        if (crate == null || (!bagSlot && !validSlot(player, slot))
                || crateIndex < 0 || crateIndex >= crate.size()) {
            return;
        }
        Item taken = crate.get(crateIndex);
        if (bagSlot && !Bags.canWear(player, taken)) {
            return;
        }
        Item outgoing = bagSlot ? Bags.wear(player, taken) : player.setSlot(slot, taken);
        if (outgoing != null) {
            crate.replace(crateIndex, outgoing);
        } else {
            crate.remove(crateIndex);
        }
        if (player.firstPickup(taken.id())) {
            player.addScore(GameConstants.SCORE_ITEM_PICKUP);
        }
        if (crate.isEmpty()) {
            room.removeCrate(player.pos());
            player.closeCrate();
        }
        room.markDirty();
    }

    /**
     * From a slot into the open crate, while it has room. The bag comes off only when
     * the slots it adds are empty.
     */
    private static void put(Room room, Player player, int slot) {
        Crate crate = openCrate(room, player);
        if (crate == null || crate.size() >= GameConstants.CRATE_CAPACITY) {
            return;
        }
        if (slot == Player.BAG_SLOT) {
            if (player.bag() == null || !Bags.canWear(player, null)) {
                return;
            }
            crate.add(Bags.wear(player, null));
        } else {
            if (!validSlot(player, slot) || player.slot(slot) == null) {
                return;
            }
            crate.add(player.setSlot(slot, null));
        }
        room.markDirty();
    }
}
