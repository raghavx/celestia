package com.celestia.agent.persistence.store;

import com.celestia.agent.persistence.entity.InboundEventEntity;
import com.celestia.agent.persistence.repository.InboundEventRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The idempotent inbound event store (ADR-0015). The webhook records an event
 * once per {@code wa_message_id}; a worker claims it. The worker loop itself is
 * SPEC-009.
 */
@Service
public class InboxStore {

    public sealed interface RecordResult permits Recorded, Duplicate {}

    public record Recorded(long id) implements RecordResult {}

    public record Duplicate(long existingId) implements RecordResult {}

    public record ClaimedEvent(long id, String waMessageId, String rawRef, int attempts) {}

    private final InboundEventRepository repo;

    public InboxStore(InboundEventRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public RecordResult recordInbound(String waMessageId, String rawRef) {
        Optional<InboundEventEntity> existing = repo.findByWaMessageId(waMessageId);
        if (existing.isPresent()) {
            return new Duplicate(existing.get().getId());
        }
        try {
            InboundEventEntity saved = repo.saveAndFlush(new InboundEventEntity(waMessageId, rawRef));
            return new Recorded(saved.getId());
        } catch (DataIntegrityViolationException race) {
            return new Duplicate(repo.findByWaMessageId(waMessageId).orElseThrow(() -> race).getId());
        }
    }

    /** Claims the oldest {@code NEW} event with {@code FOR UPDATE SKIP LOCKED}. */
    @Transactional
    public Optional<ClaimedEvent> claimNext() {
        return repo.claimNext().map(e -> {
            e.setStatus("CLAIMED");
            e.setAttempts(e.getAttempts() + 1);
            repo.save(e);
            return new ClaimedEvent(e.getId(), e.getWaMessageId(), e.getRawRef(), e.getAttempts());
        });
    }

    @Transactional
    public void markProcessed(long id) {
        InboundEventEntity e = repo.findById(id).orElseThrow();
        e.setStatus("DONE");
        e.setProcessedAt(Instant.now());
        repo.save(e);
    }

    @Transactional
    public void markFailed(long id, String error) {
        InboundEventEntity e = repo.findById(id).orElseThrow();
        e.setStatus("FAILED");
        e.setError(error == null ? null : error.substring(0, Math.min(error.length(), 500)));
        repo.save(e);
    }
}
