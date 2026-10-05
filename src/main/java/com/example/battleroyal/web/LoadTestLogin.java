package com.example.battleroyal.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Sign-in for load-test bots (docs/ROADMAP.md S2), and only under the {@code loadtest}
 * profile: production has no such endpoint at all.
 *
 * <p>A bot gets the same kind of session a Google sign-in leaves behind — an OIDC user
 * whose subject is {@code bot:<name>} — so the hideout, the stash and the errands treat
 * it exactly like a player, and nothing else in the server knows bots exist.
 *
 * <p>The application refuses to start with {@code loadtest} and {@code prod} together.
 */
@RestController
@Profile("loadtest")
public class LoadTestLogin {

    /** Bot names: short, plain, and never mistaken for a Google subject. */
    private static final Pattern NAME = Pattern.compile("[a-z0-9-]{1,12}");

    private final SecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public LoadTestLogin(Environment environment) {
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            throw new IllegalStateException("the loadtest profile must never run with prod");
        }
    }

    /** Signs this browser session in as bot {@code name}; the session cookie carries it. */
    @PostMapping("/api/loadtest/login/{name}")
    public Map<String, String> login(@PathVariable String name, HttpServletRequest request,
                                     HttpServletResponse response) {
        if (!NAME.matcher(name).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bot names are [a-z0-9-]{1,12}");
        }
        String subject = "bot:" + name;
        Instant now = Instant.now();
        OidcIdToken token = new OidcIdToken("loadtest", now, now.plusSeconds(86_400),
                Map.of("sub", subject));
        OidcUser user = new DefaultOidcUser(AuthorityUtils.createAuthorityList("OIDC_USER"),
                token);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new OAuth2AuthenticationToken(user, user.getAuthorities(),
                "loadtest"));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return Map.of("subject", subject);
    }
}
