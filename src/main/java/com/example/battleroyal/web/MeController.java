package com.example.battleroyal.web;

import com.example.battleroyal.persistence.Account;
import com.example.battleroyal.web.AccountService.NicknameAlreadyChosenException;
import com.example.battleroyal.web.AccountService.NicknameTakenException;
import com.example.battleroyal.web.GameSessionService.InvalidNicknameException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who the browser is signed in as, for the lobby to decide what to show: the Google
 * button, the nickname form, or the account's name.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    public record Me(boolean signedIn, String nickname) {
    }

    public record NicknameRequest(String nickname) {
    }

    private final AccountService accounts;

    public MeController(AccountService accounts) {
        this.accounts = accounts;
    }

    /** Open to everyone: a guest gets {@code signedIn: false} rather than an error. */
    @GetMapping
    public Me me(@AuthenticationPrincipal OidcUser user) {
        if (user == null) {
            return new Me(false, null);
        }
        Account account = accounts.signIn(user.getSubject());
        return new Me(true, account.nickname());
    }

    /** Signed-in only (see SecurityConfig). */
    @PostMapping("/nickname")
    public Me chooseNickname(@AuthenticationPrincipal OidcUser user,
                             @RequestBody NicknameRequest request) {
        Account account = accounts.chooseNickname(user.getSubject(), request.nickname());
        return new Me(true, account.nickname());
    }

    @ExceptionHandler(InvalidNicknameException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String badNickname(InvalidNicknameException e) {
        return e.getMessage();
    }

    @ExceptionHandler({NicknameTakenException.class, NicknameAlreadyChosenException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict(RuntimeException e) {
        return e.getMessage();
    }
}
