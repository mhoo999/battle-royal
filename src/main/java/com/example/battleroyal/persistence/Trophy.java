package com.example.battleroyal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * What a season leaves behind (D13): the one thing a wipe does not take. One per
 * account per season, the best tier it reached by the final ranking (Q11).
 */
@Entity
@Table(name = "trophy", indexes = @Index(name = "idx_trophy_account", columnList = "account_id"))
public class Trophy {

    /** Best first. */
    public enum Tier { CHAMPION, TOP10, PARTICIPANT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private int season;

    // A plain VARCHAR, like StashItem.kind: an @Enumerated column would be a MySQL ENUM
    // that ddl-auto=update never widens.
    @Column(nullable = false, length = 16)
    private String tier;

    /** The final rank on the season's ranking; null for a participant who found nothing. */
    @Column
    private Integer placing;

    protected Trophy() {
    }

    public Trophy(long accountId, int season, Tier tier, Integer placing) {
        this.accountId = accountId;
        this.season = season;
        this.tier = tier.name();
        this.placing = placing;
    }

    public Long accountId() {
        return accountId;
    }

    public int season() {
        return season;
    }

    public Tier tier() {
        return Tier.valueOf(tier);
    }

    public Integer placing() {
        return placing;
    }
}
