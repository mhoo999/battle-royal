package com.example.battleroyal.web;

import com.example.battleroyal.persistence.Account;
import com.example.battleroyal.persistence.AccountRepository;
import com.example.battleroyal.web.AccountService.NicknameAlreadyChosenException;
import com.example.battleroyal.web.AccountService.NicknameTakenException;
import com.example.battleroyal.web.GameSessionService.InvalidNicknameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class AccountServiceTest {

    @Autowired
    private AccountRepository repository;

    private AccountService accounts;

    @BeforeEach
    void setUp() {
        accounts = new AccountService(repository);
    }

    @Test
    void theFirstSignInCreatesTheAccountAndLaterOnesFindIt() {
        Account first = accounts.signIn("google-1");
        Account again = accounts.signIn("google-1");

        assertEquals(first.id(), again.id());
        assertEquals(1, repository.count());
        assertNull(first.nickname(), "the nickname is picked after signing in");
    }

    @Test
    void aNicknameIsChosenOnce() {
        Account account = accounts.chooseNickname("google-1", "  kang ");

        assertEquals("kang", account.nickname());
        assertThrows(NicknameAlreadyChosenException.class,
                () -> accounts.chooseNickname("google-1", "lee"));
    }

    @Test
    void twoAccountsCannotShareANickname() {
        accounts.chooseNickname("google-1", "kang");

        assertThrows(NicknameTakenException.class,
                () -> accounts.chooseNickname("google-2", "kang"),
                "a ranking row has to name one person");
    }

    @Test
    void theUnrankedPrefixBelongsToGuests() {
        assertThrows(InvalidNicknameException.class,
                () -> accounts.chooseNickname("google-1", "~kang"));
    }

    @Test
    void aNicknameHasALength() {
        assertThrows(InvalidNicknameException.class,
                () -> accounts.chooseNickname("google-1", "   "));
        assertThrows(InvalidNicknameException.class,
                () -> accounts.chooseNickname("google-1", "thirteen-char"));
    }
}
