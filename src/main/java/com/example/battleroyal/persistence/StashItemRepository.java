package com.example.battleroyal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StashItemRepository extends JpaRepository<StashItem, Long> {

    List<StashItem> findByAccountIdAndLocationOrderById(Long accountId, StashItem.Location location);

    List<StashItem> findBySortieId(Long sortieId);

    long countByAccountIdAndLocation(Long accountId, StashItem.Location location);
}
