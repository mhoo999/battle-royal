package com.example.battleroyal.persistence;

import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.loop.DeathListener;
import com.example.battleroyal.game.rule.GameConstants;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Saves a result whenever a life ends, however it ended: killed, or the disconnect
 * grace ran out.
 *
 * <p>The death is reported on the game loop thread, and a database write can take
 * longer than a tick. So the write goes to a thread of its own; the loop only hands
 * it over. A single thread keeps results in the order the deaths happened.
 */
@Component
public class ResultRecorder implements DeathListener {

    private static final Logger log = LoggerFactory.getLogger(ResultRecorder.class);

    private final GameResultRepository results;
    private final ExecutorService writer;
    private final Clock clock;

    @Autowired
    public ResultRecorder(GameResultRepository results) {
        this(results, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "result-writer");
            thread.setDaemon(true);
            return thread;
        }), Clock.systemUTC());
    }

    ResultRecorder(GameResultRepository results, ExecutorService writer, Clock clock) {
        this.results = results;
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    public void onDeath(GameEvent.Died died) {
        GameResult result = new GameResult(died.nickname(), died.score(), died.kills(),
                died.survivedTicks() / GameConstants.TICKS_PER_SECOND, clock.instant());
        writer.execute(() -> {
            try {
                results.save(result);
            } catch (RuntimeException e) {
                log.error("Could not save the result for {}", died.playerId(), e);
            }
        });
    }

    /** Lets results already handed over reach the database before shutdown. */
    @PreDestroy
    public void flush() throws InterruptedException {
        writer.shutdown();
        if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
            log.warn("Shut down with results still unsaved");
        }
    }
}
