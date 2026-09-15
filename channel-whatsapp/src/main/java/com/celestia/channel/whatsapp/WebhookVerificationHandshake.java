package com.celestia.channel.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * Meta's webhook GET setup handshake (FR-001): {@code hub.mode=subscribe} +
 * a matching {@code hub.verify_token} echoes {@code hub.challenge} back;
 * anything else is rejected, and the challenge is never echoed on a mismatch.
 */
public final class WebhookVerificationHandshake {

    private WebhookVerificationHandshake() {}

    public static Optional<String> challenge(String mode, String token, String challenge, String expectedToken) {
        if (mode == null || token == null || challenge == null || expectedToken == null) {
            return Optional.empty();
        }
        if (!"subscribe".equals(mode)) {
            return Optional.empty();
        }
        // Constant-time comparison — defence in depth, even though this token
        // is lower-stakes than the webhook app secret.
        boolean matches = MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), expectedToken.getBytes(StandardCharsets.UTF_8));
        return matches ? Optional.of(challenge) : Optional.empty();
    }
}
