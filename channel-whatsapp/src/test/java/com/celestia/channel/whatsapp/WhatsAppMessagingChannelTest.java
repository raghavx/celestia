package com.celestia.channel.whatsapp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.channel.whatsapp.OutboundMessage.InteractiveListMessage;
import com.celestia.channel.whatsapp.OutboundMessage.ListRow;
import com.celestia.channel.whatsapp.OutboundMessage.ListSection;
import com.celestia.channel.whatsapp.OutboundMessage.TemplateMessage;
import com.celestia.channel.whatsapp.OutboundMessage.TemplateParam;
import com.celestia.channel.whatsapp.OutboundMessage.TextMessage;
import com.celestia.channel.whatsapp.graphapi.GraphApiClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** SPEC-009 US4/US5 — the Graph API client, against a stubbed provider. */
class WhatsAppMessagingChannelTest {

    private WireMockServer wireMock;
    private WhatsAppMessagingChannel channel;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(0);
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());

        WhatsAppConfig config = new WhatsAppConfig(
                "pnid", "http://localhost:" + wireMock.port(), "v21.0", "test-token", "secret",
                "verify-token", null);
        channel = new WhatsAppMessagingChannel(new GraphApiClient(config), WhatsAppTemplateCatalog.loadFromClasspath());
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    private void stubSuccess() {
        wireMock.stubFor(WireMock.post(urlEqualTo("/v21.0/pnid/messages"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"messages\":[{\"id\":\"wamid.sent-0001\"}]}")));
    }

    @Test
    void aTextMessageProducesTheDocumentedGraphApiRequestAndReturnsSent() {
        stubSuccess();
        SendResult result = channel.send(new TextMessage("+919876543210", "hello there"));

        assertThat(result).isEqualTo(new SendResult.Sent("wamid.sent-0001"));
        verify(postRequestedFor(urlEqualTo("/v21.0/pnid/messages"))
                .withRequestBody(equalToJson(
                        "{\"messaging_product\":\"whatsapp\",\"to\":\"919876543210\","
                                + "\"type\":\"text\",\"text\":{\"body\":\"hello there\"}}",
                        true, false)));
    }

    @Test
    void aTemplateMessageResolvesTheKeyToTheCatalogEntry() {
        stubSuccess();
        SendResult result = channel.send(
                new TemplateMessage("+919876543210", "generic-reply", List.of(new TemplateParam("p1", "v1"))));

        assertThat(result).isInstanceOf(SendResult.Sent.class);
        verify(postRequestedFor(urlEqualTo("/v21.0/pnid/messages"))
                .withRequestBody(equalToJson(
                        "{\"messaging_product\":\"whatsapp\",\"to\":\"919876543210\",\"type\":\"template\","
                                + "\"template\":{\"name\":\"celestia_generic_reply\",\"language\":{\"code\":\"en\"},"
                                + "\"components\":[{\"type\":\"body\",\"parameters\":[{\"type\":\"text\",\"text\":\"v1\"}]}]}}",
                        true, false)));
    }

    @Test
    void anUnknownTemplateKeyFailsWithoutCallingTheProvider() {
        SendResult result = channel.send(new TemplateMessage("+1", "no-such-template", List.of()));
        assertThat(result).isInstanceOf(SendResult.Failed.class);
        assertThat(((SendResult.Failed) result).retryable()).isFalse();
        wireMock.verify(0, WireMock.postRequestedFor(urlEqualTo("/v21.0/pnid/messages")));
    }

    @Test
    void anInteractiveListMessageProducesTheDocumentedShape() {
        stubSuccess();
        var section = new ListSection("Cities", List.of(new ListRow("pune", "Pune", "Maharashtra")));
        SendResult result = channel.send(
                new InteractiveListMessage("+919876543210", "Pick a city", "Which one?", List.of(section)));

        assertThat(result).isInstanceOf(SendResult.Sent.class);
        verify(postRequestedFor(urlEqualTo("/v21.0/pnid/messages"))
                .withRequestBody(equalToJson(
                        "{\"messaging_product\":\"whatsapp\",\"to\":\"919876543210\",\"type\":\"interactive\","
                                + "\"interactive\":{\"type\":\"list\",\"header\":{\"type\":\"text\",\"text\":\"Pick a city\"},"
                                + "\"body\":{\"text\":\"Which one?\"},\"action\":{\"button\":\"Choose\",\"sections\":"
                                + "[{\"title\":\"Cities\",\"rows\":[{\"id\":\"pune\",\"title\":\"Pune\","
                                + "\"description\":\"Maharashtra\"}]}]}}}",
                        true, false)));
    }

    @Test
    void a4xxResponseIsAPermanentFailure() {
        wireMock.stubFor(WireMock.post(urlEqualTo("/v21.0/pnid/messages"))
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"message\":\"Invalid recipient\"}}")));

        SendResult result = channel.send(new TextMessage("+1", "hi"));
        assertThat(result).isInstanceOf(SendResult.Failed.class);
        var failed = (SendResult.Failed) result;
        assertThat(failed.retryable()).isFalse();
        assertThat(failed.message()).contains("Invalid recipient");
    }

    @Test
    void a5xxResponseIsARetryableFailure() {
        wireMock.stubFor(WireMock.post(urlEqualTo("/v21.0/pnid/messages"))
                .willReturn(aResponse().withStatus(503)));

        SendResult result = channel.send(new TextMessage("+1", "hi"));
        assertThat(result).isInstanceOf(SendResult.Failed.class);
        assertThat(((SendResult.Failed) result).retryable()).isTrue();
    }
}
