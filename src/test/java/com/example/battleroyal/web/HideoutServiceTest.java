package com.example.battleroyal.web;

import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.core.Item;
import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.rule.GameConstants;
import com.example.battleroyal.game.rule.ItemValues;
import com.example.battleroyal.game.rule.Quests;
import com.example.battleroyal.persistence.Account;
import com.example.battleroyal.persistence.AccountRepository;
import com.example.battleroyal.persistence.Sortie;
import com.example.battleroyal.persistence.SortieRepository;
import com.example.battleroyal.persistence.StashItem;
import com.example.battleroyal.persistence.StashItemRepository;
import com.example.battleroyal.web.GameSessionService.GameSession;
import com.example.battleroyal.web.HideoutService.AlreadyOutException;
import com.example.battleroyal.web.HideoutService.InvalidLoadoutException;
import com.example.battleroyal.web.HideoutService.TradeRefusedException;
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
    void aBagWornOutGivesMoreSlotsAndComesHomeWithWhatItHeld() {
        StashItem bag = stash(me, ItemKind.SMALL_BAG, 0);
        StashItem knife = stash(me, ItemKind.KNIFE, 0);

        GameSession session = hideout.setOut(me, "shuya",
                Arrays.asList(null, null, null, null, knife.id()), bag.id());

        assertEquals(bag.gameItemId(), session.bag().id());
        assertEquals(ItemKind.KNIFE, session.loadout().get(4).kind(), "the bag's own slot");
        assertEquals(StashItem.Location.OUT, items.findById(bag.id()).orElseThrow().location());

        hideout.onExtracted(extracted(session.playerId(), session.loadout().get(4), session.bag()));

        assertEquals(List.of(ItemKind.KNIFE, ItemKind.SMALL_BAG), hideout.view(me).stash().stream()
                .map(HideoutService.StashEntry::kind).sorted().toList());
        assertEquals(0, accounts.findById(me).orElseThrow().haul(), "own gear back is no haul");
    }

    @Test
    void theBagDecidesHowManySlotsGoOut() {
        StashItem bag = stash(me, ItemKind.SMALL_BAG, 0);
        StashItem knife = stash(me, ItemKind.KNIFE, 0);

        assertThrows(InvalidLoadoutException.class, () -> hideout.setOut(me, "shuya",
                Arrays.asList(null, null, null, knife.id())), "slot 4 needs a bag");
        assertThrows(InvalidLoadoutException.class, () -> hideout.setOut(me, "shuya",
                Arrays.asList(null, null, null, null, null, knife.id()), bag.id()),
                "a small bag gives five");
        assertThrows(InvalidLoadoutException.class, () -> hideout.setOut(me, "shuya",
                List.of(), knife.id()), "only a bag is worn");
        assertThrows(InvalidLoadoutException.class, () -> hideout.setOut(me, "shuya",
                List.of(bag.id()), bag.id()), "the same bag worn and carried");
        assertFalse(hideout.view(me).out(), "a refused loadout opens no sortie");
    }

    @Test
    void anAccountCannotSetOutTwice() {
        GameSession first = hideout.setOut(me, "shuya", List.of());
        sessions.attach(first.token());     // its socket is on the island

        assertThrows(AlreadyOutException.class, () -> hideout.setOut(me, "shuya", List.of()),
                "one sortie at a time, or one stash item could leave twice");
    }

    @Test
    void aSortieThatNeverReachedTheIslandGivesWayToTheNext() {
        StashItem pistol = stash(me, ItemKind.PISTOL, 6);
        GameSession lost = hideout.setOut(me, "shuya", List.of(pistol.id()));
        // The page closed before the socket attached.

        GameSession next = hideout.setOut(me, "shuya", List.of(pistol.id()));

        assertNull(sessions.attach(lost.token()), "the abandoned token cannot bring the pistol in");
        assertEquals(pistol.gameItemId(), next.loadout().getFirst().id(), "the pistol goes again");
        assertEquals(List.of(Sortie.Outcome.REFUNDED, Sortie.Outcome.OUT), sorties.findAll().stream()
                .sorted(java.util.Comparator.comparing(Sortie::id)).map(Sortie::outcome).toList());
        assertEquals(StashItem.Location.OUT, items.findById(pistol.id()).orElseThrow().location());
    }

    @Test
    void aSortieWhoseRetryIsRefusedStaysClosedAndItsGearAtHome() {
        StashItem pistol = stash(me, ItemKind.PISTOL, 6);
        hideout.setOut(me, "shuya", List.of(pistol.id()));

        assertThrows(InvalidLoadoutException.class,
                () -> hideout.setOut(me, "shuya", List.of(1L, 2L, 3L, 4L)));

        // The refused retry rolled back, but the old session is retired all the same:
        // the next try finds no player for the sortie and sends the gear home.
        hideout.setOut(me, "shuya", List.of());
        assertEquals(StashItem.Location.STASH, items.findById(pistol.id()).orElseThrow().location());
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

    private static GameEvent.Extracted extracted(String playerId, Item... carried) {
        return new GameEvent.Extracted(playerId, "shuya", 0, 0, 0, List.of(carried));
    }

    @Test
    void gettingOutBringsOwnGearHomeAddsWhatWasFoundAndLosesWhatWasLeft() {
        StashItem pistol = stash(me, ItemKind.PISTOL, 6);
        StashItem knife = stash(me, ItemKind.KNIFE, 0);
        GameSession session = hideout.setOut(me, "shuya", List.of(pistol.id(), knife.id()));
        Item firedTwice = new Item(pistol.gameItemId(), ItemKind.PISTOL, 4);
        Item found = new Item("i-42", ItemKind.CROSSBOW, 1);

        // The knife was left in a crate on the island.
        hideout.onExtracted(extracted(session.playerId(), firedTwice, found));

        StashItem home = items.findById(pistol.id()).orElseThrow();
        assertEquals(StashItem.Location.STASH, home.location());
        assertEquals(4, home.ammo(), "the rounds spent stay spent");
        assertTrue(items.findById(knife.id()).isEmpty(), "left behind, so lost");
        assertEquals(List.of(ItemKind.PISTOL, ItemKind.CROSSBOW), hideout.view(me).stash().stream()
                .map(HideoutService.StashEntry::kind).toList());
        assertEquals(Sortie.Outcome.EXTRACTED, sorties.findAll().getFirst().outcome());
        assertFalse(hideout.view(me).out(), "free to set out again");
    }

    @Test
    void onlyWhatWasFoundCountsTowardsTheHaul() {
        StashItem pistol = stash(me, ItemKind.PISTOL, 6);
        GameSession session = hideout.setOut(me, "shuya", List.of(pistol.id()));

        hideout.onExtracted(extracted(session.playerId(),
                new Item(pistol.gameItemId(), ItemKind.PISTOL, 6),
                new Item("i-5", ItemKind.KNIFE, 0)));

        assertEquals(ItemValues.sellPrice(ItemKind.KNIFE, 0), hideout.view(me).haul(),
                "a pistol carried out and back is not a find");
    }

    @Test
    void sellingPaysTheValueAndTheItemIsGone() {
        StashItem pistol = stash(me, ItemKind.PISTOL, 2);

        HideoutService.StashView after = hideout.sell(me, pistol.id());

        assertEquals(ItemValues.sellPrice(ItemKind.PISTOL, 2), after.money());
        assertTrue(after.stash().isEmpty());
        assertTrue(items.findById(pistol.id()).isEmpty());
    }

    @Test
    void onlyWhatIsAtHomeCanBeSold() {
        StashItem knife = stash(me, ItemKind.KNIFE, 0);
        long other = accounts.save(new Account("google-other", Instant.EPOCH)).id();
        StashItem theirs = stash(other, ItemKind.PISTOL, 6);
        hideout.setOut(me, "shuya", List.of(knife.id()));

        assertThrows(TradeRefusedException.class, () -> hideout.sell(me, knife.id()),
                "out on the island");
        assertThrows(TradeRefusedException.class, () -> hideout.sell(me, theirs.id()));
        assertEquals(0, hideout.view(me).money());
    }

    @Test
    void buyingNeedsTheMoneyAndARoomInTheStash() {
        ItemValues.Offer knife = ItemValues.offerFor(ItemKind.KNIFE);
        assertThrows(TradeRefusedException.class, () -> hideout.buy(me, ItemKind.KNIFE),
                "no money yet");
        assertThrows(TradeRefusedException.class, () -> hideout.buy(me, ItemKind.CUP),
                "the trader sells no junk");

        StashItem pistol = stash(me, ItemKind.PISTOL, 6);
        hideout.sell(me, pistol.id());
        HideoutService.StashView after = hideout.buy(me, ItemKind.KNIFE);

        assertEquals(ItemValues.sellPrice(ItemKind.PISTOL, 6) - knife.price(), after.money());
        assertEquals(List.of(ItemKind.KNIFE), after.stash().stream()
                .map(HideoutService.StashEntry::kind).toList());

        for (int i = 1; i < GameConstants.STASH_CAPACITY; i++) {
            stash(me, ItemKind.CUP, 0);
        }
        assertThrows(TradeRefusedException.class, () -> hideout.buy(me, ItemKind.KNIFE),
                "a full stash has no room for a purchase");
    }

    @Test
    void aFullStashStillTakesEverythingBroughtOut() {
        for (int i = 0; i < GameConstants.STASH_CAPACITY; i++) {
            stash(me, ItemKind.CUP, 0);
        }
        GameSession session = hideout.setOut(me, "shuya", List.of());

        hideout.onExtracted(extracted(session.playerId(), new Item("i-1", ItemKind.PISTOL, 6)));

        assertEquals(GameConstants.STASH_CAPACITY + 1, hideout.view(me).stash().size());
    }

    @Test
    void aGuestGettingOutTouchesNothing() {
        hideout.onExtracted(extracted(sessions.issueGuest("kawada").playerId(),
                new Item("i-1", ItemKind.PISTOL, 6)));

        assertEquals(0, items.count());
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

    // --- The trader's errands ------------------------------------------------

    private void climb(Quests.Category category, int steps) {
        Account account = accounts.findById(me).orElseThrow();
        for (int i = 0; i < steps; i++) {
            account.nextQuest(category);
        }
        accounts.save(account);
    }

    private QuestLedger.QuestView errand(Quests.Category category) {
        return hideout.view(me).quests().stream()
                .filter(view -> view.category() == category)
                .findFirst().orElse(null);
    }

    @Test
    void oneErrandOfEachKindIsUnderWayAtOnce() {
        List<QuestLedger.QuestView> quests = hideout.view(me).quests();

        assertEquals(List.of(Quests.Category.DELIVERY, Quests.Category.VISIT,
                Quests.Category.SOLDIER), quests.stream().map(QuestLedger.QuestView::category).toList());
        assertEquals(List.of("첫 납품", "정찰", "첫 교전"),
                quests.stream().map(QuestLedger.QuestView::title).toList());
        assertEquals(Quests.Difficulty.EASY, quests.getFirst().difficulty());
        assertEquals(Quests.LADDERS.get(Quests.Category.DELIVERY).size(), quests.getFirst().total());
    }

    @Test
    void aDeliveryIsHandedOverFromTheStash() {
        QuestLedger.QuestView first = errand(Quests.Category.DELIVERY);
        assertEquals(List.of(new QuestLedger.Need(ItemKind.SPOON, 1, 0)), first.deliver());
        assertFalse(first.ready());
        assertThrows(TradeRefusedException.class, () -> hideout.deliverQuest(me),
                "nothing to hand over yet");

        StashItem spoon = stash(me, ItemKind.SPOON, 0);
        StashItem knife = stash(me, ItemKind.KNIFE, 0);
        assertTrue(errand(Quests.Category.DELIVERY).ready());

        HideoutService.StashView after = hideout.deliverQuest(me);

        assertTrue(items.findById(spoon.id()).isEmpty(), "the spoon went to the trader");
        assertTrue(items.findById(knife.id()).isPresent(), "nothing else did");
        assertEquals(30, after.money());
        assertEquals("소풍 준비물", errand(Quests.Category.DELIVERY).title());
        assertEquals("정찰", errand(Quests.Category.VISIT).title(), "the other ladders untouched");
    }

    @Test
    void soldiersAddUpOverTripsWhetherTheyEndInDeathOrEscape() {
        climb(Quests.Category.SOLDIER, 1);      // 소탕: three soldiers, over any trips
        assertEquals("소탕", errand(Quests.Category.SOLDIER).title());

        GameSession died = hideout.setOut(me, "shuya", List.of());
        hideout.onDeath(new GameEvent.Died(died.playerId(), "shuya", 0, 0, 0, null, null, true, 2));
        assertEquals(2, errand(Quests.Category.SOLDIER).soldiersDone());

        GameSession out = hideout.setOut(me, "shuya", List.of());
        hideout.onExtracted(new GameEvent.Extracted(out.playerId(), "shuya", 0, 5, 0, List.of(), 0, 1));

        HideoutService.StashView after = hideout.view(me);
        assertEquals("초소 함락", errand(Quests.Category.SOLDIER).title());
        assertEquals(0, errand(Quests.Category.SOLDIER).soldiersDone(), "starts over for the next");
        assertEquals(450, after.money());
        assertEquals(List.of(ItemKind.PISTOL), after.stash().stream()
                .map(HideoutService.StashEntry::kind).toList(), "the pistol came with it");
    }

    @Test
    void playerKillsDoNotCountForASoldierErrand() {
        GameSession out = hideout.setOut(me, "shuya", List.of());
        hideout.onExtracted(new GameEvent.Extracted(out.playerId(), "shuya", 0, 3, 0, List.of(), 0, 0));

        assertEquals("첫 교전", errand(Quests.Category.SOLDIER).title());
    }

    @Test
    void aOneTripSoldierErrandNeedsThemAllInOneTrip() {
        climb(Quests.Category.SOLDIER, 2);      // 초소 함락: two in one trip
        GameSession one = hideout.setOut(me, "shuya", List.of());
        hideout.onExtracted(new GameEvent.Extracted(one.playerId(), "shuya", 0, 0, 0, List.of(), 0, 1));
        GameSession two = hideout.setOut(me, "shuya", List.of());
        hideout.onExtracted(new GameEvent.Extracted(two.playerId(), "shuya", 0, 0, 0, List.of(), 0, 1));
        assertEquals("초소 함락", errand(Quests.Category.SOLDIER).title(), "one and one are not two");

        GameSession both = hideout.setOut(me, "shuya", List.of());
        hideout.onExtracted(new GameEvent.Extracted(both.playerId(), "shuya", 0, 0, 0, List.of(), 0, 2));
        assertNull(errand(Quests.Category.SOLDIER), "the ladder is climbed");
    }

    @Test
    void anErrandsRewardItemLandsInTheStashEvenWhenItIsFull() {
        climb(Quests.Category.DELIVERY, 2);     // 음악 시간: two recorders, a small bag
        stash(me, ItemKind.RECORDER, 0);
        stash(me, ItemKind.RECORDER, 0);
        for (int i = 2; i < GameConstants.STASH_CAPACITY; i++) {
            stash(me, ItemKind.CUP, 0);
        }

        HideoutService.StashView after = hideout.deliverQuest(me);

        assertEquals(GameConstants.STASH_CAPACITY - 1, after.stash().size(),
                "two recorders out, a bag in");
        assertTrue(after.stash().stream().anyMatch(e -> e.kind() == ItemKind.SMALL_BAG));
    }

    @Test
    void aVisitErrandMarksPlacesForTheTripAndAnEscapeHavingReachedThemCompletesIt() {
        assertEquals("정찰", errand(Quests.Category.VISIT).title());
        assertEquals(1, errand(Quests.Category.VISIT).visits());

        GameSession first = hideout.setOut(me, "shuya", List.of());
        assertEquals(1, first.marks());
        assertEquals(2, first.markDoors());
        hideout.onExtracted(new GameEvent.Extracted(first.playerId(), "shuya", 0, 0, 0,
                List.of(), 0));
        assertEquals("정찰", errand(Quests.Category.VISIT).title(), "out without the mark: not yet");

        GameSession second = hideout.setOut(me, "shuya", List.of());
        hideout.onExtracted(new GameEvent.Extracted(second.playerId(), "shuya", 0, 0, 0,
                List.of(), 1));

        assertEquals("수색", errand(Quests.Category.VISIT).title());
        assertEquals(80, hideout.view(me).money());
    }

    @Test
    void marksCountOnlyWithinOneTrip() {
        climb(Quests.Category.VISIT, 1);        // 수색: two marks in one trip
        GameSession one = hideout.setOut(me, "shuya", List.of());
        hideout.onExtracted(new GameEvent.Extracted(one.playerId(), "shuya", 0, 0, 0, List.of(), 1));
        GameSession two = hideout.setOut(me, "shuya", List.of());
        hideout.onExtracted(new GameEvent.Extracted(two.playerId(), "shuya", 0, 0, 0, List.of(), 1));

        assertEquals("수색", errand(Quests.Category.VISIT).title(), "one and one do not make two");

        GameSession both = hideout.setOut(me, "shuya", List.of());
        hideout.onExtracted(new GameEvent.Extracted(both.playerId(), "shuya", 0, 0, 0, List.of(), 2));
        assertEquals("위험 지역", errand(Quests.Category.VISIT).title());
    }

    @Test
    void aClimbedLadderLeavesTheBoardAndMarksNothing() {
        climb(Quests.Category.VISIT, Quests.LADDERS.get(Quests.Category.VISIT).size());
        climb(Quests.Category.DELIVERY, Quests.LADDERS.get(Quests.Category.DELIVERY).size());

        assertNull(errand(Quests.Category.VISIT));
        assertNull(errand(Quests.Category.DELIVERY));
        assertThrows(TradeRefusedException.class, () -> hideout.deliverQuest(me));
        assertEquals(0, hideout.setOut(me, "shuya", List.of()).marks());
    }

    @Test
    void theStashSaysHowBigItIs() {
        assertEquals(GameConstants.STASH_CAPACITY, hideout.view(me).capacity());
    }

    private void give(long money) {
        Account account = accounts.findById(me).orElseThrow();
        account.earn(money);
        accounts.save(account);
    }

    @Test
    void aBiggerStashIsBoughtInOrderAndKept() {
        HideoutService.StashView plain = hideout.view(me);
        assertEquals(new HideoutService.StashUpgrade(20, 500), plain.upgrade());
        assertThrows(TradeRefusedException.class, () -> hideout.growStash(me), "no money yet");
        assertEquals(10, hideout.view(me).capacity(), "a refused upgrade changes nothing");

        give(2_600);
        HideoutService.StashView big = hideout.growStash(me);
        assertEquals(20, big.capacity());
        assertEquals(2_100, big.money());
        assertEquals(new HideoutService.StashUpgrade(40, 2_000), big.upgrade());

        HideoutService.StashView fine = hideout.growStash(me);
        assertEquals(40, fine.capacity());
        assertEquals(100, fine.money());
        assertNull(fine.upgrade(), "nothing bigger");
        assertThrows(TradeRefusedException.class, () -> hideout.growStash(me));
    }

    @Test
    void aBiggerStashHasRoomForMorePurchases() {
        for (int i = 0; i < GameConstants.STASH_CAPACITY; i++) {
            stash(me, ItemKind.CUP, 0);
        }
        give(500 + 120);
        assertThrows(TradeRefusedException.class, () -> hideout.buy(me, ItemKind.KNIFE));

        hideout.growStash(me);
        HideoutService.StashView after = hideout.buy(me, ItemKind.KNIFE);

        assertEquals(GameConstants.STASH_CAPACITY + 1, after.stash().size());
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
