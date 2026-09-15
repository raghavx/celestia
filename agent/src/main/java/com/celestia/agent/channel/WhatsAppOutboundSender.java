package com.celestia.agent.channel;

import com.celestia.agent.persistence.store.OutboxStore;
import com.celestia.agent.persistence.store.OutboxStore.ClaimedMessage;
import com.celestia.agent.persistence.store.WhatsAppWindowStore;
import com.celestia.channel.whatsapp.MessagingChannel;
import com.celestia.channel.whatsapp.OutboundMessage;
import com.celestia.channel.whatsapp.OutboundMessage.TemplateMessage;
import com.celestia.channel.whatsapp.SendResult;
import com.celestia.channel.whatsapp.SendResult.Failed;
import com.celestia.channel.whatsapp.SendResult.Sent;
import com.celestia.channel.whatsapp.WhatsAppConfig;
import com.celestia.channel.whatsapp.WindowPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SPEC-009 US4 — claims pending outbox rows and hands each to the
 * {@link MessagingChannel}, honouring the 24h customer-service window
 * (FR-008/FR-009): a free-form reply is sent as-is inside the window; outside
 * it, it is downgraded to the {@code generic-reply} template (ADR-0012).
 */
@Component
public class WhatsAppOutboundSender {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppOutboundSender.class);
    private static final String FALLBACK_TEMPLATE_KEY = "generic-reply";

    private final OutboxStore outboxStore;
    private final WhatsAppWindowStore windowStore;
    private final OutboundMessageCodec codec;
    private final MessagingChannel messagingChannel;
    private final WhatsAppConfig config;

    public WhatsAppOutboundSender(OutboxStore outboxStore, WhatsAppWindowStore windowStore,
            OutboundMessageCodec codec, MessagingChannel messagingChannel, WhatsAppConfig config) {
        this.outboxStore = outboxStore;
        this.windowStore = windowStore;
        this.codec = codec;
        this.messagingChannel = messagingChannel;
        this.config = config;
    }

    @Scheduled(fixedDelayString = "${whatsapp.channel.poll-interval}")
    public void tick() {
        outboxStore.reclaimStale(config.channel().reclaimAfter(), config.channel().maxAttempts());
        Optional<ClaimedMessage> next;
        while ((next = outboxStore.claimNextPending()).isPresent()) {
            processOne(next.get());
        }
    }

    void processOne(ClaimedMessage claimed) {
        try {
            OutboundMessage message = codec.read(claimed.payloadJson());
            OutboundMessage toSend = withinWindow(claimed.userId())
                    ? message
                    : asTemplateFallback(message, claimed.target());
            SendResult result = messagingChannel.send(toSend);
            handle(claimed, result);
        } catch (RuntimeException e) {
            log.warn("failed to send outbox message {}: {}", claimed.id(), e.toString());
            outboxStore.markFailed(claimed.id(), e.getMessage());
        }
    }

    private void handle(ClaimedMessage claimed, SendResult result) {
        switch (result) {
            case Sent s -> outboxStore.markSent(claimed.id(), s.providerMessageId());
            case Failed f -> {
                if (f.retryable() && claimed.attempts() < config.channel().maxAttempts()) {
                    outboxStore.markRetry(claimed.id(), f.message());
                } else {
                    outboxStore.markFailed(claimed.id(), f.message());
                }
            }
        }
    }

    private boolean withinWindow(Long userId) {
        if (userId == null) {
            return false;
        }
        return windowStore.windowExpiresAt(userId)
                .map(expiresAt -> WindowPolicy.isWithinWindow(Instant.now(), expiresAt))
                .orElse(false);
    }

    private static OutboundMessage asTemplateFallback(OutboundMessage message, String target) {
        if (message instanceof TemplateMessage) {
            return message; // templates are always window-independent
        }
        return new TemplateMessage(target, FALLBACK_TEMPLATE_KEY, List.of());
    }
}
