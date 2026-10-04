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

    /** A new season (D12): everyone back to the same starting line, errands included. */
    public void resetForSeason() {
        this.money = 0;
        this.haul = 0;
        this.stashSize = 0;
        this.deliveryStep = 0;
        this.visitStep = 0;
        this.soldierStep = 0;
        this.soldierKills = 0;
    }
}
