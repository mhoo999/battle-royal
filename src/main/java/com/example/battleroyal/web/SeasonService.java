package com.example.battleroyal.web;

import com.example.battleroyal.game.loop.RoomRegistry;
import com.example.battleroyal.game.rule.GameConstants;
import com.example.battleroyal.persistence.Account;
import com.example.battleroyal.persistence.AccountRepository;
import com.example.battleroyal.persistence.Season;
import com.example.battleroyal.persistence.SeasonRepository;
import com.example.battleroyal.persistence.Sortie;
import com.example.battleroyal.persistence.SortieRepository;
import com.example.battleroyal.persistence.StashItemRepository;
import com.example.battleroyal.persistence.Trophy;
import com.example.battleroyal.persistence.TrophyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Seasons (D12, D13): four weeks each, ending at midnight in Seoul. At the deadline
 * everyone on the island with a sortie is sent home empty-handed, trophies are handed
 * out by the final ranking, and the ranking, stashes, money and stash sizes are wiped.
 * Accounts, nicknames and trophies stay.
 *
 * <p>Checked once a minute rather than timed to the second: a season that ends a minute
 * late changes nothing anyone can notice. A server that was down at the deadline ends
 * the season when it comes back.
 */
@Service
public class SeasonService {

    private static final Logger log = LoggerFactory.getLogger(SeasonService.class);

    /** Seasons end at midnight here. */
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public record SeasonView(int number, Instant endsAt) {
    }

    private final SeasonRepository seasons;
    private final AccountRepository accounts;
    private final StashItemRepository items;
    private final SortieRepository sorties;
    private final TrophyRepository trophies;
    private final HideoutService hideout;
    private final RoomRegistry registry;
    private final TransactionTemplate tx;
    private final Clock clock;

    @Autowired
    public SeasonService(SeasonRepository seasons, AccountRepository accounts,
                         StashItemRepository items, SortieRepository sorties,
                         TrophyRepository trophies, HideoutService hideout,
                         RoomRegistry registry, PlatformTransactionManager transactions) {
        this(seasons, accounts, items, sorties, trophies, hideout, registry, transactions,
                Clock.systemUTC());
    }

    SeasonService(SeasonRepository seasons, AccountRepository accounts,
                  StashItemRepository items, SortieRepository sorties,
                  TrophyRepository trophies, HideoutService hideout, RoomRegistry registry,
                  PlatformTransactionManager transactions, Clock clock) {
        this.seasons = seasons;
        this.accounts = accounts;
        this.items = items;
        this.sorties = sorties;
        this.trophies = trophies;
        this.hideout = hideout;
        this.registry = registry;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /** The season under way. There is always one once the application has started. */
    public SeasonView current() {
        Season open = seasons.findFirstByEndedAtIsNullOrderByNumberDesc().orElse(null);
        return open == null ? null : new SeasonView(open.number(), open.endsAt());
    }

    public List<Trophy> trophiesOf(long accountId) {
        return trophies.findByAccountIdOrderBySeasonAsc(accountId);
    }

    /** The first start opens season 1. Later starts find their season already open. */
    @EventListener(ApplicationReadyEvent.class)
    public void openFirstSeason() {
        tx.executeWithoutResult(status -> {
            if (seasons.findFirstByEndedAtIsNullOrderByNumberDesc().isEmpty()) {
                int number = seasons.findFirstByOrderByNumberDesc()
                        .map(last -> last.number() + 1).orElse(1);
                Instant now = clock.instant();
                seasons.save(new Season(number, now, deadlineFrom(now)));
                log.info("Season {} opened", number);
            }
        });
    }

    /** Ends the season once its deadline has passed. */
    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void endIfDue() {
        Instant now = clock.instant();
        Season open = seasons.findFirstByEndedAtIsNullOrderByNumberDesc().orElse(null);
        if (open == null || now.isBefore(open.endsAt())) {
            return;
        }
        // Off the island first: the loop takes them on its next tick, and whatever
        // they carried is no longer anyone's.
        List<String> out = hideout.takeAllOut();
        out.forEach(registry::requestEject);
        tx.executeWithoutResult(status -> close(open.number(), now));
        log.info("Season {} ended: {} sent home from the island", open.number(), out.size());
    }

    private void close(int number, Instant now) {
        Season season = seasons.findById(number).orElseThrow();
        if (season.endedAt() != null) {
            return;
        }
        for (Sortie sortie : sorties.findByOutcome(Sortie.Outcome.OUT)) {
            sortie.end(Sortie.Outcome.REFUNDED, now);
        }
        handOutTrophies(season);
        items.deleteAllInBatch();
        accounts.findAll().forEach(Account::resetForSeason);
        season.end(now);
        seasons.save(new Season(number + 1, now, deadlineFrom(now)));
    }

    /**
     * One trophy per account, its best (Q11): first place, the top ten, or having got
     * off the island at least once this season. Equal hauls share a rank, as on the
     * ranking itself.
     */
    private void handOutTrophies(Season season) {
        Set<Long> placed = new HashSet<>();
        for (Account account : accounts.findByHaulGreaterThanOrderByHaulDescIdAsc(0, Pageable.unpaged())) {
            int rank = 1 + (int) accounts.countByHaulGreaterThan(account.haul());
            Trophy.Tier tier = rank == 1 ? Trophy.Tier.CHAMPION
                    : rank <= GameConstants.SEASON_TOP ? Trophy.Tier.TOP10
                    : Trophy.Tier.PARTICIPANT;
            trophies.save(new Trophy(account.id(), season.number(), tier, rank));
            placed.add(account.id());
        }
        for (Long accountId : sorties.accountsWith(Sortie.Outcome.EXTRACTED, season.startedAt())) {
            if (placed.add(accountId)) {
                trophies.save(new Trophy(accountId, season.number(), Trophy.Tier.PARTICIPANT, null));
            }
        }
    }

    /** Midnight in Seoul, {@link GameConstants#SEASON_DAYS} days after the day of {@code start}. */
    static Instant deadlineFrom(Instant start) {
        return start.atZone(SEOUL).toLocalDate().plusDays(GameConstants.SEASON_DAYS)
                .atStartOfDay(SEOUL).toInstant();
    }
}
