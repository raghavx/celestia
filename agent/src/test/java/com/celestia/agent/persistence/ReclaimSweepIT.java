package com.celestia.agent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.agent.persistence.repository.InboundEventRepository;
import com.celestia.agent.persistence.repository.OutboxMessageRepository;
import com.celestia.agent.persistence.store.InboxStore;
import com.celestia.agent.persistence.store.OutboxStore;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * SPEC-009 US6 — {@code InboxStore.reclaimStale} / {@code OutboxStore.reclaimStale}
 * directly: a stale {@code CLAIMED} row under the attempt cap is reset and
 * claimable again; over the cap it is marked {@code FAILED} instead. Uses
 * {@code JdbcTemplate} to backdate {@code received_at}/{@code created_at}
 * (both DB-generated, not JPA-settable) to simulate age.
 */
@Transactional
class ReclaimSweepIT extends PersistenceIT {

    @Autowired
    InboxStore inbox;

    @Autowired
    OutboxStore outbox;

    @Autowired
    InboundEventRepository inboundRepo;

    @Autowired
    OutboxMessageRepository outboxRepo;

    @Autowired
    JdbcTemplate jdbc;

    // SPEC-009: WhatsAppInboundWorkerIT is deliberately not @Transactional and
    // can leave real, committed PENDING outbox rows from earlier in the suite;
    // drain them first (within this test's own transaction) so claimNextPending()
    // below is guaranteed to return the row a given test just created.
    @BeforeEach
    void drainPreexistingOutboxPending() {
        Optional<OutboxStore.ClaimedMessage> stale;
        while ((stale = outbox.claimNextPending()).isPresent()) {
            outbox.markSent(stale.get().id());
        }
    }

    // Bound as a (zone-naive) LocalDateTime interpreted as UTC wall-clock, matching
    // this app's `hibernate.jdbc.time_zone: UTC` convention for these TIMESTAMP
    // columns — a java.sql.Timestamp/Instant parameter is instead converted via the
    // JVM default zone by the driver, silently skewing the value by the local UTC
    // offset and defeating the staleness comparison.
    private void backdateInbound(long id, Instant when) {
        int rows = jdbc.update("update inbound_event set received_at = ? where id = ?",
                java.time.LocalDateTime.ofInstant(when, java.time.ZoneOffset.UTC), id);
        assertThat(rows).isEqualTo(1);
    }

    private void backdateOutbox(long id, Instant when) {
        int rows = jdbc.update("update outbox_message set created_at = ? where id = ?",
                java.time.LocalDateTime.ofInstant(when, java.time.ZoneOffset.UTC), id);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void aStaleClaimedInboundEventUnderTheAttemptCapIsResetToNew() {
        long id = ((InboxStore.Recorded) inbox.recordInbound("wamid.reclaim-under", "raw")).id();
        var claimed = inbox.claimNext().orElseThrow();
        assertThat(claimed.id()).isEqualTo(id);
        backdateInbound(id, Instant.now().minus(10, ChronoUnit.MINUTES));

        int reset = inbox.reclaimStale(Duration.ofMinutes(2), 5);

        assertThat(reset).isEqualTo(1);
        assertThat(inboundRepo.findById(id).orElseThrow().getStatus()).isEqualTo("NEW");
    }

    @Test
    void aStaleClaimedInboundEventOverTheAttemptCapIsMarkedFailed() {
        long id = ((InboxStore.Recorded) inbox.recordInbound("wamid.reclaim-over", "raw")).id();
        var claimed = inbox.claimNext().orElseThrow();
        assertThat(claimed.id()).isEqualTo(id);
        backdateInbound(id, Instant.now().minus(10, ChronoUnit.MINUTES));

        int reset = inbox.reclaimStale(Duration.ofMinutes(2), 1); // attempts (1) >= cap (1)

        assertThat(reset).isEqualTo(0);
        assertThat(inboundRepo.findById(id).orElseThrow().getStatus()).isEqualTo("FAILED");
    }

    @Test
    void aFreshClaimedInboundEventIsUntouched() {
        long id = ((InboxStore.Recorded) inbox.recordInbound("wamid.reclaim-fresh", "raw")).id();
        inbox.claimNext();

        int reset = inbox.reclaimStale(Duration.ofMinutes(2), 5);

        assertThat(reset).isEqualTo(0);
        assertThat(inboundRepo.findById(id).orElseThrow().getStatus()).isEqualTo("CLAIMED");
    }

    @Test
    void aStaleClaimedOutboxMessageUnderTheAttemptCapIsResetToPending() {
        long id = outbox.enqueue(null, "+919000000100", "{\"kind\":\"text\"}");
        var claimed = outbox.claimNextPending().orElseThrow();
        assertThat(claimed.id()).isEqualTo(id);
        backdateOutbox(id, Instant.now().minus(10, ChronoUnit.MINUTES));

        int reset = outbox.reclaimStale(Duration.ofMinutes(2), 5);

        assertThat(reset).isEqualTo(1);
        var reclaimed = outboxRepo.findById(id).orElseThrow();
        assertThat(reclaimed.getStatus()).isEqualTo("PENDING");
        assertThat(reclaimed.getNextAttemptAt()).isNull();
    }

    @Test
    void aStaleClaimedOutboxMessageOverTheAttemptCapIsMarkedFailed() {
        long id = outbox.enqueue(null, "+919000000101", "{\"kind\":\"text\"}");
        var claimed = outbox.claimNextPending().orElseThrow();
        assertThat(claimed.id()).isEqualTo(id);
        assertThat(claimed.attempts()).isEqualTo(1);
        backdateOutbox(id, Instant.now().minus(10, ChronoUnit.MINUTES));

        int reset = outbox.reclaimStale(Duration.ofMinutes(2), 1); // attempts (1) >= cap (1)

        assertThat(reset).isEqualTo(0);
        assertThat(outboxRepo.findById(id).orElseThrow().getStatus()).isEqualTo("FAILED");
    }
}
