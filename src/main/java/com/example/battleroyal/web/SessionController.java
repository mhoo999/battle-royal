package com.example.battleroyal.web;

import com.example.battleroyal.web.GuestSessionService.GuestSession;
import com.example.battleroyal.web.GuestSessionService.InvalidNicknameException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only thing standing between the lobby screen and the game: hand over a nickname,
 * get a token, open a socket.
 */
@RestController
@RequestMapping("/api/session")
public class SessionController {

    public record CreateRequest(String nickname) {
    }

    public record CreateResponse(String token, String playerId, String nickname) {
    }

    private final GuestSessionService sessions;

    public SessionController(GuestSessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping
    public CreateResponse create(@RequestBody CreateRequest request) {
        GuestSession session = sessions.issue(request.nickname());
        return new CreateResponse(session.token(), session.playerId(), session.nickname());
    }

    @ExceptionHandler(InvalidNicknameException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String badNickname(InvalidNicknameException e) {
        return e.getMessage();
    }
}
