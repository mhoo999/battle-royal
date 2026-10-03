package com.example.battleroyal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Google sign-in, and almost nothing else.
 *
 * <p>Everything stays open — the page, the ranking, guest sessions, the game socket —
 * because a guest can play without signing in. Only choosing an account nickname needs
 * a signed-in user. The game socket is authenticated by its own session token, not by
 * this login; see {@code GameSessionService}.
 *
 * <p>CSRF tokens are off. The session cookie is {@code SameSite=Lax}, so a cross-site
 * form cannot carry it into a POST, and every state-changing call here is a POST from
 * this page's own script.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/me/nickname", "/api/hideout/**").authenticated()
                        .anyRequest().permitAll())
                // An API call without a session gets 401, not a redirect to Google.
                .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
                .oauth2Login(login -> login.defaultSuccessUrl("/", true))
                .logout(logout -> logout.logoutSuccessUrl("/"))
                // The H2 console, local only, draws itself in frames.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }
}
