package com.celestia.channel.whatsapp;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * The WhatsApp 24-hour customer-service window (ADR-0012, FR-008): free-form
 * replies are only allowed inside it; a template is required outside it.
 */
public final class WindowPolicy {

    private WindowPolicy() {}

    public static Instant windowExpiresAt(Instant lastInboundAt) {
        return lastInboundAt.plus(24, ChronoUnit.HOURS);
    }

    /** Half-open: the instant of expiry itself is already outside the window. */
    public static boolean isWithinWindow(Instant now, Instant expiresAt) {
        return now.isBefore(expiresAt);
    }
}
