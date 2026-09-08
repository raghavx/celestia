package com.celestia.ephemeris;

import java.time.Instant;

/**
 * The inputs a natal chart needs: a moment and a place.
 *
 * <p>Converting a place name to coordinates and a local civil time to UTC are
 * SPEC-007's job; {@code BirthData} is what the ephemeris consumes.
 *
 * @param instant a non-null instant on the UTC timeline
 * @param latitude geographic latitude, decimal degrees, + = North; {@code |latitude| <= 90}
 * @param longitude geographic longitude, decimal degrees, + = East; {@code |longitude| <= 180}
 */
public record BirthData(Instant instant, double latitude, double longitude) {

    public BirthData {
        if (instant == null) {
            throw new IllegalArgumentException("instant must not be null");
        }
        if (!(Math.abs(latitude) <= 90.0)) {
            throw new IllegalArgumentException("latitude out of [-90, 90]: " + latitude);
        }
        if (!(Math.abs(longitude) <= 180.0)) {
            throw new IllegalArgumentException("longitude out of [-180, 180]: " + longitude);
        }
    }
}
