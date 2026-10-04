package com.example.battleroyal.web;

import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.loop.DepartureListener;
import com.example.battleroyal.game.rule.Bags;
import com.example.battleroyal.game.rule.GameConstants;
import com.example.battleroyal.game.rule.ItemValues;
import com.example.battleroyal.persistence.Account;
import com.example.battleroyal.persistence.AccountRepository;
import com.example.battleroyal.persistence.Sortie;
import com.example.battleroyal.persistence.SortieRepository;
import com.example.battleroyal.persistence.StashItem;
import com.example.battleroyal.persistence.StashItemRepository;
import com.example.battleroyal.web.GameSessionService.GameSession;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The hideout: an account's stash, and setting out from it onto the island.
 *
 * <p>An item crosses between the database and the running game at four moments only,
 * each one transaction (docs/V2_PLAN.md §5):
 * <ol>
 *   <li><b>Setting out.</b> The chosen stash items are marked out with a new sortie,
 *       under a lock on the account row, so one item cannot leave twice.</li>
 *   <li><b>Death.</b> The sortie is closed as died and the items it carried are
 *       deleted: lost, as the rules say. Written off the game loop thread.</li>
 *   <li><b>Extraction.</b> The sortie is closed as extracted and what the player got
 *       out with lands in the stash: their own gear goes back home, anything found
 *       is added. Gear that went out and did not come back was left on the island and
 *       is gone. Also off the loop thread.</li>
 *   <li><b>Start-up.</b> Any sortie still out belongs to a server that stopped
 *       mid-trip; its items go back to the stash (decision D6). Done at start rather
 *       than shutdown so a crash is handled the same way as a deploy.</li>
 * </ol>
 *
 * <p>A full stash still takes everything brought out. Losing loot at the door of the
 * hideout would punish the best trips; until the traders of step 4 give a way to make
 * room, the stash is let run over its capacity.
 */
@Service
public class HideoutService implements DepartureListener {

    private static final Logger log = LoggerFactory.getLogger(HideoutService.class);

    /** @param price what the trader pays for it */
    public record StashEntry(long id, ItemKind kind, Integer ammo, int price) {
    }

    /** The next stash size on sale: how many slots it holds and what it costs. */
    public record StashUpgrade(int capacity, int price) {
    }

    /**
     * @param upgrade null once the biggest stash is bought
     * @param quests  the trader's errands under way, one per kind still to climb
     */
    public record StashView(List<StashEntry> stash, int capacity, StashUpgrade upgrade,
                            boolean out, long money, long haul, List<ItemValues.Offer> trader,
                            List<QuestLedger.QuestView> quests) {
    }

    /** Thrown for a sale or purchase the rules do not allow; the message says why. */
    public static class TradeRefusedException extends RuntimeException {
        public TradeRefusedException(String message) {
            super(message);
        }
    }

    /** Thrown when the account already has a sortie on the island. */
    public static class AlreadyOutException extends RuntimeException {
        public AlreadyOutException() {
            super("이미 섬에 나가 있습니다");
        }
    }

    /** Thrown for a loadout naming items that are not this account's, or not at home. */
    public static class InvalidLoadoutException extends RuntimeException {
        public InvalidLoadoutException(String message) {
            super(message);
        }
    }

    private final AccountRepository accounts;
    private final StashItemRepository items;
    private final SortieRepository sorties;
    private final GameSessionService sessions;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ExecutorService writer;

    /** Which sortie a live player is on, for closing it when they die. */
    private final Map<String, Long> sortieOfPlayer = new ConcurrentHashMap<>();

