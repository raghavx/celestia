package com.celestia.core.lordage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LongitudesTest {

    @Test
    void normalizesIntoHalfOpenCircle() {
        assertThat(Longitudes.normalize(0.0)).isEqualTo(0.0);
        assertThat(Longitudes.normalize(360.0)).isEqualTo(0.0);
        assertThat(Longitudes.normalize(-5.0)).isEqualTo(355.0);
        assertThat(Longitudes.normalize(365.0)).isEqualTo(5.0);
        assertThat(Longitudes.normalize(720.0)).isEqualTo(0.0);
        assertThat(Longitudes.normalize(-360.0)).isEqualTo(0.0);
        assertThat(Longitudes.normalize(123.456)).isEqualTo(123.456);
    }

    @Test
    void normalizeNeverReturnsNegativeZero() {
        assertThat(Double.doubleToRawLongBits(Longitudes.normalize(-360.0)))
                .isEqualTo(Double.doubleToRawLongBits(0.0));
    }

    @Test
    void rejectsNonFiniteInput() {
        assertThatThrownBy(() -> Longitudes.normalize(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Longitudes.normalize(Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Longitudes.normalize(Double.NEGATIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
