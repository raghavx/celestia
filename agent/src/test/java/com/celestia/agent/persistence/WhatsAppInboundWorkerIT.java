package com.celestia.agent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.agent.channel.OutboundMessageCodec;
import com.celestia.agent.channel.WhatsAppInboundWorker;
import com.celestia.agent.persistence.repository.OutboxMessageRepository;
import com.celestia.agent.persistence.store.InboxStore;
import com.celestia.channel.whatsapp.OutboundMessage.TextMessage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * SPEC-009 US3 — the inbound worker: claim, resolve identity, reply, queue.
 *
 * <p>Deliberately <b>not</b> {@code @Transactional}: the concurrency test needs
 * a row committed and visible across two separate worker threads/connections,
 * which a rolled-back test transaction would hide. Each test uses a unique
 * {@code wa_message_id} to stay isolated from the others.
 */
class WhatsAppInboundWorkerIT extends PersistenceIT {

    @Autowired
    WhatsAppInboundWorker worker;

    @Autowired
    InboxStore inboxStore;

    @Autowired
    OutboxMessageRepository outboxRepo;

    @Autowired
    OutboundMessageCodec codec;

    private static String messageJson(String name) throws Exception {
        byte[] body = Files.readAllBytes(Path.of("src/test/resources/whatsapp/payloads/" + name));
        return com.celestia.channel.whatsapp.InboundPayloadParser.extractRawMessageJson(body).get(0);
    }

    @Test
    void aTextMessageIsClaimedResolvedRepliedToAndQueued() throws Exception {
        inboxStore.recordInbound("wamid.single-text-0001", messageJson("single-text.json"));

        worker.tick();

        var outboxForSender = outboxRepo.findAll().stream()
                .filter(m -> "+919876543210".equals(m.getTarget()))
                .toList();
        assertThat(outboxForSender).hasSize(1);
        var reply = (TextMessage) codec.read(outboxForSender.get(0).getPayloadJson());
        assertThat(reply.to()).isEqualTo("+919876543210");
        assertThat(reply.body()).isNotBlank();
    }

    @Test
    void anUnsupportedTypeMessageStillQueuesAReply() throws Exception {
        inboxStore.recordInbound("wamid.unsupported-0001", messageJson("unsupported-type.json"));

        worker.tick();

        var outboxForSender = outboxRepo.findAll().stream()
                .filter(m -> "+919876543299".equals(m.getTarget()))
                .toList();
        assertThat(outboxForSender).hasSize(1);
    }

    @Test
    void twoConcurrentClaimersNeverClaimTheSameRow() throws Exception {
        inboxStore.recordInbound("wamid.a", messageJson("single-text.json"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Optional<InboxStore.ClaimedEvent>>> tasks =
                List.of(inboxStore::claimNext, inboxStore::claimNext);
        List<Optional<InboxStore.ClaimedEvent>> results;
        try {
            results = pool.invokeAll(tasks).stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).toList();
        } finally {
            pool.shutdown();
        }

        long claimed = results.stream().filter(Optional::isPresent).count();
        assertThat(claimed).isEqualTo(1);
    }
}
