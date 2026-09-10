package com.celestia.core.prediction;

import com.celestia.core.chart.NatalChart;
import com.celestia.core.dasha.DashaTimeline;
import com.celestia.core.judgement.SignificatorTable;
import com.celestia.ephemeris.EphemerisResult;
import com.celestia.ephemeris.Graha;
import com.celestia.ephemeris.PositionProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the v1 KP {@link DailyPrediction} for a natal chart and a date. The
 * natal side ({@code SignificatorTable}, {@code DashaTimeline}) is pure; the only
 * I/O is fetching the transiting Moon / Sun. Contract:
 * {@code specs/006-daily-prediction/contracts/daily-prediction-api.md}.
 */
public final class DailyPredictionFactory {

    private final PositionProvider positions;

    public DailyPredictionFactory(PositionProvider positions) {
        this.positions = positions;
    }

    /**
     * The daily reading for {@code chart} on {@code date}, evaluated at local noon
     * ({@code date 12:00} minus {@code longitude/15 h}, {@code research.md} §4).
     *
     * @param longitude the east-positive longitude of the place the reading is for
     *     on that date — where the querent is, not necessarily the birth place;
     *     used only to place the local-noon reference instant
     * @throws IllegalArgumentException if {@code date} is before the birth date
     */
    public DailyPrediction predict(NatalChart chart, LocalDate date, double longitude) {
        Instant birthInstant = chart.birthData().instant();
        LocalDate birthDate = birthInstant.atOffset(ZoneOffset.UTC).toLocalDate();

        long lmtOffsetSeconds = Math.round(longitude / 15.0 * 3600.0);
        Instant referenceInstant = date.atTime(12, 0)
                .toInstant(ZoneOffset.UTC)
                .minusSeconds(lmtOffsetSeconds);

        if (date.isBefore(birthDate) || referenceInstant.isBefore(birthInstant)) {
            throw new IllegalArgumentException(
                    "date " + date + " is before the birth date " + birthDate);
        }

        EphemerisResult atReference = positions.positions(referenceInstant);
        EphemerisResult twelveBefore = positions.positions(referenceInstant.minus(12, ChronoUnit.HOURS));
        EphemerisResult twelveAfter = positions.positions(referenceInstant.plus(12, ChronoUnit.HOURS));

        return compute(chart, referenceInstant, atReference, twelveBefore, twelveAfter);
    }

    /** Pure: the reading from already-resolved transit positions. */
    public static DailyPrediction compute(
            NatalChart chart, Instant referenceInstant,
            EphemerisResult atReference, EphemerisResult twelveHoursBefore,
            EphemerisResult twelveHoursAfter) {

        SignificatorTable table = SignificatorTable.of(chart);
        DashaTimeline timeline = DashaTimeline.from(
                chart.birthData().instant(), chart.position(Graha.MOON).longitude(),
                chart.accuracy(), chart.engineVersion());

        DashaSignificators dasha = DashaSignificators.compute(table, timeline, referenceInstant);
        TransitContribution transit = TransitContribution.compute(
                table,
                atReference.position(Graha.MOON).longitude(),
                atReference.position(Graha.SUN).longitude(),
                twelveHoursBefore.position(Graha.MOON).longitude(),
                twelveHoursAfter.position(Graha.MOON).longitude());

        List<MatterVerdict> verdicts = new ArrayList<>();
        for (Matter matter : Matter.values()) {
            verdicts.add(VerdictRule.evaluate(matter, dasha, transit));
        }

        return new DailyPrediction(
                referenceInstant, dasha, transit, verdicts,
                atReference.position(Graha.MOON).accuracy(), atReference.engineVersion());
    }
}
