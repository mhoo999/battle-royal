package com.example.battleroyal.web;

import com.example.battleroyal.persistence.Account;
import com.example.battleroyal.web.GameSessionService.GameSession;
import com.example.battleroyal.web.GameSessionService.InvalidNicknameException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only thing standing between the lobby screen and the game: get a token, open a
 * socket. Signed in, the account's own nickname is used and the request body is
 * ignored; otherwise the body names a guest.
 */
@RestController
@RequestMapping("/api/session")
public class SessionController {

    public record CreateRequest(String nickname) {
    }

    public record CreateResponse(String token, String playerId, String nickname) {
    }

    /** Thrown when a signed-in account has not picked a nickname yet. */
    public static class NicknameRequiredException extends RuntimeException {
        public NicknameRequiredException() {
            super("닉네임을 먼저 정하세요");
        }
    }

    private final GameSessionService sessions;
    private final AccountService accounts;

    public SessionController(GameSessionService sessions, AccountService accounts) {
        this.sessions = sessions;
        this.accounts = accounts;
    }

    @PostMapping
    public CreateResponse create(@AuthenticationPrincipal OidcUser user,
                                 @RequestBody(required = false) CreateRequest request) {
        GameSession session;
        if (user != null) {
            Account account = accounts.signIn(user.getSubject());
            if (account.nickname() == null) {
                throw new NicknameRequiredException();
            }
            session = sessions.issueForAccount(account.id(), account.nickname());
        } else {
            session = sessions.issueGuest(request == null ? null : request.nickname());
        }
        return new CreateResponse(session.token(), session.playerId(), session.nickname());
    }

    @ExceptionHandler(InvalidNicknameException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String badNickname(InvalidNicknameException e) {
        return e.getMessage();
    }

    @ExceptionHandler(NicknameRequiredException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String nicknameRequired(NicknameRequiredException e) {
        return e.getMessage();
    }
}
