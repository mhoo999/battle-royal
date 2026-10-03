package com.example.battleroyal.web;

import com.example.battleroyal.game.rule.GameConstants;
import com.example.battleroyal.persistence.Account;
import com.example.battleroyal.persistence.AccountRepository;
import com.example.battleroyal.web.GameSessionService.InvalidNicknameException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Accounts behind Google sign-in. An account is created the first time a Google
 * subject is seen, and picks its nickname once.
 */
@Service
public class AccountService {

    /** Thrown when another account already plays under the nickname asked for. */
    public static class NicknameTakenException extends RuntimeException {
        public NicknameTakenException(String nickname) {
            super("Nickname is taken: " + nickname);
        }
    }

    /** Thrown when an account that already has a nickname asks for another. */
    public static class NicknameAlreadyChosenException extends RuntimeException {
        public NicknameAlreadyChosenException() {
            super("This account already has a nickname");
        }
    }

    private final AccountRepository accounts;
    private final Clock clock;

    @Autowired
    public AccountService(AccountRepository accounts) {
        this(accounts, Clock.systemUTC());
    }

    AccountService(AccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional
    public Account signIn(String googleSubject) {
        return accounts.findByGoogleSubject(googleSubject)
                .orElseGet(() -> accounts.save(new Account(googleSubject, clock.instant())));
    }

    /**
     * Sets the nickname of an account that has none yet. Account nicknames may not
     * start with the unranked prefix: that belongs to guests and test runs.
     */
    @Transactional
    public Account chooseNickname(String googleSubject, String rawNickname) {
        Account account = signIn(googleSubject);
        if (account.nickname() != null) {
            throw new NicknameAlreadyChosenException();
        }
        String nickname = rawNickname == null ? "" : rawNickname.trim();
        GameSessionService.requireLength(nickname);
        if (nickname.startsWith(GameConstants.UNRANKED_PREFIX)) {
            throw new InvalidNicknameException("A nickname may not start with "
                    + GameConstants.UNRANKED_PREFIX);
        }
        if (accounts.existsByNickname(nickname)) {
            throw new NicknameTakenException(nickname);
        }
        account.chooseNickname(nickname);
        try {
            return accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            // Somebody took it between the check and the write; the unique index decides.
            throw new NicknameTakenException(nickname);
        }
    }
}
