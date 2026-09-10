package com.celestia.core.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.core.chart.NatalChart;
import com.celestia.core.chart.NatalChartFactory;
import com.celestia.ephemeris.Accuracy;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.Test;

/** SPEC-006 US4: the factory wiring. */
@EnabledIf("hasEphemerisData")
class DailyPredictionFactoryTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    private final SwissEphemerisPositionProvider positions = new SwissEphemerisPositionProvider();
    private final DailyPredictionFactory factory = new DailyPredictionFactory(positions);

    private NatalChart obama() {
        return new NatalChartFactory(positions, new SwissEphemerisHouseProvider())
                .cast(new BirthData(Instant.parse("1961-08-05T05:24:00Z"), 21.30694, -157.85833));
    }

    @Test
    void rejectsADateBeforeTheBirthDate() {
        assertThatThrownBy(() -> factory.predict(obama(), LocalDate.of(1950, 1, 1), -157.858))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theReferenceInstantIsLocalNoonOfTheDate() {
        LocalDate date = LocalDate.of(2001, 8, 5);
        double longitude = -157.858;
        var prediction = factory.predict(obama(), date, longitude);

        Instant expected = date.atTime(12, 0).toInstant(ZoneOffset.UTC)
                .minusSeconds(Math.round(longitude / 15.0 * 3600.0));
        assertThat(prediction.referenceInstant()).isEqualTo(expected);
    }

    @Test
    void aFarFutureDateStillReadsWithReducedAccuracy() {
        var prediction = factory.predict(obama(), LocalDate.of(2400, 6, 1), -157.858);
        assertThat(prediction.accuracy()).isEqualTo(Accuracy.REDUCED);
        assertThat(prediction.verdicts()).hasSize(Matter.values().length);
    }

    @Test
    void isDeterministic() {
        LocalDate date = LocalDate.of(2001, 8, 5);
        assertThat(factory.predict(obama(), date, -157.858))
                .isEqualTo(factory.predict(obama(), date, -157.858));
    }
}
