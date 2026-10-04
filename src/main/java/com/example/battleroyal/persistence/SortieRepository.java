package com.example.battleroyal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SortieRepository extends JpaRepository<Sortie, Long> {

    boolean existsByAccountIdAndOutcome(Long accountId, Sortie.Outcome outcome);

    List<Sortie> findByOutcome(Sortie.Outcome outcome);

    /** Accounts with a sortie that ended this way and set out at or after {@code since}. */
    @Query("select distinct s.accountId from Sortie s where s.outcome = :outcome and s.startedAt >= :since")
    List<Long> accountsWith(@Param("outcome") Sortie.Outcome outcome, @Param("since") Instant since);
}
