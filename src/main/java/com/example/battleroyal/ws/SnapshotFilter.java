package com.example.battleroyal.ws;

import com.example.battleroyal.game.core.Guard;
import com.example.battleroyal.game.core.Crate;
import com.example.battleroyal.game.core.Exit;
import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.rule.ActionResolver;
import com.example.battleroyal.game.rule.GameConstants;
import com.example.battleroyal.game.rule.RoomSimulator;
import com.example.battleroyal.game.rule.VisibilityRules;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the snapshot one viewer is allowed to receive.
 *
 * <p>This is the only place a {@link Player} becomes wire data. It asks
 * {@link VisibilityRules} who is visible and copies nothing but position and facing for
 * anyone other than the viewer.
 *
 * <p>Adding a field to {@link Snapshot.Other} means deciding to publish it to every
 * opponent. Do not do that without re-reading docs/NETWORK_PROTOCOL.md.
 */
@Component
public class SnapshotFilter {

    public Snapshot forViewer(Room room, Player viewer, long tick) {
        List<Snapshot.Other> others = new ArrayList<>();
        for (Player target : room.players()) {
            if (target.id().equals(viewer.id())) {
                continue;
            }
            if (!VisibilityRules.canSee(room.map(), viewer, target)) {
                continue;
            }
            others.add(new Snapshot.Other(
                    target.id(),
                    target.pos().x(),
                    target.pos().y(),
                    target.facing(),
                    target.alive()));
        }

        List<Snapshot.FloorItem> items = new ArrayList<>();
        for (Map.Entry<Pos, Crate> entry : room.crates().entrySet()) {
            Pos pos = entry.getKey();
            items.add(new Snapshot.FloorItem(entry.getValue().id(), pos.x(), pos.y()));
        }

        List<Snapshot.GuardView> guards = room.guards().stream()
                .filter(Guard::alive)
                .map(guard -> new Snapshot.GuardView(guard.pos().x(), guard.pos().y(),
                        guard.facing()))
                .toList();

        return Snapshot.of(tick, room.id(), room.map().terrainRows(),
                self(room, viewer, tick), others, items, guards);
    }

    private Snapshot.Self self(Room room, Player viewer, long tick) {
        Item held = viewer.heldItem();
        return new Snapshot.Self(
                viewer.id(),
                viewer.nickname(),
                viewer.pos().x(),
                viewer.pos().y(),
                viewer.facing(),
                viewer.hp(),
                held == null ? null : held.kind(),
                held != null && held.kind().usesAmmo() ? held.ammo() : null,
                viewer.inventory().stream().map(SnapshotFilter::slot).toList(),
                viewer.bag() == null ? null : slot(viewer.bag()),
                viewer.equipped(),
                crate(room, viewer),
                viewer.concealment(room.map()),
                viewer.looting()
                        ? (int) (viewer.lootDoneTick() - tick) * GameConstants.TICK_MS
                        : null,
                viewer.exits().stream().map(exit -> bearing(room, exit)).toList(),
                viewer.extracting()
                        ? (int) (viewer.extractDoneTick() - tick) * GameConstants.TICK_MS
                        : null,
                viewer.score(),
                viewer.kills(),
                ActionResolver.actionA(viewer),
                ActionResolver.actionB(room, viewer));
    }

    /** What is in the crate this viewer opened, or null when none is open. */
    private static List<Snapshot.Slot> crate(Room room, Player viewer) {
        Crate open = RoomSimulator.openCrate(room, viewer);
        return open == null ? null : open.items().stream().map(SnapshotFilter::slot).toList();
    }

    private static Snapshot.Bearing bearing(Room room, Exit exit) {
        boolean here = exit.in(room);
        return new Snapshot.Bearing(exit.dx(), exit.dy(),
                here ? exit.at().x() : null, here ? exit.at().y() : null);
    }

    /** Null in, null out: an empty slot stays a gap in the list. */
    private static Snapshot.Slot slot(Item item) {
        if (item == null) {
            return null;
        }
        return new Snapshot.Slot(item.kind(), item.kind().usesAmmo() ? item.ammo() : null);
    }
}
