package com.celestia.channel.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.channel.whatsapp.MessageContent.TextContent;
import com.celestia.channel.whatsapp.MessageContent.UnsupportedContent;
import com.celestia.channel.whatsapp.OutboundMessage.InteractiveListMessage;
import com.celestia.channel.whatsapp.OutboundMessage.ListRow;
import com.celestia.channel.whatsapp.OutboundMessage.ListSection;
import com.celestia.channel.whatsapp.OutboundMessage.TemplateMessage;
import com.celestia.channel.whatsapp.OutboundMessage.TextMessage;
import com.celestia.channel.whatsapp.SendResult.Failed;
import com.celestia.channel.whatsapp.SendResult.Sent;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FoundationalTypesTest {

    @Test
    void textMessageRejectsBlankFields() {
        assertThatThrownBy(() -> new TextMessage(" ", "hi")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TextMessage("+91987654321", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new TextMessage("+91987654321", "hi").to()).isEqualTo("+91987654321");
    }

    @Test
    void interactiveListMessageRejectsEmptySectionsAndRows() {
        assertThatThrownBy(() -> new InteractiveListMessage("+1", "h", "b", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ListSection("t", List.of())).isInstanceOf(IllegalArgumentException.class);
        var row = new ListRow("id1", "Title", null);
        assertThat(row.description()).isEmpty();
        var section = new ListSection("Section", List.of(row));
        var msg = new InteractiveListMessage("+1", "header", "body", List.of(section));
        assertThat(msg.sections()).hasSize(1);
    }

    @Test
    void templateMessageDefaultsEmptyParams() {
        var msg = new TemplateMessage("+1", "generic-reply", null);
        assertThat(msg.params()).isEmpty();
        assertThatThrownBy(() -> new TemplateMessage("+1", " ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendResultVariants() {
        assertThatThrownBy(() -> new Sent(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Sent("wamid.123").providerMessageId()).isEqualTo("wamid.123");
        Failed f = new Failed(null, null, true);
        assertThat(f.errorCode()).isEmpty();
        assertThat(f.retryable()).isTrue();
    }

    @Test
    void inboundMessageRejectsBlankOrNullFields() {
        assertThatThrownBy(() -> new InboundMessage(" ", "+1", Instant.now(), new TextContent("hi")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InboundMessage("id", "+1", null, new TextContent("hi")))
                .isInstanceOf(IllegalArgumentException.class);
        var m = new InboundMessage("wamid.1", "+1", Instant.EPOCH, new UnsupportedContent("image"));
        assertThat(m.content()).isInstanceOf(UnsupportedContent.class);
    }

    @Test
    void unsupportedContentRejectsBlankType() {
        assertThatThrownBy(() -> new UnsupportedContent(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
