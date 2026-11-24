package com.celestia.agent.persistence.repository;

import com.celestia.agent.persistence.entity.OwnerTurnRecordEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerTurnRecordRepository extends JpaRepository<OwnerTurnRecordEntity, Long> {

    List<OwnerTurnRecordEntity> findByUserIdOrderByReceivedAtAsc(Long userId);

    long countByUserId(Long userId);

    /** "Forget me" (SPEC-012 FR-020a): every question this user ever asked. */
    void deleteByUserId(Long userId);

    /** The age purge (FR-020b). */
    long deleteByReceivedAtBefore(Instant cutoff);
}
