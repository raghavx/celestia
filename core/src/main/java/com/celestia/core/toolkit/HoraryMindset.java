package com.celestia.core.toolkit;

import com.celestia.ephemeris.Graha;

/**
 * One row of the toolkit's horary 1–249 table, with its mindset reading
 * (SPEC-010 FR-006).
 *
 * <p>The sign, star and three lords are cross-checked against the engine's own
 * {@code Horary249} table at build time: 249 of 249 must agree lord for lord
 * (SC-003, Constitution III). The {@link #mindset} is the toolkit's own
 * contribution — the state of mind the number indicates — and has no engine
 * equivalent.
 *
 * @param number 1..249
 * @param sign the sign the arc falls in, as the workbook spells it
 * @param star the nakshatra, as the workbook spells it
 * @param fromDegree the arc's start, as written
 * @param toDegree the arc's end, as written
 * @param signLord ruler of the sign
 * @param starLord ruler of the nakshatra
 * @param subLord the sub lord — the quantity KP horary judges
 * @param mindset the toolkit's mindset reading for this number
 */
public record HoraryMindset(
        int number,
        String sign,
        String star,
        String fromDegree,
        String toDegree,
        Graha signLord,
        Graha starLord,
        Graha subLord,
        String mindset) {

    public HoraryMindset {
        if (number < 1 || number > 249) {
            throw new IllegalArgumentException("horary number out of 1..249: " + number);
        }
    }
}
