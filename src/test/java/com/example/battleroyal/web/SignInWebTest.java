package com.example.battleroyal.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The sign-in surface as a browser sees it: what is open to guests, what needs Google,
 * and which nickname a session plays under.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SignInWebTest {

    @Autowired
    private MockMvc mvc;

    private static RequestPostProcessor google(String subject) {
        return oidcLogin().idToken(token -> token.subject(subject));
    }

    private static String nickname(String name) {
        return "{\"nickname\":\"" + name + "\"}";
    }

    @Test
    void theGameAndTheRankingStayOpenToGuests() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/api/ranking")).andExpect(status().isOk());
        mvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signedIn").value(false));
    }

    @Test
    void aGuestSessionPlaysUnranked() throws Exception {
        mvc.perform(post("/api/session").contentType(MediaType.APPLICATION_JSON)
                        .content(nickname("kang")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("~kang"));
    }

    @Test
    void aBlockedNameIsRefusedInWordsThePlayerReads() throws Exception {
        String body = mvc.perform(post("/api/session").contentType(MediaType.APPLICATION_JSON)
                        .content(nickname("씨 발")))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals("쓸 수 없는 닉네임입니다", body);
    }

    @Test
    void theHideoutNeedsASignIn() throws Exception {
        mvc.perform(get("/api/hideout")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/hideout/sortie")).andExpect(status().isUnauthorized());
    }

    @Test
    void choosingANicknameNeedsASignIn() throws Exception {
        mvc.perform(post("/api/me/nickname").contentType(MediaType.APPLICATION_JSON)
                        .content(nickname("kang")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aSignedInAccountPicksANicknameThenPlaysUnderIt() throws Exception {
        mvc.perform(get("/api/me").with(google("g-web-1")))
                .andExpect(jsonPath("$.signedIn").value(true))
                .andExpect(jsonPath("$.nickname").doesNotExist());

        mvc.perform(post("/api/hideout/sortie").with(google("g-web-1")))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/me/nickname").with(google("g-web-1"))
                        .contentType(MediaType.APPLICATION_JSON).content(nickname("shuya")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("shuya"));

        // An account plays under its own name, and only by setting out from the hideout.
        mvc.perform(post("/api/session").with(google("g-web-1"))
                        .contentType(MediaType.APPLICATION_JSON).content(nickname("kiriyama")))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/hideout").with(google("g-web-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(10))
                .andExpect(jsonPath("$.out").value(false));
        mvc.perform(post("/api/hideout/sortie").with(google("g-web-1"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"loadout\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("shuya"));
        mvc.perform(post("/api/hideout/sortie").with(google("g-web-1")))
                .andExpect(status().isConflict());
    }

    @Test
    void aTakenNicknameIsRefused() throws Exception {
        mvc.perform(post("/api/me/nickname").with(google("g-web-2"))
                        .contentType(MediaType.APPLICATION_JSON).content(nickname("noriko")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/me/nickname").with(google("g-web-3"))
                        .contentType(MediaType.APPLICATION_JSON).content(nickname("noriko")))
                .andExpect(status().isConflict());
    }
}
