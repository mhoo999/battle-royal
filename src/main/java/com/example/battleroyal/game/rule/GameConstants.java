package com.example.battleroyal.game.rule;

import java.util.List;

/**
 * Every tunable number in the game, in one place.
 *
 * <p>All durations are in ticks, never milliseconds. The simulation advances in
 * whole ticks, so storing ms anywhere invites rounding drift and non-deterministic
 * tests. {@link #TICK_MS} exists only to drive the loop and to document the mapping.
 *
 * <p>Values are specified in docs/GAME_RULES.md. Change them here, not inline.
 */
public final class GameConstants {

    private GameConstants() {
    }

    // --- Time -------------------------------------------------------------

    /** Wall-clock length of one tick. 20Hz. */
    public static final int TICK_MS = 50;

    public static final int TICKS_PER_SECOND = 1000 / TICK_MS;

    /**
     * Ticks between accepted MOVE commands: 150ms.
     *
     * <p>Still comfortably above mobile RTT, since there is no client-side prediction
     * and a cooldown shorter than the round trip would feel sticky. Playtesting at
     * 200ms found crossing a 13 tile room took roughly three seconds, which read as
     * sluggish rather than deliberate.
     *
     * <p>The other half of that fix is {@link #MOVE_BUFFER_TICKS}: without it, a held
     * direction that lands mid-cooldown is discarded and the player skips a beat.
     */
    public static final int MOVE_COOLDOWN_TICKS = 3;

    /**
     * How long a move that arrived during the cooldown is remembered: 200ms.
     *
     * <p>One slot only. Buffering more would let a player queue a run of tiles and
     * keep moving after they meant to stop. Buffering nothing makes held input stutter
     * whenever the client and the cooldown drift out of phase.
     */
    public static final int MOVE_BUFFER_TICKS = 4;

    /** Ticks between cabinet enter/exit actions: 400ms. Blocks flicker abuse. */
    public static final int CABINET_TOGGLE_COOLDOWN_TICKS = 8;

    /**
     * Time to open a crate: 500ms with B held. Moving off the tile during it starts
     * over, so looting under fire is a commitment rather than a free action.
     */
    public static final int LOOT_TICKS = 10;

    /**
     * Time to get out through an exit: 5s with B held (D7). Moving, letting go or being
     * hit starts it over, which makes the way out the most dangerous moment of a trip.
     */
    public static final int EXTRACT_TICKS = 100;

    /**
     * How many rooms away, counted in doors, each of a player's exits lies from the room
     * they start in (D8): one near, one farther. A world too small for a distance gets
     * its farthest room instead.
     */
    public static final List<Integer> EXIT_DISTANCES = List.of(2, 3);

    /**
     * Most items a crate holds. A death leaves at most a full inventory, and putting
     * things back can add to it; past this the crate refuses.
     */
    public static final int CRATE_CAPACITY = 6;

    /**
     * Slots in a hideout stash at each size (V2 D10): the plain box, the big box, the
     * fine box. Bought in order from the 창고 page and kept for good.
     */
    public static final List<Integer> STASH_SIZES = List.of(10, 20, 40);

    /** What each next stash size costs: index 0 buys the big box, 1 the fine box. */
    public static final List<Integer> STASH_UPGRADE_PRICES = List.of(500, 2_000);

    /** Slots in a stash nobody has upgraded yet. */
    public static final int STASH_CAPACITY = STASH_SIZES.getFirst();

    /** Grace period after a socket drops before the player is killed: 15s. */
    public static final int DISCONNECT_GRACE_TICKS = 300;

    // --- Combat -----------------------------------------------------------

    public static final int MAX_HP = 100;

    /**
     * Bare hands: twenty blows to kill. Enough to finish someone off or to fight back
     * rather than run, never a plan.
     */
    public static final int FIST_RANGE = 1;
    public static final int FIST_DAMAGE = 5;
    public static final int FIST_COOLDOWN_TICKS = 10;

    public static final int KNIFE_RANGE = 1;
    public static final int KNIFE_DAMAGE = 34;
    public static final int KNIFE_COOLDOWN_TICKS = 10;

    /** Slow and heavy against the knife's quick jabs: three blows either way. */
    public static final int BAT_RANGE = 1;
    public static final int BAT_DAMAGE = 45;
    public static final int BAT_COOLDOWN_TICKS = 20;

    public static final int PISTOL_RANGE = 10;
    public static final int PISTOL_DAMAGE = 25;
    public static final int PISTOL_COOLDOWN_TICKS = 8;
    /** Rounds a pistol holds, and a bundle of rounds carries. Empty, it stays in hand. */
    public static final int PISTOL_MAGAZINE = 6;

    /** Shorter, slower and scarcer than the pistol, but three bolts make a kill. */
    public static final int CROSSBOW_RANGE = 7;
    public static final int CROSSBOW_DAMAGE = 40;
    public static final int CROSSBOW_COOLDOWN_TICKS = 30;
    /** Bolts a crossbow holds, and a bundle of bolts carries. */
    public static final int CROSSBOW_BOLTS = 3;

    /**
     * Loading an empty gun from a bundle in the inventory fills it at once, then nothing
     * can be done with A for 1.5s. Being hit does not stop it: it is already done.
     */
    public static final int RELOAD_TICKS = 30;

    public static final int MEDKIT_HEAL = 50;
    public static final int MEDKIT_COOLDOWN_TICKS = 20;

