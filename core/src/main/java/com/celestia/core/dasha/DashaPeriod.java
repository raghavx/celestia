package com.celestia.core.dasha;

import com.celestia.ephemeris.Graha;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * One Vimshottari period at one level: its lord, its half-open time span, and the
 * chain of parent lords it sits under.
 *
 * @param level which level
 * @param lord the ruling graha
 * @param start inclusive
 * @param end exclusive
 * @param parentLords Mahadasha lord … immediate parent's lord; size
 *     {@code level.rank() - 1} (empty for {@link DashaLevel#MAHADASHA})
 */
public record DashaPeriod(
        DashaLevel level, Graha lord, Instant start, Instant end, List<Graha> parentLords) {

    public DashaPeriod {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("period start must be before end: " + start + " .. " + end);
        }
        if (parentLords.size() != level.rank() - 1) {
            throw new IllegalArgumentException(
                    "expected " + (level.rank() - 1) + " parent lords for " + level + ", got "
                            + parentLords.size());
        }
        parentLords = List.copyOf(parentLords);
    }

    /** Whether {@code t} falls in this period, half-open {@code [start, end)}. */
    public boolean contains(Instant t) {
        return !t.isBefore(start) && t.isBefore(end);
    }

    public Duration duration() {
        return Duration.between(start, end);
    }
}
