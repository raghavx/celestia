package com.celestia.agent.persistence.repository;

import com.celestia.agent.persistence.entity.OutboxMessageEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessageEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")) // SKIP_LOCKED
    @Query("select m from OutboxMessageEntity m where m.status = 'PENDING' order by m.id asc limit 1")
    Optional<OutboxMessageEntity> claimNextPending();
}
