package com.example.battleroyal.bot;

import com.example.battleroyal.game.core.ActionA;
import com.example.battleroyal.game.core.Command;
import com.example.battleroyal.game.core.Crate;
import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.Exit;
import com.example.battleroyal.game.core.GridMap;
import com.example.battleroyal.game.core.Guard;
import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.rule.ActionResolver;
import com.example.battleroyal.game.rule.CombatRules;
import com.example.battleroyal.game.rule.GameConstants;
import com.example.battleroyal.game.rule.RoomSimulator;
import com.example.battleroyal.game.rule.VisibilityRules;
import com.example.battleroyal.game.rule.Weapons;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * What a server-run player does next (docs/GAME_RULES.md §10b). It starts bare-handed,
 * opens crates and holds the best thing it finds; with a knife or better it fights
 * whoever it meets, without one it runs and makes for its exit. After a few rooms it
 * heads out anyway.
 *
 * <p>It decides from what its own snapshot would show — the room, the crates as crates,
 * the people {@link VisibilityRules} lets it see, its own exits — and acts only through
 * {@link Command}s, at a person's pace. It never knows more than a player could.
 */
public final class BotBrain {

    /** How often a bot acts: every 3–5 ticks, 150–250ms, near a person's tapping. */
    static final int THINK_MIN_TICKS = 3;
    static final int THINK_SPREAD_TICKS = 3;
    /**
     * A trip, as long as a person's: at least 5–10 rooms and 1–2.5 minutes before
     * heading out, never more than 5 minutes.
     */
    static final int MIN_ROOMS = 5;
    static final int ROOM_SPREAD = 6;
    static final int MIN_TRIP_TICKS = 60 * GameConstants.TICKS_PER_SECOND;
    static final int TRIP_SPREAD_TICKS = 90 * GameConstants.TICKS_PER_SECOND;
    static final long MAX_TRIP_TICKS = 5L * 60 * GameConstants.TICKS_PER_SECOND;
    /**
     * An outmatched bot runs only from someone this close; further off, it keeps going
     * to its exit, wary. Otherwise two rooms with someone in each bounce it between them.
     */
    static final int FLEE_WITHIN = 5;
    /** With its exit in the room, it goes for it unless someone is this close. */
    static final int DASH_UNLESS_WITHIN = 2;
    /** Below this a bot holding a medkit heals when nobody is in reach. */
    static final int HEAL_BELOW_HP = 50;

    private BotBrain() {
    }

    /** What one bot remembers between thoughts. */
    public static final class Memory {
        long nextThinkTick;
        final long startTick;
        final int roomsBeforeLeaving;
        final int ticksBeforeLeaving;
        int rooms;
        String roomId;
        boolean goingOut;
        Direction wanderSide;
        /** Once it runs, the door it runs for, until it is out of this room. */
        Direction fleeSide;
        /** The door it came in by; it runs back through it only when no other is open. */
        Direction cameIn;
        final Set<String> triedCrates = new HashSet<>();
        /** The join is applied on a later tick; until then, not finding it is no loss. */
        final long arrivedBy;

        public Memory(long startTick, Random random, long arrivedBy) {
            this.startTick = startTick;
            this.roomsBeforeLeaving = MIN_ROOMS + random.nextInt(ROOM_SPREAD);
            this.ticksBeforeLeaving = MIN_TRIP_TICKS + random.nextInt(TRIP_SPREAD_TICKS);
            this.arrivedBy = arrivedBy;
        }

        public boolean goingOut() {
            return goingOut;
        }
    }

    /** The next command, or null to do nothing this tick. */
    public static Command think(Room room, Player self, Memory memory, long tick, Random random) {
        if (!self.alive() || !self.active() || tick < memory.nextThinkTick) {
            return null;
        }
        memory.nextThinkTick = tick + THINK_MIN_TICKS + random.nextInt(THINK_SPREAD_TICKS);
        if (self.extracting() || self.looting()) {
            return null; // B is held; letting go would throw the progress away
        }
        if (!room.id().equals(memory.roomId)) {
            enter(room, self, memory, random);
        }

        Crate open = RoomSimulator.openCrate(room, self);
        if (open != null) {
            return fromCrate(self, open);
        }

        Target foe = nearestThreat(room, self);
        if (foe != null) {
            if (armed(self) && foe.player != null) {
                Command equip = equipBest(self);
                return equip != null ? equip : fight(room, self, foe.pos);
            }
            int distance = distance(self.pos(), foe.pos);
            if (distance <= FLEE_WITHIN) {
                memory.goingOut = true; // outmatched and too close: leave the island
            }
            Exit exit = exitHere(room, self);
            if (memory.goingOut && exit != null && memory.fleeSide == null
                    && distance > DASH_UNLESS_WITHIN) {
                return toExitTile(room, self, exit, random); // the way out is right here
            }
            // Once it runs it keeps running until it is out of the room: no dithering.
            if (memory.fleeSide != null || distance <= FLEE_WITHIN) {
                return flee(room, self, foe.pos, memory, random);
            }
        }

        Command heal = healIfHurt(self);
        if (heal != null) {
            return heal;
        }
        Command equip = equipBest(self);
        if (equip != null) {
            return equip;
        }

        long out = tick - memory.startTick;
        if (!memory.goingOut && ((memory.rooms >= memory.roomsBeforeLeaving
                && out >= memory.ticksBeforeLeaving) || out > MAX_TRIP_TICKS)) {
            memory.goingOut = true;
        }
        if (memory.goingOut) {
            return headForExit(room, self, random);
        }

        Command loot = lootRoom(room, self, memory, random);
        if (loot != null) {
            return loot;
        }
        return toDoor(room, self, memory.wanderSide, random);
    }

