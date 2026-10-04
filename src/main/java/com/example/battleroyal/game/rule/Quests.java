package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.ItemKind;

import java.util.List;
import java.util.Map;

/**
 * The trader's errands (V2.2): three kinds, each a ladder from easy to hard, and one
 * errand of each kind under way at once, so a hard one never holds up the rest. The
 * harder the errand, the bigger the reward. Progress belongs to a season and is wiped
 * with it. Starting values, to be tuned by play (docs/GAME_RULES.md §8).
 */
public final class Quests {

    private Quests() {
    }

    /** What kind of errand: a separate ladder each. */
    public enum Category {
        /** Hand things over from the stash. */
        DELIVERY,
        /** Stand on places marked on the map and get out alive (the errand compass). */
        VISIT,
        /** Bring down outpost soldiers. */
        SOLDIER
    }

    public enum Difficulty { EASY, NORMAL, HARD }

    /**
     * @param deliver           what to hand over, by kind and count (DELIVERY)
     * @param visits            marks to stand on in one trip, then get out (VISIT)
     * @param visitDistance     how many doors from the start the marks lie (VISIT)
     * @param soldiers          soldiers to bring down (SOLDIER)
     * @param soldiersInOneTrip true when they must all fall in one trip; else they add up
     * @param reward            an item that goes to the stash on completion, or null
     */
    public record Quest(String title, Category category, Difficulty difficulty,
                        Map<ItemKind, Integer> deliver, int visits, int visitDistance,
                        int soldiers, boolean soldiersInOneTrip, int money, ItemKind reward,
                        int rewardAmmo) {
    }

    public static final Map<Category, List<Quest>> LADDERS = Map.of(
            Category.DELIVERY, List.of(
                    deliver("첫 납품", Difficulty.EASY, Map.of(ItemKind.SPOON, 1), 30, null),
                    deliver("소풍 준비물", Difficulty.EASY, Map.of(ItemKind.CUP, 2), 60, null),
                    deliver("음악 시간", Difficulty.NORMAL, Map.of(ItemKind.RECORDER, 2), 100,
                            ItemKind.SMALL_BAG),
                    deliver("출석 확인", Difficulty.NORMAL,
                            Map.of(ItemKind.REGISTER, 2, ItemKind.DOLL, 1), 150, null),
                    deliver("무기 회수", Difficulty.HARD,
                            Map.of(ItemKind.KNIFE, 1, ItemKind.BAT, 1), 300, null),
                    deliver("마지막 납품", Difficulty.HARD, Map.of(ItemKind.PISTOL, 1), 600,
                            ItemKind.BIG_BAG)),
            Category.VISIT, List.of(
                    visit("정찰", Difficulty.EASY, 1, 2, 80, null),
                    visit("수색", Difficulty.NORMAL, 2, 2, 150, ItemKind.MEDKIT),
                    visit("위험 지역", Difficulty.HARD, 1, 4, 300, ItemKind.SMALL_BAG)),
            Category.SOLDIER, List.of(
                    soldiers("첫 교전", Difficulty.NORMAL, 1, false, 200, ItemKind.ROUNDS,
                            GameConstants.PISTOL_MAGAZINE),
                    soldiers("소탕", Difficulty.HARD, 3, false, 450, ItemKind.PISTOL, 0),
                    soldiers("초소 함락", Difficulty.HARD, 2, true, 800, ItemKind.BIG_BAG, 0)));

    /** The errand of this kind at this step, or null once the ladder is climbed. */
    public static Quest at(Category category, int step) {
        List<Quest> ladder = LADDERS.get(category);
        return step >= 0 && step < ladder.size() ? ladder.get(step) : null;
    }

    private static Quest deliver(String title, Difficulty difficulty, Map<ItemKind, Integer> what,
                                 int money, ItemKind reward) {
        return new Quest(title, Category.DELIVERY, difficulty, what, 0, 0, 0, false, money,
                reward, 0);
    }

    /**
     * Places the trader marked on the map: each trip out with this errand puts that many
     * marks that many doors from the start, for the taker alone, and the compass points
     * at them. Standing on every one and getting out alive completes it.
     */
    private static Quest visit(String title, Difficulty difficulty, int marks, int doors,
                               int money, ItemKind reward) {
        return new Quest(title, Category.VISIT, difficulty, Map.of(), marks, doors, 0, false,
                money, reward, 0);
    }

    /** Soldiers count when the trip ends, death included, like any kill. */
    private static Quest soldiers(String title, Difficulty difficulty, int count,
                                  boolean oneTrip, int money, ItemKind reward, int ammo) {
        return new Quest(title, Category.SOLDIER, difficulty, Map.of(), 0, 0, count, oneTrip,
                money, reward, ammo);
    }
}
