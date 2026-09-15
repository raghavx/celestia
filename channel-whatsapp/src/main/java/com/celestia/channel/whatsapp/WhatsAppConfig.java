package com.celestia.channel.whatsapp;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WhatsApp Business Cloud API configuration — every field comes from the
 * environment (FR-012); none is ever literal in a committed file. Registered
 * via {@code @EnableConfigurationProperties(WhatsAppConfig.class)} in
 * {@code agent}'s {@code ChannelConfig}.
 *
 * @param phoneNumberId the sending number's Graph API phone number id
 * @param graphApiBaseUrl e.g. {@code https://graph.facebook.com}
 * @param graphApiVersion e.g. {@code v21.0} — a config change, not a code change
 * @param accessToken the Graph API bearer token
 * @param appSecret the webhook HMAC secret (FR-002)
 * @param verifyToken the webhook GET handshake secret (FR-001)
 */
@ConfigurationProperties("whatsapp")
public record WhatsAppConfig(
        String phoneNumberId,
        String graphApiBaseUrl,
        String graphApiVersion,
        String accessToken,
        String appSecret,
        String verifyToken,
        Channel channel) {

    /**
     * @param pollInterval how often the worker/sender loops tick
     * @param maxAttempts attempts before an inbound/outbound item is marked permanently failed
     * @param reclaimAfter how long a {@code CLAIMED} row may sit before the reclaim sweep resets it
     */
    public record Channel(Duration pollInterval, int maxAttempts, Duration reclaimAfter) {}
}
