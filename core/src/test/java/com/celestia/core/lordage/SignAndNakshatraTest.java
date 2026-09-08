package com.celestia.core.lordage;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.ephemeris.Graha;
import org.junit.jupiter.api.Test;

class SignAndNakshatraTest {

    @Test
    void signSpansAndRulerships() {
        assertThat(Sign.values()).hasSize(12);
        assertThat(Sign.ARIES.startLongitude()).isEqualTo(0.0);
        assertThat(Sign.PISCES.startLongitude()).isEqualTo(330.0);
        assertThat(Sign.ARIES.lord()).isEqualTo(Graha.MARS);
        assertThat(Sign.LEO.lord()).isEqualTo(Graha.SUN);
        assertThat(Sign.CANCER.lord()).isEqualTo(Graha.MOON);
        assertThat(Sign.CAPRICORN.lord()).isEqualTo(Graha.SATURN);
        assertThat(Sign.AQUARIUS.lord()).isEqualTo(Graha.SATURN);
        assertThat(Sign.PISCES.lord()).isEqualTo(Graha.JUPITER);
    }

    @Test
    void signAtIsHalfOpen() {
        assertThat(Sign.at(0.0)).isEqualTo(Sign.ARIES);
        assertThat(Sign.at(29.999)).isEqualTo(Sign.ARIES);
        assertThat(Sign.at(30.0)).isEqualTo(Sign.TAURUS);
        assertThat(Sign.at(359.999)).isEqualTo(Sign.PISCES);
    }

    @Test
    void nakshatraSpansAndLords() {
        assertThat(Nakshatra.values()).hasSize(27);
        assertThat(Nakshatra.SPAN_DEGREES).isEqualTo(40.0 / 3.0);
        assertThat(Nakshatra.ASHWINI.lord()).isEqualTo(Graha.KETU);
        assertThat(Nakshatra.BHARANI.lord()).isEqualTo(Graha.VENUS);
        assertThat(Nakshatra.KRITTIKA.lord()).isEqualTo(Graha.SUN);
        assertThat(Nakshatra.ROHINI.lord()).isEqualTo(Graha.MOON);
        assertThat(Nakshatra.MAGHA.lord()).isEqualTo(Graha.KETU);
        assertThat(Nakshatra.MULA.lord()).isEqualTo(Graha.KETU);
        assertThat(Nakshatra.REVATI.lord()).isEqualTo(Graha.MERCURY);
    }

    @Test
    void nakshatraAtAndPadaAreHalfOpen() {
        assertThat(Nakshatra.at(0.0)).isEqualTo(Nakshatra.ASHWINI);
        assertThat(Nakshatra.padaAt(0.0)).isEqualTo(1);
        assertThat(Nakshatra.padaAt(Nakshatra.PADA_DEGREES - 1e-9)).isEqualTo(1);
        assertThat(Nakshatra.padaAt(Nakshatra.PADA_DEGREES + 1e-9)).isEqualTo(2);
        // just inside each nakshatra: correct nakshatra, pada 1
        for (Nakshatra n : Nakshatra.values()) {
            double justInside = n.startLongitude() + 1e-6;
            assertThat(Nakshatra.at(justInside)).as("just inside %s", n).isEqualTo(n);
            assertThat(Nakshatra.padaAt(justInside)).as("pada just inside %s", n).isEqualTo(1);
            // just before the next boundary: pada 4
            double justBefore = n.startLongitude() + Nakshatra.SPAN_DEGREES - 1e-6;
            assertThat(Nakshatra.padaAt(justBefore)).as("pada near end of %s", n).isEqualTo(4);
        }
    }

    @Test
    void exactSignBoundariesResolveToHigherSign() {
        // multiples of 30 are exact doubles; the half-open rule sends them up
        for (int i = 0; i < 12; i++) {
            assertThat(Sign.at(i * 30.0)).isEqualTo(Sign.values()[i]);
        }
    }
}
