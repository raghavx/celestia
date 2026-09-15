package com.celestia.agent.channel;

import com.celestia.agent.persistence.store.AppUserRef;
import com.celestia.agent.persistence.store.InboxStore;
import com.celestia.agent.persistence.store.OutboxStore;
import com.celestia.agent.persistence.store.UserAccountService;
import com.celestia.agent.persistence.store.WhatsAppWindowStore;
import com.celestia.channel.whatsapp.InboundMessage;
import com.celestia.channel.whatsapp.InboundPayloadParser;
import com.celestia.channel.whatsapp.OutboundMessage;
import com.celestia.channel.whatsapp.WhatsAppConfig;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SPEC-009 US3 — claims recorded-but-unprocessed inbound messages off the
 * webhook thread and queues a reply for each (FR-005/FR-006/FR-007).
 */
@Component
public class WhatsAppInboundWorker {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppInboundWorker.class);

    private final InboxStore inboxStore;
    private final OutboxStore outboxStore;
    private final UserAccountService userAccountService;
    private final WhatsAppWindowStore windowStore;
    private final ReplyHandler replyHandler;
    private final OutboundMessageCodec codec;
    private final WhatsAppConfig config;

    public WhatsAppInboundWorker(InboxStore inboxStore, OutboxStore outboxStore,
            UserAccountService userAccountService, WhatsAppWindowStore windowStore,
            ReplyHandler replyHandler, OutboundMessageCodec codec, WhatsAppConfig config) {
        this.inboxStore = inboxStore;
        this.outboxStore = outboxStore;
        this.userAccountService = userAccountService;
        this.windowStore = windowStore;
        this.replyHandler = replyHandler;
        this.codec = codec;
        this.config = config;
    }

    @Scheduled(fixedDelayString = "${whatsapp.channel.poll-interval}")
    public void tick() {
        inboxStore.reclaimStale(config.channel().reclaimAfter(), config.channel().maxAttempts());
        Optional<InboxStore.ClaimedEvent> next;
        while ((next = inboxStore.claimNext()).isPresent()) {
            processOne(next.get());
        }
    }

    void processOne(InboxStore.ClaimedEvent claimed) {
        try {
            InboundMessage inbound = InboundPayloadParser.parseOne(claimed.rawRef());
            AppUserRef user = userAccountService.findOrCreate(inbound.fromE164());
            windowStore.recordInbound(user.id(), inbound.timestamp());
            OutboundMessage reply = replyHandler.reply(user, inbound);
            outboxStore.enqueue(user.id(), inbound.fromE164(), codec.write(reply));
            inboxStore.markProcessed(claimed.id());
        } catch (RuntimeException e) {
            log.warn("failed to process inbound event {}: {}", claimed.id(), e.toString());
            inboxStore.markFailed(claimed.id(), e.getMessage());
        }
    }
}
