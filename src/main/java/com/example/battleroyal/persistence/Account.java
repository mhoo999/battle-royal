package com.example.battleroyal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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
}
