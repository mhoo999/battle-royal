package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.ActionB;
import com.example.battleroyal.game.core.Command;
import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.Exit;
import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.map.MapTemplates;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Holding B on your own exit for five seconds, and everything that stops it (D7). */
class ExtractRulesTest {

    /** Plain CROSSROADS floor, out of every door's reach. */
    private static final Pos EXIT = new Pos(2, 5);

    private static Room room() {
        return new Room("room-1", MapTemplates.CROSSROADS.map());
    }

    private static Player put(Room room, String id, Pos pos) {
        Player player = new Player(id, id, pos, GameConstants.MAX_HP);
        room.add(player);
        return player;
    }

    private static Player onExit(Room room) {
        Player player = put(room, "p", EXIT);
        player.setExits(List.of(new Exit(room.id(), EXIT, 0, 0)));
        return player;
    }

    private static void apply(Room room, Command command, long tick) {
        RoomSimulator.apply(room, command, tick);
    }

    private static List<GameEvent.Extracted> extractions(Room room) {
        return room.drainEvents().stream()
                .filter(GameEvent.Extracted.class::isInstance)
                .map(GameEvent.Extracted.class::cast)
                .toList();
    }

    @Test
    void bOnYourOwnExitIsTheWayOut() {
        Room room = room();
        Player player = onExit(room);

        assertEquals(ActionB.EXTRACT, ActionResolver.actionB(room, player));
    }

    @Test
    void somebodyElsesExitIsJustFloor() {
        Room room = room();
        onExit(room);
        Player other = put(room, "q", new Pos(3, 5));
        other.setExits(List.of(new Exit("room-9", EXIT, 1, 0)));
        other.moveTo(EXIT);

        assertNull(ActionResolver.actionB(room, other));
    }

    @Test
    void fiveSecondsHeldTakesYouOutWithEverythingYouCarry() {
        Room room = room();
        Player player = onExit(room);
        Item pistol = new Item("s-1", ItemKind.PISTOL, 4);
        Item cup = new Item("i-7", ItemKind.CUP, 0);
        player.setSlot(0, pistol);
        player.setSlot(2, cup);

        apply(room, new Command.ActionB("p"), 10);
        RoomSimulator.tick(room, 10 + GameConstants.EXTRACT_TICKS - 1);
        assertFalse(player.extracted(), "not a tick early");

        RoomSimulator.tick(room, 10 + GameConstants.EXTRACT_TICKS);

        assertTrue(player.extracted());
        assertFalse(player.active());
        GameEvent.Extracted out = extractions(room).getFirst();
        assertEquals(List.of(pistol, cup), out.carried());
        assertEquals("p", out.playerId());
    }

    @Test
    void steppingOffStartsItOver() {
        Room room = room();
        Player player = onExit(room);

        apply(room, new Command.ActionB("p"), 10);
        apply(room, new Command.Move("p", Direction.RIGHT), 20);
        apply(room, new Command.Move("p", Direction.LEFT), 30);
        RoomSimulator.tick(room, 10 + GameConstants.EXTRACT_TICKS);

        assertFalse(player.extracting());
        assertFalse(player.extracted(), "coming back to the tile is not enough");
    }

    @Test
    void lettingGoOfBStartsItOver() {
        Room room = room();
        Player player = onExit(room);

        apply(room, new Command.ActionB("p"), 10);
        apply(room, new Command.ReleaseB("p"), 50);
        RoomSimulator.tick(room, 10 + GameConstants.EXTRACT_TICKS);

        assertFalse(player.extracted());
    }

    @Test
    void aHitKnocksYouOffTheWayOut() {
        Room room = room();
        Player player = onExit(room);
        Player attacker = put(room, "q", new Pos(3, 5));
        attacker.face(Direction.LEFT);

        apply(room, new Command.ActionB("p"), 10);
        apply(room, new Command.ActionA("q"), 50);
        RoomSimulator.tick(room, 10 + GameConstants.EXTRACT_TICKS);

        assertTrue(player.alive());
        assertFalse(player.extracted(), "the most dangerous moment of the trip");
    }

    @Test
    void theOneWhoGotOutTakesNoFurtherCommands() {
        Room room = room();
        Player player = onExit(room);
        apply(room, new Command.ActionB("p"), 0);
        RoomSimulator.tick(room, GameConstants.EXTRACT_TICKS);

        apply(room, new Command.Move("p", Direction.RIGHT), GameConstants.EXTRACT_TICKS + 1);

        assertEquals(EXIT, player.pos());
    }
}
