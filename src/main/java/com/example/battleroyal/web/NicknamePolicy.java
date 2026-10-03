package com.example.battleroyal.web;

import com.example.battleroyal.game.rule.GameConstants;
import com.example.battleroyal.web.GameSessionService.InvalidNicknameException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What a nickname may be: its length, and no swearing, sexual words or posing as staff.
 * Applies to guests and accounts alike — every name can end up on the ranking or in
 * someone's death screen.
 *
 * <p>The words live in {@code nickname-blocklist.txt}. A nickname is normalized before
 * the check so that spacing, symbols and digit swaps do not get a word through:
 * "씨 발", "s3x" and "f.u.c.k" all match.
 */
public final class NicknamePolicy {

    private static final String BLOCKLIST = "/nickname-blocklist.txt";

    /** Digits and symbols commonly typed in place of letters. */
    private static final Map<Character, Character> LOOKALIKE = Map.of(
            '0', 'o', '1', 'i', '3', 'e', '4', 'a', '5', 's',
            '7', 't', '@', 'a', '$', 's', '!', 'i');

    private static final List<String> BLOCKED = load();

    private NicknamePolicy() {
    }

    /**
     * Throws if the name is the wrong length or contains a blocked word. The name is
     * checked as given: callers trim it and strip any unranked prefix first.
     */
    public static void require(String nickname) {
        if (nickname.length() < GameConstants.NICKNAME_MIN_LENGTH
                || nickname.length() > GameConstants.NICKNAME_MAX_LENGTH) {
            throw new InvalidNicknameException("닉네임은 "
                    + GameConstants.NICKNAME_MIN_LENGTH + "~"
                    + GameConstants.NICKNAME_MAX_LENGTH + "자입니다");
        }
        if (blocked(nickname)) {
            throw new InvalidNicknameException("쓸 수 없는 닉네임입니다");
        }
    }

    static boolean blocked(String nickname) {
        String plain = normalize(nickname);
        for (String word : BLOCKED) {
            if (plain.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lower case, compatibility forms folded (full-width letters become ASCII), digit
     * swaps undone, and everything that is not a letter dropped. Hangul syllables and
     * the bare consonants used for abbreviations (ㅅㅂ) count as letters.
     */
    static String normalize(String text) {
        String folded = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder plain = new StringBuilder(folded.length());
        for (char c : folded.toCharArray()) {
            char mapped = LOOKALIKE.getOrDefault(c, c);
            if (Character.isLetter(mapped)) {
                plain.append(mapped);
            }
        }
        return plain.toString();
    }

    private static List<String> load() {
        try (InputStream in = NicknamePolicy.class.getResourceAsStream(BLOCKLIST)) {
            if (in == null) {
                throw new IllegalStateException(BLOCKLIST + " is missing");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(NicknamePolicy::normalize)
                    .filter(word -> !word.isEmpty())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
