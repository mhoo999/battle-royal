package com.example.battleroyal.ws;

import com.example.battleroyal.game.core.ActionA;
import com.example.battleroyal.game.core.Command;
import com.example.battleroyal.game.core.Exit;
import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.map.MapTemplates;
import com.example.battleroyal.game.rule.GameConstants;
import com.example.battleroyal.game.rule.RoomSimulator;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotFilterTest {

    private static final Pos OPEN = new Pos(1, 1);
    private static final Pos ALSO_OPEN = new Pos(2, 1);
    private static final Pos BUSH_A = new Pos(3, 2);
    private static final Pos BUSH_B = new Pos(9, 10);

    private final SnapshotFilter filter = new SnapshotFilter();

    private static Room room() {
        return new Room("room-1", MapTemplates.CROSSROADS.map());
    }

    private static Player at(String id, Pos pos) {
        return new Player(id, id, pos, GameConstants.MAX_HP);
    }

    @Test
    void aLooterIsToldHowLongIsLeftAndNobodyElseIs() {
        Room room = room();
        Player viewer = at("v", OPEN);
        room.add(viewer);
        room.placeItem(OPEN, new Item("i-1", ItemKind.KNIFE, 0));
        assertNull(filter.forViewer(room, viewer, 100).self().lootMsLeft());

        RoomSimulator.apply(room, new Command.ActionB("v"), 100);

        assertEquals(GameConstants.LOOT_TICKS * GameConstants.TICK_MS,
                filter.forViewer(room, viewer, 100).self().lootMsLeft());
        assertEquals(GameConstants.TICK_MS,
                filter.forViewer(room, viewer, 100 + GameConstants.LOOT_TICKS - 1)
                        .self().lootMsLeft());
    }

    @Test
    void theCompassIsForItsOwnerAndAnExitsTileShowsOnlyInItsRoom() throws Exception {
        Room room = room();
        Player viewer = at("v", OPEN);
        viewer.setExits(List.of(new Exit("room-1", new Pos(2, 5), 0, 0),
                new Exit("room-7", new Pos(4, 4), -1, 2)));
        Player other = at("o", ALSO_OPEN);
        room.add(viewer);
        room.add(other);

        List<Snapshot.Bearing> mine = filter.forViewer(room, viewer, 1).self().exits();
        assertEquals(new Snapshot.Bearing(0, 0, 2, 5), mine.get(0));
        assertEquals(new Snapshot.Bearing(-1, 2, null, null), mine.get(1),
                "a direction to walk, not the tile of a room you are not in");

        String theirs = new ObjectMapper().writeValueAsString(filter.forViewer(room, other, 1));
        assertFalse(theirs.contains("\"dx\":-1"), "nobody else learns where your exits are");
        assertTrue(filter.forViewer(room, other, 1).self().exits().isEmpty());
    }

    @Test
    void aCrateIsOpenedForTheOpenerAloneAndOthersStillSeeOnlyACrate() {
        Room room = room();
        Player opener = at("v", OPEN);
        Player bystander = at("b", ALSO_OPEN);
        room.add(opener);
        room.add(bystander);
        room.placeItem(OPEN, new Item("i-1", ItemKind.PISTOL, GameConstants.PISTOL_MAGAZINE));
        assertNull(filter.forViewer(room, opener, 100).self().crate(), "not before it is open");

        RoomSimulator.apply(room, new Command.ActionB("v"), 100);
        RoomSimulator.tick(room, 100 + GameConstants.LOOT_TICKS);

        assertEquals(List.of(new Snapshot.Slot(ItemKind.PISTOL, GameConstants.PISTOL_MAGAZINE)),
                filter.forViewer(room, opener, 200).self().crate());
        assertNull(filter.forViewer(room, bystander, 200).self().crate(),
                "what is inside is the opener's to know");
        assertEquals(1, filter.forViewer(room, bystander, 200).items().size(),
                "everyone still sees that a crate lies there");
    }

    @Test
    void theViewerSeesTheirWholeInventoryAndWhichSlotIsEquipped() {
        Room room = room();
        Player viewer = at("v", OPEN);
        viewer.setSlot(0, new Item("i-1", ItemKind.KNIFE, 0));
        viewer.setSlot(2, new Item("i-2", ItemKind.PISTOL, 4));
        viewer.equip(2);
        room.add(viewer);

        Snapshot.Self self = filter.forViewer(room, viewer, 100).self();

        assertEquals(Arrays.asList(new Snapshot.Slot(ItemKind.KNIFE, null), null,
                new Snapshot.Slot(ItemKind.PISTOL, 4)), self.inventory());
        assertEquals(2, self.equipped());
        assertEquals(ItemKind.PISTOL, self.item(), "item is the equipped one");
    }

    /**
     * The strongest guard in the codebase: if anyone adds hp, item or ammo to the view
     * other players receive, this fails. The type is the contract.
     */
    @Test
    void theViewOfOtherPlayersCarriesNothingPrivate() {
        List<String> fields = Arrays.stream(Snapshot.Other.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertEquals(List.of("id", "x", "y", "direction", "alive"), fields,
                "Other must expose position and facing only. Adding a field here "
                        + "publishes it to every opponent; see docs/NETWORK_PROTOCOL.md");
    }

    @Test
    void privateStateNeverReachesAnotherPlayer() throws Exception {
        Room room = room();
        Player viewer = at("viewer", OPEN);
        Player rival = at("rival", ALSO_OPEN);
        rival.hold(new Item("i-1", ItemKind.PISTOL, GameConstants.PISTOL_MAGAZINE));
        rival.takeDamage(40);
        room.add(viewer);
        room.add(rival);

        Snapshot snapshot = filter.forViewer(room, viewer, 100);
        String json = new ObjectMapper().writeValueAsString(snapshot.players());

        assertFalse(json.contains("hp"), json);
        assertFalse(json.contains("item"), json);
        assertFalse(json.contains("ammo"), json);
        assertFalse(json.contains("PISTOL"), json);
        assertFalse(json.contains("60"), json + " must not reveal the rival's hp");
    }

    @Test
    void theViewerSeesTheirOwnPrivateState() {
        Room room = room();
        Player viewer = at("viewer", OPEN);
        viewer.hold(new Item("i-1", ItemKind.PISTOL, GameConstants.PISTOL_MAGAZINE));
        viewer.takeDamage(30);
        room.add(viewer);

        Snapshot.Self self = filter.forViewer(room, viewer, 100).self();

        assertEquals(70, self.hp());
        assertEquals(ItemKind.PISTOL, self.item());
        assertEquals(GameConstants.PISTOL_MAGAZINE, self.ammo());
        assertEquals(ActionA.FIRE, self.actionA());
    }

    @Test
    void ammoIsNullForItemsThatDoNotUseIt() {
        Room room = room();
        Player viewer = at("viewer", OPEN);
        viewer.hold(new Item("i-2", ItemKind.MEDKIT, 0));
        viewer.takeDamage(10);
        room.add(viewer);

        Snapshot.Self self = filter.forViewer(room, viewer, 100).self();

        assertNull(self.ammo());
        assertEquals(ActionA.HEAL, self.actionA());
    }

    @Test
    void aMedkitIsNotOfferedAtFullHealth() {
        Room room = room();
        Player viewer = at("viewer", OPEN);
        viewer.hold(new Item("i-2", ItemKind.MEDKIT, 0));
        room.add(viewer);

        assertNull(filter.forViewer(room, viewer, 100).self().actionA(),
                "healing nothing would only throw the medkit away");
    }

    @Test
    void aPistolOnItsLastRoundStillOffersFire() {
        Room room = room();
        Player viewer = at("viewer", OPEN);
        viewer.hold(new Item("i-3", ItemKind.PISTOL, 1));
        room.add(viewer);

        Snapshot.Self self = filter.forViewer(room, viewer, 100).self();

        assertEquals(1, self.ammo());
        assertEquals(ActionA.FIRE, self.actionA());
    }

    @Test
    void cabinetOccupantsAreOmittedEntirely() {
        Room room = room();
        Player viewer = at("viewer", OPEN);
        Player hider = at("hider", room.map().cabinets().getFirst());
        hider.setInCabinet(true);
        room.add(viewer);
        room.add(hider);

        Snapshot snapshot = filter.forViewer(room, viewer, 100);

        assertTrue(snapshot.players().isEmpty(),
                "a hidden player must not appear at all, not even as a marker");
    }

    @Test
    void bushOccupantsAreOmittedFromOutsideButSeeOut() {
        Room room = room();
        Player outside = at("outside", OPEN);
        Player hidden = at("hidden", BUSH_A);
        room.add(outside);
        room.add(hidden);

        assertTrue(filter.forViewer(room, outside, 100).players().isEmpty(),
                "outside must not see into the bush");
        assertEquals(1, filter.forViewer(room, hidden, 100).players().size(),
                "inside the bush must still see the open field");
    }

    @Test
    void separateBushesHideFromEachOther() {
        Room room = room();
        Player inA = at("inA", BUSH_A);
        Player inB = at("inB", BUSH_B);
        room.add(inA);
        room.add(inB);

        assertTrue(filter.forViewer(room, inA, 100).players().isEmpty());
        assertTrue(filter.forViewer(room, inB, 100).players().isEmpty());
    }

    @Test
    void snapshotCarriesTerrainAndFloorItems() {
        Room room = room();
        Player viewer = at("viewer", OPEN);
        room.add(viewer);
        Pos spawn = room.map().itemSpawns().getFirst();
        room.placeItem(spawn, new Item("i-9", ItemKind.KNIFE, 0));

        Snapshot snapshot = filter.forViewer(room, viewer, 42);

        assertEquals(Snapshot.TYPE, snapshot.type());
        assertEquals(42, snapshot.tick());
        assertEquals("room-1", snapshot.roomId());
        assertEquals(15, snapshot.terrain().size());
        assertEquals(15, snapshot.terrain().getFirst().length());
        assertEquals(1, snapshot.items().size());
        assertEquals(spawn.x(), snapshot.items().getFirst().x());
    }

    @Test
    void aFloorItemSaysThatSomethingLiesThereNeverWhat() {
        List<String> fields = Arrays.stream(Snapshot.FloorItem.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertEquals(List.of("id", "x", "y"), fields,
                "an item's kind is learned by looting it; adding it here publishes it");
    }

    @Test
    void terrainDoesNotLeakItemSpawnMarkers() {
        Room room = room();
        for (String row : room.map().terrainRows()) {
            assertFalse(row.indexOf('*') >= 0,
                    "spawns travel as items, not as terrain: " + row);
        }
    }
}
