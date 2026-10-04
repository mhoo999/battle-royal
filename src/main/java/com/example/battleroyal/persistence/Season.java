package com.example.battleroyal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One season (D12): from its start until its deadline the ranking, the stashes and the
 * money build up; at the deadline they are wiped and trophies handed out. The open
 * season is the one with no {@code endedAt}; there is always exactly one.
 */
@Entity
@Table(name = "season")
public class Season {

    /** The season's number, 1 for the first: also its primary key. */
    @Id
    private Integer number;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    /** When the wipe actually ran; null while the season is open. */
    @Column(name = "ended_at")
    private Instant endedAt;

    protected Season() {
    }

    public Season(int number, Instant startedAt, Instant endsAt) {
        this.number = number;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
    }

    public int number() {
        return number;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endsAt() {
        return endsAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public void end(Instant at) {
        this.endedAt = at;
    }
}
