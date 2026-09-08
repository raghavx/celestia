package com.celestia.core.chart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.core.lordage.Sign;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BhavasTest {

    /** 12 equal houses starting at 5°: cusps 5, 35, 65, … 335. */
    private static final List<Double> EQUAL_RING =
            IntStream.range(0, 12).mapToObj(i -> 5.0 + i * 30.0).toList();

    @Test
    void assignsEachDegreeToExactlyOneBhava() {
        for (double lon = 0.0; lon < 360.0; lon += 0.25) {
            int b = Bhavas.bhavaOf(lon, EQUAL_RING);
            assertThat(b).isBetween(1, 12);
        }
    }

    @Test
    void halfOpenAtAnExactCusp() {
        assertThat(Bhavas.bhavaOf(5.0, EQUAL_RING)).isEqualTo(1);   // cusp 1 starts bhava 1
        assertThat(Bhavas.bhavaOf(35.0, EQUAL_RING)).isEqualTo(2);  // cusp 2 starts bhava 2
        assertThat(Bhavas.bhavaOf(34.999, EQUAL_RING)).isEqualTo(1);
    }

    @Test
    void wrapsAcrossZero() {
        // house 12 is [335, 5); 350 and 2 are both in it
        assertThat(Bhavas.bhavaOf(350.0, EQUAL_RING)).isEqualTo(12);
        assertThat(Bhavas.bhavaOf(2.0, EQUAL_RING)).isEqualTo(12);
        assertThat(Bhavas.bhavaOf(4.999, EQUAL_RING)).isEqualTo(12);
    }

    @Test
    void handlesTwoCloseCusps() {
        // cusp 2 and 3 only 1° apart (an intercepted-sign-like situation)
        List<Double> ring = List.of(0.0, 40.0, 41.0, 90.0, 120.0, 150.0, 180.0, 220.0, 221.0, 270.0, 300.0, 330.0);
        assertThat(Bhavas.bhavaOf(40.5, ring)).isEqualTo(2); // in the narrow arc
        assertThat(Bhavas.bhavaOf(41.5, ring)).isEqualTo(3);
        assertThat(Bhavas.bhavaOf(39.5, ring)).isEqualTo(1);
    }

    @Test
    void rasiHouseCountsWholeSignsFromTheAscendant() {
        assertThat(Bhavas.rasiHouseOf(Sign.LEO, Sign.LEO)).isEqualTo(1);
        assertThat(Bhavas.rasiHouseOf(Sign.VIRGO, Sign.LEO)).isEqualTo(2);
        assertThat(Bhavas.rasiHouseOf(Sign.LIBRA, Sign.LEO)).isEqualTo(3);
        assertThat(Bhavas.rasiHouseOf(Sign.CANCER, Sign.LEO)).isEqualTo(12);
        assertThat(Bhavas.rasiHouseOf(Sign.GEMINI, Sign.LEO)).isEqualTo(11);
    }

    @Test
    void rejectsWrongCuspCount() {
        assertThatThrownBy(() -> Bhavas.bhavaOf(10.0, List.of(0.0, 30.0, 60.0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
