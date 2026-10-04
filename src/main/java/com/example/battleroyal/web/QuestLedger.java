package com.example.battleroyal.web;

import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.rule.DailyQuests;
import com.example.battleroyal.game.rule.Quests;
import com.example.battleroyal.game.rule.Quests.Category;
import com.example.battleroyal.persistence.Account;
import com.example.battleroyal.persistence.StashItem;
import com.example.battleroyal.persistence.StashItemRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * An account's progress through the trader's errands ({@link Quests}): one ladder per
 * kind, one errand of each kind under way. Called inside the caller's transaction with
 * the account row already locked, so two completions cannot pay twice.
 */
public final class QuestLedger {

    /** One line of what a delivery asks for, and how many the stash holds now. */
    public record Need(ItemKind kind, int count, int have) {
    }

    /**
     * One kind's errand under way, as the hideout shows it.
     *
     * @param step         1-based place on its ladder
     * @param soldiersDone soldiers counted so far (a one-trip errand counts per trip, so 0)
     * @param ready        true when a delivery can be handed over now
     */
    public record QuestView(Category category, Quests.Difficulty difficulty, int step, int total,
                            String title, List<Need> deliver, int visits, int soldiers,
                            boolean soldiersInOneTrip, int soldiersDone, int money,
                            ItemKind reward, Integer rewardAmmo, boolean ready) {
    }

    /**
     * One of today's daily errands.
     *
     * @param count    extractions or soldiers asked for (0 for a delivery)
     * @param progress how many of those today's trips have made
     * @param ready    true when a delivery can be handed over now
     */
    public record DailyView(int slot, String title, Quests.Difficulty difficulty,
                            DailyQuests.Goal goal, List<Need> deliver, int count, int progress,
                            int money, boolean done, boolean ready) {
    }

    private QuestLedger() {
    }

    /** Today's three, done or not. */
    static List<DailyView> dailies(Account account, StashItemRepository items, LocalDate today) {
        account.dailyFor(today.toEpochDay());
        Map<ItemKind, Integer> held = held(account, items);
        List<DailyView> views = new ArrayList<>();
        for (DailyQuests.Daily daily : DailyQuests.of(account.id(), today)) {
            List<Need> needs = needs(daily.deliver(), held);
            boolean done = account.dailyDone(daily.slot());
            int progress = switch (daily.goal()) {
                case EXTRACT -> account.dailyExtracts();
                case SOLDIER -> account.dailySoldiers();
                case DELIVERY -> 0;
            };
            boolean ready = !done && daily.goal() == DailyQuests.Goal.DELIVERY
                    && needs.stream().allMatch(need -> need.have() >= need.count());
            views.add(new DailyView(daily.slot(), daily.title(), daily.difficulty(), daily.goal(),
                    needs, daily.count(), Math.min(progress, daily.count()), daily.money(), done,
                    ready));
        }
        return views;
    }

    /** Hands a daily delivery over from the stash and pays it. */
    static void deliverDaily(Account account, int slot, StashItemRepository items,
                             LocalDate today) {
        account.dailyFor(today.toEpochDay());
        DailyQuests.Daily daily = DailyQuests.of(account.id(), today).stream()
                .filter(candidate -> candidate.slot() == slot)
                .findFirst().orElse(null);
        if (daily == null || daily.goal() != DailyQuests.Goal.DELIVERY
                || account.dailyDone(slot)) {
            throw new HideoutService.TradeRefusedException("지금은 납품할 의뢰가 없습니다");
        }
        items.deleteAll(takeFromStash(account, daily.deliver(), items));
        account.earn(daily.money());
        account.markDailyDone(slot);
    }

    /** A trip ended today: an escape and soldiers count towards today's dailies. */
    static void dailyTrip(Account account, boolean extracted, int soldiers,
                          StashItemRepository items, LocalDate today) {
        account.dailyFor(today.toEpochDay());
        account.addDailyTrip(extracted, soldiers);
        for (DailyQuests.Daily daily : DailyQuests.of(account.id(), today)) {
            int progress = switch (daily.goal()) {
                case EXTRACT -> account.dailyExtracts();
                case SOLDIER -> account.dailySoldiers();
                case DELIVERY -> -1;
            };
            if (progress >= daily.count() && progress >= 0 && !account.dailyDone(daily.slot())) {
                account.earn(daily.money());
                account.markDailyDone(daily.slot());
            }
        }
    }

    private static List<Need> needs(Map<ItemKind, Integer> what, Map<ItemKind, Integer> held) {
        List<Need> needs = new ArrayList<>();
        for (Map.Entry<ItemKind, Integer> entry : what.entrySet()) {
            needs.add(new Need(entry.getKey(), entry.getValue(),
                    held.getOrDefault(entry.getKey(), 0)));
        }
        needs.sort((a, b) -> a.kind().compareTo(b.kind()));
        return needs;
    }

