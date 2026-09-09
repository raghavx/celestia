package com.celestia.core.horary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.core.chart.NatalChart;
import com.celestia.core.judgement.SignificatorTable;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.Graha;
import com.celestia.ephemeris.PlacidusUndefinedException;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHoraryHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.Test;

/** SPEC-005 US3: the full horary chart. */
@EnabledIf("hasEphemerisData")
class HoraryChartFactoryTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    private final HoraryChartFactory factory = new HoraryChartFactory(
            new SwissEphemerisPositionProvider(), new SwissEphemerisHoraryHouseProvider());

    private static final BirthData DELHI =
            new BirthData(Instant.parse("2026-01-01T12:00:00Z"), 28.6139, 77.209);

    @Test
    void cuspOneIsTheNumbersAscendantNotTheClocks() {
        NatalChart chart = factory.cast(100, DELHI);
        assertThat(chart.cusp(1).longitude()).isEqualTo(HoraryChartFactory.ascendant(100).longitude());
        assertThat(chart.ascendant().longitude()).isEqualTo(chart.cusp(1).longitude());
    }

    @Test
    void sameNumberAndPlaceFiveMinutesApartMovesOnlyTheFastPlanets() {
        NatalChart a = factory.cast(100, DELHI);
        NatalChart b = factory.cast(100,
                new BirthData(DELHI.instant().plus(Duration.ofMinutes(5)), DELHI.latitude(), DELHI.longitude()));

        for (int h = 1; h <= 12; h++) {
            // fixed by the number + latitude; only the slowly-varying obliquity /
            // ayanamsa move them, ~1e-8° over five minutes (well below KP resolution)
            assertThat(b.cusp(h).longitude()).as("cusp %d effectively unchanged", h)
                    .isCloseTo(a.cusp(h).longitude(), org.assertj.core.data.Offset.offset(1e-5));
        }
        double moonMove = Math.abs(b.position(Graha.MOON).longitude() - a.position(Graha.MOON).longitude()) * 60.0;
        // the Moon covers ~2.7'–3.4' in 5 minutes depending on its distance
        assertThat(moonMove).as("Moon moves its 5-minute arc").isBetween(2.5, 3.5);
    }

    @Test
    void sameMomentDifferentNumbersMoveOnlyTheCusps() {
        NatalChart a = factory.cast(50, DELHI);
        NatalChart b = factory.cast(200, DELHI);
        for (Graha g : Graha.values()) {
            assertThat(b.position(g).longitude()).isEqualTo(a.position(g).longitude());
        }
        assertThat(b.cusp(1).longitude()).isNotEqualTo(a.cusp(1).longitude());
    }

    @Test
    void polarJudgmentIsRejected() {
        BirthData polar = new BirthData(Instant.parse("2026-01-01T12:00:00Z"), 70.0, 15.0);
        assertThatThrownBy(() -> factory.cast(100, polar))
                .isInstanceOf(PlacidusUndefinedException.class);
    }

    @Test
    void rejectsNumbersOutOfRange() {
        assertThatThrownBy(() -> factory.cast(0, DELHI)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.cast(250, DELHI)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theHoraryChartIsAValidNatalChartForTheSignificators() {
        NatalChart chart = factory.cast(100, DELHI);
        SignificatorTable table = SignificatorTable.of(chart);
        for (int h = 1; h <= 12; h++) {
            assertThat(table.houseSignificators(h).significators()).isNotEmpty();
        }
    }
}
