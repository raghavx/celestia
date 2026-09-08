package com.celestia.ephemeris;

/**
 * One graha's state at one instant, in the sidereal (KP) frame.
 *
 * @param graha which body
 * @param longitude sidereal ecliptic longitude, degrees, in {@code [0, 360)}
 * @param latitude ecliptic latitude, degrees
 * @param speedPerDay change in longitude per day; negative means retrograde
 * @param accuracy {@link Accuracy#FULL} or {@link Accuracy#REDUCED}
 */
public record GrahaPosition(Graha graha, double longitude, double latitude, double speedPerDay, Accuracy accuracy) {

    public GrahaPosition {
        if (!(longitude >= 0.0 && longitude < 360.0)) {
            throw new IllegalArgumentException("longitude out of [0,360): " + longitude);
        }
    }

    /** {@code speedPerDay < 0}. */
    public boolean retrograde() {
        return speedPerDay < 0.0;
    }
}
