package com.example.battleroyal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeasonRepository extends JpaRepository<Season, Integer> {

    /** The open season: there is at most one. */
    Optional<Season> findFirstByEndedAtIsNullOrderByNumberDesc();

    Optional<Season> findFirstByOrderByNumberDesc();
}
