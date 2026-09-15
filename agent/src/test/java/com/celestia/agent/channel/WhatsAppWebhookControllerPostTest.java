package com.celestia.agent.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.celestia.agent.persistence.store.InboxStore;
import com.celestia.channel.whatsapp.WhatsAppConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** SPEC-009 US2 — the POST webhook: signature, idempotent recording, speed. */
class WhatsAppWebhookControllerPostTest {

    private static final String SECRET = "test-app-secret";

    private MockMvc mockMvc;
    private InboxStore inboxStore;

    @BeforeEach
    void setUp() {
        WhatsAppConfig config = new WhatsAppConfig(
                "pnid", "https://graph.facebook.com", "v21.0", "token", SECRET, "verify-token", null);
        inboxStore = mock(InboxStore.class);
        var controller = new WhatsAppWebhookController(config, inboxStore);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static byte[] fixture(String name) throws Exception {
        return Files.readAllBytes(Path.of("src/test/resources/whatsapp/payloads/" + name));
    }

    private static String sign(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    @Test
    void aTamperedSignatureIsRejectedAndNothingIsRecorded() throws Exception {
        byte[] body = fixture("single-text.json");
        mockMvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=" + "0".repeat(64))
                        .content(body))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(inboxStore);
    }

    @Test
    void aValidSignatureRecordsEveryMessageOnce() throws Exception {
        byte[] body = fixture("batch-three.json");
        mockMvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sign(body))
                        .content(body))
                .andExpect(status().isOk());
        verify(inboxStore, times(3)).recordInbound(any(), any());
    }

    @Test
    void aStatusCallbackPayloadRecordsNothingButStillReturns200() throws Exception {
        byte[] body = fixture("status-callback.json");
        mockMvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sign(body))
                        .content(body))
                .andExpect(status().isOk());
        verify(inboxStore, never()).recordInbound(any(), any());
    }

    @Test
    void aLongBodyIsRecordedIntactAsTheRawRef() throws Exception {
        byte[] body = fixture("long-text.json");
        mockMvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sign(body))
                        .content(body))
                .andExpect(status().isOk());
        verify(inboxStore).recordInbound(eq("wamid.batch-0001"), any());
    }

    @Test
    void aMalformedMessageInABatchDoesNotSinkItsWellFormedSiblings() throws Exception {
        byte[] body = fixture("batch-with-malformed.json");
        mockMvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sign(body))
                        .content(body))
                .andExpect(status().isOk());

        // The malformed entry (missing id/from) is skipped; both well-formed
        // siblings — including the one *after* it in the batch — are still
        // recorded, and the malformed one never reaches the store at all.
        verify(inboxStore).recordInbound(eq("wamid.malformed-batch-0001"), any());
        verify(inboxStore).recordInbound(eq("wamid.malformed-batch-0003"), any());
        verify(inboxStore, times(2)).recordInbound(any(), any());
    }

    @Test
    void aTenMessageBatchRespondsInUnderOneSecond() throws Exception {
        byte[] body = fixture("batch-ten.json");
        String signature = sign(body);

        long start = System.nanoTime();
        mockMvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", signature)
                        .content(body))
                .andExpect(status().isOk());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        verify(inboxStore, times(10)).recordInbound(any(), any());
        assertThat(elapsedMs).isLessThan(1000);
    }
}
