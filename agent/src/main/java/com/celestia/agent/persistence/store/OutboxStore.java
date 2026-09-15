package com.celestia.agent.persistence.store;

import com.celestia.agent.persistence.entity.OutboxMessageEntity;
import com.celestia.agent.persistence.repository.OutboxMessageRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The outbound message queue with attempt/error tracking (ADR-0015). Sender is SPEC-009. */
@Service
public class OutboxStore {

    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

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

    /** SPEC-008: marks a message sent with no provider id recorded. */
    @Transactional
    public void markSent(long id) {
        OutboxMessageEntity m = repo.findById(id).orElseThrow();
        m.setStatus("SENT");
        repo.save(m);
    }

    /** SPEC-009 overload: also records the provider's assigned message id (FR-013). */
    @Transactional
    public void markSent(long id, String providerMessageId) {
        OutboxMessageEntity m = repo.findById(id).orElseThrow();
        m.setStatus("SENT");
        m.setProviderMessageId(providerMessageId);
        repo.save(m);
    }

    @Transactional
    public void markFailed(long id, String error) {
        OutboxMessageEntity m = repo.findById(id).orElseThrow();
        m.setStatus("FAILED");
        m.setError(truncate(error));
        repo.save(m);
    }

    /**
     * SPEC-009: a transient send failure — back to {@code PENDING}, gated by a
     * capped-exponential backoff so the sender does not hammer a failing
     * provider (SC-007).
     */
    @Transactional
    public void markRetry(long id, String error) {
        OutboxMessageEntity m = repo.findById(id).orElseThrow();
        m.setStatus("PENDING");
        m.setError(truncate(error));
        m.setNextAttemptAt(Instant.now().plus(backoff(m.getAttempts())));
        repo.save(m);
    }

    static Duration backoff(int attempts) {
        long seconds = 1L << Math.min(attempts, 30); // avoid overflow
        return Duration.ofSeconds(Math.min(seconds, MAX_BACKOFF.getSeconds()));
    }

    /**
     * SPEC-009 US6: a {@code CLAIMED} row older than {@code olderThan} means the
     * sender that claimed it crashed before marking it sent/failed — reset it
     * to {@code PENDING} so it is claimable again; past {@code maxAttempts},
     * mark it {@code FAILED} instead.
     *
     * @return the number of rows reset to {@code PENDING}
     */
    @Transactional
    public int reclaimStale(Duration olderThan, int maxAttempts) {
        Instant threshold = Instant.now().minus(olderThan);
        int reclaimed = 0;
        for (OutboxMessageEntity m : repo.findStaleClaimed(threshold)) {
            if (m.getAttempts() >= maxAttempts) {
                m.setStatus("FAILED");
                m.setError("reclaim sweep: exceeded max attempts (" + maxAttempts + ")");
            } else {
                m.setStatus("PENDING");
                m.setNextAttemptAt(null);
                reclaimed++;
            }
            repo.save(m);
        }
        return reclaimed;
    }

    private static String truncate(String error) {
        return error == null ? null : error.substring(0, Math.min(error.length(), 500));
    }
}
