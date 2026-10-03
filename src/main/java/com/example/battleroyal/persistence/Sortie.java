package com.example.battleroyal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One trip from the hideout onto the island. Opened when the player sets out, closed
 * by how it ended. An account has at most one {@link Outcome#OUT} at a time.
 *
 * <p>The record of what went out is the stash items pointing at this sortie. That is
 * what lets a server that died mid-trip hand the gear back when it starts again,
 * without relying on anything having run at shutdown.
 */
@Entity
@Table(name = "sortie", indexes = @Index(name = "idx_sortie_account_outcome", columnList = "account_id, outcome"))
public class Sortie {

    public enum Outcome { OUT, DIED, EXTRACTED, REFUNDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Outcome outcome;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected Sortie() {
        // for JPA
    }

    public Sortie(Long accountId, Instant startedAt) {
        this.accountId = accountId;
        this.outcome = Outcome.OUT;
        this.startedAt = startedAt;
    }

    public Long id() {
        return id;
    }

    public Long accountId() {
        return accountId;
    }

    public Outcome outcome() {
        return outcome;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public void end(Outcome outcome, Instant at) {
        this.outcome = outcome;
        this.endedAt = at;
    }
}
