package com.celestia.channel.whatsapp;

/**
 * The provider-agnostic messaging port (Constitution IX). {@code agent}
 * orchestration code holds only this interface and {@link OutboundMessage} /
 * {@link SendResult} — never a Graph API type directly
 * ({@code com.celestia.channel.whatsapp.graphapi} is internal to this module).
 */
public interface MessagingChannel {

    /**
     * Sends {@code message}. Never throws for a provider-side failure — those
     * come back as {@link SendResult.Failed}. Exceptions are reserved for
     * programmer errors (e.g. an unresolvable template key).
     */
    SendResult send(OutboundMessage message);
}
