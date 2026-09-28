package com.example.battleroyal.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameResultRepository extends JpaRepository<GameResult, Long> {

    /**
     * The ranking: highest score first, and between equal scores whoever lasted longer,
     * then whoever got there first.
     */
    List<GameResult> findAllByOrderByScoreDescSurvivedSecondsDescEndedAtAsc(Pageable page);

    /**
     * Results strictly better than the given numbers: a higher score, or the same score
     * with a longer life. {@code And} binds tighter than {@code Or} in derived queries,
     * so this reads {@code score > ?1 or (score = ?2 and survivedSeconds > ?3)}.
     */
    long countByScoreGreaterThanOrScoreAndSurvivedSecondsGreaterThan(
            int score, int sameScore, long survivedSeconds);
}