    /** The stash items a delivery takes, oldest first; refused if any are missing. */
    private static List<StashItem> takeFromStash(Account account, Map<ItemKind, Integer> what,
                                                 StashItemRepository items) {
        List<StashItem> handed = new ArrayList<>();
        List<StashItem> stash = items.findByAccountIdAndLocationOrderById(
                account.id(), StashItem.Location.STASH);
        for (Map.Entry<ItemKind, Integer> entry : what.entrySet()) {
            List<StashItem> ofKind = stash.stream()
                    .filter(item -> item.kind() == entry.getKey())
                    .limit(entry.getValue())
                    .toList();
            if (ofKind.size() < entry.getValue()) {
                throw new HideoutService.TradeRefusedException("창고에 납품할 물건이 모자랍니다");
            }
            handed.addAll(ofKind);
        }
        return handed;
    }

    /** The errand under way of each kind, in ladder order; a climbed ladder is left out. */
    static List<QuestView> view(Account account, StashItemRepository items) {
        Map<ItemKind, Integer> held = held(account, items);
        List<QuestView> views = new ArrayList<>();
        for (Category category : Category.values()) {
            Quests.Quest quest = Quests.at(category, account.questStep(category));
            if (quest == null) {
                continue;
            }
            List<Need> needs = new ArrayList<>();
            boolean ready = category == Category.DELIVERY;
            for (Map.Entry<ItemKind, Integer> entry : quest.deliver().entrySet()) {
                int have = held.getOrDefault(entry.getKey(), 0);
                needs.add(new Need(entry.getKey(), entry.getValue(), have));
                ready &= have >= entry.getValue();
            }
            needs.sort((a, b) -> a.kind().compareTo(b.kind()));
            views.add(new QuestView(category, quest.difficulty(),
                    account.questStep(category) + 1, Quests.LADDERS.get(category).size(),
                    quest.title(), needs, quest.visits(), quest.soldiers(),
                    quest.soldiersInOneTrip(), account.soldierKills(), quest.money(),
                    quest.reward(),
                    quest.reward() != null && quest.reward().usesAmmo() ? quest.rewardAmmo() : null,
                    ready));
        }
        return views;
    }

    /**
     * Hands the delivery errand's items over from the stash, oldest first, and completes it.
     *
     * @throws HideoutService.TradeRefusedException when no delivery is under way or the
     *         stash does not hold enough
     */
    static void deliver(Account account, StashItemRepository items) {
        Quests.Quest quest = Quests.at(Category.DELIVERY, account.questStep(Category.DELIVERY));
        if (quest == null) {
            throw new HideoutService.TradeRefusedException("지금은 납품할 의뢰가 없습니다");
        }
        items.deleteAll(takeFromStash(account, quest.deliver(), items));
        complete(account, quest, items);
    }

    /** Soldiers a trip brought down, counted when it ended by death or extraction. */
    static void addSoldiers(Account account, int soldiers, StashItemRepository items) {
        Quests.Quest quest = Quests.at(Category.SOLDIER, account.questStep(Category.SOLDIER));
        if (quest == null || soldiers <= 0) {
            return;
        }
        if (quest.soldiersInOneTrip()) {
            if (soldiers >= quest.soldiers()) {
                complete(account, quest, items);
            }
            return;
        }
        account.addSoldierKills(soldiers);
        if (account.soldierKills() >= quest.soldiers()) {
            complete(account, quest, items);
        }
    }

    /** A trip that got out having stood on this many of its marks. */
    static void reachedMarks(Account account, int marks, StashItemRepository items) {
        Quests.Quest quest = Quests.at(Category.VISIT, account.questStep(Category.VISIT));
        if (quest != null && marks >= quest.visits()) {
            complete(account, quest, items);
        }
    }

    /** The marks a trip out should carry for the place errand under way: count and doors. */
    static int[] marksFor(Account account) {
        Quests.Quest quest = Quests.at(Category.VISIT, account.questStep(Category.VISIT));
        return quest != null ? new int[] {quest.visits(), quest.visitDistance()} : new int[] {0, 0};
    }

    /** Pays, and moves that ladder on. A reward item lands in the stash even when it is full. */
    private static void complete(Account account, Quests.Quest quest, StashItemRepository items) {
        account.earn(quest.money());
        if (quest.reward() != null) {
            items.save(new StashItem(account.id(), quest.reward(), quest.rewardAmmo()));
        }
        account.nextQuest(quest.category());
    }

    private static Map<ItemKind, Integer> held(Account account, StashItemRepository items) {
        Map<ItemKind, Integer> held = new EnumMap<>(ItemKind.class);
        for (StashItem item : items.findByAccountIdAndLocationOrderById(
                account.id(), StashItem.Location.STASH)) {
            held.merge(item.kind(), 1, Integer::sum);
        }
        return held;
    }
}
