package com.celestia.channel.whatsapp;

/** The outcome of {@link MessagingChannel#send}. Never thrown for a provider-side failure. */
public sealed interface SendResult permits SendResult.Sent, SendResult.Failed {

    record Sent(String providerMessageId) implements SendResult {
        public Sent {
            if (providerMessageId == null || providerMessageId.isBlank()) {
                throw new IllegalArgumentException("providerMessageId must not be blank");
            }
        }
    }

    /**
     * @param retryable {@code true} for a transient failure (network error, 5xx) that
     *     should be retried with backoff; {@code false} for a permanent one (4xx)
     */
    record Failed(String errorCode, String message, boolean retryable) implements SendResult {
        public Failed {
            errorCode = errorCode == null ? "" : errorCode;
            message = message == null ? "" : message;
        }
    }
}
