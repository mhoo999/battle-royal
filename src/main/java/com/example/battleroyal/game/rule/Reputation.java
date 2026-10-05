package com.example.battleroyal.game.rule;

import java.util.List;

/**
 * Standing with the trader (V2.3): every errand done, ladder or daily, adds to it, the
 * harder the more, and enough of it raises the trader's level. What a level unlocks is
 * not decided yet; this is the counter and the levels. Belongs to a season and is wiped
 * with it. Starting values (docs/GAME_RULES.md §8).
 */
public final class Reputation {

    private Reputation() {
    }

    /** Standing needed for each level, from level 1; the first is always 0. */
    public static final List<Integer> LEVEL_AT = List.of(0, 10, 25, 50);

    /** What finishing an errand of this difficulty adds. */
    public static int forErrand(Quests.Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 1;
            case NORMAL -> 2;
            case HARD -> 4;
        };
    }

    /** The level this much standing has reached, 1-based. */
    public static int level(int standing) {
        int level = 0;
        while (level < LEVEL_AT.size() && standing >= LEVEL_AT.get(level)) {
            level++;
        }
        return Math.max(level, 1);
    }

    /** Standing the next level needs, or null at the top level. */
    public static Integer nextAt(int standing) {
        int level = level(standing);
        return level < LEVEL_AT.size() ? LEVEL_AT.get(level) : null;
    }
}
