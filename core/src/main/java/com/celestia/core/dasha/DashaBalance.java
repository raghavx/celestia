package com.celestia.core.dasha;

import com.celestia.ephemeris.Graha;
import java.time.Duration;
import java.time.Instant;

/**
 * The balance of dasha at birth: which Mahadasha is running at the birth instant
 * (the lord of the Moon's nakshatra), how much of it has elapsed, and how much
 * remains. Source: {@code core/REFERENCES.md} (K. S. Krishnamurti, <i>KP
 * Readers</i>).
 *
 * @param mahaLord lord of the Moon's nakshatra at birth
 * @param elapsedFraction fraction of the Mahadasha already run at birth, {@code [0, 1)}
 * @param elapsed {@code elapsedFraction × mahaLord.years()} (365.25-day years)
 * @param balance the remaining Mahadasha at birth
 * @param mahaStart when this Mahadasha began — <b>before</b> the birth instant
 * @param mahaEnd when it ends ({@code birthInstant + balance})
 */
public record DashaBalance(
        Graha mahaLord, double elapsedFraction, Duration elapsed, Duration balance,
        Instant mahaStart, Instant mahaEnd) {

    public DashaBalance {
        if (!(elapsedFraction >= 0.0 && elapsedFraction < 1.0)) {
            throw new IllegalArgumentException("elapsedFraction out of [0,1): " + elapsedFraction);
        }
        if (!mahaStart.isBefore(mahaEnd)) {
            throw new IllegalArgumentException("mahaStart must be before mahaEnd");
        }
    }
}