    private static void enter(Room room, Player self, Memory memory, Random random) {
        memory.roomId = room.id();
        memory.rooms++;
        memory.triedCrates.clear();
        memory.fleeSide = null;
        // Somewhere new: any door but the one just come through.
        Direction cameIn = nearestDoorSide(room.map(), self.pos());
        memory.cameIn = cameIn;
        List<Direction> ways = new ArrayList<>();
        for (Direction side : room.map().doors().keySet()) {
            if (side != cameIn) {
                ways.add(side);
            }
        }
        if (ways.isEmpty()) {
            ways.addAll(room.map().doors().keySet());
        }
        ways.sort(null); // map order is not fixed; the choice must be
        memory.wanderSide = ways.isEmpty() ? null : ways.get(random.nextInt(ways.size()));
    }

    // --- Seeing --------------------------------------------------------------------

    private record Target(Pos pos, Player player) {
    }

    /** The nearest person it can see, or soldier; null when the room is clear. */
    private static Target nearestThreat(Room room, Player self) {
        Target best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Player other : room.players()) {
            if (other == self || !other.alive() || !other.active()
                    || !VisibilityRules.canSee(room.map(), self, other)) {
                continue;
            }
            int distance = distance(self.pos(), other.pos());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new Target(other.pos(), other);
            }
        }
        for (Guard guard : room.guards()) {
            if (!guard.alive()) {
                continue;
            }
            int distance = distance(self.pos(), guard.pos());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new Target(guard.pos(), null);
            }
        }
        return best;
    }

    // --- Gear ------------------------------------------------------------------------

    /** How much a bot wants an item; 0 is not worth a slot. */
    static int worth(Item item) {
        if (item == null) {
            return 0;
        }
        return switch (item.kind()) {
            case PISTOL -> item.hasAmmo() ? 60 : 15;
            case CROSSBOW -> item.hasAmmo() ? 50 : 12;
            case BAT -> 40;
            case KNIFE -> 35;
            case MEDKIT -> 25;
            case ROUNDS, BOLTS -> 10;
            case PAN -> 8;
            case SPOON, CUP, DOLL, RECORDER, REGISTER -> 2;
            case SMALL_BAG, BIG_BAG -> 0; // worn only from the hideout; loose, just a fist
        };
    }

    /** What it would fight with: higher is better; 0 is a fist's worth. */
    private static int fighting(Item item) {
        if (item == null) {
            return 0;
        }
        return switch (item.kind()) {
            case PISTOL -> item.hasAmmo() ? 6 : 0;
            case CROSSBOW -> item.hasAmmo() ? 5 : 0;
            case BAT -> 4;
            case KNIFE -> 3;
            case PAN -> 2;
            case SPOON, CUP, DOLL, RECORDER, REGISTER, ROUNDS, BOLTS, SMALL_BAG, BIG_BAG -> 1;
            case MEDKIT -> 0;
        };
    }

    /** A knife or better: worth a fight (the user's rule). */
    static boolean armed(Player self) {
        for (Item item : self.inventory()) {
            if (fighting(item) >= 3) {
                return true;
            }
        }
        return false;
    }

    private static int bestWeaponSlot(Player self) {
        int best = -1;
        int bestValue = 0;
        for (int slot = 0; slot < self.slotCount(); slot++) {
            int value = fighting(self.slot(slot));
            if (value > bestValue) {
                bestValue = value;
                best = slot;
            }
        }
        return best;
    }

    private static Command equipBest(Player self) {
        int best = bestWeaponSlot(self);
        if (best < 0 || best == self.equipped()
                || fighting(self.slot(best)) <= fighting(self.heldItem())) {
            return null;
        }
        return new Command.Equip(self.id(), best);
    }

    private static Command healIfHurt(Player self) {
        if (self.hp() >= HEAL_BELOW_HP) {
            return null;
        }
        for (int slot = 0; slot < self.slotCount(); slot++) {
            Item item = self.slot(slot);
            if (item != null && item.kind() == ItemKind.MEDKIT) {
                return slot == self.equipped()
                        ? new Command.ActionA(self.id())
                        : new Command.Equip(self.id(), slot);
            }
        }
        return null;
    }

    /** One item at a time: into a free slot, or over the least wanted; then close. */
    private static Command fromCrate(Player self, Crate crate) {
        int freeSlot = -1;
        int poorestSlot = -1;
        int poorest = Integer.MAX_VALUE;
        for (int slot = 0; slot < self.slotCount(); slot++) {
            Item held = self.slot(slot);
            if (held == null && freeSlot < 0) {
                freeSlot = slot;
            }
            if (held != null && worth(held) < poorest) {
                poorest = worth(held);
                poorestSlot = slot;
            }
        }
        int bestIndex = -1;
        int bestWorth = 0;
        for (int index = 0; index < crate.size(); index++) {
            int value = worth(crate.get(index));
            if (value > bestWorth) {
                bestWorth = value;
                bestIndex = index;
            }
        }
        if (bestIndex >= 0 && freeSlot >= 0) {
            return new Command.Take(self.id(), bestIndex, freeSlot);
        }
        if (bestIndex >= 0 && poorestSlot >= 0 && bestWorth > poorest) {
            return new Command.Take(self.id(), bestIndex, poorestSlot);
        }
        return new Command.CloseCrate(self.id());
    }

    private static Command lootRoom(Room room, Player self, Memory memory, Random random) {
        Pos nearest = null;
        for (Map.Entry<Pos, Crate> entry : room.crates().entrySet()) {
            if (memory.triedCrates.contains(entry.getValue().id())) {
                continue;
            }
            if (nearest == null || distance(self.pos(), entry.getKey())
                    < distance(self.pos(), nearest)) {
                nearest = entry.getKey();
            }
        }
        if (nearest == null) {
            return null;
        }
        if (nearest.equals(self.pos())) {
            memory.triedCrates.add(room.crates().get(nearest).id());
            return new Command.ActionB(self.id()); // held until the crate opens
        }
        Command step = stepToward(room, self, nearest, random);
        if (step == null) {
            memory.triedCrates.add(room.crates().get(nearest).id()); // unreachable
        }
        return step;
    }

    // --- Fighting and running -------------------------------------------------------

    private static Command fight(Room room, Player self, Pos foe) {
        if (ActionResolver.actionA(self) == ActionA.RELOAD) {
            return new Command.ActionA(self.id());
        }
        Item held = self.heldItem();
        Weapons.Strike strike = Weapons.strikeOf(held == null ? null : held.kind());
        if (strike != null && strike.shot()) {
            Direction line = lineTo(self.pos(), foe);
            if (line != null && distance(self.pos(), foe) <= strike.range()) {
                if (self.facing() != line) {
                    return new Command.Move(self.id(), line); // a step towards still lines up
                }
                CombatRules.Trace trace = CombatRules.trace(room, self.pos(), line, strike.range());
                if (trace.victim() != null && trace.victim().pos().equals(foe)) {
                    return new Command.ActionA(self.id());
                }
            }
        } else if (distance(self.pos(), foe) == 1) {
            Direction toward = lineTo(self.pos(), foe);
            return self.facing() == toward
                    ? new Command.ActionA(self.id())
                    : new Command.Move(self.id(), toward); // into them: a turn, not a step
        }
        return stepToward(room, self, foe, null);
    }

    /**
     * To the door furthest from the threat — chosen once, so a bot does not dither as
     * the threat moves. A door someone is standing in is given up for the next furthest.
     */
    private static Command flee(Room room, Player self, Pos threat, Memory memory, Random random) {
        Pos chosen = memory.fleeSide == null ? null : room.map().doorAt(memory.fleeSide);
        if (chosen == null || blockedByOther(room, self, chosen)) {
            memory.fleeSide = null;
            int furthest = Integer.MIN_VALUE;
            for (Map.Entry<Direction, Pos> door : sortedDoors(room.map())) {
                if (blockedByOther(room, self, door.getValue())) {
                    continue;
                }
                // Back the way it came only as a last resort.
                int distance = distance(door.getValue(), threat)
                        - (door.getKey() == memory.cameIn ? GridMap.SIZE * 2 : 0);
                if (distance > furthest) {
                    furthest = distance;
                    memory.fleeSide = door.getKey();
                }
            }
        }
        return toDoor(room, self, memory.fleeSide, random);
    }

    private static boolean blockedByOther(Room room, Player self, Pos pos) {
        return !pos.equals(self.pos()) && room.occupied(pos);
    }

    // --- Getting about --------------------------------------------------------------

    private static Command headForExit(Room room, Player self, Random random) {
        Exit exit = exitHere(room, self);
        if (exit != null) {
            return toExitTile(room, self, exit, random);
        }
        Exit nearest = null;
        for (Exit candidate : self.exits()) {
            if (nearest == null || Math.abs(candidate.dx()) + Math.abs(candidate.dy())
                    < Math.abs(nearest.dx()) + Math.abs(nearest.dy())) {
                nearest = candidate;
            }
        }
        if (nearest == null) {
            return toDoor(room, self, null, random);
        }
        Direction side = nearest.dx() > 0 ? Direction.RIGHT : nearest.dx() < 0 ? Direction.LEFT
                : nearest.dy() > 0 ? Direction.DOWN : Direction.UP;
        return toDoor(room, self, side, random);
    }

    private static Exit exitHere(Room room, Player self) {
        for (Exit exit : self.exits()) {
            if (exit.in(room)) {
                return exit;
            }
        }
        return null;
    }

    private static Command toExitTile(Room room, Player self, Exit exit, Random random) {
        if (self.pos().equals(exit.at())) {
            return new Command.ActionB(self.id()); // held until it gets out
        }
        return stepToward(room, self, exit.at(), random);
    }

    /** Through the door on that side; any door when there is none there. */
    private static Command toDoor(Room room, Player self, Direction side, Random random) {
        Pos door = side == null ? null : room.map().doorAt(side);
        if (door == null) {
            List<Map.Entry<Direction, Pos>> doors = sortedDoors(room.map());
            if (doors.isEmpty()) {
                return null;
            }
            door = doors.get(random.nextInt(doors.size())).getValue();
        }
        if (self.pos().equals(door)) {
            return new Command.ActionB(self.id());
        }
        return stepToward(room, self, door, random);
    }

    /**
     * The first step of a shortest walk to {@code goal} round walls, cabinets and
     * people. Null when there is no way; with {@code random}, a random step instead, so a
     * blocked bot shuffles rather than freezes.
     */
    static Command stepToward(Room room, Player self, Pos goal, Random random) {
        GridMap map = room.map();
        Pos start = self.pos();
        Map<Pos, Direction> firstStep = new HashMap<>();
        ArrayDeque<Pos> queue = new ArrayDeque<>();
        Set<Pos> seen = new HashSet<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            Pos at = queue.poll();
            if (at.equals(goal)) {
                return new Command.Move(self.id(), firstStep.get(at));
            }
            for (Direction dir : Direction.values()) {
                Pos next = at.step(dir);
                if (seen.contains(next) || !map.walkable(next)) {
                    continue;
                }
                if (room.occupied(next) && !next.equals(goal)) {
                    continue;
                }
                seen.add(next);
                firstStep.put(next, at.equals(start) ? dir : firstStep.get(at));
                queue.add(next);
            }
        }
        if (random == null) {
            return null;
        }
        Direction any = Direction.values()[random.nextInt(Direction.values().length)];
        return new Command.Move(self.id(), any);
    }

    private static List<Map.Entry<Direction, Pos>> sortedDoors(GridMap map) {
        List<Map.Entry<Direction, Pos>> doors = new ArrayList<>(map.doors().entrySet());
        doors.sort(Map.Entry.comparingByKey());
        return doors;
    }

    private static Direction nearestDoorSide(GridMap map, Pos pos) {
        Direction nearest = null;
        int best = Integer.MAX_VALUE;
        for (Map.Entry<Direction, Pos> door : sortedDoors(map)) {
            int distance = distance(pos, door.getValue());
            if (distance < best) {
                best = distance;
                nearest = door.getKey();
            }
        }
        return nearest;
    }

    /** The direction from one tile to another on the same row or column, else null. */
    private static Direction lineTo(Pos from, Pos to) {
        if (from.x() == to.x() && from.y() != to.y()) {
            return to.y() > from.y() ? Direction.DOWN : Direction.UP;
        }
        if (from.y() == to.y() && from.x() != to.x()) {
            return to.x() > from.x() ? Direction.RIGHT : Direction.LEFT;
        }
        return null;
    }

    private static int distance(Pos a, Pos b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y());
    }
}
