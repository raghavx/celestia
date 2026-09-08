package com.celestia.ephemeris;

/**
 * The sidereal reference used to convert tropical to sidereal longitude.
 *
 * <p>KP practice is committed to a single value (ADR-0003). {@link #KP_NEW} maps
 * to the Swiss Ephemeris constant {@code SE_SIDM_KRISHNAMURTI} (5) — not the
 * VP291 / "True KP" variant. Other ayanamsas are deliberately not offered
 * (FR-017); this enum exists so results can record which one produced them and to
 * leave room for a future spec.
 */
public enum Ayanamsa {
    /** Swiss Ephemeris {@code SE_SIDM_KRISHNAMURTI} (constant value 5). */
    KP_NEW
}
