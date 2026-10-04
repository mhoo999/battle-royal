package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.ItemKind;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The trader's daily errands (V2.2): three a day per account, new at midnight in Seoul,
 * apart from the ladders, so there is always something doable even when every ladder
 * has reached a hard step. Two easy and one normal. What a day holds is worked out from
 * the account and the date, so nothing about it needs storing but what has been done.
 * Starting values (docs/GAME_RULES.md §8).
 */
public final class DailyQuests {

    private DailyQuests() {
    }

    /** What a daily asks: hand things over, get out alive, or bring soldiers down. */
    public enum Goal { DELIVERY, EXTRACT, SOLDIER }

    /**
     * @param slot    0, 1 or 2: which of the day's three
     * @param deliver what to hand over (DELIVERY)
     * @param count   extractions or soldiers to make today (EXTRACT, SOLDIER)
     */
    public record Daily(int slot, String title, Quests.Difficulty difficulty, Goal goal,
                        Map<ItemKind, Integer> deliver, int count, int money) {
    }

    public static final int PER_DAY = 3;

    static final List<ItemKind> JUNK = List.of(ItemKind.SPOON, ItemKind.CUP, ItemKind.DOLL,
            ItemKind.RECORDER, ItemKind.REGISTER, ItemKind.PAN);

    /** The day's three for this account; the same every time it is asked that day. */
    public static List<Daily> of(long accountId, LocalDate day) {
        Random random = new Random(accountId * 1_000_003L + day.toEpochDay());
        ItemKind junk = JUNK.get(random.nextInt(JUNK.size()));
        Daily first = new Daily(0, "잡동사니", Quests.Difficulty.EASY, Goal.DELIVERY,
                Map.of(junk, 1 + random.nextInt(2)), 0, 40);
        Daily second = new Daily(1, "무사 귀환", Quests.Difficulty.EASY, Goal.EXTRACT,
                Map.of(), 1, 50);
        Daily third = switch (random.nextInt(3)) {
            case 0 -> new Daily(2, "구급약", Quests.Difficulty.NORMAL, Goal.DELIVERY,
                    Map.of(ItemKind.MEDKIT, 1), 0, 120);
            case 1 -> new Daily(2, "탄약 보급", Quests.Difficulty.NORMAL, Goal.DELIVERY,
                    Map.of(random.nextBoolean() ? ItemKind.ROUNDS : ItemKind.BOLTS, 1), 0, 120);
            default -> new Daily(2, "초소 견제", Quests.Difficulty.NORMAL, Goal.SOLDIER,
                    Map.of(), 1, 120);
        };
        return List.of(first, second, third);
    }
}
