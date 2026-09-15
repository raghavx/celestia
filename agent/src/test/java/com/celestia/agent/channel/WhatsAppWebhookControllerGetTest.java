package com.celestia.agent.channel;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.celestia.agent.persistence.store.InboxStore;
import com.celestia.channel.whatsapp.WhatsAppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** SPEC-009 US1 — the GET handshake, at the HTTP layer. */
class WhatsAppWebhookControllerGetTest {

    private static final String TOKEN = "verify-me";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        WhatsAppConfig config = new WhatsAppConfig(
                "pnid", "https://graph.facebook.com", "v21.0", "token", "secret", TOKEN, null);
        var controller = new WhatsAppWebhookController(config, mock(InboxStore.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void matchingTokenEchoesTheChallenge() throws Exception {
        mockMvc.perform(get("/webhooks/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", TOKEN)
                        .param("hub.challenge", "abc123"))
                .andExpect(status().isOk())
                .andExpect(content().string("abc123"));
    }

    @Test
    void wrongTokenIsRejectedWithNoChallengeInTheBody() throws Exception {
        mockMvc.perform(get("/webhooks/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "wrong")
                        .param("hub.challenge", "abc123"))
                .andExpect(status().is4xxClientError())
                .andExpect(content().string(""));
    }

    @Test
    void nonSubscribeModeIsRejected() throws Exception {
        mockMvc.perform(get("/webhooks/whatsapp")
                        .param("hub.mode", "unsubscribe")
                        .param("hub.verify_token", TOKEN)
                        .param("hub.challenge", "abc123"))
                .andExpect(status().is4xxClientError());
    }
}
