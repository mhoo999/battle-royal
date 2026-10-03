package com.example.battleroyal.web;

import com.example.battleroyal.persistence.Account;
import com.example.battleroyal.persistence.AccountRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * The biggest hauls, biggest first (D5): accounts by the value of everything they have
 * found and got off the island. Guests have no account and so no place on it.
 *
 * <p>A plain query over the account table; nothing is kept up to date in real time.
 */
@RestController
@RequestMapping("/api/ranking")
public class RankingController {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    public record Row(long rank, String nickname, long value) {
    }

    /** @param me the signed-in viewer's own row, wherever it stands; null otherwise */
    public record Ranking(List<Row> top, Row me) {
    }

    private final AccountRepository accounts;
    private final AccountService accountService;

    public RankingController(AccountRepository accounts, AccountService accountService) {
        this.accounts = accounts;
        this.accountService = accountService;
    }

    /** Equal hauls share a rank: one more than the number strictly bigger. */
    @GetMapping
    public Ranking top(@AuthenticationPrincipal OidcUser user,
                       @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        int size = Math.clamp(limit, 1, MAX_LIMIT);
        List<Row> top = new ArrayList<>();
        for (Account account : accounts.findByHaulGreaterThanOrderByHaulDescIdAsc(
                0, PageRequest.of(0, size))) {
            top.add(row(account));
        }
        return new Ranking(top, mine(user));
    }

    private Row mine(OidcUser user) {
        if (user == null) {
            return null;
        }
        Account account = accountService.signIn(user.getSubject());
        if (account.nickname() == null || account.haul() == 0) {
            return null;
        }
        return row(account);
    }

    private Row row(Account account) {
        return new Row(1 + accounts.countByHaulGreaterThan(account.haul()),
                account.nickname(), account.haul());
    }
}
