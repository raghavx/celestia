package com.celestia.agent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.agent.channel.OutboundMessageCodec;
import com.celestia.agent.channel.WhatsAppOutboundSender;
import com.celestia.agent.persistence.entity.OutboxMessageEntity;
import com.celestia.agent.persistence.repository.OutboxMessageRepository;
import com.celestia.agent.persistence.store.OutboxStore;
import com.celestia.agent.persistence.store.UserAccountService;
import com.celestia.agent.persistence.store.WhatsAppWindowStore;
import com.celestia.channel.whatsapp.OutboundMessage.TextMessage;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * SPEC-009 US4 — the outbound sender against a stubbed Graph API: sends
 * inside the 24h window as-is (FR-008), downgrades to the fallback template
 * outside it (FR-009), and retries/fails per
 * {@link com.celestia.channel.whatsapp.SendResult} (FR-010, SC-007).
 */
@Transactional
class WhatsAppOutboundSenderIT extends PersistenceIT {

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
    WhatsAppOutboundSender sender;

    @Autowired
    OutboxStore outboxStore;

    @Autowired
    OutboxMessageRepository outboxRepo;

    @Autowired
    OutboundMessageCodec codec;

    @Autowired
    UserAccountService users;

    @Autowired
    WhatsAppWindowStore windowStore;

    @BeforeEach
    void resetStubs() {
        WIRE_MOCK.resetAll();
    }

    private void stubSuccess(String providerMessageId) {
        WIRE_MOCK.stubFor(WireMock.post(WireMock.urlEqualTo("/v21.0/pnid/messages"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"messages\":[{\"id\":\"" + providerMessageId + "\"}]}")));
    }

    @Test
    void aMessageInsideTheWindowIsSentAsIs() {
        long id = users.findOrCreate("+919888810001").id();
        windowStore.recordInbound(id, Instant.now());
        stubSuccess("wamid.sender-it-0001");

        long outboxId = outboxStore.enqueue(
                id, "+919888810001", codec.write(new TextMessage("+919888810001", "hi there")));
        sender.tick();

        OutboxMessageEntity sent = outboxRepo.findById(outboxId).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo("SENT");
        assertThat(sent.getProviderMessageId()).isEqualTo("wamid.sender-it-0001");
        WIRE_MOCK.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/v21.0/pnid/messages"))
                .withRequestBody(WireMock.matchingJsonPath("$.type", WireMock.equalTo("text"))));
    }

    @Test
    void aMessageOutsideTheWindowIsDowngradedToTheFallbackTemplate() {
        long id = users.findOrCreate("+919888810002").id();
        // deliberately no recordInbound: this user has no window at all
        stubSuccess("wamid.sender-it-0002");

        long outboxId = outboxStore.enqueue(
                id, "+919888810002", codec.write(new TextMessage("+919888810002", "hi there")));
        sender.tick();

        OutboxMessageEntity sent = outboxRepo.findById(outboxId).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo("SENT");
        WIRE_MOCK.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/v21.0/pnid/messages"))
                .withRequestBody(WireMock.matchingJsonPath("$.type", WireMock.equalTo("template"))));
    }

    @Test
    void aRetryableFailureIsRescheduledWithBackoff() {
        long id = users.findOrCreate("+919888810003").id();
        windowStore.recordInbound(id, Instant.now());
        WIRE_MOCK.stubFor(WireMock.post(WireMock.urlEqualTo("/v21.0/pnid/messages"))
                .willReturn(WireMock.aResponse().withStatus(503)));

        long outboxId = outboxStore.enqueue(
                id, "+919888810003", codec.write(new TextMessage("+919888810003", "hi there")));
        sender.tick();

        OutboxMessageEntity retried = outboxRepo.findById(outboxId).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo("PENDING");
        assertThat(retried.getNextAttemptAt()).isNotNull().isAfter(Instant.now());
    }

    @Test
    void aPermanentFailureMarksTheMessageFailed() {
        long id = users.findOrCreate("+919888810004").id();
        windowStore.recordInbound(id, Instant.now());
        WIRE_MOCK.stubFor(WireMock.post(WireMock.urlEqualTo("/v21.0/pnid/messages"))
                .willReturn(WireMock.aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"message\":\"bad request\"}}")));

        long outboxId = outboxStore.enqueue(
                id, "+919888810004", codec.write(new TextMessage("+919888810004", "hi there")));
        sender.tick();

        OutboxMessageEntity failed = outboxRepo.findById(outboxId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getError()).contains("bad request");
    }
}
