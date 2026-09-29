package com.example.battleroyal.game.rule;

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
     * Time to take an item off the floor: 500ms. Moving off the tile during it starts
     * over, so grabbing a weapon under fire is a commitment rather than a free action.
     */
    public static final int LOOT_TICKS = 10;

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
    /** Rounds a pistol comes with. There is no reload: the last shot uses it up. */
    public static final int PISTOL_MAGAZINE = 6;

    /** Shorter, slower and scarcer than the pistol, but three bolts make a kill. */
    public static final int CROSSBOW_RANGE = 7;
    public static final int CROSSBOW_DAMAGE = 40;
    public static final int CROSSBOW_COOLDOWN_TICKS = 30;
    /** Bolts a crossbow comes with; like the pistol, the last one uses it up. */
    public static final int CROSSBOW_BOLTS = 3;

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
     * What a new room rolls, out of 100: one item or nothing. A little over half the
     * rooms hold something, mostly junk; a real weapon (knife, bat, crossbow, pistol)
     * turns up about one room in eight, a pistol one in a hundred. Starting values,
     * not playtested.
     */
    public static final int LOOT_WEIGHT_NOTHING = 45;
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
     * <p>The first-visit set alone does not close the exploit. Because empty rooms
     * outside the halo are discarded, a three-room cycle keeps generating genuinely
     * new rooms, which would pay out roughly every five seconds. Capping the rate
     * makes cycle length irrelevant.
     */
    public static final int ROOM_SCORE_RATE_CAP_TICKS = 600;

    // --- World ------------------------------------------------------------

    /**
     * Rooms allowed to exist per player online.
     *
     * <p>This is what makes people find each other. With an unbounded world, doors
     * always led somewhere new and locating another player was a two-dimensional random
     * walk: a third of simulated pairs never met at all. Capping the world forces doors
     * to fold back into it, so wandering converges on company instead of diverging from
     * it. Two rooms each leaves room to explore without the world going quiet.
     */
    public static final int ROOMS_PER_PLAYER = 2;

    /**
     * Floor on the world size regardless of population.
     *
     * <p>Without it a lone player gets a two-room world and spends the whole session
     * bouncing between the same pair, which reads as the game being broken rather than
     * as the world being small.
     */
    public static final int MIN_ROOMS = 4;

    /**
     * Chance that a newly opened door leads straight to someone, out of 100.
     *
     * <p>The room cap alone already guarantees people meet, but it takes its time about
     * it. Measured over sixty seeded pairs, each wandering at random until they met:
     *
     * <pre>
     *   bias   average doors   worst
     *    25%         3           16
     *    30%         3           16
     *    40%         2           15
     *    50%         2           13
     * </pre>
     *
     * <p>Thirty sits on the design target of about three doors. Pushing higher buys
     * little on the tail, which comes from unlucky routes rather than from the bias,
     * and costs the gamble of opening a door at all.
     */
    public static final int ENCOUNTER_BIAS_PERCENT = 30;

    // --- Session ----------------------------------------------------------

    public static final int NICKNAME_MIN_LENGTH = 1;
    public static final int NICKNAME_MAX_LENGTH = 12;

    public static int seconds(int ticks) {
        return ticks / TICKS_PER_SECOND;
    }
}
