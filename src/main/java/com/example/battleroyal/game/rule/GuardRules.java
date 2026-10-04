package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.Crate;
import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.core.Guard;
import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.core.Room;

import java.util.List;

/**
 * Outpost guards (V2.2 PvE): they stand on their posts, turn a quarter clockwise every
 * few seconds, and shoot down the line they face. Someone must stay in that line for a
 * moment before a guard fires, and a guard sees by the same rules as a player: a bush
 * hides you from it, a cabinet hides you and stops its shot.
 *
 * <p>A guard brought down drops a bundle of rounds and stays down until the outpost has
 * spent long enough empty. Bringing one down is worth no score and no kill: guards are
 * not players, and the ranking and the trader's kill errands are about players.
 */
public final class GuardRules {

    private GuardRules() {
    }

    static void tick(Room room, long nowTick) {
        if (room.guards().isEmpty()) {
            return;
        }
        if (room.isEmpty()) {
            standAgainWhenDue(room, nowTick);
            return;
        }
        for (Guard guard : room.guards()) {
            if (guard.alive()) {
                watch(room, guard, nowTick);
            }
        }
    }

    /** Clocked like loot regrowth: a visit pauses it rather than starting it over. */
    private static void standAgainWhenDue(Room room, long nowTick) {
        if (room.guards().stream().allMatch(Guard::alive)) {
            return;
        }
        if (room.advanceGuardRespawn() < GameConstants.GUARD_RESPAWN_TICKS) {
            return;
        }
        room.resetGuardRespawn();
        for (Guard guard : room.guards()) {
            if (!guard.alive()) {
                guard.revive(nowTick);
            }
        }
        room.markDirty();
    }

    private static void watch(Room room, Guard guard, long nowTick) {
        if (nowTick >= guard.nextTurnTick()) {
            if (guard.nextTurnTick() > 0) {
                guard.face(guard.facing().clockwise());
                guard.lostSight();
                room.markDirty();
            }
            guard.setNextTurnTick(nowTick + GameConstants.GUARD_TURN_TICKS);
        }

        CombatRules.Trace line = CombatRules.trace(room, guard.pos(), guard.facing(),
                GameConstants.GUARD_RANGE);
        Player target = line.victim();
        boolean seen = target != null && target.active()
                && VisibilityRules.visibleAt(room.map(), guard.pos(), target.pos(),
                        target.inCabinet());
        if (!seen) {
            guard.lostSight();
            return;
        }
        guard.sawSomeone();
        if (guard.seenTicks() < GameConstants.GUARD_REACTION_TICKS
                || nowTick < guard.nextShotTick()) {
            return;
        }
        guard.setNextShotTick(nowTick + GameConstants.GUARD_COOLDOWN_TICKS);
        room.emit(new GameEvent.Shot(line.path()));
        // A hit knocks you off the way out, as any other hit does.
        target.cancelExtract();
        if (target.takeDamage(GameConstants.GUARD_DAMAGE)) {
            RoomSimulator.dieToGuard(room, target, nowTick);
        }
        room.markDirty();
    }

    /** A player's blow or shot landing on a guard. Bringing one down counts for errands. */
    static void wound(Room room, Player attacker, Guard guard, int damage) {
        if (!guard.takeDamage(damage)) {
            return;
        }
        attacker.downedSoldier();
        List<String> witnesses = room.players().stream()
                .filter(Player::active)
                .map(Player::id)
                .toList();
        room.emit(new GameEvent.Fell(guard.pos(), witnesses));
        Pos spot = RoomSimulator.dropSpot(room, guard.pos());
        if (spot != null) {
            String crateId = room.newCrateId();
            room.placeCrate(spot, new Crate(crateId, List.of(new Item(crateId + "-rounds",
                    ItemKind.ROUNDS, GameConstants.PISTOL_MAGAZINE))));
        }
        room.markDirty();
    }
}
