package com.example.battleroyal.persistence;

import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.core.ItemKind;
import com.example.battleroyal.game.rule.GameConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class GameResultRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-09-28T00:00:00Z");

    @Autowired
    private GameResultRepository results;

    private void save(String nickname, int score, long seconds, int minutesLater) {
        results.save(new GameResult(nickname, score, 0, seconds,
                T0.plusSeconds(60L * minutesLater)));
    }

    private List<String> ranking(int size) {
        return results.findAllByOrderByScoreDescSurvivedSecondsDescEndedAtAsc(
                        PageRequest.of(0, size))
                .stream().map(GameResult::nickname).toList();
    }

    @Test
    void theRankingIsHighestScoreFirstThenLongestLivedThenEarliest() {
        save("low", 10, 999, 0);
        save("high", 500, 10, 0);
        save("tieLong", 100, 300, 5);
        save("tieShort", 100, 100, 0);
        save("tieLongLater", 100, 300, 9);

        assertEquals(List.of("high", "tieLong", "tieLongLater", "tieShort", "low"),
                ranking(10));
        assertEquals(List.of("high", "tieLong"), ranking(2));
    }

    @Test
    void onlyStrictlyBetterResultsCountAgainstARank() {
        save("a", 500, 10, 0);
        save("b", 100, 300, 0);
        save("c", 100, 100, 0);
        save("d", 10, 999, 0);

        // score 100, 100s: behind a (higher score) and b (same score, longer life);
        // level with c, which does not count against it.
        assertEquals(2, results.countByScoreGreaterThanOrScoreAndSurvivedSecondsGreaterThan(
                100, 100, 100));
        assertEquals(0, results.countByScoreGreaterThanOrScoreAndSurvivedSecondsGreaterThan(
                900, 900, 0));
        assertEquals(4, results.countByScoreGreaterThanOrScoreAndSurvivedSecondsGreaterThan(
                0, 0, 0));
    }

    @Test
    void aDeathIsRecordedAsOneResult() {
        ResultRecorder recorder = new ResultRecorder(results, new DirectExecutor(),
                Clock.fixed(T0, ZoneOffset.UTC));

        recorder.onDeath(new GameEvent.Died("p-1", "kang", 420, 2,
                95L * GameConstants.TICKS_PER_SECOND + 7, "lee", ItemKind.PISTOL));

        List<GameResult> saved = results.findAll();
        assertEquals(1, saved.size());
        GameResult result = saved.getFirst();
        assertEquals("kang", result.nickname());
        assertEquals(420, result.score());
        assertEquals(2, result.kills());
        assertEquals(95, result.survivedSeconds());
        assertEquals(T0, result.endedAt());
    }

    /** Runs the write on the calling thread so the test can look straight away. */
    private static final class DirectExecutor extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
