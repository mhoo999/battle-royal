package com.example.battleroyal.ws;

import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.rule.GameConstants;

import java.util.List;

/**
 * Wire shapes for events. Like {@link Snapshot}, the records are the contract: each
 * one has only the fields its audience is allowed to see.
 *
 * <p>See docs/NETWORK_PROTOCOL.md.
 */
public final class Outbound {

    private Outbound() {
    }

    /** {@code {"type":"EVENT","event":"SHOT","path":[[x,y],...]}} */
    public record Shot(String type, String event, List<int[]> path) {
    }

    /** {@code {"type":"EVENT","event":"SWING","from":[x,y],"to":[x,y]}} */
    public record Swing(String type, String event, int[] from, int[] to) {
    }

    /** {@code {"type":"EVENT","event":"FELL","at":[x,y]}}. Where, never who. */
    public record Fell(String type, String event, int[] at) {
    }

    /** {@code {"type":"EVENT","event":"HIT"}}. No target, no damage, no outcome. */
    public record Hit(String type, String event) {
    }

    /**
     * {@code {"type":"YOU_DIED","score":..,"kills":..,"survivedSeconds":..,
     * "killer":"kang","weapon":"PISTOL"}}. Only ever sent to the player who died.
     */
    /** @param byGuard shot by an outpost guard (V2.2): no killer, no weapon */
    public record YouDied(String type, int score, int kills, long survivedSeconds,
                          String killer, ItemKind weapon, boolean byGuard) {
    }

    /**
     * {@code {"type":"EXTRACTED","score":..,"kills":..,"survivedSeconds":..,
     * "carried":[{"kind":"PISTOL","ammo":4}]}}. Only ever sent to the player who got out.
     */
    public record Extracted(String type, int score, int kills, long survivedSeconds,
                            List<Snapshot.Slot> carried) {
    }

    /** {@code {"type":"SEASON_OVER"}}: the season ended and took the player off the island. */
    public record SeasonOver(String type) {
    }

    public static SeasonOver seasonOver() {
        return new SeasonOver("SEASON_OVER");
    }

    public static Extracted extracted(GameEvent.Extracted out) {
        return new Extracted("EXTRACTED", out.score(), out.kills(),
                out.survivedTicks() / GameConstants.TICKS_PER_SECOND,
                out.carried().stream()
                        .map(item -> new Snapshot.Slot(item.kind(),
                                item.kind().usesAmmo() ? item.ammo() : null))
                        .toList());
    }

    public static Shot shot(GameEvent.Shot shot) {
        List<int[]> path = shot.path().stream()
                .map(Outbound::coords)
                .toList();
        return new Shot("EVENT", "SHOT", path);
    }

    public static Swing swing(GameEvent.Swing swing) {
        return new Swing("EVENT", "SWING", coords(swing.from()), coords(swing.to()));
    }

    public static Fell fell(GameEvent.Fell fell) {
        return new Fell("EVENT", "FELL", coords(fell.at()));
    }

    public static Hit hit() {
        return new Hit("EVENT", "HIT");
    }

    public static YouDied youDied(GameEvent.Died died) {
        return new YouDied("YOU_DIED", died.score(), died.kills(),
                died.survivedTicks() / GameConstants.TICKS_PER_SECOND,
                died.killerNickname(), died.weapon(), died.byGuard());
    }

    private static int[] coords(Pos pos) {
        return new int[] {pos.x(), pos.y()};
    }
}
