package com.example.battleroyal.web;

import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.loop.RoomRegistry;
import com.example.battleroyal.persistence.Account;
import com.example.battleroyal.persistence.AccountRepository;
import com.example.battleroyal.persistence.SeasonRepository;
import com.example.battleroyal.persistence.Sortie;
import com.example.battleroyal.persistence.SortieRepository;
import com.example.battleroyal.persistence.StashItem;
import com.example.battleroyal.persistence.StashItemRepository;
import com.example.battleroyal.persistence.Trophy;
import com.example.battleroyal.persistence.TrophyRepository;
import com.example.battleroyal.web.GameSessionService.GameSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seasons: the deadline, the trophies, the wipe, and the players sent home from the
 * island when it comes.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SeasonServiceTest {

    @Autowired private AccountRepository accounts;
    @Autowired private StashItemRepository items;
    @Autowired private SortieRepository sorties;
    @Autowired private SeasonRepository seasonRows;
    @Autowired private TrophyRepository trophies;
    @Autowired private PlatformTransactionManager transactions;

    /** 2026-10-04 10:00 in Seoul. */
    private static final Instant START = Instant.parse("2026-10-04T01:00:00Z");

    private final MutableClock clock = new MutableClock(START);
    private GameSessionService sessions;
    private HideoutService hideout;
    private RoomRegistry registry;
    private SeasonService seasons;

    @BeforeEach
    @AfterEach
    void clear() {
        trophies.deleteAll();
        seasonRows.deleteAll();
        items.deleteAll();
        sorties.deleteAll();
        accounts.deleteAll();
    }

    @BeforeEach
    void setUp() {
        sessions = new GameSessionService();
        hideout = new HideoutService(accounts, items, sorties, sessions, transactions, clock,
                new DirectExecutor());
        registry = new RoomRegistry(new Random(1));
        seasons = new SeasonService(seasonRows, accounts, items, sorties, trophies, hideout,
                registry, transactions, clock);
        seasons.openFirstSeason();
    }

    private Account account(String name, long haul, long money) {
        Account account = new Account("google-" + name, Instant.EPOCH);
        account.chooseNickname(name);
        account.addHaul(haul);
        account.earn(money);
        account.growStash();
        account.nextQuest();
        return accounts.save(account);
    }

    @Test
    void theFirstSeasonEndsAtMidnightInSeoulFourWeeksOn() {
        SeasonService.SeasonView first = seasons.current();

        assertEquals(1, first.number());
        assertEquals(Instant.parse("2026-11-01T00:00:00+09:00"), first.endsAt());

        seasons.openFirstSeason();
        assertEquals(1, seasonRows.count(), "a restart finds its season open");
    }

    @Test
    void nothingHappensBeforeTheDeadline() {
        account("shuya", 100, 50);
        clock.set(Instant.parse("2026-10-31T23:59:00+09:00"));

        seasons.endIfDue();

        assertEquals(1, seasons.current().number());
        assertEquals(100, accounts.findAll().getFirst().haul());
    }

    @Test
    void theDeadlineHandsOutTrophiesAndWipesEverythingElse() {
        long first = account("shuya", 300, 900).id();
        long second = account("noriko", 200, 10).id();
        long tied = account("kawada", 200, 0).id();
        long eleventh = account("mitsuko", 0, 0).id();
        items.save(new StashItem(first, ItemKind.PISTOL, 6));
        // Got out once this season with nothing found: a participant.
        Sortie trip = sorties.save(new Sortie(eleventh, START.plusSeconds(60)));
        trip.end(Sortie.Outcome.EXTRACTED, START.plusSeconds(120));
        sorties.save(trip);

        clock.set(Instant.parse("2026-11-01T00:00:30+09:00"));
        seasons.endIfDue();

        assertEquals(List.of(Trophy.Tier.CHAMPION), tiers(first));
        assertEquals(List.of(Trophy.Tier.TOP10), tiers(second));
        assertEquals(2, trophies.findByAccountIdOrderBySeasonAsc(tied).getFirst().placing(),
                "equal hauls share a rank");
        assertEquals(List.of(Trophy.Tier.PARTICIPANT), tiers(eleventh));
        assertNull(trophies.findByAccountIdOrderBySeasonAsc(eleventh).getFirst().placing());

        for (Account account : accounts.findAll()) {
            assertEquals(0, account.haul());
            assertEquals(0, account.money());
            assertEquals(0, account.stashSize(), "back to the plain box");
            assertEquals(0, account.questStep(), "errands start over");
        }
        assertEquals(0, items.count());
        assertEquals(4, accounts.count(), "accounts and nicknames stay");

        SeasonService.SeasonView next = seasons.current();
        assertEquals(2, next.number());
        assertEquals(Instant.parse("2026-11-29T00:00:00+09:00"), next.endsAt());
    }

    private List<Trophy.Tier> tiers(long accountId) {
        return trophies.findByAccountIdOrderBySeasonAsc(accountId).stream().map(Trophy::tier).toList();
    }

    @Test
    void whoeverIsOnTheIslandIsSentHomeAndTheirTripCountsForNothing() {
        long me = account("shuya", 0, 0).id();
        StashItem pistol = items.save(new StashItem(me, ItemKind.PISTOL, 6));
        GameSession session = hideout.setOut(me, "shuya", List.of(pistol.id()));
        registry.requestJoin(session.playerId(), "shuya", session.loadout());
        registry.processPending(0);

        clock.set(Instant.parse("2026-11-01T00:00:00+09:00"));
        seasons.endIfDue();
        registry.processPending(1);

        assertFalse(registry.player(session.playerId()).active(), "taken off the island");
        assertEquals(Sortie.Outcome.REFUNDED, sorties.findAll().getFirst().outcome());
        assertEquals(0, items.count(), "the pistol went with the wipe");
        assertFalse(hideout.view(me).out(), "free to set out in the new season");

        // An extraction already on its way lands after the wipe and changes nothing.
        hideout.onExtracted(new GameEvent.Extracted(session.playerId(), "shuya", 0, 0, 0,
                List.of(new Item(pistol.gameItemId(), ItemKind.PISTOL, 6))));
        assertEquals(0, items.count());
        assertTrue(trophies.findAll().isEmpty(), "set out but never got out: no trophy");
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    /** Runs the write on the calling thread so the test can look straight away. */
    private static final class DirectExecutor extends AbstractExecutorService {
        private boolean shutdown;

        @Override public void execute(Runnable command) { command.run(); }
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }
}
