package com.celestia.agent.persistence.store;

import com.celestia.agent.llm.SourceRef;
import com.celestia.agent.llm.ToolCallRecord;
import com.celestia.agent.llm.TurnDetail;
import com.celestia.agent.persistence.entity.OwnerTurnRecordEntity;
import com.celestia.agent.persistence.json.PersistenceJson;
import com.celestia.agent.persistence.repository.OwnerTurnRecordRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The turn record, as the rest of the application sees it (SPEC-012 FR-020).
 *
 * <p>{@code llm/} and {@code channel/} talk to this; neither touches the entity,
 * which is the layering SPEC-008 established and {@code PersistenceArchitectureTest}
 * guards.
 *
 * <p><b>It holds personal data, so it is deleted.</b> Two rules, and the second
 * must not be mistaken for a way round the first:
 *
 * <ol>
 *   <li>{@link #deleteForUser(long)} — "forget me" removes every turn record for
 *       that user (FR-020a). A deletion request that leaves every question a user
 *       ever asked is not a deletion.
 *   <li>{@link #purgeOlderThan(Duration)} — records past the retention age go,
 *       default 90 days (FR-020b). <b>A retention of {@code 0} means never purge
 *       on age</b> — not "never delete", because rule 1 is a standing obligation
 *       that no setting switches off.
 * </ol>
 *
 * <p>The purge takes its "now" from an injected {@link Clock}, so SC-013's
 * "a record past the retention age is gone" can be tested without waiting ninety
 * days.
 */
@Service
public class TurnRecordStore {

    private static final Logger log = LoggerFactory.getLogger(TurnRecordStore.class);

    /** Matches the column width, which matches WhatsApp's own text bound (FR-017). */
    static final int MAX_BODY = 4096;

    private final OwnerTurnRecordRepository repo;
    private final Clock clock;
    private final int retentionDays;

    public TurnRecordStore(
            OwnerTurnRecordRepository repo,
            Clock clock,
            @Value("${celestia.turn-record.retention-days:90}") int retentionDays) {
        this.repo = repo;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    /** Writes one turn. Called for every turn that began, faults included. */
    @Transactional
    public long record(TurnDetail detail) {
        OwnerTurnRecordEntity saved = repo.save(new OwnerTurnRecordEntity(
                detail.userId(),
                detail.receivedAt(),
                clip(detail.question()),
                detail.deliveredReply().map(TurnRecordStore::clip).orElse(null),
                detail.entitlement().name(),
                detail.gateOutcome().orElse(null),
                detail.gateReason().orElse(null),
                detail.outcome().name(),
                detail.sensitivityUsed().name(),
                detail.deliveredLength(),
                detail.faultKind().map(Enum::name).orElse(null),
                detail.modelUsed().orElse(null),
                detail.fallbackSwitched(),
                detail.withheldFamily().orElse(null),
                detail.replyTrimmed(),
                toJson(detail.toolCalls()),
                toJson(detail.resolvedSources())));
        return saved.getId();
    }

    /** How many turns this user has on record — the assertion "forget me" has to drive to zero. */
    public long countForUser(long userId) {
        return repo.countByUserId(userId);
    }

    /**
     * "Forget me" (FR-020a). Wired into SPEC-008's hard delete: a new table
     * holding message bodies does not get to opt out of Constitution VII.
     */
    @Transactional
    public void deleteForUser(long userId) {
        repo.deleteByUserId(userId);
    }

    /**
     * Purges records older than {@code retention}, measured from the injected
     * clock. A zero or negative retention purges <b>nothing</b> on age; it does
     * not touch {@link #deleteForUser(long)}.
     *
     * @return how many rows went
     */
    @Transactional
    public long purgeOlderThan(Duration retention) {
        if (retention == null || retention.isZero() || retention.isNegative()) {
            return 0;
        }
        Instant cutoff = clock.instant().minus(retention);
        long removed = repo.deleteByReceivedAtBefore(cutoff);
        if (removed > 0) {
            log.info("purged {} owner turn record(s) older than {}", removed, cutoff);
        }
        return removed;
    }

    /**
     * The scheduled purge (FR-020b), on {@code @EnableScheduling} already turned
     * on by {@code ChannelConfig} for SPEC-009's worker — one scheduler, not two.
     */
    @Scheduled(cron = "${celestia.turn-record.purge-cron:0 30 3 * * *}")
    public void purgeOnSchedule() {
        purgeOlderThan(Duration.ofDays(retentionDays));
    }

    /** The configured retention, so a caller and a test agree on what "the default" is. */
    public Duration retention() {
        return Duration.ofDays(retentionDays);
    }

    private static String clip(String body) {
        return body.length() <= MAX_BODY ? body : body.substring(0, MAX_BODY);
    }

    private static String toJson(List<ToolCallRecord> calls) {
        return PersistenceJson.mapper().writeValueAsString(calls);
    }

    private static String toJson(Map<String, SourceRef> sources) {
        return PersistenceJson.mapper().writeValueAsString(sources);
    }
}
