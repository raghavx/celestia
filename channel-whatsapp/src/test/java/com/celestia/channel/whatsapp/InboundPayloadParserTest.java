package com.celestia.channel.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class InboundPayloadParserTest {

    private static byte[] fixture(String name) throws IOException {
        Path p = Path.of("src/test/resources/whatsapp/payloads/" + name);
        return Files.readAllBytes(p);
    }

    @Test
    void singleTextMessageParsesToOneInboundMessage() throws IOException {
        List<InboundMessage> messages = InboundPayloadParser.parseAll(fixture("single-text.json"));
        assertThat(messages).hasSize(1);
        InboundMessage m = messages.get(0);
        assertThat(m.providerId()).isEqualTo("wamid.single-text-0001");
        assertThat(m.fromE164()).isEqualTo("+919876543210");
        assertThat(m.content()).isEqualTo(new MessageContent.TextContent("hello"));
    }

    @Test
    void batchOfThreeParsesToThreeIndependentlyReparseableMessages() throws IOException {
        byte[] body = fixture("batch-three.json");
        List<InboundMessage> messages = InboundPayloadParser.parseAll(body);
        List<String> rawJson = InboundPayloadParser.extractRawMessageJson(body);
        assertThat(messages).hasSize(3);
        assertThat(rawJson).hasSize(3);

        for (int i = 0; i < 3; i++) {
            InboundMessage reparsed = InboundPayloadParser.parseOne(rawJson.get(i));
            assertThat(reparsed).isEqualTo(messages.get(i));
        }
    }

    @Test
    void aLongBodyNearTheWhatsappLimitIsPreservedExactly() throws IOException {
        List<InboundMessage> messages = InboundPayloadParser.parseAll(fixture("long-text.json"));
        assertThat(messages).hasSize(1);
        MessageContent.TextContent content = (MessageContent.TextContent) messages.get(0).content();
        assertThat(content.body()).hasSize(4096);

        // Round-trips through the raw_ref extraction + re-parse too (H1).
        String raw = InboundPayloadParser.extractRawMessageJson(fixture("long-text.json")).get(0);
        assertThat(InboundPayloadParser.parseOne(raw).content()).isEqualTo(content);
    }

    @Test
    void statusCallbackPayloadYieldsNoMessages() throws IOException {
        assertThat(InboundPayloadParser.parseAll(fixture("status-callback.json"))).isEmpty();
        assertThat(InboundPayloadParser.extractRawMessageJson(fixture("status-callback.json"))).isEmpty();
    }

    @Test
    void unsupportedTypeParsesWithUnsupportedContent() throws IOException {
        List<InboundMessage> messages = InboundPayloadParser.parseAll(fixture("unsupported-type.json"));
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).content()).isEqualTo(new MessageContent.UnsupportedContent("image"));
    }

    @Test
    void garbageBodyYieldsNoMessagesNotAnException() {
        assertThat(InboundPayloadParser.parseAll("not json".getBytes())).isEmpty();
    }
}
