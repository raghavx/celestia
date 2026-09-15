package com.celestia.agent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.agent.channel.OutboundMessageCodec;
import com.celestia.agent.channel.WhatsAppInboundWorker;
import com.celestia.agent.channel.WhatsAppOutboundSender;
import com.celestia.agent.persistence.repository.InboundEventRepository;
import com.celestia.agent.persistence.repository.OutboxMessageRepository;
import com.celestia.agent.persistence.store.InboxStore;
import com.celestia.agent.persistence.store.OutboxStore;
import com.celestia.channel.whatsapp.OutboundMessage.TextMessage;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * SPEC-009 US6 / T040 — the channel survives a worker or sender restart: a
 * {@code NEW}/{@code PENDING} row untouched by a "crashed" run is still
 * processed by the next tick, and a {@code CLAIMED} row older than the
 * reclaim threshold is reclaimed and processed instead of stuck forever.
 */
@Transactional
class RestartSurvivalIT extends PersistenceIT {

    private static final WireMockServer WIRE_MOCK = new WireMockServer(0);

    static {
        WIRE_MOCK.start();
    }

    @DynamicPropertySource
    static void wiremockProps(DynamicPropertyRegistry registry) {
        registry.add("whatsapp.graph-api-base-url", () -> "http://localhost:" + WIRE_MOCK.port());
        registry.add("whatsapp.phone-number-id", () -> "pnid");
    }

    @AfterAll
    static void stopWireMock() {
        WIRE_MOCK.stop();
    }

    @Autowired
    WhatsAppInboundWorker worker;

    @Autowired
    WhatsAppOutboundSender sender;

    @Autowired
    InboxStore inboxStore;

    @Autowired
    OutboxStore outboxStore;

    @Autowired
    OutboxMessageRepository outboxRepo;

    @Autowired
    InboundEventRepository inboundRepo;

    @Autowired
    OutboundMessageCodec codec;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void stubGraphApiSuccess() {
        WIRE_MOCK.resetAll();
        WIRE_MOCK.stubFor(WireMock.post(WireMock.urlEqualTo("/v21.0/pnid/messages"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"messages\":[{\"id\":\"wamid.restart-it\"}]}")));
    }

    private static String messageJson(String name) throws Exception {
        byte[] body = Files.readAllBytes(Path.of("src/test/resources/whatsapp/payloads/" + name));
        return com.celestia.channel.whatsapp.InboundPayloadParser.extractRawMessageJson(body).get(0);
    }

    @Test
    void aFreshInboundRowSurvivesAnEarlierNoOpTick() throws Exception {
        worker.tick(); // simulates an empty "crashed-then-restarted" cycle with nothing to do
        inboxStore.recordInbound("wamid.restart-fresh", messageJson("single-text.json"));

        worker.tick();

        assertThat(inboundRepo.findByWaMessageId("wamid.restart-fresh").orElseThrow().getStatus())
                .isEqualTo("DONE");
    }

    @Test
    void aStaleClaimedInboundRowIsReclaimedAndProcessedOnTheNextTick() throws Exception {
        inboxStore.recordInbound("wamid.restart-stale", messageJson("single-text.json"));
        var claimed = inboxStore.claimNext().orElseThrow(); // simulates a worker that crashed mid-processing
        // Bound as UTC-interpreted LocalDateTime to match hibernate.jdbc.time_zone: UTC —
        // see ReclaimSweepIT for why a Timestamp/Instant parameter here is unsafe.
        jdbc.update("update inbound_event set received_at = ? where id = ?",
                java.time.LocalDateTime.ofInstant(
                        Instant.now().minus(10, ChronoUnit.MINUTES), java.time.ZoneOffset.UTC),
                claimed.id());

        worker.tick(); // reclaim-sweep runs first, then claims+processes the reclaimed row

        assertThat(inboundRepo.findByWaMessageId("wamid.restart-stale").orElseThrow().getStatus())
                .isEqualTo("DONE");
    }

    @Test
    void aFreshOutboxRowSurvivesAnEarlierNoOpTick() {
        sender.tick(); // empty cycle
        long id = outboxStore.enqueue(
                null, "+919000000200", codec.write(new TextMessage("+919000000200", "hi")));

        sender.tick();

        assertThat(outboxRepo.findById(id).orElseThrow().getStatus()).isEqualTo("SENT");
    }

    @Test
    void aStaleClaimedOutboxRowIsReclaimedAndSentOnTheNextTick() {
        // Drain any pre-existing PENDING rows first (WhatsAppInboundWorkerIT is not
        // @Transactional and can leave committed ones) so the claim below is
        // guaranteed to be the row this test just created.
        sender.tick();

        long id = outboxStore.enqueue(
                null, "+919000000201", codec.write(new TextMessage("+919000000201", "hi")));
        var claimed = outboxStore.claimNextPending().orElseThrow(); // simulates a sender that crashed mid-send
        assertThat(claimed.id()).isEqualTo(id);
        jdbc.update("update outbox_message set created_at = ? where id = ?",
                java.time.LocalDateTime.ofInstant(
                        Instant.now().minus(10, ChronoUnit.MINUTES), java.time.ZoneOffset.UTC),
                id);

        sender.tick(); // reclaim-sweep runs first, then claims+sends the reclaimed row

        assertThat(outboxRepo.findById(id).orElseThrow().getStatus()).isEqualTo("SENT");
    }
}
