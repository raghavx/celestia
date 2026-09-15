package com.celestia.agent.channel;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.agent.persistence.store.AppUserRef;
import com.celestia.channel.whatsapp.InboundMessage;
import com.celestia.channel.whatsapp.MessageContent.TextContent;
import com.celestia.channel.whatsapp.MessageContent.UnsupportedContent;
import com.celestia.channel.whatsapp.OutboundMessage.TextMessage;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EchoReplyHandlerTest {

    private final EchoReplyHandler handler = new EchoReplyHandler();
    private final AppUserRef user = new AppUserRef(1L, "+919876543210", false);

    @Test
    void aTextMessageGetsANonBlankAcknowledgement() {
        InboundMessage m = new InboundMessage("wamid.1", "+919876543210", Instant.EPOCH,
                new TextContent("hello"));
        var reply = (TextMessage) handler.reply(user, m);
        assertThat(reply.to()).isEqualTo("+919876543210");
        assertThat(reply.body()).isNotBlank();
    }

    @Test
    void anUnsupportedMessageStillGetsAReply_notAnException() {
        InboundMessage m = new InboundMessage("wamid.2", "+919876543210", Instant.EPOCH,
                new UnsupportedContent("image"));
        var reply = (TextMessage) handler.reply(user, m);
        assertThat(reply.body()).isNotBlank();
    }
}
