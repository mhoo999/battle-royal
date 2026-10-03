package com.example.battleroyal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SortieRepository extends JpaRepository<Sortie, Long> {

    boolean existsByAccountIdAndOutcome(Long accountId, Sortie.Outcome outcome);

    List<Sortie> findByOutcome(Sortie.Outcome outcome);
}
