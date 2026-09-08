package com.celestia.core.lordage;

import com.celestia.ephemeris.Graha;

/**
 * The full KP lordage of one ecliptic longitude.
 *
 * @param longitude the normalised input, degrees in {@code [0, 360)}
 * @param sign the zodiac sign
 * @param signLord ruler of {@code sign}
 * @param nakshatra the nakshatra
 * @param pada 1-4
 * @param starLord ruler (Vimshottari lord) of {@code nakshatra}
 * @param subLord Vimshottari sub lord
 * @param subSubLord Vimshottari sub-sub lord
 */
public record LordChain(
        double longitude,
        Sign sign,
        Graha signLord,
        Nakshatra nakshatra,
        int pada,
        Graha starLord,
        Graha subLord,
        Graha subSubLord) {}
