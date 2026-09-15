package com.celestia.agent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.celestia.agent.channel.WhatsAppInboundWorker;
import com.celestia.agent.channel.WhatsAppOutboundSender;
import com.celestia.agent.persistence.repository.InboundEventRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SPEC-009 T041 — the Phase 4 exit criterion end to end: {@code POST
 * /webhooks/whatsapp} with a real signed fixture payload → the worker → the
 * outbox → a WireMock-stubbed Graph API receives exactly one send. Plus the
 * M2-remediation replay case (SC-004/FR-017): a webhook delivered twice, and
 * an already-processed message re-delivered, still results in exactly one
 * Graph API send.
 */
@AutoConfigureMockMvc
class WhatsAppChannelEndToEndIT extends PersistenceIT {

    private static final String APP_SECRET = "e2e-test-app-secret";
    private static final WireMockServer WIRE_MOCK = new WireMockServer(0);

    static {
        WIRE_MOCK.start();
    }

    @DynamicPropertySource
    static void wiremockProps(DynamicPropertyRegistry registry) {
        registry.add("whatsapp.graph-api-base-url", () -> "http://localhost:" + WIRE_MOCK.port());
        registry.add("whatsapp.phone-number-id", () -> "pnid");
        registry.add("whatsapp.app-secret", () -> APP_SECRET);
    }

    @AfterAll
    static void stopWireMock() {
        WIRE_MOCK.stop();
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    WhatsAppInboundWorker worker;

    @Autowired
    WhatsAppOutboundSender sender;

    @Autowired
    InboundEventRepository inboundRepo;

    @BeforeEach
    void resetStubs() {
        WIRE_MOCK.resetAll();
        WIRE_MOCK.stubFor(WireMock.post(WireMock.urlEqualTo("/v21.0/pnid/messages"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"messages\":[{\"id\":\"wamid.e2e-sent\"}]}")));
    }

    /** {@code single-text.json} with its wamid swapped for one unique to this test class. */
    private static byte[] fixtureWithWamid(String wamid) throws Exception {
        String raw = Files.readString(Path.of("src/test/resources/whatsapp/payloads/single-text.json"));
        return raw.replace("wamid.single-text-0001", wamid).getBytes(StandardCharsets.UTF_8);
    }

    private static String sign(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    private void postWebhook(byte[] body) throws Exception {
        mockMvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sign(body))
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void aWebhookMessageRoundTripsToExactlyOneGraphApiSend() throws Exception {
        byte[] body = fixtureWithWamid("wamid.e2e-roundtrip-0001");

        postWebhook(body);
        worker.tick();
        sender.tick();

        WIRE_MOCK.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/v21.0/pnid/messages")));
        assertThat(inboundRepo.findByWaMessageId("wamid.e2e-roundtrip-0001").orElseThrow().getStatus())
                .isEqualTo("DONE");
    }

    @Test
    void aReplayedWebhookDeliveryNeverProducesASecondSend() throws Exception {
        byte[] body = fixtureWithWamid("wamid.e2e-replay-0001");

        // Delivered twice before the worker ever runs — idempotent recording (FR-017).
        postWebhook(body);
        postWebhook(body);
        long recordedRows = inboundRepo.findAll().stream()
                .filter(e -> "wamid.e2e-replay-0001".equals(e.getWaMessageId()))
                .count();
        assertThat(recordedRows).isEqualTo(1);

        worker.tick();
        sender.tick();
        WIRE_MOCK.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/v21.0/pnid/messages")));

        // Re-delivered again after processing completed, and the worker runs again.
        postWebhook(body);
        worker.tick();
        sender.tick();

        // Still exactly one send in total — never a second reply for the same message.
        WIRE_MOCK.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/v21.0/pnid/messages")));
    }
}
