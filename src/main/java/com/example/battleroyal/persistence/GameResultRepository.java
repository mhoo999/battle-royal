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
}