    /** Junk that happens to hurt: seven blows to kill, against the knife's three. */
    public static final int PAN_RANGE = 1;
    public static final int PAN_DAMAGE = 15;
    public static final int PAN_COOLDOWN_TICKS = 10;

    /**
     * The rest of the junk swings like a fist, a point or three harder at best. Never
     * below a fist: there is no way to drop an item, so junk that hit softer would
     * leave you worse off than empty-handed.
     */
    public static final int JUNK_RANGE = 1;
    public static final int JUNK_COOLDOWN_TICKS = 10;
    public static final int SPOON_DAMAGE = FIST_DAMAGE;
    public static final int DOLL_DAMAGE = FIST_DAMAGE;
    public static final int CUP_DAMAGE = FIST_DAMAGE + 1;
    public static final int RECORDER_DAMAGE = FIST_DAMAGE + 2;
    public static final int REGISTER_DAMAGE = FIST_DAMAGE + 3;

    // --- Loot ---------------------------------------------------------------

    /**
     * What a new room rolls, out of 100: one item or nothing. Six rooms in ten hold
     * something, mostly junk; a real weapon (knife, bat, crossbow, pistol)
     * turns up about one room in eight, a pistol one in a hundred. Starting values,
     * not playtested.
     */
    public static final int LOOT_WEIGHT_NOTHING = 39;
    public static final int LOOT_WEIGHT_SPOON = 6;
    public static final int LOOT_WEIGHT_DOLL = 6;
    public static final int LOOT_WEIGHT_CUP = 6;
    public static final int LOOT_WEIGHT_RECORDER = 6;
    public static final int LOOT_WEIGHT_REGISTER = 6;
    public static final int LOOT_WEIGHT_PAN = 5;
    public static final int LOOT_WEIGHT_MEDKIT = 8;
    public static final int LOOT_WEIGHT_KNIFE = 5;
    public static final int LOOT_WEIGHT_BAT = 4;
    public static final int LOOT_WEIGHT_CROSSBOW = 2;
    public static final int LOOT_WEIGHT_PISTOL = 1;
    /** Ammunition, a full magazine's worth, for a gun someone already has. */
    public static final int LOOT_WEIGHT_ROUNDS = 3;
    public static final int LOOT_WEIGHT_BOLTS = 3;

    /**
     * A room rolls again after 30s in all with nobody in it and nothing on its floor.
     * Anyone inside pauses the clock, so camping never restocks a room; leaving does.
     * A pause, not a reset: resetting on every visit meant a player touring a small
     * world kept every room's clock at zero and never found anything.
     */
    public static final int LOOT_REGROW_TICKS = 600;

    // --- Score ------------------------------------------------------------

    public static final int SCORE_ROOM_ENTER = 10;
    public static final int SCORE_ITEM_PICKUP = 5;
    public static final int SCORE_HIT = 20;
    public static final int SCORE_KILL = 100;
    public static final int SCORE_SURVIVAL = 1;

    /** Survival score is granted once per 10s, and never while in a cabinet. */
    public static final int SURVIVAL_INTERVAL_TICKS = 200;

    /**
     * Minimum ticks between two room-entry score awards: 30s.
     *
     * <p>The first-visit set alone does not close the exploit. Every room a resize adds
     * is genuinely new, and a fresh world of nine rooms would otherwise pay out its
     * whole tour in under a minute. Capping the rate keeps exploring worth a trickle,
     * not a jackpot.
     */
    public static final int ROOM_SCORE_RATE_CAP_TICKS = 600;

    // --- World ------------------------------------------------------------

    /**
     * The world is never narrower than this many rooms a side.
     *
     * <p>Three is the least for which east and west lead to different rooms. Below it a
     * lone player bounces between the same pair, which reads as the game being broken.
     */
    public static final int MIN_WORLD_SIDE = 3;

    /**
     * World rooms per player other than yourself; see {@link WorldSize}.
     *
     * <p>The design target is meeting somebody after four or five doors, with time to
     * loot on the way. Simulated with everyone wandering at random, one door at a time
     * in no fixed order, doors one player takes before sharing a room (average / 90th
     * percentile):
     *
     * <pre>
     *   players   grid   doors
     *      2      3x3    4.4 / 10
     *      3      4x4    4.8 / 11
     *      4      5x5    5.3 / 13
     *      5      6x5    4.8 / 11
     *      6      6x6    4.8 / 11
     * </pre>
     *
     * <p>Players who never walk straight back meet a little sooner (3.9 for two on 3x3).
     * An unbounded grid was tried before any of this and failed: a third of simulated
     * pairs never met at all. What makes a grid work is that it wraps and is sized to
     * the population.
     */
    public static final int ROOMS_PER_OTHER_PLAYER = 7;

    // --- Session ----------------------------------------------------------

    public static final int NICKNAME_MIN_LENGTH = 1;
    public static final int NICKNAME_MAX_LENGTH = 12;

    /**
     * A nickname starting with this plays as normal but is never saved as a result.
     *
     * <p>For test runs against the live server, which otherwise filled the ranking with
     * {@code alpha} and {@code bravo}. Anyone may use it; it only means practice.
     */
    public static final String UNRANKED_PREFIX = "~";

    public static int seconds(int ticks) {
        return ticks / TICKS_PER_SECOND;
    }
}
