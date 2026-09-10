package com.celestia.agent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.agent.persistence.repository.InboundEventRepository;
import com.celestia.agent.persistence.store.InboxStore;
import com.celestia.agent.persistence.store.OutboxStore;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

/** SPEC-008 US5 / SC-007 — idempotent inbox + outbox. */
@Transactional
class AsyncIoStoreIT extends PersistenceIT {

    @Autowired
    InboxStore inbox;

    @Autowired
    OutboxStore outbox;

    @Autowired
    InboundEventRepository inboundRepo;

    @Test
    void recordingTheSameWaMessageIdTwiceYieldsOneRow() {
        InboxStore.RecordResult first = inbox.recordInbound("wamid.AAA", "s3://raw/1");
        InboxStore.RecordResult second = inbox.recordInbound("wamid.AAA", "s3://raw/1");

        assertThat(first).isInstanceOf(InboxStore.Recorded.class);
        assertThat(second).isInstanceOf(InboxStore.Duplicate.class);
        assertThat(inboundRepo.findAll().stream()
                .filter(e -> e.getWaMessageId().equals("wamid.AAA")).count()).isEqualTo(1);
    }

    @Test
    void aClaimedEventIsNotReClaimed() {
        inbox.recordInbound("wamid.BBB", "s3://raw/2");
        Optional<InboxStore.ClaimedEvent> a = inbox.claimNext();
        Optional<InboxStore.ClaimedEvent> b = inbox.claimNext();

        assertThat(a).isPresent();
        assertThat(a.get().waMessageId()).isEqualTo("wamid.BBB");
        assertThat(a.get().attempts()).isEqualTo(1);
        assertThat(b).isEmpty();

        inbox.markProcessed(a.get().id());
        assertThat(inboundRepo.findByWaMessageId("wamid.BBB").orElseThrow().getProcessedAt())
                .isNotNull();
    }

    @Test
    void outboxTracksAttemptsAndError() {
        long id = outbox.enqueue(null, "+919000000000", "{\"text\":\"hi\"}");
        OutboxStore.ClaimedMessage claimed = outbox.claimNextPending().orElseThrow();
        assertThat(claimed.id()).isEqualTo(id);
        assertThat(claimed.attempts()).isEqualTo(1);

        outbox.markFailed(id, "graph api 500");
        assertThat(outbox.claimNextPending()).isEmpty(); // no longer PENDING
    }
}
