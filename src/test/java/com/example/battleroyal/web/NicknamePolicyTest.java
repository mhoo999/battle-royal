package com.example.battleroyal.web;

import com.example.battleroyal.web.GameSessionService.InvalidNicknameException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NicknamePolicyTest {

    @Test
    void ordinaryNamesPass() {
        for (String name : List.of("shuya", "noriko", "kiriyama", "mitsuko", "유승훈",
                "사스케", "hunter2", "alpha", "강", "Kawada_07")) {
            assertFalse(NicknamePolicy.blocked(name), name + " should be allowed");
        }
    }

    @Test
    void blockedWordsAreCaughtHoweverTheyAreSpelled() {
        for (String name : List.of(
                "씨발",          // plain
                "씨 발",         // spaced
                "씨.발놈",        // punctuated, inside a longer name
                "ㅅㅂ",          // abbreviated to consonants
                "FUCK",         // upper case
                "f.u.c.k",      // dotted
                "s3x",          // digit for a letter
                "ｆｕｃｋ",       // full-width letters
                "병신123",
                "porn_king",
                "운영자",         // posing as staff
                "Admin")) {
            assertTrue(NicknamePolicy.blocked(name), name + " should be blocked");
        }
    }

    @Test
    void normalizingDropsEverythingButLetters() {
        assertEquals("fuck", NicknamePolicy.normalize("F.u-c_K"));
        assertEquals("sex", NicknamePolicy.normalize("$3x"));
        assertEquals(NicknamePolicy.normalize("ㅅㅂ"), NicknamePolicy.normalize("ㅅ ㅂ"));
    }

    @Test
    void theReasonIsGivenInWordsThePlayerReads() {
        InvalidNicknameException blocked = assertThrows(InvalidNicknameException.class,
                () -> NicknamePolicy.require("섹스왕"));
        assertEquals("쓸 수 없는 닉네임입니다", blocked.getMessage());

        InvalidNicknameException tooLong = assertThrows(InvalidNicknameException.class,
                () -> NicknamePolicy.require("thirteen-char"));
        assertEquals("닉네임은 1~12자입니다", tooLong.getMessage());

        assertDoesNotThrow(() -> NicknamePolicy.require("shuya"));
    }

    @Test
    void guestsAndAccountsAreHeldToTheSameWords() {
        GameSessionService sessions = new GameSessionService();
        assertThrows(InvalidNicknameException.class, () -> sessions.issueGuest("~씨발"),
                "the unranked prefix is no way round the filter");
    }
}
