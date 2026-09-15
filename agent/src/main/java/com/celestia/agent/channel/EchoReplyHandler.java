package com.celestia.agent.channel;

import com.celestia.agent.persistence.store.AppUserRef;
import com.celestia.channel.whatsapp.InboundMessage;
import com.celestia.channel.whatsapp.MessageContent.TextContent;
import com.celestia.channel.whatsapp.OutboundMessage;
import com.celestia.channel.whatsapp.OutboundMessage.TextMessage;
import org.springframework.stereotype.Component;

/**
 * v1's {@link ReplyHandler}: a canned acknowledgement, proving the channel
 * round trip end-to-end (Phase 4 exit criterion). Not the real product — see
 * spec.md Assumptions.
 */
@Component
public class EchoReplyHandler implements ReplyHandler {

    private static final String KNOWN_TEXT = "Thanks for your message! Celestia is still getting set up — "
            + "we'll be able to answer your questions properly very soon.";

    private static final String UNSUPPORTED = "Thanks for your message! Right now Celestia can only read "
            + "text messages — please reply with text for now.";

    @Override
    public OutboundMessage reply(AppUserRef user, InboundMessage inbound) {
        String body = inbound.content() instanceof TextContent ? KNOWN_TEXT : UNSUPPORTED;
        return new TextMessage(inbound.fromE164(), body);
    }
}
