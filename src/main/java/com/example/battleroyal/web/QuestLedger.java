package com.example.battleroyal.web;

import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.rule.Quests;
import com.example.battleroyal.persistence.Account;
import com.example.battleroyal.persistence.StashItem;
import com.example.battleroyal.persistence.StashItemRepository;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * An account's progress through the trader's errands ({@link Quests}). Called inside the
 * caller's transaction with the account row already locked, so two completions cannot
 * pay twice.
 */
public final class QuestLedger {

    /** One line of what a delivery asks for, and how many the stash holds now. */
    public record Need(ItemKind kind, int count, int have) {
    }

    /**
     * The errand under way, as the hideout shows it.
     *
     * @param ready true when a delivery can be handed over now
     */
    public record QuestView(int step, int total, String title, List<Need> deliver, int kills,
                     int killsDone, int money, ItemKind reward, Integer rewardAmmo,
                     boolean ready) {
    }

    private QuestLedger() {
    }

    /** @return null once every errand is done */
    static QuestView view(Account account, StashItemRepository items) {
        Quests.Quest quest = Quests.at(account.questStep());
        if (quest == null) {
            return null;
        }
        Map<ItemKind, Integer> held = held(account, items);
        List<Need> needs = new ArrayList<>();
        boolean ready = quest.isDelivery();
        for (Map.Entry<ItemKind, Integer> entry : quest.deliver().entrySet()) {
            int have = held.getOrDefault(entry.getKey(), 0);
            needs.add(new Need(entry.getKey(), entry.getValue(), have));
            ready &= have >= entry.getValue();
        }
        needs.sort((a, b) -> a.kind().compareTo(b.kind()));
        return new QuestView(account.questStep() + 1, Quests.ALL.size(), quest.title(), needs,
                quest.kills(), account.questKills(), quest.money(), quest.reward(),
                quest.reward() != null && quest.reward().usesAmmo() ? quest.rewardAmmo() : null,
                ready);
    }

    /**
     * Hands the errand's items over from the stash, oldest first, and completes it.
     *
     * @throws HideoutService.TradeRefusedException when there is no delivery under way or
     *         the stash does not hold enough
     */
    static void deliver(Account account, StashItemRepository items) {
        Quests.Quest quest = Quests.at(account.questStep());
        if (quest == null || !quest.isDelivery()) {
            throw new HideoutService.TradeRefusedException("지금은 납품할 의뢰가 없습니다");
        }
        List<StashItem> handed = new ArrayList<>();
        List<StashItem> stash = items.findByAccountIdAndLocationOrderById(
                account.id(), StashItem.Location.STASH);
        for (Map.Entry<ItemKind, Integer> entry : quest.deliver().entrySet()) {
            List<StashItem> ofKind = stash.stream()
                    .filter(item -> item.kind() == entry.getKey())
                    .limit(entry.getValue())
                    .toList();
            if (ofKind.size() < entry.getValue()) {
                throw new HideoutService.TradeRefusedException("창고에 납품할 물건이 모자랍니다");
            }
            handed.addAll(ofKind);
        }
        items.deleteAll(handed);
        complete(account, quest, items);
    }

    /** Kills from a trip that ended, by death or extraction, count towards a kill errand. */
    static void addKills(Account account, int kills, StashItemRepository items) {
        Quests.Quest quest = Quests.at(account.questStep());
        if (quest == null || quest.isDelivery() || kills <= 0) {
            return;
        }
        account.addQuestKills(kills);
        if (account.questKills() >= quest.kills()) {
            complete(account, quest, items);
        }
    }

    /** Pays, and moves on. A reward item lands in the stash even when it is full. */
    private static void complete(Account account, Quests.Quest quest, StashItemRepository items) {
        account.earn(quest.money());
        if (quest.reward() != null) {
            items.save(new StashItem(account.id(), quest.reward(), quest.rewardAmmo()));
        }
        account.nextQuest();
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
