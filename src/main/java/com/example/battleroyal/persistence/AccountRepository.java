package com.example.battleroyal.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByGoogleSubject(String googleSubject);

    /** The ranking: the biggest haul first, and between equals whoever signed up first. */
    List<Account> findByHaulGreaterThanOrderByHaulDescIdAsc(long haul, Pageable page);

    long countByHaulGreaterThan(long haul);

    boolean existsByNickname(String nickname);

    /**
     * Reads the account and holds a write lock on its row until the transaction ends.
     * Setting out takes it first, so two sorties for one account queue up rather than
     * both reading the same items as still in the stash.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> lockById(@Param("id") Long id);
}
