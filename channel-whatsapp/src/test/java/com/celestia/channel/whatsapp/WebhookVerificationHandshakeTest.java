package com.celestia.channel.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebhookVerificationHandshakeTest {

    private static final String EXPECTED = "my-verify-token";

    @Test
    void matchingSubscribeReturnsTheChallenge() {
        assertThat(WebhookVerificationHandshake.challenge("subscribe", EXPECTED, "abc123", EXPECTED))
                .contains("abc123");
    }

    @Test
    void wrongTokenIsRejected() {
        assertThat(WebhookVerificationHandshake.challenge("subscribe", "wrong", "abc123", EXPECTED))
                .isEmpty();
    }

    @Test
    void nonSubscribeModeIsRejected() {
        assertThat(WebhookVerificationHandshake.challenge("unsubscribe", EXPECTED, "abc123", EXPECTED))
                .isEmpty();
        assertThat(WebhookVerificationHandshake.challenge("", EXPECTED, "abc123", EXPECTED))
                .isEmpty();
    }

    @Test
    void nullFieldsAreRejectedNotThrown() {
        assertThat(WebhookVerificationHandshake.challenge(null, EXPECTED, "abc123", EXPECTED))
                .isEmpty();
        assertThat(WebhookVerificationHandshake.challenge("subscribe", null, "abc123", EXPECTED))
                .isEmpty();
        assertThat(WebhookVerificationHandshake.challenge("subscribe", EXPECTED, null, EXPECTED))
                .isEmpty();
    }

    @Test
    void aDifferentLengthTokenIsRejected() {
        // Exercises the constant-time path with mismatched lengths too.
        assertThat(WebhookVerificationHandshake.challenge("subscribe", "short", "abc123", EXPECTED))
                .isEmpty();
    }
}