    @Autowired
    public HideoutService(AccountRepository accounts, StashItemRepository items,
                          SortieRepository sorties, GameSessionService sessions,
                          PlatformTransactionManager transactions) {
        this(accounts, items, sorties, sessions, transactions, Clock.systemUTC(),
                Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "sortie-writer");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    HideoutService(AccountRepository accounts, StashItemRepository items,
                   SortieRepository sorties, GameSessionService sessions,
                   PlatformTransactionManager transactions, Clock clock, ExecutorService writer) {
        this.accounts = accounts;
        this.items = items;
        this.sorties = sorties;
        this.sessions = sessions;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
        this.writer = writer;
    }

    public StashView view(long accountId) {
        return tx.execute(status -> viewInside(accountId));
    }

    private StashView viewInside(long accountId) {
        Account account = accounts.findById(accountId).orElseThrow();
        List<StashEntry> stash = items
                .findByAccountIdAndLocationOrderById(accountId, StashItem.Location.STASH)
                .stream()
                .map(item -> new StashEntry(item.id(), item.kind(),
                        item.kind().usesAmmo() ? item.ammo() : null,
                        ItemValues.sellPrice(item.kind(), item.ammo())))
                .toList();
        int size = account.stashSize();
        StashUpgrade upgrade = size + 1 < GameConstants.STASH_SIZES.size()
                ? new StashUpgrade(GameConstants.STASH_SIZES.get(size + 1),
                        GameConstants.STASH_UPGRADE_PRICES.get(size))
                : null;
        return new StashView(stash, capacity(account), upgrade,
                sorties.existsByAccountIdAndOutcome(accountId, Sortie.Outcome.OUT),
                account.money(), account.haul(), ItemValues.STOCK,
                QuestLedger.view(account, items));
    }

    private static int capacity(Account account) {
        return GameConstants.STASH_SIZES.get(account.stashSize());
    }

    /** Buys the next stash size. For good: the account keeps it. */
    public StashView growStash(long accountId) {
        return tx.execute(status -> {
            Account account = accounts.lockById(accountId).orElseThrow();
            int size = account.stashSize();
            if (size + 1 >= GameConstants.STASH_SIZES.size()) {
                throw new TradeRefusedException("더 큰 창고는 없습니다");
            }
            if (!account.spend(GameConstants.STASH_UPGRADE_PRICES.get(size))) {
                throw new TradeRefusedException("돈이 모자랍니다");
            }
            account.growStash();
            return viewInside(accountId);
        });
    }

    /** Hands the errand's items to the trader from the stash, and takes the reward. */
    public StashView deliverQuest(long accountId) {
        return tx.execute(status -> {
            QuestLedger.deliver(accounts.lockById(accountId).orElseThrow(), items);
            return viewInside(accountId);
        });
    }

    // --- The trader ---------------------------------------------------------

    /** Sells a stash item to the trader for its value. Only what is at home can be sold. */
    public StashView sell(long accountId, long itemId) {
        return tx.execute(status -> {
            Account account = accounts.lockById(accountId).orElseThrow();
            StashItem item = items.findById(itemId)
                    .filter(found -> found.accountId().equals(accountId))
                    .filter(found -> found.location() == StashItem.Location.STASH)
                    .orElseThrow(() -> new TradeRefusedException("창고에 없는 아이템입니다"));
            account.earn(ItemValues.sellPrice(item.kind(), item.ammo()));
            items.delete(item);
            return viewInside(accountId);
        });
    }

    /** Buys from the trader's stock into the stash, which needs a free slot. */
    public StashView buy(long accountId, ItemKind kind) {
        ItemValues.Offer offer = kind == null ? null : ItemValues.offerFor(kind);
        if (offer == null) {
            throw new TradeRefusedException("상인이 팔지 않는 물건입니다");
        }
        return tx.execute(status -> {
            Account account = accounts.lockById(accountId).orElseThrow();
            if (items.countByAccountIdAndLocation(accountId, StashItem.Location.STASH)
                    >= capacity(account)) {
                throw new TradeRefusedException("창고가 가득 찼습니다");
            }
            if (!account.spend(offer.price())) {
                throw new TradeRefusedException("돈이 모자랍니다");
            }
            items.save(new StashItem(accountId, offer.kind(), offer.ammo()));
            return viewInside(accountId);
        });
    }

    public GameSession setOut(long accountId, String nickname, List<Long> loadout) {
        return setOut(accountId, nickname, loadout, null);
    }

    /**
     * Sets out with the chosen stash items, slot by slot (null for an empty slot), and a
     * bag to wear (or null), and hands back the game session that carries them in. The
     * bag decides how many slots there are.
     */
    public GameSession setOut(long accountId, String nickname, List<Long> loadout, Long bagId) {
        Set<Long> seen = new HashSet<>();
        if (bagId != null) {
            seen.add(bagId);
        }
        for (Long id : loadout) {
            if (id != null && !seen.add(id)) {
                throw new InvalidLoadoutException("같은 아이템을 두 번 고를 수 없습니다");
            }
        }

        record Departure(long sortieId, List<Item> carried, Item bag, int[] marks) {
        }
        Departure departure = tx.execute(status -> {
            Account account = accounts.lockById(accountId).orElseThrow();
            Sortie open = sorties.findFirstByAccountIdAndOutcome(accountId, Sortie.Outcome.OUT)
                    .orElse(null);
            if (open != null && !abandonIfNeverJoined(open)) {
                throw new AlreadyOutException();
            }
            Sortie sortie = sorties.save(new Sortie(accountId, clock.instant()));
            Item bag = null;
            if (bagId != null) {
                StashItem worn = atHome(accountId, bagId);
                if (!Bags.isBag(worn.kind())) {
                    throw new InvalidLoadoutException("가방이 아닙니다");
                }
                worn.sendOut(sortie.id());
                bag = new Item(worn.gameItemId(), worn.kind(), worn.ammo());
            }
            int slots = Bags.slotsWith(bag);
            if (loadout.size() > slots) {
                throw new InvalidLoadoutException("가져갈 수 있는 것은 " + slots + "개까지입니다");
            }
            List<Item> carried = new ArrayList<>();
            for (Long id : loadout) {
                if (id == null) {
                    carried.add(null);
                    continue;
                }
                StashItem item = atHome(accountId, id);
                item.sendOut(sortie.id());
                carried.add(new Item(item.gameItemId(), item.kind(), item.ammo()));
            }
            return new Departure(sortie.id(), carried, bag, QuestLedger.marksFor(account));
        });

        GameSession session = sessions.issueForAccount(accountId, nickname, departure.carried(),
                departure.bag(), departure.marks()[0], departure.marks()[1]);
        sortieOfPlayer.put(session.playerId(), departure.sortieId());
        log.info("Account {} set out on sortie {} as {}", accountId, departure.sortieId(),
                session.playerId());
        return session;
    }

    /**
     * A sortie whose player never reached the island (the page closed between setting
     * out and the socket attaching) would otherwise hold the account until a restart.
     * Its session is retired first, so its token cannot bring the gear in afterwards,
     * and only then does the gear go home.
     *
     * @return false, changing nothing, while the player is in the game
     */
    private boolean abandonIfNeverJoined(Sortie sortie) {
        String playerId = sortieOfPlayer.entrySet().stream()
                .filter(entry -> entry.getValue().equals(sortie.id()))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
        // No player for it at all: nobody can be on the island with it.
        if (playerId != null && !sessions.retireIfNeverAttached(playerId)) {
            return false;
        }
        if (playerId != null) {
            sortieOfPlayer.remove(playerId);
        }
        items.findBySortieId(sortie.id()).forEach(StashItem::bringBack);
        sortie.end(Sortie.Outcome.REFUNDED, clock.instant());
        log.info("Sortie {} never reached the island; its gear went home", sortie.id());
        return true;
    }

    private StashItem atHome(long accountId, long id) {
        return items.findById(id)
                .filter(found -> found.accountId().equals(accountId))
                .filter(found -> found.location() == StashItem.Location.STASH)
                .orElseThrow(() -> new InvalidLoadoutException("창고에 없는 아이템입니다"));
    }

    /**
     * Every player now on the island with a sortie, forgotten at once: the season is
     * ending and its wipe closes their sorties. A death or extraction that still comes
     * through finds its sortie already closed and changes nothing.
     */
    public List<String> takeAllOut() {
        List<String> out = new ArrayList<>(sortieOfPlayer.keySet());
        out.forEach(sortieOfPlayer::remove);
        return out;
    }

    /** Called on the game loop thread, so the write goes to a thread of its own. */
    @Override
    public void onDeath(GameEvent.Died died) {
        Long sortieId = sortieOfPlayer.remove(died.playerId());
        if (sortieId == null) {
            return;     // a guest
        }
        writer.execute(() -> {
            try {
                tx.executeWithoutResult(status -> {
                    Sortie sortie = sorties.findById(sortieId).orElse(null);
                    if (sortie == null || sortie.outcome() != Sortie.Outcome.OUT) {
                        return;     // closed by a season's end in the meantime
                    }
                    sortie.end(Sortie.Outcome.DIED, clock.instant());
                    items.deleteAll(items.findBySortieId(sortieId));
                    // Kills count for an errand even on a trip that ended badly.
                    QuestLedger.addSoldiers(accounts.lockById(sortie.accountId()).orElseThrow(),
                            died.soldiersDowned(), items);
                });
            } catch (RuntimeException e) {
                // Left out: the next start-up hands the gear back rather than losing it.
                log.error("Could not close sortie {} as died", sortieId, e);
            }
        });
    }

    /** Called on the game loop thread, so the write goes to a thread of its own. */
    @Override
    public void onExtracted(GameEvent.Extracted extracted) {
        Long sortieId = sortieOfPlayer.remove(extracted.playerId());
        if (sortieId == null) {
            return;     // a guest: nowhere to keep anything
        }
        List<Item> carried = extracted.carried();
        writer.execute(() -> {
            try {
                tx.executeWithoutResult(status -> settle(sortieId, carried,
                        extracted.soldiersDowned(), extracted.marksReached()));
            } catch (RuntimeException e) {
                // Left out: the next start-up hands back what was taken out, at least.
                log.error("Could not close sortie {} as extracted", sortieId, e);
            }
        });
    }

    private void settle(long sortieId, List<Item> carried, int soldiers, int marksReached) {
        Sortie sortie = sorties.findById(sortieId).orElseThrow();
        if (sortie.outcome() != Sortie.Outcome.OUT) {
            return;     // closed by a season's end: what came out went with the wipe
        }
        sortie.end(Sortie.Outcome.EXTRACTED, clock.instant());
        Map<String, StashItem> wentOut = new HashMap<>();
        for (StashItem item : items.findBySortieId(sortieId)) {
            wentOut.put(item.gameItemId(), item);
        }
        long found = 0;
        for (Item item : carried) {
            StashItem own = wentOut.remove(item.id());
            if (own != null) {
                own.bringBack(item.ammo());
            } else {
                items.save(new StashItem(sortie.accountId(), item.kind(), item.ammo()));
                found += ItemValues.sellPrice(item.kind(), item.ammo());
            }
        }
        // Only what was found counts towards the ranking (D5).
        Account account = accounts.lockById(sortie.accountId()).orElseThrow();
        account.addHaul(found);
        QuestLedger.addSoldiers(account, soldiers, items);
        QuestLedger.reachedMarks(account, marksReached, items);
        // Taken out and not brought back: left in a crate somewhere, so lost.
        items.deleteAll(wentOut.values());
    }

    /** Gear carried by sorties a stopped server never closed goes home (D6). */
    @EventListener(ApplicationReadyEvent.class)
    public void refundUnfinished() {
        int refunded = tx.execute(status -> {
            List<Sortie> open = sorties.findByOutcome(Sortie.Outcome.OUT);
            for (Sortie sortie : open) {
                items.findBySortieId(sortie.id()).forEach(StashItem::bringBack);
                sortie.end(Sortie.Outcome.REFUNDED, clock.instant());
            }
            return open.size();
        });
        if (refunded > 0) {
            log.info("Refunded {} sorties left open by the last run", refunded);
        }
    }

    /** Lets deaths already handed over reach the database before shutdown. */
    @PreDestroy
    public void flush() throws InterruptedException {
        writer.shutdown();
        if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
            log.warn("Shut down with sorties still unwritten");
        }
    }
}
