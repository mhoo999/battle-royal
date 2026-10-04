package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.ItemKind;

import java.util.List;
import java.util.Map;

/**
 * The trader's errands (V2.2): one at a time, in order. Each asks for things handed over
 * from the stash, or for kills, and pays money and sometimes an item. Progress belongs
 * to a season and is wiped with it. Starting values, to be tuned by play
 * (docs/GAME_RULES.md §8).
 */
public final class Quests {

    private Quests() {
    }

    /**
     * @param deliver what to hand over, by kind and count; empty for a kill errand
     * @param kills   kills to make since the errand was taken; 0 for a delivery
     * @param reward  an item that goes to the stash on completion, or null
     */
    public record Quest(String title, Map<ItemKind, Integer> deliver, int kills, int money,
                        ItemKind reward, int rewardAmmo) {

        public boolean isDelivery() {
            return !deliver.isEmpty();
        }
    }

    public static final List<Quest> ALL = List.of(
            deliver("첫 납품", Map.of(ItemKind.SPOON, 1), 30, null),
            deliver("소풍 준비물", Map.of(ItemKind.CUP, 2), 60, null),
            kill("첫 피", 1, 100, null, 0),
            deliver("음악 시간", Map.of(ItemKind.RECORDER, 2), 80, ItemKind.SMALL_BAG),
            deliver("응급 상자", Map.of(ItemKind.MEDKIT, 1), 120, null),
            kill("사냥", 3, 200, ItemKind.ROUNDS, GameConstants.PISTOL_MAGAZINE),
            deliver("출석 확인", Map.of(ItemKind.REGISTER, 2, ItemKind.DOLL, 1), 150, null),
            deliver("무기 회수", Map.of(ItemKind.KNIFE, 1, ItemKind.BAT, 1), 250, null),
            kill("학살", 5, 400, ItemKind.PISTOL, 0),
            deliver("마지막 의뢰", Map.of(ItemKind.PISTOL, 1), 600, ItemKind.BIG_BAG));

    /** The errand at this step, or null once every one is done. */
    public static Quest at(int step) {
        return step >= 0 && step < ALL.size() ? ALL.get(step) : null;
    }

    private static Quest deliver(String title, Map<ItemKind, Integer> what, int money,
                                 ItemKind reward) {
        return new Quest(title, what, 0, money, reward, 0);
    }

    private static Quest kill(String title, int kills, int money, ItemKind reward, int ammo) {
        return new Quest(title, Map.of(), kills, money, reward, ammo);
    }
}
