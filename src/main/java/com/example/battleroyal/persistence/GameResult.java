package com.example.battleroyal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One finished life. Written once, when the player dies, and never updated.
 *
 * <p>This and nothing else goes to the database from a game: no positions, no ticks,
 * no world state. The ranking is a query over these rows, not a table of its own.
 */
@Entity
@Table(name = "game_result", indexes = @Index(name = "idx_game_result_score", columnList = "score"))
public class GameResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 12)
    private String nickname;

    private int score;

    private int kills;

    private long survivedSeconds;

    @Column(nullable = false)
    private Instant endedAt;

    protected GameResult() {
        // for JPA
    }

    public GameResult(String nickname, int score, int kills, long survivedSeconds,
                      Instant endedAt) {
        this.nickname = nickname;
        this.score = score;
        this.kills = kills;
        this.survivedSeconds = survivedSeconds;
        this.endedAt = endedAt;
    }

    public Long id() {
        return id;
    }

    public String nickname() {
        return nickname;
    }

    public int score() {
        return score;
    }

    public int kills() {
        return kills;
    }

    public long survivedSeconds() {
        return survivedSeconds;
    }

    public Instant endedAt() {
        return endedAt;
    }
}
