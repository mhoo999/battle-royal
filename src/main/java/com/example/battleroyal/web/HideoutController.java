package com.example.battleroyal.web;

import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.persistence.Account;
import com.example.battleroyal.web.HideoutService.AlreadyOutException;
import com.example.battleroyal.web.HideoutService.InvalidLoadoutException;
import com.example.battleroyal.web.HideoutService.StashView;
import com.example.battleroyal.web.HideoutService.TradeRefusedException;
import com.example.battleroyal.web.SessionController.CreateResponse;
import com.example.battleroyal.web.SessionController.NicknameRequiredException;
import com.example.battleroyal.web.GameSessionService.GameSession;
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

import java.util.List;

/**
 * The hideout, for signed-in accounts: look at the stash, trade with the trader, set
 * out with up to three of its items. Guests have no hideout and go straight to the island.
 */
@RestController
@RequestMapping("/api/hideout")
public class HideoutController {

    /** @param loadout stash item ids slot by slot, null for an empty slot */
    public record SetOutRequest(List<Long> loadout) {
    }

    public record SellRequest(long itemId) {
    }

    public record BuyRequest(ItemKind kind) {
    }

    private final AccountService accounts;
    private final HideoutService hideout;

    public HideoutController(AccountService accounts, HideoutService hideout) {
        this.accounts = accounts;
        this.hideout = hideout;
    }

    @GetMapping
    public StashView stash(@AuthenticationPrincipal OidcUser user) {
        return hideout.view(named(user).id());
    }

    @PostMapping("/sortie")
    public CreateResponse setOut(@AuthenticationPrincipal OidcUser user,
                                 @RequestBody(required = false) SetOutRequest request) {
        Account account = named(user);
        List<Long> loadout = request == null || request.loadout() == null
                ? List.of() : request.loadout();
        GameSession session = hideout.setOut(account.id(), account.nickname(), loadout);
        return new CreateResponse(session.token(), session.playerId(), session.nickname());
    }

    @PostMapping("/sell")
    public StashView sell(@AuthenticationPrincipal OidcUser user, @RequestBody SellRequest request) {
        return hideout.sell(named(user).id(), request.itemId());
    }

    @PostMapping("/buy")
    public StashView buy(@AuthenticationPrincipal OidcUser user, @RequestBody BuyRequest request) {
        return hideout.buy(named(user).id(), request.kind());
    }

    private Account named(OidcUser user) {
        Account account = accounts.signIn(user.getSubject());
        if (account.nickname() == null) {
            throw new NicknameRequiredException();
        }
        return account;
    }

    @ExceptionHandler({NicknameRequiredException.class, AlreadyOutException.class,
            TradeRefusedException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict(RuntimeException e) {
        return e.getMessage();
    }

    @ExceptionHandler(InvalidLoadoutException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String badLoadout(InvalidLoadoutException e) {
        return e.getMessage();
    }
}
