package com.celestia.core.dasha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.ephemeris.Graha;
import org.apache.commons.numbers.fraction.BigFraction;
import org.junit.jupiter.api.Test;

class SpanTest {

    @Test
    void containsIsHalfOpen() {
        Span s = new Span(Graha.KETU, BigFraction.of(10), BigFraction.of(20));
        assertThat(s.contains(10.0)).isTrue();
        assertThat(s.contains(15.0)).isTrue();
        assertThat(s.contains(19.999999)).isTrue();
        assertThat(s.contains(20.0)).isFalse();
        assertThat(s.contains(9.999999)).isFalse();
    }

    @Test
    void exposesDoubleViewsAndExactWidth() {
        Span s = new Span(Graha.VENUS, BigFraction.of(40, 3), BigFraction.of(60, 3));
        assertThat(s.startDeg()).isEqualTo(40.0 / 3.0);
        assertThat(s.endDeg()).isEqualTo(20.0);
        assertThat(s.width()).isEqualTo(BigFraction.of(20, 3));
    }

    @Test
    void rejectsNonPositiveWidth() {
        assertThatThrownBy(() -> new Span(Graha.SUN, BigFraction.of(5), BigFraction.of(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Span(Graha.SUN, BigFraction.of(5), BigFraction.of(4)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
