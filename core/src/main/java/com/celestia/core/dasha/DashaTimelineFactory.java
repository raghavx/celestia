package com.celestia.core.dasha;

import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.EphemerisResult;
import com.celestia.ephemeris.Graha;
import com.celestia.ephemeris.GrahaPosition;
import com.celestia.ephemeris.PositionProvider;

/**
 * Builds a {@link DashaTimeline} for a birth, resolving the Moon from a
 * {@link PositionProvider}. Contract:
 * {@code specs/004-vimshottari-dasha/contracts/dasha-api.md}.
 */
public final class DashaTimelineFactory {

    private final PositionProvider positions;

    public DashaTimelineFactory(PositionProvider positions) {
        this.positions = positions;
    }

    /**
     * The dasha timeline for {@code birth}. Only the birth instant and the Moon's
     * sidereal longitude matter; {@code birth}'s coordinates are unused.
     */
    public DashaTimeline at(BirthData birth) {
        EphemerisResult ephemeris = positions.positions(birth.instant());
        GrahaPosition moon = ephemeris.position(Graha.MOON);
        return DashaTimeline.from(
                birth.instant(), moon.longitude(), moon.accuracy(), ephemeris.engineVersion());
    }
}
