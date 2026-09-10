package com.celestia.agent.persistence.store;

import com.celestia.agent.persistence.entity.OutboxMessageEntity;
import com.celestia.agent.persistence.repository.OutboxMessageRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The outbound message queue with attempt/error tracking (ADR-0015). Sender is SPEC-009. */
@Service
public class OutboxStore {

    public record ClaimedMessage(long id, Long userId, String target, String payloadJson, int attempts) {}

    private final OutboxMessageRepository repo;

    public OutboxStore(OutboxMessageRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public long enqueue(Long userId, String target, String payloadJson) {
        return repo.save(new OutboxMessageEntity(userId, target, payloadJson)).getId();
    }

    @Transactional
    public Optional<ClaimedMessage> claimNextPending() {
        return repo.claimNextPending().map(m -> {
            m.setStatus("CLAIMED");
            m.setAttempts(m.getAttempts() + 1);
            repo.save(m);
            return new ClaimedMessage(m.getId(), m.getUserId(), m.getTarget(), m.getPayloadJson(),
                    m.getAttempts());
        });
    }

    @Transactional
    public void markSent(long id) {
        OutboxMessageEntity m = repo.findById(id).orElseThrow();
        m.setStatus("SENT");
        repo.save(m);
    }

    @Transactional
    public void markFailed(long id, String error) {
        OutboxMessageEntity m = repo.findById(id).orElseThrow();
        m.setStatus("FAILED");
        m.setError(error == null ? null : error.substring(0, Math.min(error.length(), 500)));
        repo.save(m);
    }
}
