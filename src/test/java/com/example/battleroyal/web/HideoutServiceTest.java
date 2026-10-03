package com.example.battleroyal.web;

import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.rule.GameConstants;
import com.example.battleroyal.persistence.Account;
import com.example.battleroyal.persistence.AccountRepository;
import com.example.battleroyal.persistence.Sortie;
import com.example.battleroyal.persistence.SortieRepository;
import com.example.battleroyal.persistence.StashItem;
import com.example.battleroyal.persistence.StashItemRepository;
import com.example.battleroyal.web.GameSessionService.GameSession;
import com.example.battleroyal.web.HideoutService.AlreadyOutException;
import com.example.battleroyal.web.HideoutService.InvalidLoadoutException;
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
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stash and the trip out: what crosses between the database and the game, and the
 * rules that keep an item from being in two places or vanishing by accident.
 */
// Not wrapped in a test transaction: each step commits or rolls back on its own, as it does
// in the running server, so a refused sortie is seen to leave nothing behind.
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HideoutServiceTest {

    @Autowired private AccountRepository accounts;
    @Autowired private StashItemRepository items;
    @Autowired private SortieRepository sorties;
    @Autowired private PlatformTransactionManager transactions;

    private GameSessionService sessions;
    private HideoutService hideout;
    private long me;

    /** Commits are real here, so clear up before and after: other tests share the database. */
    @BeforeEach
    @AfterEach
    void clear() {
        items.deleteAll();
        sorties.deleteAll();
        accounts.deleteAll();
    }

    @BeforeEach
    void setUp() {
        sessions = new GameSessionService();
        hideout = new HideoutService(accounts, items, sorties, sessions, transactions,
                Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"), ZoneOffset.UTC),
                new DirectExecutor());
        Account account = new Account("google-me", Instant.EPOCH);
        account.chooseNickname("shuya");
        me = accounts.save(account).id();
    }

    private StashItem stash(long accountId, ItemKind kind, int ammo) {
        return items.save(new StashItem(accountId, kind, ammo));
    }

    private static GameEvent.Died died(String playerId) {
        return new GameEvent.Died(playerId, "shuya", 0, 0, 0, null, null);
    }

    @Test
    void settingOutCarriesTheChosenItemsSlotBySlotAndTakesThemOutOfTheStash() {
        StashItem knife = stash(me, ItemKind.KNIFE, 0);
        StashItem pistol = stash(me, ItemKind.PISTOL, 4);
        StashItem cup = stash(me, ItemKind.CUP, 0);

        GameSession session = hideout.setOut(me, "shuya", Arrays.asList(pistol.id(), null, knife.id()));

        List<Item> carried = session.loadout();
        assertEquals(pistol.gameItemId(), carried.get(0).id());
        assertEquals(4, carried.get(0).ammo(), "ammo travels with the item");
        assertNull(carried.get(1));
        assertEquals(ItemKind.KNIFE, carried.get(2).kind());

        assertEquals(List.of(cup.id()), hideout.view(me).stash().stream()
                .map(HideoutService.StashEntry::id).toList(), "only what stayed home");
        assertTrue(hideout.view(me).out());
        assertEquals(StashItem.Location.OUT, items.findById(pistol.id()).orElseThrow().location());
    }

    @Test
    void anAccountCannotSetOutTwice() {
        hideout.setOut(me, "shuya", List.of());

        assertThrows(AlreadyOutException.class, () -> hideout.setOut(me, "shuya", List.of()),
                "one sortie at a time, or one stash item could leave twice");
    }

    @Test
    void onlyThisAccountsItemsAtHomeCanBeCarried() {
        long other = accounts.save(new Account("google-other", Instant.EPOCH)).id();
        StashItem theirs = stash(other, ItemKind.PISTOL, 6);
        StashItem mine = stash(me, ItemKind.KNIFE, 0);

        assertThrows(InvalidLoadoutException.class,
                () -> hideout.setOut(me, "shuya", List.of(theirs.id())));
        assertThrows(InvalidLoadoutException.class,
                () -> hideout.setOut(me, "shuya", List.of(mine.id(), mine.id())),
                "the same item in two slots");
        assertThrows(InvalidLoadoutException.class,
                () -> hideout.setOut(me, "shuya", List.of(1L, 2L, 3L, 4L)),
                "more than the inventory holds");
        assertFalse(hideout.view(me).out(), "a refused loadout opens no sortie");
    }

    @Test
    void dyingLosesWhatWasCarriedAndFreesTheAccountToSetOutAgain() {
        StashItem knife = stash(me, ItemKind.KNIFE, 0);
        StashItem cup = stash(me, ItemKind.CUP, 0);
        GameSession session = hideout.setOut(me, "shuya", List.of(knife.id()));

        hideout.onDeath(died(session.playerId()));

        assertTrue(items.findById(knife.id()).isEmpty(), "carried and lost");
        assertTrue(items.findById(cup.id()).isPresent(), "what stayed home is safe");
        Sortie sortie = sorties.findAll().getFirst();
        assertEquals(Sortie.Outcome.DIED, sortie.outcome());
        assertFalse(hideout.view(me).out());
        hideout.setOut(me, "shuya", List.of(cup.id()));
    }

    @Test
    void aServerThatStoppedMidTripHandsTheGearBackWhenItStarts() {
        StashItem pistol = stash(me, ItemKind.PISTOL, 2);
        hideout.setOut(me, "shuya", List.of(pistol.id()));

        hideout.refundUnfinished();

        StashItem back = items.findById(pistol.id()).orElseThrow();
        assertEquals(StashItem.Location.STASH, back.location());
        assertNull(back.sortieId());
        assertEquals(2, back.ammo());
        assertEquals(Sortie.Outcome.REFUNDED, sorties.findAll().getFirst().outcome());
        assertFalse(hideout.view(me).out(), "free to set out again");
    }

    @Test
    void aGuestsDeathTouchesNothing() {
        StashItem knife = stash(me, ItemKind.KNIFE, 0);

        hideout.onDeath(died(sessions.issueGuest("kawada").playerId()));

        assertTrue(items.findById(knife.id()).isPresent());
        assertEquals(0, sorties.count());
    }

    @Test
    void theStashSaysHowBigItIs() {
        assertEquals(GameConstants.STASH_CAPACITY, hideout.view(me).capacity());
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
