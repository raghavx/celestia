package com.celestia.core.dasha;

import com.celestia.ephemeris.Graha;
import java.time.Instant;
import java.util.List;

/**
 * The stack of Vimshottari periods active at one instant — Mahadasha first, then
 * one entry per deeper level down to the requested depth.
 *
 * @param instant the query instant
 * @param stack index 0 = Mahadasha … index {@code depth() - 1}; each period
 *     {@link DashaPeriod#contains(Instant) contains} {@code instant}, and each is
 *     nested in the previous
 */
public record RunningDasha(Instant instant, List<DashaPeriod> stack) {

    public RunningDasha {
        if (stack.isEmpty() || stack.size() > 5) {
            throw new IllegalArgumentException("stack size must be 1..5, got " + stack.size());
        }
        for (int i = 0; i < stack.size(); i++) {
            DashaPeriod p = stack.get(i);
            if (p.level().rank() != i + 1) {
                throw new IllegalArgumentException(
                        "stack[" + i + "] is " + p.level() + ", expected rank " + (i + 1));
            }
            if (!p.contains(instant)) {
                throw new IllegalArgumentException(p.level() + " does not contain " + instant);
            }
            if (i > 0) {
                DashaPeriod parent = stack.get(i - 1);
                if (p.start().isBefore(parent.start()) || p.end().isAfter(parent.end())) {
                    throw new IllegalArgumentException(p.level() + " is not nested in its parent");
                }
            }
        }
        stack = List.copyOf(stack);
    }

    public int depth() {
        return stack.size();
    }

    /** The period at {@code level}. */
    public DashaPeriod period(DashaLevel level) {
        if (level.rank() > stack.size()) {
            throw new IllegalArgumentException(level + " is deeper than the resolved depth " + depth());
        }
        return stack.get(level.rank() - 1);
    }

    public Graha lord(DashaLevel level) {
        return period(level).lord();
    }
}
