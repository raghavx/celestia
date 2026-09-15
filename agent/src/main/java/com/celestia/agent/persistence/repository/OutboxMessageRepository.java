package com.celestia.agent.persistence.repository;

import com.celestia.agent.persistence.entity.OutboxMessageEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessageEntity, Long> {

    /** SPEC-009: also gated on the retry-backoff column — {@code null} claims immediately. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")) // SKIP_LOCKED
    @Query("select m from OutboxMessageEntity m where m.status = 'PENDING' "
            + "and (m.nextAttemptAt is null or m.nextAttemptAt <= CURRENT_TIMESTAMP) "
            + "order by m.id asc limit 1")
    Optional<OutboxMessageEntity> claimNextPending();

    /** SPEC-009 US6: rows claimed and then abandoned (a crashed sender). */
    @Query("select m from OutboxMessageEntity m where m.status = 'CLAIMED' and m.createdAt < :threshold")
    List<OutboxMessageEntity> findStaleClaimed(@Param("threshold") Instant threshold);
}
