package com.celestia.core.judgement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.ephemeris.Graha;
import java.time.DayOfWeek;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FoundationalTypesTest {

    @Test
    void stepRanksAreOneToFourStrongestFirst() {
        assertThat(Step.STAR_OF_OCCUPANT.rank()).isEqualTo(1);
        assertThat(Step.OCCUPANT.rank()).isEqualTo(2);
        assertThat(Step.STAR_OF_OWNER.rank()).isEqualTo(3);
        assertThat(Step.OWNER.rank()).isEqualTo(4);
    }

    @Test
    void significatorRejectsEmptyStepsAndOutOfRangeHouse() {
        assertThatThrownBy(() -> new Significator(Graha.SUN, 5, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Significator(Graha.SUN, 13, Set.of(Step.OWNER)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void significatorStrongestStepAndOrdering() {
        var a = new Significator(Graha.SUN, 1, Set.of(Step.OCCUPANT, Step.OWNER));
        assertThat(a.strongestStep()).isEqualTo(Step.OCCUPANT);

        var b = new Significator(Graha.KETU, 1, Set.of(Step.STAR_OF_OCCUPANT));
        assertThat(Significator.BY_STRENGTH.compare(b, a)).isNegative(); // step 1 before step 2
    }

    @Test
    void kpWeekdayMapsWeekdaysToDayLords() {
        assertThat(KpWeekday.SUNDAY.lord()).isEqualTo(Graha.SUN);
        assertThat(KpWeekday.MONDAY.lord()).isEqualTo(Graha.MOON);
        assertThat(KpWeekday.TUESDAY.lord()).isEqualTo(Graha.MARS);
        assertThat(KpWeekday.WEDNESDAY.lord()).isEqualTo(Graha.MERCURY);
        assertThat(KpWeekday.THURSDAY.lord()).isEqualTo(Graha.JUPITER);
        assertThat(KpWeekday.FRIDAY.lord()).isEqualTo(Graha.VENUS);
        assertThat(KpWeekday.SATURDAY.lord()).isEqualTo(Graha.SATURN);
    }

    @Test
    void kpWeekdayOfDayOfWeekRoundTrips() {
        for (DayOfWeek dow : DayOfWeek.values()) {
            assertThat(KpWeekday.of(dow).name()).isEqualTo(dow.name());
        }
    }

    @Test
    void nodeAgencyRejectsNonNodes() {
        assertThatThrownBy(() -> new NodeAgency(Graha.SUN, Set.of(), Graha.MARS, Graha.VENUS, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
