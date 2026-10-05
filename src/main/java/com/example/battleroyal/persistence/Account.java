package com.example.battleroyal.persistence;

import com.example.battleroyal.game.rule.Quests;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

/**
 * A signed-in player. Knows only Google's opaque subject id and the nickname chosen
 * here: no email, no name, no picture, because nothing in the game needs them.
 *
 * <p>The nickname is null until the first sign-in picks one, and unique among accounts,
 * so a ranking row names one person.
 */
@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Google's {@code sub}: stable for the user, meaningless anywhere else. */
    @Column(name = "google_subject", nullable = false, unique = true, length = 255)
    private String googleSubject;

    @Column(unique = true, length = 12)
    private String nickname;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Earned by selling to the trader, spent buying from it. Never negative. */
    // A default, so adding the column to a table that already has accounts works.
    @ColumnDefault("0")
    @Column(nullable = false)
    private long money;

    /**
     * The value of everything found on the island and brought out, added up over every
     * extraction: what the ranking counts (D5). Gear carried out from the stash and back
     * again is not counted, or a quick in-and-out with a pistol would farm it.
     */
    @ColumnDefault("0")
    @Column(nullable = false)
    private long haul;

    /** Which stash size has been bought: 0 is the plain box (GameConstants.STASH_SIZES). */
    @ColumnDefault("0")
    @Column(name = "stash_size", nullable = false)
    private int stashSize;

    /*
     * Where each of the trader's ladders stands (Quests.LADDERS): the index of the errand
     * under way, or the ladder's size once it is climbed. quest_step is the delivery
     * ladder; it held the one list of errands before they were split by kind.
     */
    @ColumnDefault("0")
    @Column(name = "quest_step", nullable = false)
    private int deliveryStep;

    @ColumnDefault("0")
    @Column(name = "quest_visit_step", nullable = false)
    private int visitStep;

    @ColumnDefault("0")
    @Column(name = "quest_soldier_step", nullable = false)
    private int soldierStep;

    /** Soldiers counted towards the soldier errand under way, since it was taken. */
    @ColumnDefault("0")
    @Column(name = "quest_kills", nullable = false)
    private int soldierKills;

    /*
     * The daily errands (V2.2): which day the counts below belong to (epoch day in
     * Seoul), which of the day's three are done (a bit each), and what today's trips
     * have added up. A new day starts them all over.
     */
    @ColumnDefault("0")
    @Column(name = "daily_day", nullable = false)
    private long dailyDay;

    @ColumnDefault("0")
    @Column(name = "daily_done", nullable = false)
    private int dailyDone;

    @ColumnDefault("0")
    @Column(name = "daily_extracts", nullable = false)
    private int dailyExtracts;

    @ColumnDefault("0")
    @Column(name = "daily_soldiers", nullable = false)
    private int dailySoldiers;

    /** Standing with the trader, from errands done (Reputation). */
    @ColumnDefault("0")
    @Column(nullable = false)
    private int reputation;

    protected Account() {
    }

    public Account(String googleSubject, Instant createdAt) {
        this.googleSubject = googleSubject;
        this.createdAt = createdAt;
    }

    public Long id() {
        return id;
    }

    public String googleSubject() {
        return googleSubject;
    }

    public String nickname() {
        return nickname;
    }

    public void chooseNickname(String nickname) {
        this.nickname = nickname;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public long money() {
        return money;
    }

    public void earn(long amount) {
        this.money += amount;
    }

    /** @return false, changing nothing, when there is not enough */
    public boolean spend(long amount) {
        if (amount > money) {
            return false;
        }
        this.money -= amount;
        return true;
    }

    public long haul() {
        return haul;
    }

    public void addHaul(long value) {
        this.haul += value;
    }

    public int stashSize() {
        return stashSize;
    }

    public void growStash() {
        this.stashSize++;
    }

    public int questStep(Quests.Category category) {
        return switch (category) {
            case DELIVERY -> deliveryStep;
            case VISIT -> visitStep;
            case SOLDIER -> soldierStep;
        };
    }

    public int soldierKills() {
        return soldierKills;
    }

    public void addSoldierKills(int kills) {
        this.soldierKills += kills;
    }

    /** That kind's errand is done: the next on its ladder; a soldier errand starts from none. */
    public void nextQuest(Quests.Category category) {
        switch (category) {
            case DELIVERY -> deliveryStep++;
            case VISIT -> visitStep++;
            case SOLDIER -> {
                soldierStep++;
                soldierKills = 0;
            }
        }
    }

    /** Makes the daily counts today's, starting them over if they were another day's. */
    public void dailyFor(long epochDay) {
        if (dailyDay != epochDay) {
            dailyDay = epochDay;
            dailyDone = 0;
            dailyExtracts = 0;
            dailySoldiers = 0;
        }
    }

    public boolean dailyDone(int slot) {
        return (dailyDone & (1 << slot)) != 0;
    }

    public void markDailyDone(int slot) {
        dailyDone |= 1 << slot;
    }

    public int dailyExtracts() {
        return dailyExtracts;
    }

    public int dailySoldiers() {
        return dailySoldiers;
    }

    /** A trip ended today: whether it got out, and how many soldiers it brought down. */
    public void addDailyTrip(boolean extracted, int soldiers) {
        if (extracted) {
            dailyExtracts++;
        }
        dailySoldiers += soldiers;
    }

    public int reputation() {
        return reputation;
    }

    public void addReputation(int standing) {
        this.reputation += standing;
    }

    /** A new season (D12): everyone back to the same starting line, errands included. */
    public void resetForSeason() {
        this.reputation = 0;
        this.money = 0;
        this.haul = 0;
        this.stashSize = 0;
        this.deliveryStep = 0;
        this.visitStep = 0;
        this.soldierStep = 0;
        this.soldierKills = 0;
    }
}
