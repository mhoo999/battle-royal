package com.example.battleroyal.ws;

import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.rule.ActionResolver;
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
        for (Map.Entry<Pos, Item> entry : room.floorItems().entrySet()) {
            Pos pos = entry.getKey();
            Item item = entry.getValue();
            items.add(new Snapshot.FloorItem(item.id(), pos.x(), pos.y(), item.kind()));
        }

        return Snapshot.of(tick, room.id(), room.map().terrainRows(),
                self(room, viewer, tick), others, items);
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
                viewer.concealment(room.map()),
                viewer.score(),
                viewer.kills(),
                ActionResolver.actionA(viewer),
                ActionResolver.actionB(room, viewer));
    }
}
