package com.celestia.channel.whatsapp;

import java.time.Instant;

/**
 * One message extracted from a WhatsApp webhook delivery — provider-agnostic,
 * the shape {@code ReplyHandler} and the worker deal in.
 *
 * @param providerId the WhatsApp message id ({@code wamid...}); the idempotency key
 * @param fromE164 the sender's phone number
 */
public record InboundMessage(String providerId, String fromE164, Instant timestamp, MessageContent content) {

    public InboundMessage {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        if (fromE164 == null || fromE164.isBlank()) {
            throw new IllegalArgumentException("fromE164 must not be blank");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
    }
}
