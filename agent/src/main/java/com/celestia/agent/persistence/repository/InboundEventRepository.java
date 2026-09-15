package com.celestia.agent.persistence.repository;

import com.celestia.agent.persistence.entity.InboundEventEntity;
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

public interface InboundEventRepository extends JpaRepository<InboundEventEntity, Long> {
    Optional<InboundEventEntity> findByWaMessageId(String waMessageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")) // SKIP_LOCKED
    @Query("select e from InboundEventEntity e where e.status = 'NEW' order by e.id asc limit 1")
    Optional<InboundEventEntity> claimNext();

    /** SPEC-009 US6: rows claimed and then abandoned (a crashed worker). */
    @Query("select e from InboundEventEntity e where e.status = 'CLAIMED' and e.receivedAt < :threshold")
    List<InboundEventEntity> findStaleClaimed(@Param("threshold") Instant threshold);
}
