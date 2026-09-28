package com.example.battleroyal.web;

import com.example.battleroyal.persistence.GameResultRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The best finished lives, best first. A plain query over saved results; nothing is
 * kept up to date in real time.
 */
@RestController
@RequestMapping("/api/ranking")
public class RankingController {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    public record Row(String nickname, int score, int kills, long survivedSeconds) {
    }

    public record Position(long rank) {
    }

    private final GameResultRepository results;

    public RankingController(GameResultRepository results) {
        this.results = results;
    }

    @GetMapping
    public List<Row> top(@RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        int size = Math.clamp(limit, 1, MAX_LIMIT);
        return results.findAllByOrderByScoreDescSurvivedSecondsDescEndedAtAsc(
                        PageRequest.of(0, size))
                .stream()
                .map(r -> new Row(r.nickname(), r.score(), r.kills(), r.survivedSeconds()))
                .toList();
    }

    /**
     * Where a result with these numbers stands: one more than the number of results
     * strictly better. Equal results share a rank.
     *
     * <p>Asked by the number, not by nickname, because nicknames repeat. The client asks
     * about the life it just finished; a client lying about its numbers only changes
     * what it shows itself. It also means the answer does not wait for that result to
     * reach the database.
     */
    @GetMapping("/rank")
    public Position rank(@RequestParam int score, @RequestParam long survivedSeconds) {
        return new Position(1 + results.countByScoreGreaterThanOrScoreAndSurvivedSecondsGreaterThan(
                score, score, survivedSeconds));
    }
}
