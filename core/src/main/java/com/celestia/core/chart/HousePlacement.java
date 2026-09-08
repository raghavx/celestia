package com.celestia.core.chart;

import com.celestia.ephemeris.Graha;

/**
 * Where one graha sits by house.
 *
 * @param graha the graha
 * @param bhava KP house (1..12), determined by which two cusps it lies between
 *     (cusp-to-cusp, half-open) — the KP judgement view
 * @param rasiHouse sign-based house (1..12), whole signs from the Ascendant's sign
 */
public record HousePlacement(Graha graha, int bhava, int rasiHouse) {

    public HousePlacement {
        if (bhava < 1 || bhava > 12) {
            throw new IllegalArgumentException("bhava out of 1..12: " + bhava);
        }
        if (rasiHouse < 1 || rasiHouse > 12) {
            throw new IllegalArgumentException("rasiHouse out of 1..12: " + rasiHouse);
        }
    }
}
