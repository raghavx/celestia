package com.celestia.ephemeris;

/**
 * House cusps from a <b>given Ascendant longitude</b>, for KP horary — where the
 * Ascendant is fixed by the querent's number 1–249, not by the clock.
 *
 * <p>Contract (see
 * {@code specs/005-kp-horary/contracts/horary-houses-api.md}):
 *
 * <ul>
 *   <li>Cusp 1 of the returned {@link HouseResult} is bit-identical to
 *       {@code ascendantLongitude}.</li>
 *   <li>The other eleven cusps are <b>Placidus</b>, sidereal, consistent with that
 *       Ascendant at {@code judgment.latitude()} for the judgment instant's
 *       obliquity and ayanamsa: the right ascension of the MC that produces the
 *       Ascendant is found by a closed-form inversion of the Ascendant equation,
 *       the tropical cusps are computed from it, then converted back to
 *       sidereal.</li>
 *   <li>{@code birthData()} of the result is {@code judgment} even though the
 *       cusps are Ascendant-seeded rather than instant-seeded — this
 *       {@link HouseResult} is only meaningful as a horary chart's houses.</li>
 *   <li>{@code |judgment.latitude()| >=} the polar limit &rarr;
 *       {@link PlacidusUndefinedException}, as for a natal chart.</li>
 *   <li>Deterministic, thread-safe; no Swiss Ephemeris type in this API.</li>
 * </ul>
 */
public interface HoraryHouseProvider {

    /**
     * @param judgment non-null; supplies the instant (&rarr; obliquity, ayanamsa)
     *     and the latitude / longitude of the judgment moment
     * @param ascendantLongitude sidereal (KP-New) longitude to place at cusp 1;
     *     normalised into {@code [0, 360)}
     */
    HouseResult housesFor(BirthData judgment, double ascendantLongitude);
}
