package com.celestia.ephemeris;

/**
 * Whether a position was computed at full ephemeris accuracy.
 *
 * <p>{@link #FULL} for instants inside the supported ephemeris range (1800-2100,
 * ADR-0002); {@link #REDUCED} outside it, where the analytical (Moshier) model is
 * used instead. An out-of-range instant is never an error (FR-016).
 */
public enum Accuracy {
    FULL,
    REDUCED
}
