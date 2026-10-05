package com.example.battleroyal.bot;

import com.example.battleroyal.game.core.Command;
import com.example.battleroyal.game.core.Crate;
import com.example.battleroyal.game.core.Direction;
import com.example.battleroyal.game.core.Exit;
import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.map.MapTemplates;
import com.example.battleroyal.game.rule.GameConstants;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bots' rules (docs/GAME_RULES.md §10c) on CROSSROADS: open floor along row 1, a
 * bush at x3-5 y2-4, a cabinet at (9,4).
 */
class BotBrainTest {

    private static final long TICK = 1_000;

    private final Random random = new Random(7);

    private static Room room() {
        return new Room("room-1", MapTemplates.CROSSROADS.map());
    }

    private static Player put(Room room, String id, Pos pos) {
        Player player = new Player(id, id, pos, GameConstants.MAX_HP);
        room.add(player);
        return player;
    }

    private BotBrain.Memory memory() {
        return new BotBrain.Memory(TICK, random, TICK);
    }

    private Command think(Room room, Player bot, BotBrain.Memory memory) {
        return BotBrain.think(room, bot, memory, TICK, random);
    }

    private static Item item(ItemKind kind) {
        return new Item("i-" + kind, kind, kind.usesAmmo() ? 6 : 0);
    }

    @Test
    void emptyHandedItRunsFromSomeoneRatherThanSwinging() {
        Room room = room();
        Player bot = put(room, "bot", new Pos(1, 1));
        put(room, "person", new Pos(2, 1));
        BotBrain.Memory memory = memory();

        Command command = think(room, bot, memory);

        assertFalse(command instanceof Command.ActionA, "no fist fights");
        assertTrue(memory.goingOut(), "outmatched, it makes for its exit");
    }

    @Test
    void withAKnifeItTurnsToSomeoneBesideItAndStrikes() {
        Room room = room();
        Player bot = put(room, "bot", new Pos(1, 1));
        bot.setSlot(0, item(ItemKind.KNIFE));
        put(room, "person", new Pos(2, 1));

        Command turn = think(room, bot, memory());
        assertEquals(new Command.Move("bot", Direction.RIGHT), turn, "a step into them only turns");

        bot.face(Direction.RIGHT);
        assertInstanceOf(Command.ActionA.class, think(room, bot, memory()));
    }

    @Test
    void withAKnifeItClosesInOnSomeoneFurtherAway() {
        Room room = room();
        Player bot = put(room, "bot", new Pos(1, 1));
        bot.setSlot(0, item(ItemKind.KNIFE));
        put(room, "person", new Pos(6, 1));
        BotBrain.Memory memory = memory();

        assertEquals(new Command.Move("bot", Direction.RIGHT), think(room, bot, memory));
        assertFalse(memory.goingOut());
    }

    @Test
    void itDoesNotSeeSomeoneHidingInABush() {
        Room room = room();
        Player bot = put(room, "bot", new Pos(1, 1));
        put(room, "hider", new Pos(4, 3));
        BotBrain.Memory memory = memory();

        think(room, bot, memory);

        assertFalse(memory.goingOut(), "nothing seen, nothing to run from");
    }

    @Test
    void standingOnACrateItHoldsBToOpenIt() {
        Room room = room();
        Player bot = put(room, "bot", new Pos(1, 1));
        room.placeCrate(new Pos(1, 1), new Crate("c-1", List.of(item(ItemKind.SPOON))));

        assertInstanceOf(Command.ActionB.class, think(room, bot, memory()));
    }

    @Test
    void fromAnOpenCrateItTakesTheBestThingFirst() {
        Room room = room();
        Player bot = put(room, "bot", new Pos(1, 1));
        room.placeCrate(new Pos(1, 1), new Crate("c-1",
                List.of(item(ItemKind.SPOON), item(ItemKind.KNIFE))));
        bot.openCrate(new Pos(1, 1));

        assertEquals(new Command.Take("bot", 1, 0), think(room, bot, memory()));
    }

    @Test
    void withNothingLeftWorthTakingItClosesTheCrate() {
        Room room = room();
        Player bot = put(room, "bot", new Pos(1, 1));
        room.placeCrate(new Pos(1, 1), new Crate("c-1", List.of(item(ItemKind.SMALL_BAG))));
        bot.openCrate(new Pos(1, 1));

        assertInstanceOf(Command.CloseCrate.class, think(room, bot, memory()));
    }

    @Test
    void itHoldsItsBestWeapon() {
        Room room = room();
        Player bot = put(room, "bot", new Pos(1, 1));
        bot.setSlot(0, item(ItemKind.SPOON));
        bot.setSlot(2, item(ItemKind.BAT));

        assertEquals(new Command.Equip("bot", 2), think(room, bot, memory()));
    }

    @Test
    void hurtAndAloneItUsesAMedkit() {
        Room room = room();
        Player bot = put(room, "bot", new Pos(1, 1));
        bot.setSlot(0, item(ItemKind.MEDKIT));
        bot.takeDamage(GameConstants.MAX_HP - 30);

        assertInstanceOf(Command.ActionA.class, think(room, bot, memory()));
    }

    @Test
    void onItsWayOutAndOnItsExitItHoldsB() {
        Room room = room();
        Player bot = put(room, "bot", new Pos(1, 1));
        bot.setExits(List.of(new Exit("room-1", new Pos(1, 1), 0, 0)));
        BotBrain.Memory memory = memory();
        put(room, "person", new Pos(2, 1)); // empty-handed: this sends it out

        think(room, bot, memory);
        assertTrue(memory.goingOut());
        room.remove("person");
        memory.nextThinkTick = 0;

        assertInstanceOf(Command.ActionB.class, think(room, bot, memory));
    }

    @Test
    void itActsAtAPersonsPaceNotEveryTick() {
        Room room = room();
        Player bot = put(room, "bot", new Pos(1, 1));
        BotBrain.Memory memory = memory();

        think(room, bot, memory);

        assertNull(BotBrain.think(room, bot, memory, TICK + 1, random));
    }
}
