package com.celestia.agent.channel;

import com.celestia.agent.persistence.store.InboxStore;
import com.celestia.channel.whatsapp.InboundPayloadParser;
import com.celestia.channel.whatsapp.WebhookSignatureVerifier;
import com.celestia.channel.whatsapp.WebhookVerificationHandshake;
import com.celestia.channel.whatsapp.WhatsAppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /webhooks/whatsapp} — the front door (SPEC-009 US1/US2).
 *
 * <p>{@code GET} answers Meta's setup handshake (FR-001). {@code POST}
 * verifies the raw-body signature before any parsing (FR-002), records every
 * message it contains exactly once via {@link InboxStore} (FR-003), and
 * returns promptly — no reply-generation work happens here (FR-004); a
 * non-message payload (status callback, ping) is accepted and ignored
 * (FR-015).
 */
@RestController
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);
    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";

    private final WhatsAppConfig config;
    private final InboxStore inboxStore;

    public WhatsAppWebhookController(WhatsAppConfig config, InboxStore inboxStore) {
        this.config = config;
        this.inboxStore = inboxStore;
    }

    @GetMapping("/webhooks/whatsapp")
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {
        return WebhookVerificationHandshake.challenge(mode, token, challenge, config.verifyToken())
                .map(c -> ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(c))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @PostMapping(path = "/webhooks/whatsapp", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(
            @RequestBody byte[] rawBody, @RequestHeader(SIGNATURE_HEADER) String signature) {
        if (!WebhookSignatureVerifier.valid(rawBody, signature, config.appSecret())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        for (String messageJson : InboundPayloadParser.extractRawMessageJson(rawBody)) {
            // One malformed message must never sink its well-formed siblings in the
            // same delivery, nor turn this into a non-200 response Meta would retry
            // forever against the same payload (spec.md Edge Cases).
            try {
                String providerId = InboundPayloadParser.parseOne(messageJson).providerId();
                inboxStore.recordInbound(providerId, messageJson);
            } catch (RuntimeException malformed) {
                log.warn("skipping malformed inbound message: {}", malformed.toString());
            }
        }
        return ResponseEntity.ok().build();
    }
}
