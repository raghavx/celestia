package com.celestia.agent.persistence.store;

import com.celestia.agent.persistence.entity.InboundEventEntity;
import com.celestia.agent.persistence.repository.InboundEventRepository;
import java.time.Duration;
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

    /**
     * SPEC-009 US6: a {@code CLAIMED} row older than {@code olderThan} means the
     * worker that claimed it crashed before marking it done — reset it to
     * {@code NEW} so it is claimable again; past {@code maxAttempts}, mark it
     * {@code FAILED} instead (never retried forever). Uses {@code received_at}
     * as the staleness clock (an approximation — v1's echo handling is cheap;
     * a real {@code claimed_at} column is a later refinement, research.md §7).
     *
     * @return the number of rows reset to {@code NEW}
     */
    @Transactional
    public int reclaimStale(Duration olderThan, int maxAttempts) {
        Instant threshold = Instant.now().minus(olderThan);
        int reclaimed = 0;
        for (InboundEventEntity e : repo.findStaleClaimed(threshold)) {
            if (e.getAttempts() >= maxAttempts) {
                e.setStatus("FAILED");
                e.setError("reclaim sweep: exceeded max attempts (" + maxAttempts + ")");
            } else {
                e.setStatus("NEW");
                reclaimed++;
            }
            repo.save(e);
        }
        return reclaimed;
    }
}
