package com.example.battleroyal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrophyRepository extends JpaRepository<Trophy, Long> {

    List<Trophy> findByAccountIdOrderBySeasonAsc(Long accountId);
}
