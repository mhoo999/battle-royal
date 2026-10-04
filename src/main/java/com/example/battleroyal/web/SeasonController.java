package com.example.battleroyal.web;

import com.example.battleroyal.persistence.Account;
import com.example.battleroyal.persistence.Trophy;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * The season under way, open to everyone, and the signed-in viewer's trophies: the one
 * thing a wipe leaves (D13).
 */
@RestController
@RequestMapping("/api/season")
public class SeasonController {

    /** @param placing the final rank, or null for having taken part */
    public record TrophyView(int season, Trophy.Tier tier, Integer placing) {
    }

    /** @param trophies the viewer's, oldest season first; empty for a guest */
    public record SeasonResponse(int number, Instant endsAt, List<TrophyView> trophies) {
    }

    private final SeasonService seasons;
    private final AccountService accounts;

    public SeasonController(SeasonService seasons, AccountService accounts) {
        this.seasons = seasons;
        this.accounts = accounts;
    }

    @GetMapping
    public SeasonResponse season(@AuthenticationPrincipal OidcUser user) {
        SeasonService.SeasonView current = seasons.current();
        List<TrophyView> mine = List.of();
        if (user != null) {
            Account account = accounts.signIn(user.getSubject());
            mine = seasons.trophiesOf(account.id()).stream()
                    .map(trophy -> new TrophyView(trophy.season(), trophy.tier(), trophy.placing()))
                    .toList();
        }
        return current == null
                ? new SeasonResponse(0, null, mine)
                : new SeasonResponse(current.number(), current.endsAt(), mine);
    }
}
