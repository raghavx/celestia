package com.celestia.channel.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class WindowPolicyTest {

    @Test
    void windowExpiresExactly24hAfterTheLastInboundMessage() {
        Instant last = Instant.parse("2026-01-01T00:00:00Z");
        assertThat(WindowPolicy.windowExpiresAt(last)).isEqualTo(Instant.parse("2026-01-02T00:00:00Z"));
    }

    @Test
    void isWithinWindowIsStrictAtTheBoundary() {
        Instant expiresAt = Instant.parse("2026-01-02T00:00:00Z");
        assertThat(WindowPolicy.isWithinWindow(expiresAt.minusSeconds(1), expiresAt)).isTrue();
        assertThat(WindowPolicy.isWithinWindow(expiresAt, expiresAt)).isFalse();
        assertThat(WindowPolicy.isWithinWindow(expiresAt.plusSeconds(1), expiresAt)).isFalse();
    }

    @Test
    void justMessagedIsWithinWindow() {
        Instant now = Instant.now();
        Instant expiresAt = WindowPolicy.windowExpiresAt(now);
        assertThat(WindowPolicy.isWithinWindow(now, expiresAt)).isTrue();
    }

    @Test
    void thirtyHoursSinceLastInboundIsOutsideTheWindow() {
        Instant last = Instant.parse("2026-01-01T00:00:00Z");
        Instant expiresAt = WindowPolicy.windowExpiresAt(last);
        Instant now = last.plus(30, ChronoUnit.HOURS);
        assertThat(WindowPolicy.isWithinWindow(now, expiresAt)).isFalse();
    }
}
