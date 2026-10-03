package com.example.battleroyal.web;

import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.loop.DepartureListener;
import com.example.battleroyal.game.rule.GameConstants;
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

    public record StashEntry(long id, ItemKind kind, Integer ammo) {
    }

    public record StashView(List<StashEntry> stash, int capacity, boolean out) {
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
        return tx.execute(status -> {
            List<StashEntry> stash = items
                    .findByAccountIdAndLocationOrderById(accountId, StashItem.Location.STASH)
                    .stream()
                    .map(item -> new StashEntry(item.id(), item.kind(),
                            item.kind().usesAmmo() ? item.ammo() : null))
                    .toList();
            return new StashView(stash, GameConstants.STASH_CAPACITY,
                    sorties.existsByAccountIdAndOutcome(accountId, Sortie.Outcome.OUT));
        });
    }

    /**
     * Sets out with the chosen stash items, slot by slot (null for an empty slot), and
     * hands back the game session that carries them in.
     */
    public GameSession setOut(long accountId, String nickname, List<Long> loadout) {
        if (loadout.size() > Player.INVENTORY_SLOTS) {
            throw new InvalidLoadoutException("가져갈 수 있는 것은 "
                    + Player.INVENTORY_SLOTS + "개까지입니다");
        }
        Set<Long> seen = new HashSet<>();
        for (Long id : loadout) {
            if (id != null && !seen.add(id)) {
                throw new InvalidLoadoutException("같은 아이템을 두 번 고를 수 없습니다");
            }
        }

        record Departure(long sortieId, List<Item> carried) {
        }
        Departure departure = tx.execute(status -> {
            accounts.lockById(accountId).orElseThrow();
            if (sorties.existsByAccountIdAndOutcome(accountId, Sortie.Outcome.OUT)) {
                throw new AlreadyOutException();
            }
            Sortie sortie = sorties.save(new Sortie(accountId, clock.instant()));
            List<Item> carried = new ArrayList<>();
            for (Long id : loadout) {
                if (id == null) {
                    carried.add(null);
                    continue;
                }
                StashItem item = items.findById(id)
                        .filter(found -> found.accountId().equals(accountId))
                        .filter(found -> found.location() == StashItem.Location.STASH)
                        .orElseThrow(() -> new InvalidLoadoutException("창고에 없는 아이템입니다"));
                item.sendOut(sortie.id());
                carried.add(new Item(item.gameItemId(), item.kind(), item.ammo()));
            }
            return new Departure(sortie.id(), carried);
        });

        GameSession session = sessions.issueForAccount(accountId, nickname, departure.carried());
        sortieOfPlayer.put(session.playerId(), departure.sortieId());
        log.info("Account {} set out on sortie {} as {}", accountId, departure.sortieId(),
                session.playerId());
        return session;
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
                    sorties.findById(sortieId).ifPresent(sortie ->
                            sortie.end(Sortie.Outcome.DIED, clock.instant()));
                    items.deleteAll(items.findBySortieId(sortieId));
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
                tx.executeWithoutResult(status -> settle(sortieId, carried));
            } catch (RuntimeException e) {
                // Left out: the next start-up hands back what was taken out, at least.
                log.error("Could not close sortie {} as extracted", sortieId, e);
            }
        });
    }

    private void settle(long sortieId, List<Item> carried) {
        Sortie sortie = sorties.findById(sortieId).orElseThrow();
        sortie.end(Sortie.Outcome.EXTRACTED, clock.instant());
        Map<String, StashItem> wentOut = new HashMap<>();
        for (StashItem item : items.findBySortieId(sortieId)) {
            wentOut.put(item.gameItemId(), item);
        }
        for (Item item : carried) {
            StashItem own = wentOut.remove(item.id());
            if (own != null) {
                own.bringBack(item.ammo());
            } else {
                items.save(new StashItem(sortie.accountId(), item.kind(), item.ammo()));
            }
        }
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
