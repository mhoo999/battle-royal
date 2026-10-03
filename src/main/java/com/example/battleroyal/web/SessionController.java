package com.example.battleroyal.web;

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
 * Guests into the game: name in, token out, open a socket. A signed-in account does
 * not come this way; it sets out from the hideout ({@code POST /api/hideout/sortie}).
 */
@RestController
@RequestMapping("/api/session")
public class SessionController {

    public record CreateRequest(String nickname) {
    }

    public record CreateResponse(String token, String playerId, String nickname) {
    }

    /** Thrown when a signed-in account asks here instead of setting out from the hideout. */
    public static class UseTheHideoutException extends RuntimeException {
        public UseTheHideoutException() {
            super("거점에서 출발하세요");
        }
    }

    /** Thrown when a signed-in account has not picked a nickname yet. */
    public static class NicknameRequiredException extends RuntimeException {
        public NicknameRequiredException() {
            super("닉네임을 먼저 정하세요");
        }
    }

    private final GameSessionService sessions;

    public SessionController(GameSessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping
    public CreateResponse create(@AuthenticationPrincipal OidcUser user,
                                 @RequestBody(required = false) CreateRequest request) {
        if (user != null) {
            // An account sets out from the hideout, with whatever it chose to carry.
            throw new UseTheHideoutException();
        }
        GameSession session = sessions.issueGuest(request == null ? null : request.nickname());
        return new CreateResponse(session.token(), session.playerId(), session.nickname());
    }

    @ExceptionHandler(InvalidNicknameException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String badNickname(InvalidNicknameException e) {
        return e.getMessage();
    }

    @ExceptionHandler({NicknameRequiredException.class, UseTheHideoutException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict(RuntimeException e) {
        return e.getMessage();
    }
}
