package com.example.battleroyal.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Load-test bots sign in like players, and only where the loadtest profile is on. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("loadtest")
class LoadTestLoginTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void aBotSessionIsASignedInPlayer() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/loadtest/login/bot-001").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("bot:bot-001"));

        mvc.perform(get("/api/me").session(session))
                .andExpect(jsonPath("$.signedIn").value(true));
        mvc.perform(post("/api/me/nickname").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"bot-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("bot-001"));
        mvc.perform(get("/api/hideout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stash").isArray());
    }

    @Test
    void botNamesArePlain() throws Exception {
        mvc.perform(post("/api/loadtest/login/Bot_1"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void loadtestRefusesToRunBesideProd() {
        MockEnvironment both = new MockEnvironment();
        both.setActiveProfiles("prod", "loadtest");
        assertThrows(IllegalStateException.class, () -> new LoadTestLogin(both));
    }
}
